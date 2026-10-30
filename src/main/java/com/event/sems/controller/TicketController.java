package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.*;
import com.event.sems.service.TicketService;
import com.event.sems.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketController {
  private final TicketService service;
  private final EventService events;

  @PostMapping("/events/{eventId}/registrations")
  @ResponseStatus(HttpStatus.CREATED)
  public TicketView register(
      @PathVariable long eventId, @Valid @RequestBody RegistrationInput input) {
    return service.register(eventId, input);
  }

  @GetMapping("/events/{eventId}/registrations")
  public List<TicketView> list(@PathVariable long eventId) {
    events.requireManager(eventId);
    return service.list(eventId);
  }

  @GetMapping("/tickets/mine")
  public List<TicketView> mine() {
    return service.mine();
  }

  @GetMapping("/tickets/{code}")
  public TicketView get(@PathVariable String code) {
    return service.get(code);
  }

  @PostMapping("/tickets/{code}/cancel")
  public TicketView cancel(@PathVariable String code) {
    return service.cancel(code);
  }

  @GetMapping(value = "/tickets/{code}/qr", produces = MediaType.IMAGE_PNG_VALUE)
  public byte[] qr(@PathVariable String code) {
    return service.qr(code);
  }
}
