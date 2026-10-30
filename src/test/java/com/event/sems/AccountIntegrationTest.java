package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import com.event.sems.config.DashboardJwtHandshake;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.HashMap;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:account_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserAccountRepository users;
  @Autowired RoleRepository roles;
  @Autowired PasswordEncoder passwords;
  @Autowired DashboardJwtHandshake handshake;

  @BeforeEach
  void seed() {
    SampleAccounts.seed(roles, users, passwords);
  }

  @Test
  void bearerTokenEnforcesRolesAndCanBeRevoked() throws Exception {
    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/login").param("username", "admin")
        .param("password", "wrong")).andExpect(status().isUnauthorized());
    String admin = login("admin", "Admin@123456");
    String staff = login("staff", "Staff@123456");
    assertThat(admin.split("\\.")).hasSize(3);
    mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(admin)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(staff)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(admin) + "tampered"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(admin)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(admin)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void lockedAccountAndPasswordChangeInvalidateExistingToken() throws Exception {
    String admin = login("admin", "Admin@123456");
    String staff = login("staff", "Staff@123456");
    long staffId = users.findByUsernameIgnoreCase("staff").orElseThrow().getId();
    mvc.perform(patch("/api/v1/admin/users/" + staffId + "/active")
        .header("Authorization", bearer(admin))
        .contentType("application/json").content("{\"active\":false}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(staff)))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/login").param("username", "staff")
        .param("password", "Staff@123456")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/account/change-password")
        .header("Authorization", bearer(admin)).contentType("application/json")
        .content("{\"currentPassword\":\"Admin@123456\",\"newPassword\":\"NewAdminPassword123!\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(admin)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void dashboardHandshakeRequiresValidBearerJwt() throws Exception {
    String token = login("organizer", "Organizer@123456");
    var request = new MockHttpServletRequest("GET", "/ws/dashboard/1");
    var response = new MockHttpServletResponse();
    request.addHeader("Sec-WebSocket-Protocol", "sems-jwt, " + token);
    var attributes = new HashMap<String, Object>();
    assertThat(handshake.beforeHandshake(new ServletServerHttpRequest(request),
        new ServletServerHttpResponse(response), null, attributes)).isTrue();
    assertThat(attributes.get("jwtPrincipal")).isNotNull();
    var invalidRequest = new MockHttpServletRequest("GET", "/ws/dashboard/1");
    invalidRequest.addHeader("Sec-WebSocket-Protocol", "sems-jwt, bad-token");
    assertThat(handshake.beforeHandshake(new ServletServerHttpRequest(invalidRequest),
        new ServletServerHttpResponse(response), null, new HashMap<>())).isFalse();
  }

  private String login(String username, String password) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/login").param("username", username)
        .param("password", password)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
