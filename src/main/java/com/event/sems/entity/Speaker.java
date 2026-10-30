package com.event.sems.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "speakers")
@Getter
@Setter
public class Speaker {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id")
  private Event event;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String biography;

  @Column(length = 180)
  private String topic;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "is_vip", nullable = false)
  private boolean vip;
}
