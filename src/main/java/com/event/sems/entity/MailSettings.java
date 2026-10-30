package com.event.sems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mail_settings")
@Getter
@Setter
public class MailSettings {
  @Id private Long id = 1L;
  private boolean enabled;

  @Column(nullable = false, length = 255)
  private String host;

  @Column(nullable = false)
  private int port;

  @Column(nullable = false, length = 190)
  private String username;

  @Column(name = "from_address", nullable = false, length = 190)
  private String fromAddress;

  @Column(name = "encrypted_password", length = 1000)
  private String encryptedPassword;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
