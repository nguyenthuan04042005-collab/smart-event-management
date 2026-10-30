package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.config.DashboardSocketHandler;
import com.event.sems.dto.ApiDtos.*;
import com.event.sems.entity.*;
import com.event.sems.repository.*;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
public class EventService {
  private final EventRepository events;
  private final VenueRepository venues;
  private final TicketTypeRepository ticketTypes;
  private final TicketRepository tickets;
  private final UserAccountRepository users;
  private final EventReviewRepository reviews;
  private final EventStaffRepository eventStaff;
  private final ObjectProvider<TicketMailService> mailService;
  private final DashboardService dashboard;
  private final DashboardSocketHandler socket;

  @Transactional(readOnly = true)
  public List<EventView> list() {
    boolean admin = hasRole("ADMIN");
    Long owner = hasRole("ORGANIZER") ? currentUser().getId() : null;
    boolean staff = hasRole("STAFF");
    Set<Long> assigned = staff
        ? eventStaff.findByStaffUsernameIgnoreCase(currentUser().getUsername()).stream()
            .map(item -> item.getEvent().getId()).collect(Collectors.toSet())
        : Set.of();
    return events.findAll().stream()
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> admin || (owner != null ? e.getOrganizer().getId().equals(owner)
            : staff ? assigned.contains(e.getId())
                : "APPROVED".equals(e.getApprovalStatus())
                    && !"CANCELLED".equals(e.getLifecycleStatus())))
        .map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<PublicEventView> publicEvents() {
    return events.findAll().stream()
        .filter(event -> "APPROVED".equals(event.getApprovalStatus())
            && "SCHEDULED".equals(event.getLifecycleStatus()) && event.getDeletedAt() == null
            && event.getEndsAt().isAfter(TimeUtil.now()))
        .sorted(Comparator.comparing(Event::getStartsAt))
        .map(this::publicView)
        .toList();
  }

  @Transactional(readOnly = true)
  public PublicEventView publicEvent(long id) {
    Event event = require(id);
    if (!"APPROVED".equals(event.getApprovalStatus()) || event.getDeletedAt() != null)
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện.");
    return publicView(event);
  }

  @Transactional(readOnly = true)
  public EventView get(long id) {
    Event event = require(id);
    if (!hasRole("ADMIN") && !isOwner(event) && !"APPROVED".equals(event.getApprovalStatus()))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện.");
    return view(event);
  }

  @Transactional
  public EventView create(EventInput input) {
    requireOrganizer();
    Venue venue = validate(input, 0L, 0);
    Event event = new Event();
    event.setOrganizer(currentUser());
    fill(event, input, venue);
    event.setApprovalStatus("DRAFT");
    event.setCreatedAt(TimeUtil.now());
    events.saveAndFlush(event);
    TicketType type = new TicketType();
    type.setEvent(event);
    type.setQuota(input.capacity());
    ticketTypes.save(type);
    return view(event);
  }

  @Transactional
  public EventView update(long id, EventInput input) {
    Event event = requireOwnerForUpdate(id);
    if ("CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, không thể chỉnh sửa.");
    long issued = tickets.countByEventIdAndStatusNot(id, "CANCELLED");
    boolean changed = contentChanged(event, input);
    if (issued > 0 && changed)
      throw new ApiException(HttpStatus.CONFLICT,
          "Sự kiện đã phát hành vé; chỉ có thể mở hoặc đóng đăng ký.");
    Venue venue = validate(input, id, issued);
    if (changed) fill(event, input, venue);
    else event.setStatus(input.status());
    if (changed) {
      event.setApprovalStatus("DRAFT");
      event.setReviewNote(null);
    }
    ticketTypes.findByEventId(id).ifPresent(type -> type.setQuota(input.capacity()));
    return view(event);
  }

  @Transactional
  public EventView changeBanner(long id, String bannerUrl) {
    checkBannerChange(id);
    Event event = requireOwnerForUpdate(id);
    event.setBannerUrl(bannerUrl);
    event.setApprovalStatus("DRAFT");
    event.setReviewNote(null);
    return view(event);
  }

  @Transactional(readOnly = true)
  public void checkBannerChange(long id) {
    Event event = requireOwnerForUpdate(id);
    if ("CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, không thể chỉnh sửa.");
    if (tickets.countByEventIdAndStatusNot(id, "CANCELLED") > 0)
      throw new ApiException(HttpStatus.CONFLICT,
          "Sự kiện đã phát hành vé; không thể đổi ảnh banner.");
  }

  @Transactional
  public EventView submit(long id) {
    requireOrganizer();
    Event event = requireOwnerForUpdate(id);
    if (!List.of("DRAFT", "REJECTED").contains(event.getApprovalStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ bản nháp hoặc sự kiện bị từ chối mới được gửi duyệt.");
    ensureVenueAvailable(event);
    event.setApprovalStatus("PENDING");
    event.setReviewNote(null);
    return view(event);
  }

  @Transactional
  public EventView withdraw(long id) {
    Event event = requireOwnerForUpdate(id);
    if (!"PENDING".equals(event.getApprovalStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ có thể rút sự kiện đang chờ duyệt.");
    recordReview(event, "DRAFT", "Ban tổ chức rút yêu cầu duyệt.");
    event.setApprovalStatus("DRAFT");
    event.setReviewNote(null);
    return view(event);
  }

  @Transactional
  public void deleteDraft(long id) {
    Event event = requireOwnerForUpdate(id);
    if (!"DRAFT".equals(event.getApprovalStatus()) || tickets.existsByEventId(id))
      throw new ApiException(HttpStatus.CONFLICT,
          "Chỉ có thể xóa bản nháp chưa từng phát hành vé.");
    event.setDeletedAt(TimeUtil.now());
  }

  @Transactional
  public EventView cancel(long id, String reason) {
    if (reason == null || reason.isBlank() || reason.trim().length() > 500)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cần nhập lý do hủy (tối đa 500 ký tự).");
    Event event = requireForUpdate(id);
    if (!hasRole("ADMIN") && !isOwner(event))
      throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền hủy sự kiện này.");
    if (!"APPROVED".equals(event.getApprovalStatus())
        || !"SCHEDULED".equals(event.getLifecycleStatus())
        || !TimeUtil.now().isBefore(event.getStartsAt())
        || tickets.countByEventIdAndStatus(id, "CHECKED_IN") > 0)
      throw new ApiException(HttpStatus.CONFLICT,
          "Chỉ có thể hủy sự kiện đã duyệt trước giờ bắt đầu và chưa có khách check-in.");
    event.setLifecycleStatus("CANCELLED");
    event.setStatus("CLOSED");
    event.setCancelReason(reason.trim());
    event.setCancelledAt(TimeUtil.now());
    event.setCancelledBy(currentUser());
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        socket.broadcast(id, dashboard.get(id));
      }
    });
    var mail = mailService.getIfAvailable();
    if (mail != null && mail.isEnabled()) {
      String title = event.getTitle();
      String explanation = event.getCancelReason();
      var addresses = tickets.findByEventIdAndStatusNotOrderByIdDesc(id, "CANCELLED").stream()
          .map(ticket -> ticket.getAttendee().getEmail()).distinct().toList();
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          addresses.forEach(address -> mail.sendEventCancellation(address, title, explanation));
        }
      });
    }
    return view(event);
  }

  @Transactional
  public EventView review(long id, String decision, String note) {
    if (!hasRole("ADMIN")) throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền duyệt.");
    Event event = requireForUpdate(id);
    if (event.getDeletedAt() != null || "CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Không thể duyệt sự kiện đã xóa hoặc hủy.");
    if (!"PENDING".equals(event.getApprovalStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ duyệt sự kiện đang chờ xét.");
    if (decision == null || !List.of("APPROVED", "REJECTED").contains(decision))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Quyết định không hợp lệ.");
    if ("REJECTED".equals(decision) && (note == null || note.isBlank()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ghi lý do từ chối.");
    if ("APPROVED".equals(decision)) ensureVenueAvailable(event);
    recordReview(event, decision, note);
    event.setApprovalStatus(decision);
    event.setReviewNote(note == null ? null : note.trim());
    return view(event);
  }

  @Transactional
  public EventView reconsider(long id, String reason) {
    if (!hasRole("ADMIN")) throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền xét lại.");
    if (reason == null || reason.isBlank())
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ghi lý do xét lại quyết định.");
    Event event = requireForUpdate(id);
    if (event.getDeletedAt() != null || "CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Không thể xét lại sự kiện đã xóa hoặc hủy.");
    if (!List.of("REJECTED", "APPROVED").contains(event.getApprovalStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ xét lại sự kiện đã được duyệt hoặc bị từ chối.");
    if ("APPROVED".equals(event.getApprovalStatus())
        && (tickets.countByEventIdAndStatusNot(id, "CANCELLED") > 0
            || !TimeUtil.now().isBefore(event.getStartsAt())))
      throw new ApiException(HttpStatus.CONFLICT,
          "Sự kiện đã có vé hoặc đã bắt đầu; không thể thu hồi phê duyệt. Nếu không thể tổ chức, hãy hủy sự kiện và thông báo khách.");
    recordReview(event, "PENDING", reason);
    event.setApprovalStatus("PENDING");
    event.setReviewNote(reason.trim());
    return view(event);
  }

  @Transactional(readOnly = true)
  public List<EventReviewView> reviewHistory(long id) {
    requireManager(id);
    return reviews.findByEventIdOrderByIdDesc(id).stream()
        .map(item -> new EventReviewView(item.getPreviousStatus(), item.getStatus(),
            item.getReviewer().getDisplayName(), item.getNote(), item.getReviewedAt()))
        .toList();
  }

  private void recordReview(Event event, String decision, String note) {
    EventReview review = new EventReview();
    review.setEvent(event);
    review.setReviewer(currentUser());
    review.setPreviousStatus(event.getApprovalStatus());
    review.setStatus(decision);
    review.setNote(note == null || note.isBlank() ? null : note.trim());
    review.setReviewedAt(TimeUtil.now());
    reviews.save(review);
  }

  private void ensureVenueAvailable(Event event) {
    if (event.getVenue() == null) return;
    Venue venue = venues.findById(event.getVenue().getId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm."));
    venues.lockPhysicalVenue(venue.getName(), venue.getAddress());
    if (!venue.isActive())
      throw new ApiException(HttpStatus.CONFLICT, "Địa điểm đã ngừng sử dụng.");
    if (events.countPhysicalVenueConflicts(venue.getName(), venue.getAddress(), event.getId(),
        event.getStartsAt(), event.getEndsAt()) > 0)
      throw new ApiException(HttpStatus.CONFLICT,
          "Địa điểm đã có sự kiện được duyệt trong khoảng thời gian này.");
  }

  @Transactional(readOnly = true)
  public Event requireManager(long id) {
    Event event = require(id);
    if (!hasRole("ADMIN") && !isOwner(event))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện.");
    return event;
  }

  private Event requireForUpdate(long id) {
    return events.findForUpdate(id)
        .filter(event -> event.getDeletedAt() == null)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
  }

  private Event requireOwnerForUpdate(long id) {
    Event event = requireForUpdate(id);
    if (!isOwner(event))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện.");
    return event;
  }

  private void requireOrganizer() {
    if (!hasRole("ORGANIZER"))
      throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ ban tổ chức được tạo và sửa sự kiện.");
  }

  private Venue validate(EventInput input, long excludeId, long issued) {
    if (input.endsAt() == null
        || input.startsAt() == null
        || input.endsAt().isBefore(input.startsAt().plusMinutes(30)))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Sự kiện phải kéo dài ít nhất 30 phút.");
    if (input.capacity() < 1 || input.capacity() < issued)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số vé tối đa không hợp lệ.");
    if (!List.of("OPEN", "CLOSED").contains(input.status()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Trạng thái đăng ký không hợp lệ.");
    if (!List.of("TECHNOLOGY", "ENTERTAINMENT", "EDUCATION", "OTHER")
        .contains(category(input.category())))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Danh mục không hợp lệ.");
    imageUrl(input.bannerUrl(), "Banner");
    webUrl(input.onlineUrl(), "Liên kết trực tuyến");
    Venue venue = null;
    if (input.onlineUrl() == null || input.onlineUrl().isBlank()) {
      if (input.venueId() == null)
        throw new ApiException(HttpStatus.BAD_REQUEST, "Cần chọn địa điểm hoặc nhập liên kết trực tuyến.");
      venue = venues.findById(input.venueId())
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm."));
      if (!venue.getOrganizer().getId().equals(currentUser().getId()))
        throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm.");
      Long currentVenueId = null;
      if (excludeId != 0) {
        Event current = require(excludeId);
        currentVenueId = current.getVenue() == null ? null : current.getVenue().getId();
      }
      if (!venue.isActive() && !Objects.equals(currentVenueId, venue.getId()))
        throw new ApiException(HttpStatus.CONFLICT, "Địa điểm đã ngừng sử dụng.");
      if (input.capacity() > venue.getCapacity())
        throw new ApiException(HttpStatus.BAD_REQUEST, "Số vé vượt sức chứa địa điểm.");
      if (events.countPhysicalVenueConflicts(venue.getName(), venue.getAddress(), excludeId,
          input.startsAt(), input.endsAt()) > 0)
        throw new ApiException(HttpStatus.CONFLICT, "Địa điểm đã có sự kiện khác trong khoảng thời gian này.");
    }
    if (input.agenda() != null) {
      if (input.agenda().size() > 30) throw new ApiException(HttpStatus.BAD_REQUEST, "Lịch trình quá dài.");
      for (AgendaInput item : input.agenda()) {
        if (item.startsAt().isBefore(input.startsAt()) || item.endsAt().isAfter(input.endsAt())
            || !item.endsAt().isAfter(item.startsAt()))
          throw new ApiException(HttpStatus.BAD_REQUEST, "Lịch trình phải nằm trong thời gian sự kiện.");
      }
    }
    if (input.speakers() != null) {
      if (input.speakers().size() > 30) throw new ApiException(HttpStatus.BAD_REQUEST, "Quá nhiều diễn giả.");
      input.speakers().forEach(s -> imageUrl(s.imageUrl(), "Ảnh diễn giả"));
    }
    return venue;
  }

  private void fill(Event event, EventInput input, Venue venue) {
    event.setVenue(venue);
    event.setTitle(input.title().trim());
    event.setDescription(input.description() == null ? "" : input.description().trim());
    event.setStartsAt(input.startsAt());
    event.setEndsAt(input.endsAt());
    event.setCapacity(input.capacity());
    event.setStatus(input.status());
    event.setCategory(category(input.category()));
    event.setBannerUrl(blankToNull(input.bannerUrl()));
    event.setOnlineUrl(blankToNull(input.onlineUrl()));
    event.getAgenda().clear();
    if (input.agenda() != null) {
      int index = 0;
      for (AgendaInput source : input.agenda()) {
        AgendaItem item = new AgendaItem();
        item.setEvent(event);
        item.setTitle(source.title().trim());
        item.setStartsAt(source.startsAt());
        item.setEndsAt(source.endsAt());
        item.setRoomName(blankToNull(source.roomName()));
        item.setSortOrder(index++);
        event.getAgenda().add(item);
      }
    }
    event.getSpeakers().clear();
    if (input.speakers() != null) {
      for (SpeakerInput source : input.speakers()) {
        Speaker speaker = new Speaker();
        speaker.setEvent(event);
        speaker.setName(source.name().trim());
        speaker.setBiography(blankToNull(source.biography()));
        speaker.setTopic(blankToNull(source.topic()));
        speaker.setImageUrl(blankToNull(source.imageUrl()));
        speaker.setVip(source.vip());
        event.getSpeakers().add(speaker);
      }
    }
  }

  private boolean contentChanged(Event e, EventInput input) {
    return !e.getTitle().equals(input.title().trim())
        || !e.getDescription().equals(input.description() == null ? "" : input.description().trim())
        || !Objects.equals(e.getVenue() == null ? null : e.getVenue().getId(),
            input.onlineUrl() == null || input.onlineUrl().isBlank() ? input.venueId() : null)
        || !e.getStartsAt().equals(input.startsAt()) || !e.getEndsAt().equals(input.endsAt())
        || e.getCapacity() != input.capacity()
        || !e.getCategory().equals(category(input.category()))
        || !Objects.equals(e.getBannerUrl(), blankToNull(input.bannerUrl()))
        || !Objects.equals(e.getOnlineUrl(), blankToNull(input.onlineUrl()))
        || !agendaViews(e).equals(input.agenda() == null ? List.of() : input.agenda().stream()
            .map(a -> new AgendaView(a.title().trim(), a.startsAt(), a.endsAt(), blankToNull(a.roomName())))
            .toList())
        || !speakerViews(e).equals(input.speakers() == null ? List.of() : input.speakers().stream()
            .map(s -> new SpeakerView(s.name().trim(), blankToNull(s.biography()),
                blankToNull(s.topic()), blankToNull(s.imageUrl()), s.vip())).toList());
  }

  private EventView view(Event e) {
    Venue venue = e.getVenue();
    boolean manager = hasRole("ADMIN") || isOwner(e);
    return new EventView(
        e.getId(),
        e.getTitle(),
        e.getDescription(),
        venue == null ? null : venue.getId(),
        venue == null ? "Trực tuyến" : venue.getName(),
        venue == null ? "" : venue.getAddress(),
        e.getStartsAt(),
        e.getEndsAt(),
        e.getCapacity(),
        e.getStatus(),
        tickets.countByEventIdAndStatusNot(e.getId(), "CANCELLED"),
        e.getOrganizer().getDisplayName(), e.getApprovalStatus(), manager ? e.getReviewNote() : null,
        e.getBannerUrl(), e.getCategory(), manager ? e.getOnlineUrl() : null,
        agendaViews(e), speakerViews(e), e.getLifecycleStatus(), e.getCancelReason(),
        e.getCancelledAt(), tickets.countByEventId(e.getId()));
  }

  private PublicEventView publicView(Event e) {
    Venue venue = e.getVenue();
    return new PublicEventView(
        e.getId(),
        e.getTitle(),
        e.getDescription(),
        venue == null ? "Trực tuyến" : venue.getName(),
        venue == null ? "" : venue.getAddress(),
        e.getStartsAt(),
        e.getEndsAt(),
        e.getCapacity(),
        e.getStatus(),
        tickets.countByEventIdAndStatusNot(e.getId(), "CANCELLED"),
        e.getOrganizer().getDisplayName(), e.getBannerUrl(), e.getCategory(),
        agendaViews(e), speakerViews(e), e.getLifecycleStatus(), e.getCancelReason());
  }

  private List<AgendaView> agendaViews(Event e) {
    return e.getAgenda().stream().map(a -> new AgendaView(a.getTitle(), a.getStartsAt(),
        a.getEndsAt(), a.getRoomName())).toList();
  }

  private List<SpeakerView> speakerViews(Event e) {
    return e.getSpeakers().stream().map(s -> new SpeakerView(s.getName(), s.getBiography(),
        s.getTopic(), s.getImageUrl(), s.isVip())).toList();
  }

  private static String category(String value) {
    return value == null || value.isBlank() ? "OTHER" : value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void webUrl(String value, String label) {
    if (value == null || value.isBlank()) return;
    try {
      URI uri = URI.create(value.trim());
      if (uri.getScheme() == null || !List.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null)
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException error) {
      throw new ApiException(HttpStatus.BAD_REQUEST, label + " phải là URL http/https hợp lệ.");
    }
  }

  private static void imageUrl(String value, String label) {
    if (value != null && value.matches("/api/v1/public/banners/[a-f0-9-]{36}\\.(png|jpg)")) return;
    webUrl(value, label);
  }

  private boolean hasRole(String role) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }

  private UserAccount currentUser() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return users.findByUsernameIgnoreCase(auth.getName())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại."));
  }

  private boolean isOwner(Event event) {
    return hasRole("ORGANIZER") && event.getOrganizer().getId().equals(currentUser().getId());
  }

  public Event require(long id) {
    return events
        .findById(id)
        .filter(event -> event.getDeletedAt() == null)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
  }
}
