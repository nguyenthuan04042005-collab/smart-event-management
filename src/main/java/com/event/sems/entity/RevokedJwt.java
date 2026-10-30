package com.event.sems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "revoked_jwts")
@Getter
@Setter
public class RevokedJwt {
  @Id
  @Column(name = "token_id", length = 36)
  private String tokenId;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;
}
