package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mail_deliveries")
@Getter
@Setter
public class MailDelivery {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_type", nullable = false, length = 40)
  private String messageType;

  @Column(nullable = false, length = 190)
  private String recipient;

  @Column(nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "error_code", length = 40)
  private String errorCode;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;
}
