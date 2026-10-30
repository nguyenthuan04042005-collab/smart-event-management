package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.entity.Role;
import com.event.sems.entity.UserAccount;
import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder passwords;
  private final AccountRecoveryService recovery;

  public AccountService(
      UserAccountRepository users,
      RoleRepository roles,
      PasswordEncoder passwords,
      AccountRecoveryService recovery) {
    this.users = users;
    this.roles = roles;
    this.passwords = passwords;
    this.recovery = recovery;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) {
    UserAccount account =
        users
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản."));
    String[] authorities =
        account.getRoles().stream().map(role -> "ROLE_" + role.getName()).toArray(String[]::new);
    return User.withUsername(account.getUsername())
        .password(account.getPasswordHash())
        .authorities(authorities)
        .disabled(!account.isActive())
        .build();
  }

  @Transactional(readOnly = true)
  public List<UserAccount> list() {
    return users.findAllWithRoles();
  }

  @Transactional(readOnly = true)
  public UserAccount get(String username) {
    return users.findByUsernameIgnoreCase(username).orElseThrow();
  }

  @Transactional
  public UserAccount createAttendee(
      String username, String displayName, String email, String password) {
    return createAttendeeWithHash(username, displayName, email, passwords.encode(password));
  }

  @Transactional
  public UserAccount createAttendeeWithHash(
      String username, String displayName, String email, String passwordHash) {
    if (users.existsByUsernameIgnoreCase(username)) {
      throw new ApiException(HttpStatus.CONFLICT, "Tên tài khoản đã tồn tại.");
    }
    Role attendee =
        roles
            .findByName("ATTENDEE")
            .orElseGet(
                () -> {
                  Role role = new Role();
                  role.setName("ATTENDEE");
                  return roles.save(role);
                });
    UserAccount account = new UserAccount();
    account.setUsername(username.trim());
    account.setDisplayName(displayName.trim());
    account.setEmail(uniqueEmail(email));
    account.setPasswordHash(passwordHash);
    account.setEmailVerified(true);
    account.getRoles().add(attendee);
    users.saveAndFlush(account);
    return account;
  }

  @Transactional
  public UserAccount createStaff(
      String username, String displayName, String email, String password, String roleName) {
    if (!List.of("STAFF", "ORGANIZER").contains(roleName))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ.");
    if (users.existsByUsernameIgnoreCase(username)) {
      throw new ApiException(HttpStatus.CONFLICT, "Tên tài khoản đã tồn tại.");
    }
    UserAccount staff = new UserAccount();
    staff.setUsername(username);
    staff.setDisplayName(displayName.trim());
    staff.setEmail(uniqueEmail(email));
    staff.setPasswordHash(passwords.encode(password));
    staff.setEmailVerified(!recovery.mailEnabled());
    staff.getRoles().add(roles.findByName(roleName).orElseThrow());
    users.saveAndFlush(staff);
    recovery.sendVerification(staff);
    return staff;
  }

  @Transactional
  public UserAccount changeOwnEmail(String username, String currentPassword, String email) {
    UserAccount user = get(username);
    if (!passwords.matches(currentPassword, user.getPasswordHash()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (!normalized.equals(user.getEmail())) {
      user.setEmail(uniqueEmail(normalized));
      user.setEmailVerified(true);
    }
    return user;
  }

  @Transactional
  public UserAccount completeEmailChange(Long userId, String email) {
    UserAccount user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
    if (!user.isActive())
      throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa.");
    user.setEmail(uniqueEmail(email));
    user.setEmailVerified(true);
    return user;
  }

  @Transactional
  public UserAccount changeOwnDisplayName(String username, String displayName) {
    UserAccount user = get(username);
    user.setDisplayName(displayName.trim());
    return user;
  }

  private String uniqueEmail(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmailIgnoreCase(normalized))
      throw new ApiException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác.");
    return normalized;
  }

  @Transactional
  public UserAccount setStaffActive(Long id, boolean active) {
    UserAccount staff = staff(id);
    staff.setActive(active);
    return staff;
  }

  @Transactional
  public void resetStaffPassword(Long id, String password) {
    staff(id).setPasswordHash(passwords.encode(password));
  }

  private UserAccount staff(Long id) {
    UserAccount user =
        users
            .findWithRolesById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
    if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ADMIN"))) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "Không thể sửa tài khoản quản trị qua chức năng này.");
    }
    return user;
  }

  @Transactional
  public void changeOwnPassword(String username, String currentPassword, String newPassword) {
    UserAccount user = users.findByUsernameIgnoreCase(username).orElseThrow();
    if (!passwords.matches(currentPassword, user.getPasswordHash())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
    }
    user.setPasswordHash(passwords.encode(newPassword));
  }
}
