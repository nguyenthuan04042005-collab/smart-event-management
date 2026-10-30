package com.event.sems.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiDtos {
  private ApiDtos() {}

  public record EventInput(
      @NotBlank @Size(max = 180) String title,
      String description,
      Long venueId,
      @NotNull LocalDateTime startsAt,
      @NotNull LocalDateTime endsAt,
      @Min(1) int capacity,
      @NotBlank String status,
      @Size(max = 500) String bannerUrl,
      @Size(max = 30) String category,
      @Size(max = 500) String onlineUrl,
      List<@NotNull @Valid AgendaInput> agenda,
      List<@NotNull @Valid SpeakerInput> speakers) {}

  public record AgendaInput(
      @NotBlank @Size(max = 180) String title,
      @NotNull LocalDateTime startsAt,
      @NotNull LocalDateTime endsAt,
      @Size(max = 160) String roomName) {}

  public record SpeakerInput(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 500) String biography,
      @Size(max = 180) String topic,
      @Size(max = 500) String imageUrl,
      boolean vip) {}

  public record AgendaView(String title, LocalDateTime startsAt, LocalDateTime endsAt, String roomName) {}

  public record SpeakerView(String name, String biography, String topic, String imageUrl, boolean vip) {}

  public record EventView(
      Long id,
      String title,
      String description,
      Long venueId,
      String venueName,
      String venueAddress,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      int capacity,
      String status,
      long registered,
      String organizerName,
      String approvalStatus,
      String reviewNote,
      String bannerUrl,
      String category,
      String onlineUrl,
      List<AgendaView> agenda,
      List<SpeakerView> speakers,
      String lifecycleStatus,
      String cancelReason,
      LocalDateTime cancelledAt,
      long ticketHistoryCount) {}

  public record EventReviewView(
      String previousStatus, String status, String reviewerName, String note,
      LocalDateTime reviewedAt) {}

  public record AssignedStaffView(Long id, String username, String displayName) {}

  public record PublicEventView(
      Long id,
      String title,
      String description,
      String venueName,
      String venueAddress,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      int capacity,
      String status,
      long registered,
      String organizerName,
      String bannerUrl,
      String category,
      List<AgendaView> agenda,
      List<SpeakerView> speakers,
      String lifecycleStatus,
      String cancelReason) {}

  public record RegistrationInput(
      @NotBlank @Size(max = 120) String fullName,
      @NotBlank @Size(max = 20) String phone,
      @Size(max = 160) String organization,
      @Size(max = 120) String positionTitle) {}

  public record TicketView(
      String code,
      Long eventId,
      String eventTitle,
      String attendeeName,
      String attendeeEmail,
      String attendeeOrganization,
      String attendeePositionTitle,
      String status,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      String venueName,
      String onlineUrl,
      LocalDateTime checkedInAt,
      LocalDateTime cancelledAt,
      boolean feedbackSent,
      String eventLifecycleStatus,
      String eventCancelReason) {}

  public record CheckinInput(String qrToken, String ticketCode, @NotBlank String method) {}

  public record CheckinResult(
      String message, String attendeeName, String ticketCode, LocalDateTime checkedInAt) {}

  public record CheckinCandidate(
      String attendeeName, String attendeeEmail, String ticketCode, String status) {}

  public record CheckinSearchView(
      List<CheckinCandidate> items, int page, int pageSize, long total) {}

  public record DashboardView(
      Long eventId,
      String eventTitle,
      int capacity,
      long registered,
      long checkedIn,
      double attendanceRate,
      double occupancyRate,
      String occupancyAlert,
      String eventLifecycleStatus,
      String eventCancelReason) {}

  public record FeedbackInput(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {}

  public record FeedbackView(
      String attendeeName, int rating, String comment, LocalDateTime createdAt) {}

  public record RatingBreakdown(int rating, long count, double percentage) {}

  public record FeedbackTrendPoint(LocalDateTime period, long count, double averageRating) {}

  public record FeedbackSummary(
      long count,
      double averageRating,
      List<RatingBreakdown> ratings,
      String trendInterval,
      List<FeedbackTrendPoint> trend) {}

  public record FeedbackPage(List<FeedbackView> items, int page, int pageSize, long total) {}

  public record MyFeedbackView(
      String ticketCode, String eventTitle, int rating, String comment,
      LocalDateTime createdAt) {}

  public record VenueInput(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 255) String address,
      @Min(1) int capacity) {}

  public record VenueView(
      Long id, String name, String address, String organizerName, int capacity, long eventCount,
      long approvedEventCount, boolean active) {}

  public record ErrorView(String message, LocalDateTime timestamp) {}
}
