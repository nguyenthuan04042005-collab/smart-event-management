package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "event_reviews")
@Getter
@Setter
public class EventReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reviewer_id", nullable = false)
  private UserAccount reviewer;

  @Column(name = "previous_status", nullable = false, length = 20)
  private String previousStatus;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(length = 500)
  private String note;

  @Column(name = "reviewed_at", nullable = false)
  private LocalDateTime reviewedAt;
}
