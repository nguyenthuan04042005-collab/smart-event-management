package com.event.sems.controller;

import com.event.sems.service.ReportService;
import com.event.sems.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/reports")
@RequiredArgsConstructor
public class ReportController {
  private final ReportService reports;
  private final EventService events;

  @GetMapping("/feedback.xlsx")
  public ResponseEntity<byte[]> feedbackExcel(@PathVariable long eventId) {
    events.requireManager(eventId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=event-" + eventId + "-feedback.xlsx")
        .contentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(reports.feedbackExcel(eventId));
  }

  @GetMapping("/{type}")
  public ResponseEntity<byte[]> download(@PathVariable long eventId, @PathVariable String type) {
    events.requireManager(eventId);
    byte[] body = reports.csv(eventId, type);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=event-" + eventId + "-" + type + ".csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(body);
  }
}
