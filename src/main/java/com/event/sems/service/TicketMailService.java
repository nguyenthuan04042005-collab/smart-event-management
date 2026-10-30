package com.event.sems.service;

import com.event.sems.common.ApiException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketMailService {
  private static final Logger log = LoggerFactory.getLogger(TicketMailService.class);
  private static final DateTimeFormatter EVENT_TIME =
      DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
  private final MailSettingsService settings;
  private final MailDeliveryService deliveries;

  public enum SendResult {
    SMTP_ACCEPTED, RETRYABLE_FAILURE, PERMANENT_FAILURE, CONFIG_FAILURE, DISABLED
  }

  @Value("${app.public-base-url}")
  private String baseUrl;

  public boolean isEnabled() {
    return settings.connection().enabled();
  }

  public SendResult send(String address, String eventTitle, String attendeeName, String venue,
      LocalDateTime startsAt, String ticketCode, byte[] qrPng) {
    String ticketUrl = url("/pages/ticket.html?code=" + ticketCode);
    String time = startsAt.format(EVENT_TIME);
    String plainText = "Xin chào " + attendeeName + ",\n\n"
        + "Bạn đã đăng ký thành công sự kiện " + eventTitle + ".\n"
        + "Thời gian: " + time + "\nĐịa điểm: " + venue + "\nMã vé: " + ticketCode
        + "\nXem vé: " + ticketUrl + "\n\n"
        + "Vui lòng xuất trình mã QR khi check-in. Ảnh QR cũng được đính kèm trong email này.";
    return deliver("TICKET", address, "Vé tham dự: " + eventTitle, plainText,
        ticketHtml(eventTitle, attendeeName, venue, time, ticketCode, ticketUrl), qrPng);
  }

  static String ticketHtml(String eventTitle, String attendeeName, String venue, String time,
      String ticketCode, String ticketUrl) {
    return """
        <!doctype html>
        <html lang="vi">
        <head><meta charset="utf-8"><title>Vé tham dự SEMS</title></head>
        <body style="margin:0;padding:24px;background:#f4f6fa;font-family:Arial,sans-serif;color:#17213a">
          <table role="presentation" cellpadding="0" cellspacing="0"
              style="max-width:600px;width:100%%;margin:auto;background:#ffffff;border:1px solid #e2e7f0;border-radius:14px">
            <tr><td style="padding:28px 32px 16px;border-top:6px solid #d31932">
              <p style="margin:0 0 8px;color:#d31932;font-size:13px;font-weight:bold;letter-spacing:2px">SEMS · VÉ ĐIỆN TỬ</p>
              <h1 style="margin:0;font-size:26px;line-height:1.3">%s</h1>
            </td></tr>
            <tr><td style="padding:0 32px">
              <p>Xin chào <strong>%s</strong>, bạn đã đăng ký tham dự thành công.</p>
              <p style="margin:8px 0"><strong>Thời gian:</strong> %s</p>
              <p style="margin:8px 0"><strong>Địa điểm:</strong> %s</p>
              <p style="margin:8px 0"><strong>Mã vé:</strong> %s</p>
            </td></tr>
            <tr><td style="padding:20px 32px;text-align:center">
              <img src="cid:ticketQr" alt="Mã QR của vé" width="240" height="240"
                  style="display:block;margin:auto;border:1px solid #e2e7f0" />
              <p style="color:#60708c;font-size:14px">Xuất trình mã QR này cho nhân viên tại cổng check-in.</p>
              <a href="%s" style="display:inline-block;padding:13px 22px;background:#d31932;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:bold">Xem vé của tôi</a>
            </td></tr>
            <tr><td style="padding:12px 32px 28px;color:#60708c;font-size:13px;border-top:1px solid #e2e7f0">
              Ảnh QR được đính kèm để bạn lưu trên thiết bị. Đăng nhập bằng tài khoản đã đăng ký để xem vé trên website. Không chia sẻ mã vé với người khác.
            </td></tr>
          </table>
        </body>
        </html>
        """.formatted(MailTemplates.escape(eventTitle), MailTemplates.escape(attendeeName),
        MailTemplates.escape(time), MailTemplates.escape(venue), MailTemplates.escape(ticketCode),
        MailTemplates.escape(ticketUrl));
  }

  public void sendEventCancellation(String address, String eventTitle, String reason) {
    String body = "Sự kiện " + eventTitle + " đã bị hủy.\nLý do: " + reason
        + "\nVé QR không còn hiệu lực.";
    notify("EVENT_CANCELLED", address, "Thông báo hủy sự kiện: " + eventTitle, body,
        null, "Xem vé của tôi", url("/pages/my-tickets.html"),
        "Đăng nhập website để xem lịch sử vé.");
  }

  public SendResult sendSurveyInvitation(String address, String eventTitle, String ticketCode) {
    return notify("SURVEY_INVITATION", address, "Mời đánh giá sự kiện: " + eventTitle,
        "Cảm ơn bạn đã tham dự sự kiện " + eventTitle
            + ". Hãy chia sẻ trải nghiệm của bạn qua liên kết dưới đây.",
        null, "Đánh giá sự kiện", url("/pages/feedback.html?code=" + ticketCode),
        "Đăng nhập bằng tài khoản đã đăng ký vé để gửi đánh giá.");
  }

  public void sendVerification(String address, String token) {
    notify("VERIFY_EMAIL", address, "Xác minh email tài khoản SEMS",
        "Bạn đã được tạo tài khoản SEMS. Xác minh địa chỉ email để sử dụng các chức năng tài khoản.",
        null, "Xác minh email", url("/verify-email.html?token=" + token),
        "Liên kết có hiệu lực trong 30 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
  }

  public void sendConfirmationCode(String address, String code) {
    if (!isEnabled())
      throw new ApiException(HttpStatus.CONFLICT, "Hệ thống chưa bật gửi email.");
    SendResult result = notify("CONFIRMATION_CODE", address, "Mã xác minh email SEMS",
        "Dùng mã sau để xác minh địa chỉ email của bạn:", code, null, null,
        "Mã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
    if (result != SendResult.SMTP_ACCEPTED)
      throw new ApiException(HttpStatus.BAD_GATEWAY,
          "Không gửi được mã xác minh. Hãy kiểm tra email hoặc liên hệ quản trị viên.");
  }

  public void sendPasswordReset(String address, String token) {
    notify("PASSWORD_RESET", address, "Đặt lại mật khẩu SEMS",
        "Bạn đã yêu cầu đặt lại mật khẩu tài khoản SEMS.", null,
        "Đặt lại mật khẩu", url("/reset-password.html?token=" + token),
        "Liên kết có hiệu lực trong 20 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
  }

  public void sendPasswordChanged(String address) {
    notify("PASSWORD_CHANGED", address, "Mật khẩu SEMS đã được thay đổi",
        "Mật khẩu tài khoản SEMS của bạn đã được thay đổi.", null, null, null,
        "Nếu không phải bạn thực hiện, hãy liên hệ quản trị viên.");
  }

  public void sendTest(String address) {
    if (!isEnabled())
      throw new ApiException(HttpStatus.CONFLICT, "Hãy bật gửi email và lưu cấu hình trước.");
    SendResult result = deliver("SMTP_TEST", address, "Kiểm tra cấu hình email SEMS",
        "SEMS đã kết nối và gửi email thử bằng cấu hình SMTP hiện tại.", null, null);
    if (result != SendResult.SMTP_ACCEPTED)
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Không gửi được email thử. Kiểm tra máy chủ, tài khoản, mật khẩu ứng dụng và log"
              + " server.");
  }

  private SendResult notify(String type, String address, String subject, String body,
      String highlight, String actionLabel, String actionUrl, String note) {
    String plain = body + (highlight == null ? "" : "\n" + highlight)
        + (actionUrl == null ? "" : "\n" + actionUrl) + "\n" + note;
    return deliver(type, address, subject, plain,
        MailTemplates.message(subject, body, highlight, actionLabel, actionUrl, note), null);
  }

  private String url(String path) {
    return baseUrl.replaceAll("/$", "") + path;
  }

  private SendResult deliver(String type, String address, String subject, String plain,
      String html, byte[] qrPng) {
    var connection = settings.connection();
    if (!connection.enabled()) return SendResult.DISABLED;
    long deliveryId = deliveries.begin(type, address);
    try {
      JavaMailSenderImpl sender = sender(connection);
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, html != null || qrPng != null,
              StandardCharsets.UTF_8.name());
      helper.setFrom(connection.fromAddress());
      helper.setTo(address);
      helper.setSubject(subject);
      if (html == null) helper.setText(plain);
      else helper.setText(plain, html);
      if (qrPng != null) {
        helper.addInline("ticketQr", new ByteArrayResource(qrPng), "image/png");
        helper.addAttachment("ve-qr.png", new ByteArrayResource(qrPng), "image/png");
      }
      sender.send(message);
    } catch (Exception ex) {
      SendResult result = classify(ex);
      completeDelivery(deliveryId, "FAILED", result.name());
      log.warn("Không gửi được email {} tới {}.", subject, address, ex);
      return result;
    }
    completeDelivery(deliveryId, "SMTP_ACCEPTED", null);
    return SendResult.SMTP_ACCEPTED;
  }

  private void completeDelivery(long id, String status, String errorCode) {
    try {
      deliveries.complete(id, status, errorCode);
    } catch (Exception ex) {
      log.error("Không cập nhật được lịch sử gửi thư {} với trạng thái {}.", id, status, ex);
    }
  }

  private SendResult classify(Exception failure) {
    var pending = new ArrayDeque<Throwable>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    pending.add(failure);
    while (!pending.isEmpty()) {
      Throwable cause = pending.removeFirst();
      if (!seen.add(cause)) continue;
      if (cause instanceof MailAuthenticationException
          || cause instanceof AuthenticationFailedException)
        return SendResult.CONFIG_FAILURE;
      if (cause instanceof AddressException)
        return SendResult.PERMANENT_FAILURE;
      if (cause instanceof SendFailedException invalid
          && invalid.getInvalidAddresses() != null
          && invalid.getInvalidAddresses().length > 0)
        return SendResult.PERMANENT_FAILURE;
      if (cause instanceof MailSendException mailError
          && mailError.getMessageExceptions() != null)
        Collections.addAll(pending, mailError.getMessageExceptions());
      if (cause instanceof MessagingException mailError && mailError.getNextException() != null)
        pending.add(mailError.getNextException());
      if (cause.getCause() != null) pending.add(cause.getCause());
    }
    return SendResult.RETRYABLE_FAILURE;
  }

  private JavaMailSenderImpl sender(MailSettingsService.Connection connection) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(connection.host());
    sender.setPort(connection.port());
    sender.setUsername(connection.username());
    sender.setPassword(connection.password());
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    Properties properties = sender.getJavaMailProperties();
    properties.put("mail.smtp.auth", "true");
    properties.put("mail.smtp.starttls.enable", "true");
    properties.put("mail.smtp.connectiontimeout", "5000");
    properties.put("mail.smtp.timeout", "5000");
    properties.put("mail.smtp.writetimeout", "5000");
    return sender;
  }
}
