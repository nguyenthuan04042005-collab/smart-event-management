package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.DashboardView;
import com.event.sems.service.DashboardService;
import com.event.sems.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {
  private final DashboardService service;
  private final EventService events;

  @GetMapping
  public DashboardView get(@PathVariable long eventId) {
    events.requireManager(eventId);
    return service.get(eventId);
  }
}
