package com.event.sems.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.event.sems.entity.Attendee;
import com.event.sems.entity.Event;
import com.event.sems.entity.Ticket;
import com.event.sems.entity.TicketMailJob;
import com.event.sems.repository.TicketMailJobRepository;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketMailQueueServiceTest {
  private TicketMailQueueService queue;
  private TicketMailJob job;
  private FakeMail mail;

  private static class FakeMail extends TicketMailService {
    SendResult result = SendResult.SMTP_ACCEPTED;
    int calls;

    FakeMail() { super(null, null); }

    @Override public boolean isEnabled() { return true; }

    @Override public SendResult send(String address, String eventTitle, String attendeeName,
        String venue, LocalDateTime startsAt, String ticketCode, byte[] qrPng) {
      calls++;
      assertThat(qrPng).isNotEmpty();
      return result;
    }
  }

  @BeforeEach
  void setUp() {
    Event event = new Event();
    event.setTitle("Hội thảo");
    event.setStartsAt(LocalDateTime.now().plusDays(2));
    Attendee attendee = new Attendee();
    attendee.setFullName("Khách mời");
    attendee.setEmail("khach@example.com");
    Ticket ticket = new Ticket();
    ticket.setEvent(event);
    ticket.setAttendee(attendee);
    ticket.setCode("ticket-1");
    ticket.setQrToken("qr-token");
    job = new TicketMailJob();
    job.setTicket(ticket);
    job.setRecipient(attendee.getEmail());
    job.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
    TicketMailJobRepository repository = (TicketMailJobRepository) Proxy.newProxyInstance(
        TicketMailJobRepository.class.getClassLoader(),
        new Class<?>[] {TicketMailJobRepository.class},
        (proxy, method, args) -> {
          if (method.getName().equals("findForUpdate")) return Optional.of(job);
          throw new UnsupportedOperationException(method.getName());
        });
    mail = new FakeMail();
    queue = new TicketMailQueueService(repository, mail, null);
  }

  @Test
  void smtpAcceptedStopsQueueAfterOneAttempt() {
    queue.process(1L);
    queue.process(1L);
    assertThat(job.getStatus()).isEqualTo("SMTP_ACCEPTED");
    assertThat(job.getAttempts()).isEqualTo(1);
    assertThat(mail.calls).isEqualTo(1);
  }

  @Test
  void transientFailureRetriesAfterFiveMinutesAndStopsAtThreeAttempts() {
    mail.result = TicketMailService.SendResult.RETRYABLE_FAILURE;
    for (int attempt = 1; attempt <= 3; attempt++) {
      LocalDateTime before = LocalDateTime.now();
      queue.process(1L);
      assertThat(job.getAttempts()).isEqualTo(attempt);
      if (attempt < 3) {
        assertThat(job.getStatus()).isEqualTo("PENDING");
        assertThat(job.getNextAttemptAt()).isAfter(before.plusMinutes(4));
        job.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
      }
    }
    assertThat(job.getStatus()).isEqualTo("FAILED");
    assertThat(job.getNextAttemptAt()).isNull();
  }

  @Test
  void authenticationFailurePausesWithoutSpendingAttempt() {
    mail.result = TicketMailService.SendResult.CONFIG_FAILURE;
    queue.process(1L);
    assertThat(job.getStatus()).isEqualTo("PAUSED");
    assertThat(job.getAttempts()).isZero();
  }
}
