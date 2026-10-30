package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.event.sems.common.TimeUtil;
import com.event.sems.entity.Venue;
import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.EventRepository;
import com.event.sems.repository.TicketRepository;
import com.event.sems.repository.UserAccountRepository;
import com.event.sems.repository.VenueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sems_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
class EventFlowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired VenueRepository venues;
  @Autowired TicketRepository tickets;
  @Autowired EventRepository events;
  @Autowired RoleRepository roles;
  @Autowired UserAccountRepository users;
  @Autowired PasswordEncoder passwords;

  @BeforeEach
  void sampleAccounts() {
    SampleAccounts.seed(roles, users, passwords);
  }

  @Test
  void onlineEventRequiresApprovalAndKeepsMeetingLinkOnTicket() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(3).withSecond(0).withNano(0);
    Map<String, Object> input = Map.of(
        "title", "Hội thảo trực tuyến", "description", "Họp online",
        "onlineUrl", "https://meeting.example.org/room/private",
        "bannerUrl", "/api/v1/public/banners/22222222-2222-4222-8222-222222222222.png",
        "startsAt", start.toString(), "endsAt", start.plusHours(1).toString(),
        "capacity", 10, "status", "OPEN", "category", "EDUCATION");
    long id = parse(postJson("/api/v1/events", input, "organizer")).get("id").asLong();
    assertThat(postJson("/api/v1/events/" + id + "/submit", Map.of(), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/api/v1/admin/events/" + id + "/review",
        Map.of("decision", "REJECTED", "note", "Cần mô tả rõ hơn"), "admin")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(mvc.perform(get("/api/v1/public/events/" + id)).andReturn().getResponse().getStatus())
        .isEqualTo(404);
    input = new java.util.HashMap<>(input);
    input.put("description", "Nội dung chương trình và cách tham dự đã bổ sung.");
    MvcResult edited = mvc.perform(put("/api/v1/events/" + id).with(csrf())
        .with(user("organizer").roles("ORGANIZER"))
        .contentType("application/json").content(json.writeValueAsBytes(input))).andReturn();
    assertThat(edited.getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(edited).get("approvalStatus").asText()).isEqualTo("DRAFT");
    postJson("/api/v1/events/" + id + "/submit", Map.of(), "organizer");
    assertThat(postJson("/api/v1/admin/events/" + id + "/review",
        Map.of("decision", "APPROVED"), "admin").getResponse().getStatus()).isEqualTo(200);
    JsonNode publicEvent = json.readTree(mvc.perform(get("/api/v1/public/events/" + id))
        .andReturn().getResponse().getContentAsByteArray());
    JsonNode emptyFeedback = parse(getJson("/api/v1/events/" + id + "/feedback", "admin"));
    assertThat(emptyFeedback.get("count").asLong()).isZero();
    assertThat(emptyFeedback.get("averageRating").asDouble()).isZero();
    assertThat(emptyFeedback.get("ratings").get(0).get("percentage").asDouble()).isZero();
    assertThat(emptyFeedback.get("trend").isEmpty()).isTrue();
    assertThat(publicEvent.has("onlineUrl")).isFalse();
    assertThat(publicEvent.get("bannerUrl").asText())
        .isEqualTo("/api/v1/public/banners/22222222-2222-4222-8222-222222222222.png");
    assertThat(parse(getJson("/api/v1/events/" + id, "visitor")).get("onlineUrl").isNull())
        .isTrue();
    MvcResult ticket = postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách online", "email", "online@example.com", "phone", "0918888888"),
        "visitor");
    assertThat(ticket.getResponse().getStatus()).isEqualTo(201);
    assertThat(parse(ticket).get("onlineUrl").asText())
        .isEqualTo("https://meeting.example.org/room/private");
  }

  @Test
  void reviewHistoryAndReconsiderationKeepTheReasonVisible() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(8).withSecond(0).withNano(0);
    long id = parse(postJson("/api/v1/events", Map.of(
        "title", "Sự kiện cần xét duyệt", "onlineUrl", "https://meeting.example.org/review",
        "startsAt", start.toString(), "endsAt", start.plusHours(2).toString(),
        "capacity", 10, "status", "OPEN"), "organizer")).get("id").asLong();
    postJson("/api/v1/events/" + id + "/submit", Map.of(), "organizer");
    assertThat(postJson("/api/v1/admin/events/" + id + "/review",
        Map.of("decision", "REJECTED", "note", "Thiếu lịch trình"), "admin")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(getJson("/api/v1/events/" + id, "organizer"))
        .get("reviewNote").asText()).isEqualTo("Thiếu lịch trình");
    assertThat(parse(getJson("/api/v1/events/" + id + "/reviews", "organizer"))
        .size()).isEqualTo(1);
    assertThat(postJson("/api/v1/admin/events/" + id + "/reconsider",
        Map.of("reason", "Admin chọn nhầm"), "admin").getResponse().getStatus())
        .isEqualTo(200);
    assertThat(postJson("/api/v1/admin/events/" + id + "/review",
        Map.of("decision", "APPROVED"), "admin").getResponse().getStatus())
        .isEqualTo(200);
    assertThat(parse(getJson("/api/v1/events/" + id + "/reviews", "organizer"))
        .size()).isEqualTo(3);
    assertThat(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách", "email", "review@example.com", "phone", "0912223333"),
        "visitor").getResponse().getStatus()).isEqualTo(201);
    assertThat(postJson("/api/v1/admin/events/" + id + "/reconsider",
        Map.of("reason", "Thử thu hồi"), "admin").getResponse().getStatus())
        .isEqualTo(409);
  }

  @Test
  void staffCanOnlySearchAssignedEvents() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(9).withSecond(0).withNano(0);
    long id = parse(postJson("/api/v1/events", Map.of(
        "title", "Sự kiện chưa giao staff", "onlineUrl", "https://meeting.example.org/staff",
        "startsAt", start.toString(), "endsAt", start.plusHours(2).toString(),
        "capacity", 10, "status", "OPEN"), "organizer")).get("id").asLong();
    assertThat(parse(getJson("/api/v1/events", "staff")).toString())
        .doesNotContain("Sự kiện chưa giao staff");
    assertThat(getStatus("/api/v1/events/" + id + "/check-ins/search?query=khach", "staff"))
        .isEqualTo(403);
    long staffId = users.findByUsernameIgnoreCase("staff").orElseThrow().getId();
    assertThat(postJson("/api/v1/admin/events/" + id + "/staff/" + staffId,
        Map.of(), "organizer").getResponse().getStatus()).isEqualTo(403);
    assertThat(postJson("/api/v1/admin/events/" + id + "/staff/" + staffId,
        Map.of(), "admin").getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(getJson("/api/v1/events", "staff")).toString())
        .contains("Sự kiện chưa giao staff");
    assertThat(parse(getJson("/api/v1/events/" + id + "/check-ins/search?query=khach", "staff"))
        .get("total").asLong()).isZero();
  }

  @Test
  void organizerApprovalControlsPublicationAndRegistration() throws Exception {
    Venue venue = new Venue();
    venue.setName("Phòng duyệt sự kiện");
    venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
    venue.setAddress("Khu A");
    venue.setCapacity(20);
    venue = venues.save(venue);
    LocalDateTime start = TimeUtil.now().plusDays(2).withSecond(0).withNano(0);
    Map<String, Object> input = Map.of(
        "title", "Hội thảo an toàn dữ liệu",
        "description", "Chương trình chuyên đề",
        "venueId", venue.getId(),
        "startsAt", start.toString(),
        "endsAt", start.plusHours(2).toString(),
        "capacity", 20,
        "status", "OPEN",
        "category", "TECHNOLOGY",
        "agenda", java.util.List.of(Map.of("title", "Khai mạc", "startsAt", start.toString(),
            "endsAt", start.plusMinutes(30).toString(), "roomName", "Phòng A")),
        "speakers", java.util.List.of(Map.of("name", "Nguyễn A", "topic", "Bảo vệ dữ liệu",
            "biography", "Chuyên gia", "vip", true)));
    MvcResult created = postJson("/api/v1/events", input, "organizer");
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    long id = parse(created).get("id").asLong();
    assertThat(parse(created).get("approvalStatus").asText()).isEqualTo("DRAFT");
    assertThat(parse(getJson("/api/v1/events/" + id, "organizer")).get("agenda").size()).isEqualTo(1);
    assertThat(getStatus("/api/v1/events/" + id, "organizer2")).isEqualTo(404);
    assertThat(mvc.perform(get("/api/v1/public/events/" + id)).andReturn().getResponse().getStatus())
        .isEqualTo(404);
    assertThat(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách A", "email", "a@example.com", "phone", "0911111111"),
        "visitor").getResponse().getStatus()).isEqualTo(409);
    assertThat(postJson("/api/v1/events/" + id + "/submit", Map.of(), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/api/v1/admin/events/" + id + "/review",
        Map.of("decision", "APPROVED"), "organizer").getResponse().getStatus()).isEqualTo(403);
    assertThat(postJson("/api/v1/admin/events/" + id + "/review",
        Map.of("decision", "APPROVED"), "admin").getResponse().getStatus()).isEqualTo(200);
    JsonNode publicEvent = json.readTree(mvc.perform(get("/api/v1/public/events/" + id))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    assertThat(publicEvent.get("category").asText()).isEqualTo("TECHNOLOGY");
    assertThat(publicEvent.get("speakers").size()).isEqualTo(1);
    assertThat(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách A", "email", "a@example.com", "phone", "0911111111"),
        "visitor").getResponse().getStatus()).isEqualTo(201);
    assertThat(getStatus("/api/v1/events/" + id + "/dashboard", "organizer")).isEqualTo(200);
    assertThat(getStatus("/api/v1/events/" + id + "/dashboard", "organizer2")).isEqualTo(404);
  }

  @Test
  void completeThreeModuleFlowAndRejectDuplicateActions() throws Exception {
    Venue venue = new Venue();
    venue.setName("Hội trường kiểm thử");
    venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
    venue.setAddress("Tầng 1");
    venue.setCapacity(10);
    venue = venues.save(venue);
    // Ô datetime-local chỉ có độ chính xác đến phút: vẫn tạo và đăng ký được ngay.
    LocalDateTime start = TimeUtil.now().withSecond(0).withNano(0);
    Map<String, Object> event =
        Map.of(
            "title",
            "Ngày hội công nghệ",
            "description",
            "Bản demo",
            "venueId",
            venue.getId(),
            "startsAt",
            start.toString(),
            "endsAt",
            start.plusHours(2).toString(),
            "capacity",
            2,
            "status",
            "OPEN");
    assertThat(postJson("/api/v1/events", event, null).getResponse().getStatus()).isEqualTo(401);
    assertThat(postJson("/api/v1/events", event, "visitor").getResponse().getStatus())
        .isEqualTo(403);
    assertThat(postJson("/api/v1/events", event, "admin").getResponse().getStatus())
        .isEqualTo(403);
    MvcResult created = postJson("/api/v1/events", event, "organizer");
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    long eventId = parse(created).get("id").asLong();
    assertThat(mvc.perform(put("/api/v1/events/" + eventId).with(csrf())
        .with(user("admin").roles("ADMIN")).contentType("application/json")
        .content(json.writeValueAsBytes(event))).andReturn().getResponse().getStatus())
        .isEqualTo(403);
    approveEvent(eventId);

    Map<String, Object> alice =
        Map.of("fullName", "Nguyễn An", "email", "an@example.com", "phone", "0912345678",
            "organization", "Đại học PTIT", "positionTitle", "Sinh viên");
    assertThat(
            postJson("/api/v1/events/" + eventId + "/registrations", alice, "staff")
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    MvcResult ticket = postJson("/api/v1/events/" + eventId + "/registrations", alice, "visitor");
    assertThat(ticket.getResponse().getStatus()).isEqualTo(201);
    assertThat(parse(ticket).get("attendeeOrganization").asText()).isEqualTo("Đại học PTIT");
    assertThat(parse(ticket).get("attendeePositionTitle").asText()).isEqualTo("Sinh viên");
    String code = parse(ticket).get("code").asText();
    assertThat(tickets.findByCode(code).orElseThrow().getUser().getId())
        .isEqualTo(users.findByUsernameIgnoreCase("visitor").orElseThrow().getId());
    JsonNode myTickets = parse(getJson("/api/v1/tickets/mine", "visitor"));
    assertThat(myTickets.size()).isEqualTo(1);
    assertThat(myTickets.get(0).get("code").asText()).isEqualTo(code);
    assertThat(parse(getJson("/api/v1/tickets/mine", "visitor2")).size()).isZero();
    assertThat(getStatus("/api/v1/tickets/mine", "admin")).isEqualTo(403);
    String searchPath = "/api/v1/events/" + eventId + "/check-ins/search?query=visitor@sems.example";
    assertThat(getStatus(searchPath, "visitor")).isEqualTo(403);
    assertThat(getStatus(searchPath, "admin")).isEqualTo(403);
    assertThat(postJson("/api/v1/events/" + eventId + "/check-ins",
        Map.of("method", "MANUAL", "ticketCode", code), "admin")
        .getResponse().getStatus()).isEqualTo(403);
    assertThat(parse(getJson(searchPath, "staff")).get("items").get(0).get("ticketCode").asText())
        .isEqualTo(code);
    assertThat(
            parse(getJson("/api/v1/events/" + eventId + "/check-ins/search?query=khongco", "staff"))
                .get("items").size())
        .isZero();
    assertThat(getStatus("/api/v1/events/" + eventId + "/check-ins/search?query=a", "staff"))
        .isEqualTo(400);
    assertThat(
            getStatus("/api/v1/events/" + (eventId + 100) + "/check-ins/search?query=an", "staff"))
        .isEqualTo(404);
    String registrationsPath = "/api/v1/events/" + eventId + "/reports/registrations";
    assertThat(getStatus(registrationsPath, "staff")).isEqualTo(403);
    assertThat(getStatus(registrationsPath, "visitor")).isEqualTo(403);
    assertThat(getText(registrationsPath, "admin"))
        .contains("visitor@sems.example", "Đại học PTIT", "Sinh viên", code);
    assertThat(getStatus("/api/v1/tickets/" + code, "visitor2")).isEqualTo(404);
    assertThat(getStatus("/api/v1/tickets/" + code + "/qr", "visitor2")).isEqualTo(404);
    assertThat(getStatus("/api/v1/tickets/" + code, "admin")).isEqualTo(200);
    assertThat(
            postJson("/api/v1/events/" + eventId + "/registrations", alice, "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(
            postJson(
                    "/api/v1/events/" + eventId + "/registrations",
                    Map.of(
                        "fullName", "An khác", "email", "other@example.com", "phone", "0912345679"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    byte[] qr =
        mvc.perform(get("/api/v1/tickets/" + code + "/qr").with(user("visitor").roles("ATTENDEE")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(qr).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    assertThat(
            postJson(
                    "/api/v1/tickets/" + code + "/feedback",
                    Map.of("rating", 5, "comment", "Tốt"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(409);

    MvcResult checked =
        postJson(
            "/api/v1/events/" + eventId + "/check-ins",
            Map.of("method", "MANUAL", "ticketCode", code),
            "staff");
    assertThat(checked.getResponse().getStatus()).isEqualTo(200);
    assertThat(getText("/api/v1/events/" + eventId + "/reports/checkins", "admin"))
        .contains("visitor@sems.example", code, "MANUAL", "staff");
    assertThat(
            postJson(
                    "/api/v1/tickets/" + code + "/feedback",
                    Map.of("rating", 1, "comment", "Không phải chủ vé"),
                    "visitor2")
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    assertThat(
            postJson(
                    "/api/v1/events/" + eventId + "/check-ins",
                    Map.of("method", "MANUAL", "ticketCode", code),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            postJson(
                    "/api/v1/events/" + eventId + "/check-ins",
                    Map.of("method", "MANUAL", "ticketCode", code),
                    "staff")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(postJson("/api/v1/tickets/" + code + "/feedback",
        Map.of("rating", 5, "comment", "Còn đang diễn ra"), "visitor")
        .getResponse().getStatus()).isEqualTo(409);
    var finishedEvent = events.findById(eventId).orElseThrow();
    finishedEvent.setStartsAt(TimeUtil.now().minusHours(3));
    finishedEvent.setEndsAt(TimeUtil.now().minusHours(1));
    events.saveAndFlush(finishedEvent);
    assertThat(tickets.findSurveyCandidates(TimeUtil.now(),
        org.springframework.data.domain.PageRequest.of(0, 10))).extracting(t -> t.getCode())
        .contains(code);
    assertThat(
            postJson(
                    "/api/v1/tickets/" + code + "/feedback",
                    Map.of("rating", 5, "comment", "=2+2 Tốt"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    assertThat(tickets.findSurveyCandidates(TimeUtil.now(),
        org.springframework.data.domain.PageRequest.of(0, 10))).extracting(t -> t.getCode())
        .doesNotContain(code);
    assertThat(getText("/api/v1/events/" + eventId + "/reports/feedback", "admin"))
        .contains("'=2+2 Tốt", code);
    assertThat(
            postJson(
                    "/api/v1/tickets/" + code + "/feedback",
                    Map.of("rating", 4, "comment", "Lại"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(
            postJson(
                    "/api/v1/tickets/" + code + "/feedback",
                    Map.of("rating", 6, "comment", "Sai"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(400);

    JsonNode dashboard = parse(getJson("/api/v1/events/" + eventId + "/dashboard", "admin"));
    assertThat(dashboard.get("registered").asInt()).isEqualTo(1);
    assertThat(dashboard.get("checkedIn").asInt()).isEqualTo(1);
    JsonNode summary = parse(getJson("/api/v1/events/" + eventId + "/feedback", "admin"));
    assertThat(summary.get("count").asInt()).isEqualTo(1);
    assertThat(summary.get("averageRating").asDouble()).isEqualTo(5.0);
    assertThat(summary.get("ratings").get(0).get("percentage").asDouble()).isEqualTo(100.0);
    assertThat(summary.get("ratings").get(1).get("count").asInt()).isZero();
    assertThat(summary.get("trend").get(0).get("count").asInt()).isEqualTo(1);
    JsonNode feedbackPage = parse(getJson(
        "/api/v1/events/" + eventId + "/feedback/items?rating=5&query=An", "admin"));
    assertThat(feedbackPage.get("total").asLong()).isEqualTo(1);
    assertThat(feedbackPage.get("pageSize").asInt()).isEqualTo(20);
    assertThat(feedbackPage.get("items").get(0).get("rating").asInt()).isEqualTo(5);
    assertThat(parse(getJson(
        "/api/v1/events/" + eventId + "/feedback/items?rating=4", "admin"))
        .get("total").asLong()).isZero();
    assertThat(getStatus("/api/v1/events/" + eventId + "/feedback/items?page=-1", "admin"))
        .isEqualTo(400);
    String excelPath = "/api/v1/events/" + eventId + "/reports/feedback.xlsx";
    assertThat(getStatus(excelPath, "visitor")).isEqualTo(403);
    assertThat(getStatus(excelPath, "organizer2")).isEqualTo(404);
    assertThat(getStatus("/api/v1/events/" + eventId + "/feedback/items", "organizer2"))
        .isEqualTo(404);
    byte[] excel = mvc.perform(get(excelPath).with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
      assertThat(workbook.getSheet("Tổng quan").getRow(3).getCell(1).getNumericCellValue())
          .isEqualTo(1);
      assertThat(workbook.getSheet("Chi tiết").getRow(1).getCell(2).getNumericCellValue())
          .isEqualTo(5);
      assertThat(workbook.getSheet("Chi tiết").getRow(1).getCell(0).getCellType())
          .isEqualTo(CellType.NUMERIC);
      assertThat(workbook.getSheet("Chi tiết").getRow(1).getCell(3).getCellType())
          .isEqualTo(CellType.STRING);
      assertThat(workbook.getSheet("Chi tiết").getRow(1).getCell(3).getStringCellValue())
          .isEqualTo("=2+2 Tốt");
    }
  }

  @Test
  void qrCheckinRejectsTamperingWrongEventAndOverCapacity() throws Exception {
    Venue venue = new Venue();
    venue.setName("Phòng QR");
    venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
    venue.setAddress("Khu C");
    venue.setCapacity(5);
    venue = venues.save(venue);
    LocalDateTime start = TimeUtil.now().plusMinutes(25).withNano(0);
    long id =
        parse(
                postJson(
                    "/api/v1/events",
                    Map.of(
                        "title",
                        "Quét QR",
                        "description",
                        "Thử nghiệm",
                        "venueId",
                        venue.getId(),
                        "startsAt",
                        start.toString(),
                        "endsAt",
                        start.plusHours(2).toString(),
                        "capacity",
                        1,
                        "status",
                        "OPEN"),
                    "organizer"))
            .get("id")
            .asLong();
    approveEvent(id);
    MvcResult registration =
        postJson(
            "/api/v1/events/" + id + "/registrations",
            Map.of("fullName", "Bình", "email", "binh@example.com", "phone", "0987654321"),
            "visitor");
    assertThat(registration.getResponse().getStatus()).isEqualTo(201);
    String code = parse(registration).get("code").asText();
    String token = tickets.findByCode(code).orElseThrow().getQrToken();
    assertThat(
            postJson(
                    "/api/v1/events/" + id + "/registrations",
                    Map.of("fullName", "Chi", "email", "chi@example.com", "phone", "0911111111"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(
            postJson(
                    "/api/v1/events/" + (id + 1) + "/check-ins",
                    Map.of("method", "QR", "qrToken", token),
                    "staff")
                .getResponse()
                .getStatus())
        .isIn(400, 403, 404);
    assertThat(
            postJson(
                    "/api/v1/events/" + id + "/check-ins",
                    Map.of("method", "QR", "qrToken", token + "x"),
                    "staff")
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    assertThat(
            postJson(
                    "/api/v1/events/" + id + "/check-ins",
                    Map.of("method", "QR", "qrToken", token),
                    "staff")
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            postJson(
                    "/api/v1/events/" + id + "/check-ins",
                    Map.of("method", "QR", "qrToken", token),
                    "staff")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
  }

  @Test
  void rejectShortEventsAndRegistrationAfterCheckinCloses() throws Exception {
    Venue venue = new Venue();
    venue.setName("Phòng kiểm thử thời gian");
    venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
    venue.setAddress("Khu kiểm thử");
    venue.setCapacity(10);
    venue = venues.save(venue);
    LocalDateTime start = TimeUtil.now().minusHours(2).withNano(0);
    Map<String, Object> input =
        new java.util.HashMap<>(
            Map.of(
                "title",
                "Kiểm tra thời gian",
                "venueId",
                venue.getId(),
                "startsAt",
                start.toString(),
                "endsAt",
                start.plusMinutes(29).toString(),
                "capacity",
                5,
                "status",
                "OPEN"));
    assertThat(postJson("/api/v1/events", input, "organizer").getResponse().getStatus()).isEqualTo(400);
    input.put("endsAt", TimeUtil.now().plusMinutes(10).toString());
    MvcResult created = postJson("/api/v1/events", input, "organizer");
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    long eventId = parse(created).get("id").asLong();
    approveEvent(eventId);
    assertThat(
            postJson(
                    "/api/v1/events/" + eventId + "/registrations",
                    Map.of(
                        "fullName",
                        "Khách đến muộn",
                        "email",
                        "late@example.com",
                        "phone",
                        "0991112223"),
                    "visitor")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
  }

  @Test
  void closedRegistrationStillReservesVenue() throws Exception {
    Venue venue = new Venue();
    venue.setName("Phòng kiểm thử trùng lịch");
    venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
    venue.setAddress("Khu kiểm thử");
    venue.setCapacity(10);
    venue = venues.save(venue);
    LocalDateTime start = TimeUtil.now().plusDays(1).withNano(0);
    Map<String, Object> input =
        new java.util.HashMap<>(
            Map.of(
                "title",
                "Sự kiện đã đóng đăng ký",
                "venueId",
                venue.getId(),
                "startsAt",
                start.toString(),
                "endsAt",
                start.plusHours(2).toString(),
                "capacity",
                5,
                "status",
                "CLOSED"));
    long firstId = parse(postJson("/api/v1/events", input, "organizer")).get("id").asLong();
    input.put("title", "Sự kiện trùng địa điểm");
    input.put("status", "OPEN");
    assertThat(postJson("/api/v1/events", input, "organizer").getResponse().getStatus()).isEqualTo(201);
    approveEvent(firstId);
    assertThat(postJson("/api/v1/events", input, "organizer").getResponse().getStatus()).isEqualTo(409);
    input.put("startsAt", start.plusHours(2).toString());
    input.put("endsAt", start.plusHours(3).toString());
    assertThat(postJson("/api/v1/events", input, "organizer").getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
  void cancellationReleasesCapacityAndInvalidatesQrButKeepsHistory() throws Exception {
    Venue venue = new Venue();
    venue.setName("Phòng hủy vé");
    venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
    venue.setAddress("Tầng 2");
    venue.setCapacity(1);
    venue = venues.save(venue);
    LocalDateTime start = TimeUtil.now().plusHours(2).withNano(0);
    long eventId =
        parse(
                postJson(
                    "/api/v1/events",
                    Map.of(
                        "title", "Sự kiện hủy vé",
                        "description", "Kiểm thử hủy vé",
                        "venueId", venue.getId(),
                        "startsAt", start.toString(),
                        "endsAt", start.plusHours(2).toString(),
                        "capacity", 1,
                        "status", "OPEN"),
                    "organizer"))
            .get("id")
            .asLong();
    approveEvent(eventId);
    assertThat(mvc.perform(get("/api/v1/public/events")).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(mvc.perform(get("/api/v1/public/events/" + eventId)).andReturn().getResponse().getStatus())
        .isEqualTo(200);

    Map<String, Object> registration =
        Map.of("fullName", "Khách thử", "email", "khach@example.com", "phone", "0912345678");
    String registerPath = "/api/v1/events/" + eventId + "/registrations";
    MvcResult issued = postJson(registerPath, registration, "visitor");
    assertThat(issued.getResponse().getStatus()).isEqualTo(201);
    String code = parse(issued).get("code").asText();
    String token = tickets.findByCode(code).orElseThrow().getQrToken();
    String cancelPath = "/api/v1/tickets/" + code + "/cancel";
    assertThat(postJson(registerPath,
            Map.of("fullName", "Khách khác", "email", "khac@example.com", "phone", "0912345679"),
            "visitor2").getResponse().getStatus()).isEqualTo(409);
    assertThat(postJson(cancelPath, Map.of(), "visitor2").getResponse().getStatus())
        .isEqualTo(404);
    assertThat(postJson(cancelPath, Map.of(), "staff").getResponse().getStatus())
        .isEqualTo(403);
    assertThat(postJson(cancelPath, Map.of(), "visitor").getResponse().getStatus())
        .isEqualTo(200);
    assertThat(postJson(cancelPath, Map.of(), "visitor").getResponse().getStatus())
        .isEqualTo(409);
    assertThat(parse(getJson("/api/v1/tickets/mine", "visitor")).get(0).get("status").asText())
        .isEqualTo("CANCELLED");
    assertThat(getStatus("/api/v1/tickets/" + code + "/qr", "visitor")).isEqualTo(409);
    assertThat(postJson("/api/v1/events/" + eventId + "/check-ins",
            Map.of("method", "QR", "qrToken", token), "staff").getResponse().getStatus())
        .isEqualTo(409);
    assertThat(parse(getJson("/api/v1/events/" + eventId + "/dashboard", "admin"))
            .get("registered").asInt()).isZero();
    assertThat(getText("/api/v1/events/" + eventId + "/reports/registrations", "admin"))
        .contains("visitor@sems.example", "CANCELLED");

    MvcResult replacement = postJson(registerPath, registration, "visitor");
    assertThat(replacement.getResponse().getStatus()).isEqualTo(201);
    assertThat(parse(replacement).get("code").asText()).isNotEqualTo(code);
    assertThat(parse(getJson("/api/v1/tickets/mine", "visitor")).size()).isEqualTo(2);
    assertThat(parse(getJson("/api/v1/events/" + eventId + "/dashboard", "admin"))
            .get("registered").asInt()).isEqualTo(1);
  }

  @Test
  void organizerCanWithdrawAndDeleteDraftButCannotDeleteTicketedEvent() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(4).withSecond(0).withNano(0);
    Map<String, Object> input = Map.of("title", "Sự kiện kiểm thử rút duyệt",
        "onlineUrl", "https://meeting.example.org/withdraw", "startsAt", start.toString(),
        "endsAt", start.plusHours(2).toString(), "capacity", 10, "status", "OPEN");
    long id = parse(postJson("/api/v1/events", input, "organizer")).get("id").asLong();
    assertThat(postJson("/api/v1/events/" + id + "/submit", Map.of(), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/api/v1/events/" + id + "/withdraw", Map.of(), "organizer2")
        .getResponse().getStatus()).isEqualTo(404);
    assertThat(postJson("/api/v1/events/" + id + "/withdraw", Map.of(), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(getJson("/api/v1/events/" + id + "/reviews", "organizer"))
        .get(0).get("status").asText()).isEqualTo("DRAFT");
    assertThat(mvc.perform(delete("/api/v1/events/" + id).with(csrf())
        .with(user("organizer2").roles("ORGANIZER"))).andReturn()
        .getResponse().getStatus()).isEqualTo(404);
    assertThat(mvc.perform(delete("/api/v1/events/" + id).with(csrf())
        .with(user("organizer").roles("ORGANIZER"))).andReturn()
        .getResponse().getStatus()).isEqualTo(204);
    assertThat(getStatus("/api/v1/events/" + id, "admin")).isEqualTo(404);
  }

  @Test
  void cancellationKeepsTicketsButStopsRegistrationQrAndCheckin() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(5).withSecond(0).withNano(0);
    Map<String, Object> input = Map.of("title", "Sự kiện kiểm thử hủy",
        "onlineUrl", "https://meeting.example.org/cancel", "startsAt", start.toString(),
        "endsAt", start.plusHours(2).toString(), "capacity", 10, "status", "OPEN");
    long id = parse(postJson("/api/v1/events", input, "organizer")).get("id").asLong();
    approveEvent(id);
    String code = parse(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách hủy sự kiện", "email", "cancel-event@example.com",
            "phone", "0912345670"), "visitor")).get("code").asText();
    assertThat(postJson("/api/v1/admin/events/" + id + "/reconsider",
        Map.of("reason", "Lỡ duyệt"), "admin").getResponse().getStatus()).isEqualTo(409);
    assertThat(postJson("/api/v1/events/" + id + "/cancel",
        Map.of("reason", "Thiếu địa điểm tổ chức"), "organizer2")
        .getResponse().getStatus()).isEqualTo(403);
    assertThat(postJson("/api/v1/events/" + id + "/cancel",
        Map.of("reason", "Thiếu địa điểm tổ chức"), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(getJson("/api/v1/tickets/" + code, "visitor"))
        .get("eventCancelReason").asText()).isEqualTo("Thiếu địa điểm tổ chức");
    assertThat(getStatus("/api/v1/tickets/" + code + "/qr", "visitor")).isEqualTo(409);
    assertThat(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách mới", "email", "new@example.com", "phone", "0912345671"),
        "visitor").getResponse().getStatus()).isEqualTo(409);
    assertThat(postJson("/api/v1/events/" + id + "/check-ins",
        Map.of("method", "MANUAL", "ticketCode", code), "staff")
        .getResponse().getStatus()).isEqualTo(409);
    assertThat(postJson("/api/v1/events/" + id + "/cancel",
        Map.of("reason", "Hủy lần nữa"), "organizer").getResponse().getStatus()).isEqualTo(409);
    assertThat(parse(getJson("/api/v1/events/" + id, "admin"))
        .get("ticketHistoryCount").asLong()).isEqualTo(1);
  }

  @Test
  void adminCanReconsiderApprovalBeforeTicketsAreIssued() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(7).withSecond(0).withNano(0);
    long id = parse(postJson("/api/v1/events", Map.of("title", "Sự kiện duyệt nhầm",
        "onlineUrl", "https://meeting.example.org/reconsider", "startsAt", start.toString(),
        "endsAt", start.plusHours(1).toString(), "capacity", 8, "status", "OPEN"),
        "organizer")).get("id").asLong();
    approveEvent(id);
    assertThat(postJson("/api/v1/admin/events/" + id + "/reconsider",
        Map.of("reason", "Cần rà soát lại nội dung"), "admin")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(getJson("/api/v1/events/" + id, "organizer"))
        .get("approvalStatus").asText()).isEqualTo("PENDING");
    assertThat(mvc.perform(get("/api/v1/public/events/" + id)).andReturn()
        .getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void closingRegistrationKeepsExistingTicketAndCanReopen() throws Exception {
    LocalDateTime start = TimeUtil.now().plusDays(8).withSecond(0).withNano(0);
    Map<String, Object> input = new java.util.HashMap<>(Map.of("title", "Đóng đăng ký tạm thời",
        "onlineUrl", "https://meeting.example.org/pause", "startsAt", start.toString(),
        "endsAt", start.plusHours(1).toString(), "capacity", 8, "status", "OPEN"));
    long id = parse(postJson("/api/v1/events", input, "organizer")).get("id").asLong();
    approveEvent(id);
    String code = parse(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách đã có vé", "email", "paused@example.com",
            "phone", "0912345680"), "visitor")).get("code").asText();
    input.put("status", "CLOSED");
    assertThat(mvc.perform(put("/api/v1/events/" + id).with(csrf())
        .with(user("organizer").roles("ORGANIZER"))
        .contentType("application/json").content(json.writeValueAsBytes(input)))
        .andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/api/v1/events/" + id + "/registrations",
        Map.of("fullName", "Khách mới", "email", "new-paused@example.com",
            "phone", "0912345681"), "visitor")
        .getResponse().getStatus()).isEqualTo(409);
    assertThat(getStatus("/api/v1/tickets/" + code + "/qr", "visitor")).isEqualTo(200);
    input.put("status", "OPEN");
    assertThat(mvc.perform(put("/api/v1/events/" + id).with(csrf())
        .with(user("organizer").roles("ORGANIZER"))
        .contentType("application/json").content(json.writeValueAsBytes(input)))
        .andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(parse(getJson("/api/v1/events/" + id, "admin"))
        .get("approvalStatus").asText()).isEqualTo("APPROVED");
  }

  private void approveEvent(long eventId) throws Exception {
    assertThat(postJson("/api/v1/events/" + eventId + "/submit", Map.of(), "organizer")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/api/v1/admin/events/" + eventId + "/review",
        Map.of("decision", "APPROVED"), "admin").getResponse().getStatus()).isEqualTo(200);
    long staffId = users.findByUsernameIgnoreCase("staff").orElseThrow().getId();
    assertThat(postJson("/api/v1/admin/events/" + eventId + "/staff/" + staffId,
        Map.of(), "admin").getResponse().getStatus()).isEqualTo(200);
  }

  private MvcResult postJson(String path, Object body, String role) throws Exception {
    var request =
        post(path)
            .with(csrf())
            .contentType("application/json")
            .content(json.writeValueAsBytes(body));
    if (role != null)
      request.with(
          user(role)
              .roles(
                  role.equals("admin")
                      ? "ADMIN"
                      : role.startsWith("visitor") ? "ATTENDEE"
                      : role.startsWith("organizer") ? "ORGANIZER" : "STAFF"));
    return mvc.perform(request).andReturn();
  }

  private MvcResult getJson(String path, String role) throws Exception {
    String authority =
        role.equals("admin") ? "ADMIN" : role.startsWith("visitor") ? "ATTENDEE"
            : role.startsWith("organizer") ? "ORGANIZER" : "STAFF";
    return mvc.perform(get(path).with(user(role).roles(authority)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private int getStatus(String path, String role) throws Exception {
    String authority = role.equals("admin") ? "ADMIN" : role.equals("staff") ? "STAFF"
        : role.startsWith("organizer") ? "ORGANIZER" : "ATTENDEE";
    return mvc.perform(get(path).with(user(role).roles(authority)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private String getText(String path, String role) throws Exception {
    String authority = role.equals("admin") ? "ADMIN" : "ATTENDEE";
    return mvc.perform(get(path).with(user(role).roles(authority)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  }

  private JsonNode parse(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsByteArray());
  }
}
