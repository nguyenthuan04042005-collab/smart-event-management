package com.event.sems.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.event.sems.entity.Ticket;
import com.event.sems.repository.TicketRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurveyInvitationServiceTest {
  @Test
  void eligibleTicketCreatesInvitationJob() {
    Ticket ticket = new Ticket();
    ticket.setId(7L);
    TicketRepository tickets = (TicketRepository) Proxy.newProxyInstance(
        TicketRepository.class.getClassLoader(), new Class<?>[] {TicketRepository.class},
        (proxy, method, args) -> {
          if (method.getName().equals("findSurveyCandidates")) return List.of(ticket);
          throw new UnsupportedOperationException(method.getName());
        });
    long[] queued = {0};
    TicketMailQueueService queue = new TicketMailQueueService(null, null, null) {
      @Override public void enqueueSurvey(Ticket candidate) {
        assertThat(candidate).isSameAs(ticket);
        queued[0]++;
      }
    };
    new SurveyInvitationService(tickets, queue).sendDueInvitations();
    assertThat(queued[0]).isEqualTo(1);
  }
}
