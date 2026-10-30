package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mail_jobs")
@Getter
@Setter
public class TicketMailJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Column(name = "message_type", nullable = false, length = 30)
  private String messageType = "TICKET";

  @Column(nullable = false, length = 190)
  private String recipient;

  @Column(nullable = false, length = 20)
  private String status = "PENDING";

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private LocalDateTime nextAttemptAt;

  @Column(name = "last_error_code", length = 40)
  private String lastErrorCode;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;
}
