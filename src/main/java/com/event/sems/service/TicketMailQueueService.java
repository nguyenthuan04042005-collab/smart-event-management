package com.event.sems.service;

import com.event.sems.common.TimeUtil;
import com.event.sems.entity.Ticket;
import com.event.sems.entity.TicketMailJob;
import com.event.sems.repository.TicketMailJobRepository;
import com.event.sems.repository.FeedbackRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class TicketMailQueueService {
  private final TicketMailJobRepository jobs;
  private final TicketMailService mail;
  private final FeedbackRepository feedback;

  public record JobView(Long id, String messageType, String eventTitle, String ticketCode, String recipient,
      String status, int attempts, LocalDateTime nextAttemptAt, String lastErrorCode) {}

  @Transactional
  public long enqueue(Ticket ticket) {
    TicketMailJob job = new TicketMailJob();
    job.setTicket(ticket);
    job.setRecipient(ticket.getAttendee().getEmail());
    job.setCreatedAt(TimeUtil.now());
    job.setNextAttemptAt(TimeUtil.now());
    return jobs.saveAndFlush(job).getId();
  }

  @Transactional
  public void enqueueSurvey(Ticket ticket) {
    if (jobs.existsByTicketIdAndMessageType(ticket.getId(), "SURVEY_INVITATION")) return;
    TicketMailJob job = new TicketMailJob();
    job.setTicket(ticket);
    job.setMessageType("SURVEY_INVITATION");
    job.setRecipient(ticket.getUser().getEmail() == null ? "" : ticket.getUser().getEmail());
    job.setCreatedAt(TimeUtil.now());
    job.setNextAttemptAt(TimeUtil.now());
    jobs.saveAndFlush(job);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(long id) {
    TicketMailJob job = jobs.findForUpdate(id).orElse(null);
    if (job == null || !"PENDING".equals(job.getStatus())
        || job.getNextAttemptAt().isAfter(TimeUtil.now())) return;
    Ticket ticket = job.getTicket();
    boolean survey = "SURVEY_INVITATION".equals(job.getMessageType());
    if ("CANCELLED".equals(ticket.getStatus())
        || "CANCELLED".equals(ticket.getEvent().getLifecycleStatus())
        || (survey && (feedback.existsByTicketId(ticket.getId()) || ticket.getCheckedInAt() == null
            || ticket.getEvent().getEndsAt().isAfter(TimeUtil.now())))) {
      job.setStatus("SKIPPED");
      job.setNextAttemptAt(null);
      job.setCompletedAt(TimeUtil.now());
      return;
    }
    if (!mail.isEnabled()) {
      job.setStatus("PAUSED");
      job.setNextAttemptAt(null);
      job.setLastErrorCode("DISABLED");
      return;
    }
    var event = ticket.getEvent();
    String venue = event.getVenue() == null ? "Trực tuyến"
        : event.getVenue().getName() + " — " + event.getVenue().getAddress();
    var result = survey
        ? mail.sendSurveyInvitation(job.getRecipient(), event.getTitle(), ticket.getCode())
        : mail.send(job.getRecipient(), event.getTitle(), ticket.getAttendee().getFullName(),
            venue, event.getStartsAt(), ticket.getCode(), qr(ticket.getQrToken()));
    if (result == TicketMailService.SendResult.DISABLED
        || result == TicketMailService.SendResult.CONFIG_FAILURE) {
      job.setStatus("PAUSED");
      job.setNextAttemptAt(null);
      job.setLastErrorCode(result.name());
      return;
    }
    job.setAttempts(job.getAttempts() + 1);
    job.setLastErrorCode(result == TicketMailService.SendResult.SMTP_ACCEPTED
        ? null : result.name());
    if (result == TicketMailService.SendResult.SMTP_ACCEPTED) {
      job.setStatus("SMTP_ACCEPTED");
      job.setNextAttemptAt(null);
      job.setCompletedAt(TimeUtil.now());
    } else if (result == TicketMailService.SendResult.PERMANENT_FAILURE
        || job.getAttempts() >= 3) {
      job.setStatus("FAILED");
      job.setNextAttemptAt(null);
      job.setCompletedAt(TimeUtil.now());
    } else {
      job.setNextAttemptAt(TimeUtil.now().plusMinutes(5));
    }
  }

  @Transactional(readOnly = true)
  public List<Long> dueIds() {
    return jobs.dueIds(TimeUtil.now(), PageRequest.of(0, 50));
  }

  @Transactional(readOnly = true)
  public List<JobView> latest() {
    return jobs.findByOrderByIdDesc(PageRequest.of(0, 50)).stream()
        .map(job -> new JobView(job.getId(), job.getMessageType(), job.getTicket().getEvent().getTitle(),
            job.getTicket().getCode(), job.getRecipient(), job.getStatus(),
            job.getAttempts(), job.getNextAttemptAt(), job.getLastErrorCode()))
        .toList();
  }

  private static byte[] qr(String token) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(
          new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 320, 320), "PNG", out);
      return out.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể tạo ảnh QR để gửi vé.", ex);
    }
  }
}
