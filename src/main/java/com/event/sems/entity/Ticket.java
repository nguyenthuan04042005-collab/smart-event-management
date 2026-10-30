package com.event.sems.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tickets")
@Getter
@Setter
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_type_id")
  private TicketType ticketType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attendee_id")
  private Attendee attendee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(name = "qr_token", nullable = false, length = 700)
  private String qrToken;

  @Column(name = "email_key", nullable = false, length = 190)
  private String emailKey;

  @Column(name = "phone_key", nullable = false, length = 20)
  private String phoneKey;

  @Column(nullable = false, length = 20)
  private String status = "ISSUED";

  @Column(name = "issued_at", nullable = false, updatable = false)
  private LocalDateTime issuedAt;

  @Column(name = "checked_in_at")
  private LocalDateTime checkedInAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

}
