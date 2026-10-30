package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.entity.PendingEmailAction;
import com.event.sems.entity.UserAccount;
import com.event.sems.repository.PendingEmailActionRepository;
import com.event.sems.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailConfirmationService {
  private static final String SIGNUP = "SIGNUP";
  private static final String CHANGE_EMAIL = "CHANGE_EMAIL";
  private final PendingEmailActionRepository pending;
  private final UserAccountRepository users;
  private final AccountService accounts;
  private final TicketMailService mail;
  private final PasswordEncoder passwords;
  private final SecureRandom random = new SecureRandom();

  public record StartResult(boolean verificationRequired, String requestId, String message) {}

  public StartResult register(String username, String displayName, String email, String password) {
    pending.deleteByExpiresAtBefore(TimeUtil.now());
    String address = normalize(email);
    if (users.existsByUsernameIgnoreCase(username.trim()) || users.existsByEmailIgnoreCase(address))
      throw new ApiException(HttpStatus.CONFLICT, "Tên tài khoản hoặc email đã được sử dụng.");
    if (!mail.isEnabled()) {
      accounts.createAttendee(username, displayName, address, password);
      return new StartResult(false, null, "Tài khoản đã được tạo.");
    }
    throttle(address);
    PendingEmailAction action = newAction(SIGNUP, address);
    action.setUsername(username.trim());
    action.setDisplayName(displayName.trim());
    action.setPasswordHash(passwords.encode(password));
    sendNewCode(action);
    return new StartResult(true, action.getRequestId(), "Đã gửi mã xác minh đến email của bạn.");
  }

  public void confirmRegistration(String requestId, String code) {
    PendingEmailAction action = checkCode(requestId, code, SIGNUP);
    accounts.createAttendeeWithHash(
        action.getUsername(), action.getDisplayName(), action.getEmail(), action.getPasswordHash());
    pending.delete(action);
  }

  public StartResult changeEmail(String username, String currentPassword, String email) {
    pending.deleteByExpiresAtBefore(TimeUtil.now());
    UserAccount user = accounts.get(username);
    if (!passwords.matches(currentPassword, user.getPasswordHash()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
    String address = normalize(email);
    if (address.equalsIgnoreCase(user.getEmail()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Email mới phải khác email hiện tại.");
    if (users.existsByEmailIgnoreCase(address))
      throw new ApiException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác.");
    if (!mail.isEnabled()) {
      accounts.changeOwnEmail(username, currentPassword, address);
      return new StartResult(false, null, "Đã đổi email tài khoản.");
    }
    throttle(address);
    PendingEmailAction action = newAction(CHANGE_EMAIL, address);
    action.setUserId(user.getId());
    action.setPreviousEmail(user.getEmail());
    sendNewCode(action);
    return new StartResult(true, action.getRequestId(), "Đã gửi mã xác minh đến email mới.");
  }

  public void confirmEmailChange(String username, String requestId, String code) {
    PendingEmailAction action = validRequest(requestId);
    UserAccount user = accounts.get(username);
    if (!CHANGE_EMAIL.equals(action.getPurpose()) || !user.getId().equals(action.getUserId()))
      throw new ApiException(HttpStatus.FORBIDDEN, "Yêu cầu đổi email không thuộc tài khoản này.");
    if (!user.getEmail().equalsIgnoreCase(action.getPreviousEmail()))
      throw new ApiException(HttpStatus.CONFLICT, "Email tài khoản đã thay đổi. Hãy thực hiện lại.");
    action = checkCode(requestId, code, CHANGE_EMAIL);
    accounts.completeEmailChange(user.getId(), action.getEmail());
    pending.delete(action);
  }

  public void resend(String requestId, String username) {
    PendingEmailAction action = validRequest(requestId);
    if (CHANGE_EMAIL.equals(action.getPurpose())) {
      if (username == null || !accounts.get(username).getId().equals(action.getUserId()))
        throw new ApiException(HttpStatus.FORBIDDEN, "Yêu cầu không thuộc tài khoản này.");
    } else if (!SIGNUP.equals(action.getPurpose()) || username != null) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Yêu cầu không hợp lệ.");
    }
    if (!mail.isEnabled())
      throw new ApiException(HttpStatus.CONFLICT, "Hệ thống chưa bật gửi email.");
    if (action.getLastSentAt().plusSeconds(60).isAfter(TimeUtil.now()))
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ 60 giây trước khi gửi lại.");
    sendNewCode(action);
  }

  private PendingEmailAction newAction(String purpose, String email) {
    PendingEmailAction action = new PendingEmailAction();
    action.setRequestId(UUID.randomUUID().toString());
    action.setPurpose(purpose);
    action.setEmail(email);
    action.setCreatedAt(TimeUtil.now());
    return action;
  }

  private void sendNewCode(PendingEmailAction action) {
    String code = "%06d".formatted(random.nextInt(1_000_000));
    // Gửi thất bại thì không lưu mã mới, để người dùng thử lại.
    mail.sendConfirmationCode(action.getEmail(), code);
    action.setCodeHash(hash(action.getRequestId(), code));
    action.setAttempts(0);
    action.setLastSentAt(TimeUtil.now());
    action.setExpiresAt(TimeUtil.now().plusMinutes(10));
    pending.saveAndFlush(action);
  }

  private PendingEmailAction checkCode(String requestId, String code, String purpose) {
    PendingEmailAction action = validRequest(requestId);
    if (!purpose.equals(action.getPurpose()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Yêu cầu xác minh không hợp lệ.");
    if (action.getAttempts() >= 5)
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai quá nhiều lần. Hãy gửi lại mã.");
    if (code == null || !code.matches("[0-9]{6}")
        || !MessageDigest.isEqual(
            action.getCodeHash().getBytes(StandardCharsets.US_ASCII),
            hash(requestId, code).getBytes(StandardCharsets.US_ASCII))) {
      action.setAttempts(action.getAttempts() + 1);
      pending.saveAndFlush(action);
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mã xác minh không đúng.");
    }
    return action;
  }

  private PendingEmailAction validRequest(String requestId) {
    if (requestId == null || !requestId.matches("[0-9a-fA-F-]{36}"))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Yêu cầu xác minh không hợp lệ.");
    PendingEmailAction action = pending.findById(requestId)
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Yêu cầu xác minh không còn hợp lệ."));
    if (!TimeUtil.now().isBefore(action.getExpiresAt()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mã đã hết hạn. Hãy bắt đầu lại.");
    return action;
  }

  private void throttle(String email) {
    if (pending.existsByEmailIgnoreCaseAndCreatedAtAfter(email, TimeUtil.now().minusSeconds(60)))
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ 60 giây trước khi yêu cầu mã mới.");
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String hash(String requestId, String code) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest((requestId + ":" + code).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể xử lý mã xác minh.", ex);
    }
  }
}
