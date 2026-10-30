package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.AssignedStaffView;
import com.event.sems.service.EventStaffService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/staff")
@RequiredArgsConstructor
public class EventStaffController {
  private final EventStaffService service;

  @GetMapping
  public List<AssignedStaffView> list(@PathVariable long eventId) {
    return service.list(eventId);
  }

  @PostMapping("/{staffId}")
  public List<AssignedStaffView> assign(@PathVariable long eventId, @PathVariable long staffId) {
    return service.assign(eventId, staffId);
  }

  @DeleteMapping("/{staffId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassign(@PathVariable long eventId, @PathVariable long staffId) {
    service.unassign(eventId, staffId);
  }
}
