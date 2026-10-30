package com.event.sems.controller;

import com.event.sems.dto.ApiDtos.VenueInput;
import com.event.sems.dto.ApiDtos.VenueView;
import com.event.sems.service.VenueService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {
  private final VenueService service;

  @GetMapping
  public List<VenueView> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VenueView create(@Valid @RequestBody VenueInput input) {
    return service.create(input);
  }

  @PutMapping("/{id}")
  public VenueView update(@PathVariable long id, @Valid @RequestBody VenueInput input) {
    return service.update(id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id) {
    service.delete(id);
  }

  @PatchMapping("/{id}/active")
  public VenueView setActive(@PathVariable long id, @RequestBody ActiveInput input) {
    return service.setActive(id, input.active());
  }

  public record ActiveInput(boolean active) {}
}
