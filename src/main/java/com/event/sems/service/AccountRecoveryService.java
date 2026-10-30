package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.entity.AccountToken;
import com.event.sems.entity.UserAccount;
import com.event.sems.repository.AccountTokenRepository;
import com.event.sems.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
  private final UserAccountRepository users;
  private final AccountTokenRepository tokens;
  private final TicketMailService mail;
  private final PasswordEncoder passwords;
  private final SecureRandom random = new SecureRandom();

  public boolean mailEnabled() {
    return mail.isEnabled();
  }

  @Transactional
  public void sendVerification(UserAccount user) {
    if (user.getEmail() == null || user.isEmailVerified() || !mail.isEnabled()) return;
    issue(user, "VERIFY_EMAIL", 30);
  }

  @Transactional
  public void resendVerification(String username) {
    UserAccount user = users.findByUsernameIgnoreCase(username).orElseThrow();
    if (user.getEmail() == null)
      throw new ApiException(HttpStatus.CONFLICT, "Hãy thêm email tài khoản trước.");
    if (user.isEmailVerified())
      throw new ApiException(HttpStatus.CONFLICT, "Email đã được xác minh.");
    if (!mail.isEnabled())
      throw new ApiException(HttpStatus.CONFLICT, "Hệ thống chưa bật gửi email.");
    issue(user, "VERIFY_EMAIL", 30);
  }

  @Transactional
  public void verify(String token) {
    AccountToken record = valid(token, "VERIFY_EMAIL");
    UserAccount user = record.getUser();
    if (!Objects.equals(user.getEmail(), record.getEmailAddress()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Liên kết xác minh không còn hợp lệ.");
    user.setEmailVerified(true);
    record.setUsedAt(TimeUtil.now());
  }

  @Transactional
  public void requestReset(String email) {
    if (!mail.isEnabled() || email == null) return;
    users
        .findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
        .filter(UserAccount::isActive)
        .filter(UserAccount::isEmailVerified)
        .ifPresent(user -> issue(user, "RESET_PASSWORD", 20));
  }

  @Transactional
  public void reset(String token, String newPassword) {
    AccountToken record = valid(token, "RESET_PASSWORD");
    UserAccount user = record.getUser();
    if (!user.isActive()
        || !user.isEmailVerified()
        || !Objects.equals(user.getEmail(), record.getEmailAddress()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Liên kết đặt lại không còn hợp lệ.");
    user.setPasswordHash(passwords.encode(newPassword));
    LocalDateTime now = TimeUtil.now();
    tokens
        .findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), "RESET_PASSWORD")
        .forEach(item -> item.setUsedAt(now));
    afterCommit(() -> mail.sendPasswordChanged(user.getEmail()));
  }

  private void issue(UserAccount user, String purpose, int minutes) {
    LocalDateTime now = TimeUtil.now();
    if (tokens.existsByUserIdAndPurposeAndEmailAddressAndCreatedAtAfterAndUsedAtIsNull(
        user.getId(), purpose, user.getEmail(), now.minusMinutes(2))) return;
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    AccountToken record = new AccountToken();
    record.setUser(user);
    record.setEmailAddress(user.getEmail());
    record.setTokenHash(hash(raw));
    record.setPurpose(purpose);
    record.setCreatedAt(now);
    record.setExpiresAt(now.plusMinutes(minutes));
    tokens.save(record);
    String address = user.getEmail();
    afterCommit(
        () -> {
          if ("VERIFY_EMAIL".equals(purpose)) mail.sendVerification(address, raw);
          else mail.sendPasswordReset(address, raw);
        });
  }

  private AccountToken valid(String raw, String purpose) {
    if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}"))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Liên kết không hợp lệ hoặc đã hết hạn.");
    AccountToken record =
        tokens
            .findByTokenHashAndPurpose(hash(raw), purpose)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.BAD_REQUEST, "Liên kết không hợp lệ hoặc đã hết hạn."));
    if (record.getUsedAt() != null || !TimeUtil.now().isBefore(record.getExpiresAt()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Liên kết không hợp lệ hoặc đã hết hạn.");
    return record;
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể xử lý mã xác minh.", ex);
    }
  }

  private void afterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
