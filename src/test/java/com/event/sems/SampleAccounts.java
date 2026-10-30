package com.event.sems;

import com.event.sems.entity.Role;
import com.event.sems.entity.UserAccount;
import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

final class SampleAccounts {
  private SampleAccounts() {}

  static void seed(RoleRepository roles, UserAccountRepository users, PasswordEncoder passwords) {
    Role adminRole = role(roles, "ADMIN");
    Role staffRole = role(roles, "STAFF");
    Role organizerRole = role(roles, "ORGANIZER");
    Role attendeeRole = role(roles, "ATTENDEE");
    account(users, passwords, adminRole, "admin", "Quản trị viên", "Admin@123456");
    account(users, passwords, staffRole, "staff", "Nhân viên check-in 1", "Staff@123456");
    account(users, passwords, staffRole, "staff2", "Nhân viên check-in 2", "Staff2@123456");
    account(users, passwords, organizerRole, "organizer", "Ban tổ chức", "Organizer@123456");
    account(users, passwords, organizerRole, "organizer2", "Ban tổ chức khác", "Organizer2@123456");
    account(users, passwords, attendeeRole, "visitor", "Người tham dự", "Visitor@123456");
    account(users, passwords, attendeeRole, "visitor2", "Người tham dự khác", "Visitor2@123456");
  }

  private static Role role(RoleRepository roles, String name) {
    return roles
        .findByName(name)
        .orElseGet(
            () -> {
              Role role = new Role();
              role.setName(name);
              return roles.save(role);
            });
  }

  private static void account(
      UserAccountRepository users,
      PasswordEncoder passwords,
      Role role,
      String username,
      String displayName,
      String password) {
    if (users.existsByUsernameIgnoreCase(username)) return;
    UserAccount account = new UserAccount();
    account.setUsername(username);
    account.setDisplayName(displayName);
    account.setEmail(username + "@sems.example");
    account.setEmailVerified(true);
    account.setPasswordHash(passwords.encode(password));
    account.getRoles().add(role);
    users.save(account);
  }
}
