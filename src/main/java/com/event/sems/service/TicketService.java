package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.config.DashboardSocketHandler;
import com.event.sems.dto.ApiDtos.*;
import com.event.sems.entity.*;
import com.event.sems.repository.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class TicketService {
  private final EventRepository events;
  private final TicketTypeRepository ticketTypes;
  private final AttendeeRepository attendees;
  private final UserAccountRepository users;
  private final TicketRepository tickets;
  private final FeedbackRepository feedback;
  private final TicketTokenService tokens;
  private final DashboardService dashboard;
  private final DashboardSocketHandler socket;
  private final ObjectProvider<TicketMailService> mailService;
  private final TicketMailQueueService mailQueue;

  @Transactional
  public TicketView register(long eventId, RegistrationInput input) {
    UserAccount user = currentUser();
    if (!user.isEmailVerified())
      throw new ApiException(HttpStatus.CONFLICT, "Hãy xác minh email tài khoản trước khi đăng ký vé.");
    Event event =
        events
            .findForUpdate(eventId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    // Nhận đăng ký bổ sung trong thời gian đón khách, đến trước giờ đóng check-in.
    if (!"APPROVED".equals(event.getApprovalStatus())
        || !"SCHEDULED".equals(event.getLifecycleStatus())
        || !"OPEN".equals(event.getStatus())
        || !TimeUtil.now().isBefore(event.getEndsAt().minusMinutes(15)))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã đóng đăng ký.");
    String email = user.getEmail();
    String phone = input.phone().replaceAll("[\\s.()-]", "");
    if (!phone.matches("\\+?[0-9]{9,15}"))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
    if (tickets.existsByEventIdAndUserIdAndStatusNot(eventId, user.getId(), "CANCELLED"))
      throw new ApiException(HttpStatus.CONFLICT, "Tài khoản này đã đăng ký sự kiện.");
    if (tickets.existsByEventIdAndEmailKeyAndStatusNot(eventId, email, "CANCELLED")
        || tickets.existsByEventIdAndPhoneKeyAndStatusNot(eventId, phone, "CANCELLED"))
      throw new ApiException(
          HttpStatus.CONFLICT, "Email hoặc số điện thoại đã đăng ký sự kiện này.");
    long used = tickets.countByEventIdAndStatusNot(eventId, "CANCELLED");
    if (used >= event.getCapacity()) {
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hết vé.");
    }
    TicketType type =
        ticketTypes
            .findByEventId(eventId)
            .orElseThrow(() -> new IllegalStateException("Thiếu hạng vé của sự kiện"));
    Attendee attendee = new Attendee();
    attendee.setFullName(input.fullName().trim());
    attendee.setEmail(email);
    attendee.setPhone(phone);
    attendee.setOrganization(optional(input.organization()));
    attendee.setPositionTitle(optional(input.positionTitle()));
    attendee.setCreatedAt(TimeUtil.now());
    attendees.save(attendee);
    Ticket ticket = new Ticket();
    ticket.setEvent(event);
    ticket.setTicketType(type);
    ticket.setAttendee(attendee);
    ticket.setUser(user);
    ticket.setCode(UUID.randomUUID().toString());
    ticket.setQrToken("PENDING");
    ticket.setEmailKey(email);
    ticket.setPhoneKey(phone);
    ticket.setIssuedAt(TimeUtil.now());
    tickets.saveAndFlush(ticket);
    long issuedAt = ZonedDateTime.now(TimeUtil.ZONE).toEpochSecond();
    ticket.setQrToken(tokens.issue(ticket.getId(), eventId, issuedAt));
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            socket.broadcast(eventId, dashboard.get(eventId));
          }
        });
    TicketMailService mail = mailService.getIfAvailable();
    if (mail != null && mail.isEnabled()) {
      long jobId = mailQueue.enqueue(ticket);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                mailQueue.process(jobId);
              } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(TicketService.class)
                    .warn("Lần gửi vé đầu tiên chưa hoàn tất; công việc {} còn trong hàng đợi.", jobId, ex);
              }
            }
          });
    }
    return view(ticket);
  }

  @Transactional(readOnly = true)
  public TicketView get(String code) {
    return view(requireAccessible(code));
  }

  @Transactional(readOnly = true)
  public List<TicketView> list(long eventId) {
    return tickets.findByEventIdAndStatusNotOrderByIdDesc(eventId, "CANCELLED").stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  public TicketView cancel(String code) {
    Ticket owned = requireOwned(code);
    Event event =
        events
            .findForUpdate(owned.getEvent().getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    Ticket ticket = tickets.findForUpdate(owned.getId()).orElseThrow();
    if ("CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy; vé được giữ trong lịch sử.");
    if (!"ISSUED".equals(ticket.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ vé chưa check-in mới có thể hủy.");
    if (!TimeUtil.now().isBefore(event.getStartsAt()))
      throw new ApiException(HttpStatus.CONFLICT, "Không thể hủy vé sau khi sự kiện bắt đầu.");
    ticket.setStatus("CANCELLED");
    ticket.setCancelledAt(TimeUtil.now());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            socket.broadcast(event.getId(), dashboard.get(event.getId()));
          }
        });
    return view(ticket);
  }

  @Transactional(readOnly = true)
  public List<TicketView> mine() {
    return tickets.findByUserIdOrderByIdDesc(currentUser().getId()).stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public byte[] qr(String code) {
    Ticket ticket = requireAccessible(code);
    if ("CANCELLED".equals(ticket.getEvent().getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, mã QR không còn hiệu lực.");
    if ("CANCELLED".equals(ticket.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Vé đã hủy, mã QR không còn hiệu lực.");
    return makeQr(ticket.getQrToken());
  }

  private byte[] makeQr(String token) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(
          new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 320, 320), "PNG", out);
      return out.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể tạo ảnh QR", ex);
    }
  }

  public Ticket require(String code) {
    return tickets
        .findByCode(code)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy vé."));
  }

  public Ticket requireOwned(String code) {
    Ticket ticket = require(code);
    if (!ticket.getUser().getId().equals(currentUser().getId()))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy vé.");
    return ticket;
  }

  private Ticket requireAccessible(String code) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean admin =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    return admin ? require(code) : requireOwned(code);
  }

  private UserAccount currentUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return users
        .findByUsernameIgnoreCase(authentication.getName())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại."));
  }

  private String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private TicketView view(Ticket t) {
    return new TicketView(
        t.getCode(),
        t.getEvent().getId(),
        t.getEvent().getTitle(),
        t.getAttendee().getFullName(),
        t.getAttendee().getEmail(),
        t.getAttendee().getOrganization(),
        t.getAttendee().getPositionTitle(),
        t.getStatus(),
        t.getEvent().getStartsAt(),
        t.getEvent().getEndsAt(),
        t.getEvent().getVenue() == null ? "Trực tuyến" : t.getEvent().getVenue().getName(),
        "CANCELLED".equals(t.getStatus()) || "CANCELLED".equals(t.getEvent().getLifecycleStatus())
            ? null : t.getEvent().getOnlineUrl(),
        t.getCheckedInAt(),
        t.getCancelledAt(),
        feedback.existsByTicketId(t.getId()), t.getEvent().getLifecycleStatus(),
        t.getEvent().getCancelReason());
  }
}
