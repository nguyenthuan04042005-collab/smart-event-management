package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.dto.ApiDtos.*;
import com.event.sems.entity.*;
import com.event.sems.repository.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {
  private final TicketRepository tickets;
  private final FeedbackRepository feedback;
  private final EventRepository events;
  private final TicketService ticketService;
  private final UserAccountRepository users;

  @Transactional
  public FeedbackView submit(String code, FeedbackInput input) {
    Ticket found = ticketService.requireOwned(code);
    Ticket ticket = tickets.findForUpdate(found.getId()).orElseThrow();
    if (!"CHECKED_IN".equals(ticket.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ khách đã check-in mới được đánh giá.");
    if ("CANCELLED".equals(ticket.getEvent().getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, không thể gửi đánh giá.");
    if (TimeUtil.now().isBefore(ticket.getEvent().getEndsAt()))
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ có thể đánh giá sau khi sự kiện kết thúc.");
    if (feedback.existsByTicketId(ticket.getId()))
      throw new ApiException(HttpStatus.CONFLICT, "Vé này đã gửi đánh giá.");
    Feedback item = new Feedback();
    item.setTicket(ticket);
    item.setEvent(ticket.getEvent());
    item.setRating(input.rating());
    item.setCommentText(input.comment() == null ? "" : input.comment().trim());
    item.setCreatedAt(TimeUtil.now());
    feedback.saveAndFlush(item);
    return new FeedbackView(
        ticket.getAttendee().getFullName(),
        item.getRating(),
        item.getCommentText(),
        item.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public FeedbackSummary summary(long eventId) {
    var event = events.findById(eventId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    long[] counts = new long[6];
    for (Object[] row : feedback.ratingCounts(eventId))
      counts[((Number) row[0]).intValue()] = ((Number) row[1]).longValue();
    long total = 0;
    long score = 0;
    for (int rating = 1; rating <= 5; rating++) {
      total += counts[rating];
      score += rating * counts[rating];
    }
    List<RatingBreakdown> ratings = new ArrayList<>();
    for (int rating = 5; rating >= 1; rating--)
      ratings.add(new RatingBreakdown(
          rating, counts[rating], total == 0 ? 0 : round(100.0 * counts[rating] / total)));

    boolean hourly = Duration.between(event.getStartsAt(), event.getEndsAt()).toHours() <= 24;
    Map<LocalDateTime, long[]> buckets = new TreeMap<>();
    for (Object[] row : feedback.trendValues(eventId)) {
      LocalDateTime time = (LocalDateTime) row[0];
      LocalDateTime period = hourly ? time.withMinute(0).withSecond(0).withNano(0)
          : time.toLocalDate().atStartOfDay();
      long[] bucket = buckets.computeIfAbsent(period, ignored -> new long[2]);
      bucket[0]++;
      bucket[1] += ((Number) row[1]).intValue();
    }
    List<FeedbackTrendPoint> trend = buckets.entrySet().stream()
        .map(entry -> new FeedbackTrendPoint(entry.getKey(), entry.getValue()[0],
            round((double) entry.getValue()[1] / entry.getValue()[0])))
        .toList();
    return new FeedbackSummary(total, total == 0 ? 0 : round((double) score / total),
        ratings, hourly ? "HOUR" : "DAY", trend);
  }

  @Transactional(readOnly = true)
  public FeedbackPage page(long eventId, String query, int rating, int page) {
    if (!events.existsById(eventId))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện.");
    if (rating < 0 || rating > 5)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số sao lọc phải từ 1 đến 5.");
    if (page < 0 || page > 10000)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
    String term = query == null ? "" : query.trim();
    if (term.length() > 190)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa tìm kiếm quá dài.");
    var found = feedback.search(eventId, rating, term, PageRequest.of(page, 20));
    var items = found.getContent().stream()
        .map(item -> new FeedbackView(item.getTicket().getAttendee().getFullName(),
            item.getRating(), item.getCommentText(), item.getCreatedAt()))
        .toList();
    return new FeedbackPage(items, page, 20, found.getTotalElements());
  }

  private static double round(double value) {
    return Math.round(value * 10) / 10.0;
  }

  @Transactional(readOnly = true)
  public List<MyFeedbackView> mine() {
    String username = org.springframework.security.core.context.SecurityContextHolder.getContext()
        .getAuthentication().getName();
    UserAccount user = users.findByUsernameIgnoreCase(username)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại."));
    return feedback.findByTicketUserIdOrderByIdDesc(user.getId()).stream()
        .map(item -> new MyFeedbackView(item.getTicket().getCode(),
            item.getEvent().getTitle(), item.getRating(), item.getCommentText(),
            item.getCreatedAt()))
        .toList();
  }
}
