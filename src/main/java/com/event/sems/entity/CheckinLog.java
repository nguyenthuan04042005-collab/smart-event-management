package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "checkin_logs")
@Getter
@Setter
public class CheckinLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id")
  private Ticket ticket;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  @Column(nullable = false, length = 12)
  private String method;

  @Column(name = "operator_name", nullable = false, length = 80)
  private String operatorName;

  @Column(name = "checked_in_at", nullable = false)
  private LocalDateTime checkedInAt;
}
