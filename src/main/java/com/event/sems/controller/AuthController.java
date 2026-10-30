package com.event.sems.controller;

import com.event.sems.entity.UserAccount;
import com.event.sems.config.JwtService;
import com.event.sems.service.AccountRecoveryService;
import com.event.sems.service.AccountService;
import com.event.sems.service.EmailConfirmationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class AuthController {
  private final AccountService accounts;
  private final AccountRecoveryService recovery;
  private final EmailConfirmationService confirmations;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwt;

  public AuthController(AccountService accounts, AccountRecoveryService recovery,
      EmailConfirmationService confirmations, AuthenticationManager authenticationManager,
      JwtService jwt) {
    this.accounts = accounts;
    this.recovery = recovery;
    this.confirmations = confirmations;
    this.authenticationManager = authenticationManager;
    this.jwt = jwt;
  }

  @PostMapping("/api/v1/auth/login")
  public Map<String, Object> login(@RequestParam String username, @RequestParam String password) {
    try {
      Authentication auth = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(username, password));
      return Map.of("username", auth.getName(), "roles", auth.getAuthorities(),
          "token", jwt.issue(auth.getName()), "tokenType", "Bearer");
    } catch (AuthenticationException ex) {
      throw new com.event.sems.common.ApiException(HttpStatus.UNAUTHORIZED,
          "Sai tài khoản, mật khẩu hoặc tài khoản đã bị khóa.");
    }
  }

  @PostMapping("/api/v1/auth/logout")
  public Map<String, String> logout(JwtAuthenticationToken auth) {
    if (auth != null) jwt.revoke(auth.getToken());
    return Map.of("message", "Đã đăng xuất.");
  }

  @GetMapping("/api/v1/me")
  public Map<String, Object> me(Authentication auth) {
    UserAccount user = accounts.get(auth.getName());
    return Map.of(
        "username", auth.getName(),
        "displayName", user.getDisplayName(),
        "email", user.getEmail() == null ? "" : user.getEmail(),
        "emailVerified", user.isEmailVerified(),
        "roles", auth.getAuthorities());
  }

  @PostMapping("/api/v1/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public EmailConfirmationService.StartResult register(@Valid @RequestBody NewAttendee input) {
    return confirmations.register(
        input.username(), input.displayName(), input.email(), input.password());
  }

  @PostMapping("/api/v1/auth/confirm-registration")
  public Map<String, String> confirmRegistration(@Valid @RequestBody CodeInput input) {
    confirmations.confirmRegistration(input.requestId(), input.code());
    return Map.of("message", "Email đã được xác minh. Tài khoản đã được tạo.");
  }

  @PostMapping("/api/v1/auth/resend-registration-code")
  public Map<String, String> resendRegistrationCode(@Valid @RequestBody RequestIdInput input) {
    confirmations.resend(input.requestId(), null);
    return Map.of("message", "Đã gửi mã mới đến email của bạn.");
  }

  @GetMapping("/api/v1/admin/users")
  public List<UserView> list() {
    return accounts.list().stream().map(UserView::from).toList();
  }

  @PostMapping("/api/v1/admin/users")
  @ResponseStatus(HttpStatus.CREATED)
  public UserView createStaff(@Valid @RequestBody NewStaff input) {
    return UserView.from(
        accounts.createStaff(
            input.username(),
            input.displayName(),
            input.email(),
            input.password(),
            input.role() == null ? "STAFF" : input.role()));
  }

  @PatchMapping("/api/v1/admin/users/{id}/active")
  public UserView setActive(@PathVariable Long id, @RequestBody ActiveInput input) {
    return UserView.from(accounts.setStaffActive(id, input.active()));
  }

  @PostMapping("/api/v1/admin/users/{id}/reset-password")
  public Map<String, String> resetPassword(
      @PathVariable Long id, @Valid @RequestBody PasswordInput input) {
    accounts.resetStaffPassword(id, input.password());
    return Map.of("message", "Đã đặt lại mật khẩu.");
  }

  @PostMapping("/api/v1/account/change-password")
  public Map<String, String> changePassword(
      Authentication auth, @Valid @RequestBody ChangePasswordInput input) {
    accounts.changeOwnPassword(auth.getName(), input.currentPassword(), input.newPassword());
    return Map.of("message", "Đã đổi mật khẩu.");
  }

  @PostMapping("/api/v1/account/email")
  public EmailConfirmationService.StartResult changeEmail(
      Authentication auth, @Valid @RequestBody ChangeEmailInput input) {
    return confirmations.changeEmail(auth.getName(), input.currentPassword(), input.email());
  }

  @PostMapping("/api/v1/account/confirm-email-change")
  public Map<String, String> confirmEmailChange(
      Authentication auth, @Valid @RequestBody CodeInput input) {
    confirmations.confirmEmailChange(auth.getName(), input.requestId(), input.code());
    return Map.of("message", "Email mới đã được xác minh và lưu vào tài khoản.");
  }

  @PostMapping("/api/v1/account/resend-email-change-code")
  public Map<String, String> resendEmailChangeCode(
      Authentication auth, @Valid @RequestBody RequestIdInput input) {
    confirmations.resend(input.requestId(), auth.getName());
    return Map.of("message", "Đã gửi mã mới đến email của bạn.");
  }

  @PostMapping("/api/v1/account/profile")
  public UserView changeProfile(Authentication auth, @Valid @RequestBody ProfileInput input) {
    return UserView.from(accounts.changeOwnDisplayName(auth.getName(), input.displayName()));
  }

  @PostMapping("/api/v1/account/resend-verification")
  public Map<String, String> resendVerification(Authentication auth) {
    recovery.resendVerification(auth.getName());
    return Map.of(
        "message", "Nếu email đã được cấu hình, liên kết xác minh sẽ được gửi đến hộp thư.");
  }

  @PostMapping("/api/v1/auth/verify-email")
  public Map<String, String> verifyEmail(@Valid @RequestBody TokenInput input) {
    recovery.verify(input.token());
    return Map.of("message", "Email đã được xác minh.");
  }

  @PostMapping("/api/v1/auth/forgot-password")
  public Map<String, String> forgotPassword(@Valid @RequestBody EmailInput input) {
    recovery.requestReset(input.email());
    return Map.of(
        "message", "Nếu email đã được xác minh, bạn sẽ nhận được liên kết đặt lại mật khẩu.");
  }

  @PostMapping("/api/v1/auth/reset-password")
  public Map<String, String> resetOwnPassword(@Valid @RequestBody ResetPasswordInput input) {
    recovery.reset(input.token(), input.newPassword());
    return Map.of("message", "Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.");
  }

  public record NewStaff(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}") String username,
      @NotBlank @Size(max = 80) String displayName,
      @NotBlank @Email @Size(max = 190) String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      String role) {}

  public record NewAttendee(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}") String username,
      @NotBlank @Size(max = 80) String displayName,
      @NotBlank @Email @Size(max = 190) String email,
      @NotBlank @Size(min = 12, max = 128) String password) {}

  public record EmailInput(@NotBlank @Email String email) {}

  public record TokenInput(@NotBlank String token) {}

  public record RequestIdInput(@NotBlank String requestId) {}

  public record CodeInput(@NotBlank String requestId, @NotBlank String code) {}

  public record ResetPasswordInput(
      @NotBlank String token, @NotBlank @Size(min = 12, max = 128) String newPassword) {}

  public record ChangeEmailInput(
      @NotBlank String currentPassword, @NotBlank @Email @Size(max = 190) String email) {}

  public record ProfileInput(@NotBlank @Size(max = 80) String displayName) {}

  public record PasswordInput(@NotBlank @Size(min = 12, max = 128) String password) {}

  public record ChangePasswordInput(
      @NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 128) String newPassword) {}

  public record ActiveInput(boolean active) {}

  public record UserView(
      Long id,
      String username,
      String displayName,
      String email,
      boolean emailVerified,
      boolean active,
      List<String> roles) {
    static UserView from(UserAccount user) {
      return new UserView(
          user.getId(),
          user.getUsername(),
          user.getDisplayName(),
          user.getEmail(),
          user.isEmailVerified(),
          user.isActive(),
          user.getRoles().stream().map(role -> role.getName()).sorted().toList());
    }
  }
}
