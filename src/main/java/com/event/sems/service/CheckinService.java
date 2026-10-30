package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.config.DashboardSocketHandler;
import com.event.sems.dto.ApiDtos.*;
import com.event.sems.entity.*;
import com.event.sems.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CheckinService {
  private final EventRepository events;
  private final TicketRepository tickets;
  private final CheckinLogRepository logs;
  private final TicketTokenService tokens;
  private final DashboardService dashboard;
  private final DashboardSocketHandler socket;
  private final EventStaffService eventStaff;

  @Transactional(readOnly = true)
  public CheckinSearchView search(long eventId, String query, int page) {
    Event event = events.findById(eventId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    requireOperator(eventId);
    if ("CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, không thể tìm khách để check-in.");
    String term = query == null ? "" : query.trim();
    if (term.length() < 2 || term.length() > 190)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Nhập từ 2 đến 190 ký tự để tìm khách.");
    if (page < 0 || page > 10000)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số trang tìm kiếm không hợp lệ.");
    var found = tickets.searchForCheckin(eventId, term, PageRequest.of(page, 20));
    var items = found.getContent().stream()
        .map(
            ticket ->
                new CheckinCandidate(
                    ticket.getAttendee().getFullName(),
                    ticket.getAttendee().getEmail(),
                    ticket.getCode(),
                    ticket.getStatus()))
        .toList();
    return new CheckinSearchView(items, page, 20, found.getTotalElements());
  }

  @Transactional
  public CheckinResult checkin(long eventId, CheckinInput input, String operator) {
    requireOperator(eventId);
    Event event =
        events
            .findForUpdate(eventId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    if ("CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, không thể check-in.");
    if (!"APPROVED".equals(event.getApprovalStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện chưa được duyệt để check-in.");
    String method = input.method() == null ? "" : input.method().toUpperCase();
    Ticket ticket;
    if ("QR".equals(method)) {
      TicketTokenService.Payload payload = tokens.verify(input.qrToken());
      if (payload.eventId() != eventId)
        throw new ApiException(HttpStatus.BAD_REQUEST, "Vé không thuộc sự kiện này.");
      ticket =
          tickets
              .findForUpdate(payload.ticketId())
              .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Vé không hợp lệ."));
      if (!MessageDigest.isEqual(
          ticket.getQrToken().getBytes(StandardCharsets.UTF_8),
          input.qrToken().getBytes(StandardCharsets.UTF_8)))
        throw new ApiException(HttpStatus.BAD_REQUEST, "Mã QR không khớp với vé đã phát hành.");
    } else if ("MANUAL".equals(method)) {
      if (input.ticketCode() == null || input.ticketCode().isBlank())
        throw new ApiException(HttpStatus.BAD_REQUEST, "Cần nhập mã vé.");
      Ticket found =
          tickets
              .findByCode(input.ticketCode().trim())
              .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy mã vé."));
      ticket = tickets.findForUpdate(found.getId()).orElseThrow();
    } else throw new ApiException(HttpStatus.BAD_REQUEST, "Phương thức check-in không hợp lệ.");
    if (!ticket.getEvent().getId().equals(eventId))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Vé không thuộc sự kiện này.");
    LocalDateTime now = TimeUtil.now();
    if (now.isBefore(event.getStartsAt().minusMinutes(60))
        || !now.isBefore(event.getEndsAt().minusMinutes(15)))
      throw new ApiException(
          HttpStatus.CONFLICT, "Hiện không nằm trong khung giờ check-in của sự kiện.");
    if ("CANCELLED".equals(ticket.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Vé đã hủy, không thể check-in.");
    if (!"ISSUED".equals(ticket.getStatus()))
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Vé đã check-in lúc "
              + ticket.getCheckedInAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
              + ".");
    ticket.setStatus("CHECKED_IN");
    ticket.setCheckedInAt(now);
    CheckinLog log = new CheckinLog();
    log.setTicket(ticket);
    log.setEvent(event);
    log.setMethod(method);
    log.setOperatorName(operator);
    log.setCheckedInAt(now);
    logs.save(log);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            socket.broadcast(eventId, dashboard.get(eventId));
          }
        });
    return new CheckinResult(
        "Check-in thành công!", ticket.getAttendee().getFullName(), ticket.getCode(), now);
  }

  private void requireOperator(long eventId) {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext()
        .getAuthentication();
    if (auth == null || auth.getAuthorities().stream()
        .noneMatch(a -> "ROLE_STAFF".equals(a.getAuthority())))
      throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ nhân viên được phân công mới có thể check-in.");
    eventStaff.requireAssigned(eventId, auth.getName());
  }
}
