package com.event.sems.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "venues")
@Getter
@Setter
public class Venue {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organizer_id", nullable = false)
  private UserAccount organizer;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false)
  private int capacity;

  @Column(nullable = false)
  private boolean active = true;
}
