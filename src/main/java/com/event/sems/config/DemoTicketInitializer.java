package com.event.sems.config;

import com.event.sems.common.TimeUtil;
import com.event.sems.repository.TicketRepository;
import com.event.sems.service.TicketTokenService;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DemoTicketInitializer implements ApplicationRunner {
  private final TicketRepository tickets;
  private final TicketTokenService tokens;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (var ticket : tickets.findByQrToken("DEMO_PENDING")) {
      long issuedAt = ZonedDateTime.of(ticket.getIssuedAt(), TimeUtil.ZONE).toEpochSecond();
      ticket.setQrToken(tokens.issue(ticket.getId(), ticket.getEvent().getId(), issuedAt));
    }
  }
}
