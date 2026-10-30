package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.event.sems.repository.MailSettingsRepository;
import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import com.event.sems.service.MailSettingsService;
import com.event.sems.service.TicketMailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:recovery_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
class AccountRecoveryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserAccountRepository users;
  @Autowired RoleRepository roles;
  @Autowired PasswordEncoder passwords;
  @Autowired MailSettingsRepository settings;
  @Autowired MailSettingsService mailSettings;
  @MockitoBean TicketMailService mail;

  @BeforeEach
  void seed() {
    SampleAccounts.seed(roles, users, passwords);
    when(mail.isEnabled()).thenReturn(true);
  }

  @Test
  void emailVerificationAndOneTimePasswordReset() throws Exception {
    String address = "recovery@example.com";
    String response = mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        java.util.Map.of(
                            "username",
                            "recovery",
                            "displayName",
                            "Người thử nghiệm",
                            "email",
                            address,
                            "password",
                            "OldPassword123!"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.verificationRequired").value(true))
        .andReturn().getResponse().getContentAsString();
    String requestId = json.readTree(response).get("requestId").asText();
    assertThat(users.findByUsernameIgnoreCase("recovery")).isEmpty();
    ArgumentCaptor<String> verification = ArgumentCaptor.forClass(String.class);
    verify(mail).sendConfirmationCode(eq(address), verification.capture());

    mvc.perform(
            post("/api/v1/auth/confirm-registration")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(java.util.Map.of("requestId", requestId, "code", verification.getValue()))))
        .andExpect(status().isOk());
    assertThat(users.findByUsernameIgnoreCase("recovery").orElseThrow().isEmailVerified()).isTrue();
    mvc.perform(
            post("/api/v1/auth/confirm-registration")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(java.util.Map.of("requestId", requestId, "code", verification.getValue()))))
        .andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(java.util.Map.of("email", address))))
        .andExpect(status().isOk());
    ArgumentCaptor<String> reset = ArgumentCaptor.forClass(String.class);
    verify(mail).sendPasswordReset(eq(address), reset.capture());
    String body =
        json.writeValueAsString(
            java.util.Map.of("token", reset.getValue(), "newPassword", "NewPassword123!"));
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .param("username", "recovery")
                .param("password", "OldPassword123!"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .param("username", "recovery")
                .param("password", "NewPassword123!"))
        .andExpect(status().isOk());
  }

  @Test
  void emailChangeKeepsOldAddressUntilCodeIsConfirmed() throws Exception {
    String token = login("visitor", "Visitor@123456");
    String oldAddress = users.findByUsernameIgnoreCase("visitor").orElseThrow().getEmail();
    String newAddress = "new-visitor@example.com";
    String response = mvc.perform(post("/api/v1/account/email").header("Authorization", bearer(token)).with(csrf())
        .contentType("application/json")
        .content(json.writeValueAsBytes(java.util.Map.of(
            "currentPassword", "Visitor@123456", "email", newAddress))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationRequired").value(true))
        .andReturn().getResponse().getContentAsString();
    String requestId = json.readTree(response).get("requestId").asText();
    assertThat(users.findByUsernameIgnoreCase("visitor").orElseThrow().getEmail())
        .isEqualTo(oldAddress);
    mvc.perform(post("/api/v1/account/confirm-email-change").header("Authorization", bearer(token)).with(csrf())
        .contentType("application/json")
        .content(json.writeValueAsBytes(java.util.Map.of(
            "requestId", requestId, "code", "000000"))))
        .andExpect(status().isBadRequest());
    assertThat(users.findByUsernameIgnoreCase("visitor").orElseThrow().getEmail())
        .isEqualTo(oldAddress);
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(mail).sendConfirmationCode(eq(newAddress), code.capture());
    mvc.perform(post("/api/v1/account/confirm-email-change").header("Authorization", bearer(token)).with(csrf())
        .contentType("application/json")
        .content(json.writeValueAsBytes(java.util.Map.of(
            "requestId", requestId, "code", code.getValue()))))
        .andExpect(status().isOk());
    assertThat(users.findByUsernameIgnoreCase("visitor").orElseThrow().getEmail())
        .isEqualTo(newAddress);
  }

  @Test
  void unknownEmailReceivesNeutralResponseAndAdminSettingsAreProtected() throws Exception {
    mvc.perform(
            post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"unknown@example.com\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/mail-settings")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/mail-settings/password")).andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/v1/admin/mail-settings")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"enabled\":true,\"host\":\"smtp.gmail.com\",\"port\":587,"
                        + "\"username\":\"sender@example.com\",\"fromAddress\":\"sender@example.com\","
                        + "\"password\":\"example-app-password\"}"))
        .andExpect(status().isUnauthorized());

    String adminToken = login("admin", "Admin@123456");
    String response =
        mvc.perform(
                put("/api/v1/admin/mail-settings")
                    .header("Authorization", bearer(adminToken))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        "{\"enabled\":true,\"host\":\"smtp.gmail.com\",\"port\":587,"
                            + "\"username\":\"sender@example.com\",\"fromAddress\":\"sender@example.com\","
                            + "\"password\":\"example-app-password\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).contains("passwordConfigured").doesNotContain("example-app-password");
    assertThat(settings.findById(1L).orElseThrow().getEncryptedPassword())
        .isNotEqualTo("example-app-password");
    assertThat(mailSettings.connection().password()).isEqualTo("example-app-password");
    var secretResult =
        mvc.perform(get("/api/v1/admin/mail-settings/password").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(secretResult.getResponse().getContentAsString()).contains("example-app-password");
    assertThat(secretResult.getResponse().getHeader("Cache-Control")).contains("no-store");

    String organizerToken = login("organizer", "Organizer@123456");
    mvc.perform(get("/api/v1/admin/mail-settings/password")
            .header("Authorization", bearer(organizerToken)))
        .andExpect(status().isForbidden());
  }

  private String login(String username, String password) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/login")
        .param("username", username).param("password", password))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
