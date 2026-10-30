package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.*;
import com.event.sems.service.FeedbackService;
import com.event.sems.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeedbackController {
  private final FeedbackService service;
  private final EventService events;

  @PostMapping("/tickets/{code}/feedback")
  @ResponseStatus(HttpStatus.CREATED)
  public FeedbackView submit(@PathVariable String code, @Valid @RequestBody FeedbackInput input) {
    return service.submit(code, input);
  }

  @GetMapping("/events/{eventId}/feedback")
  public FeedbackSummary summary(@PathVariable long eventId) {
    events.requireManager(eventId);
    return service.summary(eventId);
  }

  @GetMapping("/events/{eventId}/feedback/items")
  public FeedbackPage items(@PathVariable long eventId,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "0") int rating,
      @RequestParam(defaultValue = "0") int page) {
    events.requireManager(eventId);
    return service.page(eventId, query, rating, page);
  }

  @GetMapping("/feedback/mine")
  public List<MyFeedbackView> mine() {
    return service.mine();
  }
}
