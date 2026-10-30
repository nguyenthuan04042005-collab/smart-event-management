package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "attendees")
@Getter
@Setter
public class Attendee {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "full_name", nullable = false, length = 120)
  private String fullName;

  @Column(nullable = false, length = 190)
  private String email;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(name = "organization", length = 160)
  private String organization;

  @Column(name = "position_title", length = 120)
  private String positionTitle;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
