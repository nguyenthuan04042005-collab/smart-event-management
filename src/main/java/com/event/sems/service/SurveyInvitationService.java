package com.event.sems.service;

import com.event.sems.common.TimeUtil;
import com.event.sems.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SurveyInvitationService {
  private final TicketRepository tickets;
  private final TicketMailQueueService queue;

  @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
  public void sendDueInvitations() {
    for (var ticket : tickets.findSurveyCandidates(TimeUtil.now(), PageRequest.of(0, 50))) {
      queue.enqueueSurvey(ticket);
    }
  }
}
