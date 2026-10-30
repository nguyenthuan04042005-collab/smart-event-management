package com.event.sems.controller;

import com.event.sems.service.MailSettingsService;
import com.event.sems.service.MailDeliveryService;
import com.event.sems.service.TicketMailService;
import com.event.sems.service.TicketMailQueueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MailSettingsController {
  private final MailSettingsService settings;
  private final MailDeliveryService deliveries;
  private final TicketMailService mail;
  private final TicketMailQueueService ticketMailQueue;

  @GetMapping("/api/v1/admin/mail-settings")
  public MailSettingsService.View view() {
    return settings.view();
  }

  @GetMapping("/api/v1/admin/mail-settings/deliveries")
  public List<MailDeliveryService.View> deliveries() {
    return deliveries.latest();
  }

  @GetMapping("/api/v1/admin/mail-settings/jobs")
  public List<TicketMailQueueService.JobView> ticketJobs() {
    return ticketMailQueue.latest();
  }

  @GetMapping("/api/v1/admin/mail-settings/password")
  public ResponseEntity<Map<String, String>> password() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("password", settings.savedPassword()));
  }

  @PutMapping("/api/v1/admin/mail-settings")
  public MailSettingsService.View save(@RequestBody MailSettingsService.Input input) {
    return settings.save(input);
  }

  @PostMapping("/api/v1/admin/mail-settings/test")
  public Map<String, String> test(@Valid @RequestBody TestMail input) {
    mail.sendTest(input.address());
    return Map.of("message", "Đã gửi email thử. Hãy kiểm tra hộp thư và thư rác.");
  }

  public record TestMail(@NotBlank @Email String address) {}
}
