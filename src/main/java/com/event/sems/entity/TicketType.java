package com.event.sems.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ticket_types")
@Getter
@Setter
public class TicketType {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  @Column(nullable = false, length = 80)
  private String name = "Vé miễn phí";

  @Column(nullable = false)
  private int quota;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price = BigDecimal.ZERO;
}
