package com.event.sems.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

class TicketMailServiceTest {
  @Test
  void ticketHtmlIncludesDetailsAndEscapesUserContent() {
    String html = TicketMailService.ticketHtml(
        "Hội thảo <AI>", "Nguyễn & An", "Hội trường A", "08:30, 30/10/2026",
        "ticket-123", "https://example.test/pages/ticket.html?code=ticket-123");

    assertThat(html).contains("Hội thảo &lt;AI&gt;", "Nguyễn &amp; An", "Hội trường A",
        "08:30, 30/10/2026", "ticket-123", "cid:ticketQr", "Xem vé của tôi");
    assertThat(html).doesNotContain("Hội thảo <AI>");
  }

  @Test
  void notificationHtmlEscapesMessageAndKeepsActionLink() {
    String html = MailTemplates.message("Mã xác minh", "Xin chào <b>Lan</b>",
        "123456", "Xác minh", "https://example.test/verify?a=1&b=2", "Hết hạn sau 10 phút.");

    assertThat(html).contains("Xin chào &lt;b&gt;Lan&lt;/b&gt;", "123456",
        "https://example.test/verify?a=1&amp;b=2", "Hết hạn sau 10 phút.");
    assertThat(html).doesNotContain("<b>Lan</b>");
  }

  @Test
  void notificationContainsHtmlAndPlainTextAlternatives() throws Exception {
    var message = new JavaMailSenderImpl().createMimeMessage();
    var helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setText("Code: 123456", "<strong>Code: 123456</strong>");
    message.saveChanges();

    assertThat(textPart(message, "text/plain")).isEqualTo("Code: 123456");
    assertThat(textPart(message, "text/html")).contains("<strong>Code: 123456</strong>");
  }

  private String textPart(Part part, String type) throws Exception {
    Object content = part.getContent();
    if (content instanceof MimeMultipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        String result = textPart(multipart.getBodyPart(i), type);
        if (result != null) return result;
      }
    }
    if (part.isMimeType(type)) return content.toString();
    return null;
  }
}
