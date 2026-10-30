package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.event.sems.common.TimeUtil;
import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:venue_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
class VenueIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired RoleRepository roles;
  @Autowired UserAccountRepository users;
  @Autowired PasswordEncoder passwords;

  @BeforeEach
  void sampleAccounts() {
    SampleAccounts.seed(roles, users, passwords);
  }

  @Test
  void organizerOwnsVenuesAndCannotBreakExistingEvents() throws Exception {
    Map<String, Object> room =
        Map.of("name", "Hội trường C", "address", "Tầng 3", "capacity", 80);
    assertThat(write("POST", "/api/v1/venues", room, null).getResponse().getStatus())
        .isEqualTo(401);
    assertThat(write("POST", "/api/v1/venues", room, "staff").getResponse().getStatus())
        .isEqualTo(403);
    assertThat(write("POST", "/api/v1/venues", room, "visitor").getResponse().getStatus())
        .isEqualTo(403);
    assertThat(write("POST", "/api/v1/venues", room, "admin").getResponse().getStatus())
        .isEqualTo(403);
    MvcResult created = write("POST", "/api/v1/venues", room, "organizer");
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    long venueId = parse(created).get("id").asLong();
    assertThat(parse(created).get("eventCount").asLong()).isZero();
    assertThat(write("POST", "/api/v1/venues", room, "organizer").getResponse().getStatus())
        .isEqualTo(409);
    assertThat(write("POST", "/api/v1/venues",
            Map.of("name", "  ", "address", "Tầng 4", "capacity", 0), "organizer")
        .getResponse().getStatus()).isEqualTo(400);
    assertThat(write("PUT", "/api/v1/venues/" + venueId, room, "organizer2")
        .getResponse().getStatus()).isEqualTo(404);
    assertThat(write("DELETE", "/api/v1/venues/" + venueId, null, "organizer2")
        .getResponse().getStatus()).isEqualTo(404);
    assertThat(write("PUT", "/api/v1/venues/" + venueId, room, "admin")
        .getResponse().getStatus()).isEqualTo(403);
    assertThat(write("POST", "/api/v1/events",
            Map.of("title", "Không được dùng phòng của nhóm khác", "venueId", venueId,
                "startsAt", TimeUtil.now().plusDays(2).withNano(0).toString(),
                "endsAt", TimeUtil.now().plusDays(2).plusHours(2).withNano(0).toString(),
                "capacity", 10, "status", "OPEN"), "organizer2")
        .getResponse().getStatus()).isEqualTo(404);

    var start = TimeUtil.now().plusDays(1).withNano(0);
    MvcResult draft = write("POST", "/api/v1/events",
            Map.of("title", "Hội thảo", "venueId", venueId,
                "startsAt", start.toString(), "endsAt", start.plusHours(2).toString(),
                "capacity", 60, "status", "OPEN"), "organizer");
    assertThat(draft.getResponse().getStatus()).isEqualTo(201);
    long eventId = parse(draft).get("id").asLong();
    assertThat(write("PUT", "/api/v1/venues/" + venueId,
            Map.of("name", "Hội trường C", "address", "Tầng 3", "capacity", 50), "organizer")
        .getResponse().getStatus()).isEqualTo(409);
    assertThat(write("DELETE", "/api/v1/venues/" + venueId, null, "organizer")
        .getResponse().getStatus()).isEqualTo(409);
    MvcResult updated = write("PUT", "/api/v1/venues/" + venueId,
        Map.of("name", "Hội trường C mới", "address", "Tầng 3", "capacity", 90), "organizer");
    assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(updated).get("eventCount").asLong()).isEqualTo(1);
    assertThat(write("POST", "/api/v1/events/" + eventId + "/submit", Map.of(), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(write("POST", "/api/v1/admin/events/" + eventId + "/review",
        Map.of("decision", "APPROVED"), "admin").getResponse().getStatus()).isEqualTo(200);
    assertThat(write("PUT", "/api/v1/venues/" + venueId,
        Map.of("name", "Tên thay đổi", "address", "Tầng 4", "capacity", 90), "organizer")
        .getResponse().getStatus()).isEqualTo(409);

    long spareId = parse(write("POST", "/api/v1/venues",
        Map.of("name", "Phòng dự phòng", "address", "Tầng 4", "capacity", 20), "organizer"))
        .get("id").asLong();
    assertThat(write("DELETE", "/api/v1/venues/" + spareId, null, "organizer")
        .getResponse().getStatus()).isEqualTo(204);
    assertThat(write("DELETE", "/api/v1/venues/" + spareId, null, "organizer")
        .getResponse().getStatus()).isEqualTo(404);
    long otherId = parse(write("POST", "/api/v1/venues",
        Map.of("name", "Phòng riêng nhóm 2", "address", "Tầng 5", "capacity", 30), "organizer2"))
        .get("id").asLong();
    assertThat(otherId).isPositive();
    assertThat(mvc.perform(get("/api/v1/venues").with(user("organizer").roles("ORGANIZER")))
        .andReturn().getResponse().getContentAsString()).contains("Hội trường C mới")
        .doesNotContain("Phòng dự phòng", "Phòng riêng nhóm 2");
    assertThat(mvc.perform(get("/api/v1/venues").with(user("admin").roles("ADMIN")))
        .andReturn().getResponse().getContentAsString())
        .contains("Hội trường C mới", "Phòng riêng nhóm 2");
  }

  @Test
  void inactiveVenueCannotBeUsedForNewEventButKeepsHistory() throws Exception {
    long venueId = parse(write("POST", "/api/v1/venues",
        Map.of("name", "Phòng ngừng sử dụng", "address", "Tầng 9", "capacity", 40),
        "organizer")).get("id").asLong();
    var disabled = mvc.perform(patch("/api/v1/venues/" + venueId + "/active")
        .with(csrf()).with(user("organizer").roles("ORGANIZER"))
        .contentType("application/json").content("{\"active\":false}")).andReturn();
    assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(disabled).get("active").asBoolean()).isFalse();
    var start = TimeUtil.now().plusDays(6).withNano(0);
    assertThat(write("POST", "/api/v1/events", Map.of("title", "Sự kiện mới",
        "venueId", venueId, "startsAt", start.toString(),
        "endsAt", start.plusHours(2).toString(), "capacity", 10, "status", "OPEN"),
        "organizer").getResponse().getStatus()).isEqualTo(409);
    assertThat(mvc.perform(patch("/api/v1/venues/" + venueId + "/active")
        .with(csrf()).with(user("admin").roles("ADMIN"))
        .contentType("application/json").content("{\"active\":true}"))
        .andReturn().getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void samePhysicalRoomCannotBeDoubleBookedAcrossOrganizers() throws Exception {
    Map<String, Object> room = Map.of("name", "Hội trường chung",
        "address", "Tầng 1, nhà A", "capacity", 50);
    long firstVenue = parse(write("POST", "/api/v1/venues", room, "organizer"))
        .get("id").asLong();
    long secondVenue = parse(write("POST", "/api/v1/venues", room, "organizer2"))
        .get("id").asLong();
    var start = TimeUtil.now().plusDays(3).withNano(0);
    long eventId = parse(write("POST", "/api/v1/events", Map.of("title", "Sự kiện nhóm một",
        "venueId", firstVenue, "startsAt", start.toString(),
        "endsAt", start.plusHours(2).toString(), "capacity", 20, "status", "OPEN"),
        "organizer")).get("id").asLong();
    assertThat(write("POST", "/api/v1/events/" + eventId + "/submit", Map.of(),
        "organizer").getResponse().getStatus()).isEqualTo(200);
    assertThat(write("POST", "/api/v1/admin/events/" + eventId + "/review",
        Map.of("decision", "APPROVED"), "admin").getResponse().getStatus()).isEqualTo(200);
    assertThat(write("POST", "/api/v1/events", Map.of("title", "Sự kiện nhóm hai",
        "venueId", secondVenue, "startsAt", start.plusMinutes(30).toString(),
        "endsAt", start.plusHours(1).toString(), "capacity", 20, "status", "OPEN"),
        "organizer2").getResponse().getStatus()).isEqualTo(409);
  }

  private MvcResult write(String method, String path, Object body, String role) throws Exception {
    var request = switch (method) {
      case "POST" -> post(path);
      case "PUT" -> put(path);
      case "DELETE" -> delete(path);
      default -> throw new IllegalArgumentException(method);
    };
    request.with(csrf());
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    if (role != null)
      request.with(user(role).roles(role.equals("admin") ? "ADMIN"
          : role.equals("organizer") ? "ORGANIZER"
          : role.startsWith("organizer") ? "ORGANIZER"
          : role.equals("staff") ? "STAFF" : "ATTENDEE"));
    return mvc.perform(request).andReturn();
  }

  private JsonNode parse(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsByteArray());
  }
}
