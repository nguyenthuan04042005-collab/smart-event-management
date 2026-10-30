package com.event.sems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pending_email_actions")
@Getter
@Setter
public class PendingEmailAction {
  @Id
  @Column(name = "request_id", length = 36)
  private String requestId;

  @Column(nullable = false, length = 20)
  private String purpose;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "previous_email", length = 190)
  private String previousEmail;

  @Column(length = 32)
  private String username;

  @Column(name = "display_name", length = 80)
  private String displayName;

  @Column(nullable = false, length = 190)
  private String email;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String codeHash;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "last_sent_at", nullable = false)
  private LocalDateTime lastSentAt;
}
