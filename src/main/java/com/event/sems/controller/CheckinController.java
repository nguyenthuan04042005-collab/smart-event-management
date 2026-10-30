package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.*;
import com.event.sems.service.CheckinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/check-ins")
@RequiredArgsConstructor
public class CheckinController {
  private final CheckinService service;

  @GetMapping("/search")
  public CheckinSearchView search(@PathVariable long eventId, @RequestParam String query,
      @RequestParam(defaultValue = "0") int page) {
    return service.search(eventId, query, page);
  }

  @PostMapping
  public CheckinResult checkin(
      @PathVariable long eventId, @Valid @RequestBody CheckinInput input, Authentication auth) {
    return service.checkin(eventId, input, auth.getName());
  }
}
