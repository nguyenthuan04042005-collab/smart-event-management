package com.event.sems.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketMailJobScheduler {
  private final TicketMailQueueService queue;

  @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
  public void sendDueMail() {
    for (long id : queue.dueIds()) {
      try {
        queue.process(id);
      } catch (Exception ex) {
        log.error("Không xử lý được công việc gửi thư {}.", id, ex);
      }
    }
  }
}
