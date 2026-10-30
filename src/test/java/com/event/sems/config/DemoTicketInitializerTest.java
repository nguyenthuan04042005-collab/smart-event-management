package com.event.sems.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.event.sems.common.TimeUtil;
import com.event.sems.entity.Event;
import com.event.sems.entity.Ticket;
import com.event.sems.repository.TicketRepository;
import com.event.sems.service.TicketTokenService;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoTicketInitializerTest {
  @Test
  void sampleTicketGetsVerifiableQrToken() {
    TicketRepository tickets = mock(TicketRepository.class);
    TicketTokenService tokens =
        new TicketTokenService("LOCAL_DEMO_CHANGE_THIS_SECRET_BEFORE_SHARING_2026");
    Event event = new Event();
    event.setId(5L);
    Ticket ticket = new Ticket();
    ticket.setId(9L);
    ticket.setEvent(event);
    ticket.setIssuedAt(LocalDateTime.now(TimeUtil.ZONE).withNano(0));
    ticket.setQrToken("DEMO_PENDING");
    when(tickets.findByQrToken("DEMO_PENDING")).thenReturn(List.of(ticket));

    new DemoTicketInitializer(tickets, tokens).run(null);

    var payload = tokens.verify(ticket.getQrToken());
    assertThat(payload.ticketId()).isEqualTo(9L);
    assertThat(payload.eventId()).isEqualTo(5L);
    assertThat(payload.epochSecond())
        .isEqualTo(ZonedDateTime.of(ticket.getIssuedAt(), TimeUtil.ZONE).toEpochSecond());
  }
}
