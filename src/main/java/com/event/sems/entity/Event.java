package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "venue_id")
  private Venue venue;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organizer_id")
  private UserAccount organizer;

  @Column(nullable = false, length = 180)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "banner_url", length = 500)
  private String bannerUrl;

  @Column(nullable = false, length = 30)
  private String category = "OTHER";

  @Column(name = "online_url", length = 500)
  private String onlineUrl;

  @Column(name = "approval_status", nullable = false, length = 20)
  private String approvalStatus = "DRAFT";

  @Column(name = "review_note", length = 500)
  private String reviewNote;

  @Column(name = "lifecycle_status", nullable = false, length = 20)
  private String lifecycleStatus = "SCHEDULED";

  @Column(name = "cancel_reason", length = 500)
  private String cancelReason;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cancelled_by_id")
  private UserAccount cancelledBy;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC, id ASC")
  private java.util.List<AgendaItem> agenda = new java.util.ArrayList<>();

  @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private java.util.List<Speaker> speakers = new java.util.ArrayList<>();

  @Column(name = "starts_at", nullable = false)
  private LocalDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private LocalDateTime endsAt;

  @Column(nullable = false)
  private int capacity;

  @Column(nullable = false, length = 20)
  private String status = "OPEN";

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
