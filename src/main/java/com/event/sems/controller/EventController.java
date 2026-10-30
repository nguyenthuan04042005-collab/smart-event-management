package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.*;
import com.event.sems.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {
  private final EventService service;

  @GetMapping("/events")
  public List<EventView> list() {
    return service.list();
  }

  @GetMapping("/public/events")
  public List<PublicEventView> publicEvents() {
    return service.publicEvents();
  }

  @GetMapping("/public/events/{id}")
  public PublicEventView publicEvent(@PathVariable long id) {
    return service.publicEvent(id);
  }

  @GetMapping("/events/{id}")
  public EventView get(@PathVariable long id) {
    return service.get(id);
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public EventView create(@Valid @RequestBody EventInput input) {
    return service.create(input);
  }

  @PutMapping("/events/{id}")
  public EventView update(@PathVariable long id, @Valid @RequestBody EventInput input) {
    return service.update(id, input);
  }

  @PostMapping("/events/{id}/submit")
  public EventView submit(@PathVariable long id) {
    return service.submit(id);
  }

  @PostMapping("/events/{id}/withdraw")
  public EventView withdraw(@PathVariable long id) {
    return service.withdraw(id);
  }

  @DeleteMapping("/events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDraft(@PathVariable long id) {
    service.deleteDraft(id);
  }

  @PostMapping("/events/{id}/cancel")
  public EventView cancel(@PathVariable long id, @Valid @RequestBody CancelInput input) {
    return service.cancel(id, input.reason());
  }

  @PostMapping("/admin/events/{id}/review")
  public EventView review(@PathVariable long id, @Valid @RequestBody ReviewInput input) {
    return service.review(id, input.decision(), input.note());
  }

  @PostMapping("/admin/events/{id}/reconsider")
  public EventView reconsider(@PathVariable long id, @Valid @RequestBody ReconsiderInput input) {
    return service.reconsider(id, input.reason());
  }

  @GetMapping("/events/{id}/reviews")
  public List<EventReviewView> reviewHistory(@PathVariable long id) {
    return service.reviewHistory(id);
  }

  public record ReviewInput(@jakarta.validation.constraints.NotBlank String decision,
      @jakarta.validation.constraints.Size(max = 500) String note) {}

  public record ReconsiderInput(
      @jakarta.validation.constraints.NotBlank
      @jakarta.validation.constraints.Size(max = 500) String reason) {}

  public record CancelInput(
      @jakarta.validation.constraints.NotBlank
      @jakarta.validation.constraints.Size(max = 500) String reason) {}
}
