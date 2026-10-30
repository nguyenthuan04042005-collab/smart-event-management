package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.common.TimeUtil;
import com.event.sems.entity.MailSettings;
import com.event.sems.repository.MailSettingsRepository;
import com.event.sems.repository.TicketMailJobRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MailSettingsService {
  private static final String DEFAULT_HOST = "smtp.gmail.com";
  private static final int DEFAULT_PORT = 587;
  private final MailSettingsRepository settings;
  private final TicketMailJobRepository ticketMailJobs;
  private final SecureRandom random = new SecureRandom();

  @Value("${app.ticket-secret}")
  private String ticketSecret;

  @Value("${MAIL_CONFIG_KEY:}")
  private String dedicatedKey;

  public record View(
      boolean enabled,
      String host,
      int port,
      String username,
      String fromAddress,
      boolean passwordConfigured,
      boolean saved) {}

  public record Input(
      boolean enabled,
      String host,
      int port,
      String username,
      String fromAddress,
      String password) {}

  public record Connection(
      boolean enabled,
      String host,
      int port,
      String username,
      String fromAddress,
      String password) {}

  @Transactional(readOnly = true)
  public View view() {
    return settings
        .findById(1L)
        .map(
            row ->
                new View(
                    row.isEnabled(),
                    row.getHost(),
                    row.getPort(),
                    row.getUsername(),
                    row.getFromAddress(),
                    row.getEncryptedPassword() != null,
                    true))
        .orElseGet(() -> new View(false, DEFAULT_HOST, DEFAULT_PORT, "", "", false, false));
  }

  @Transactional(readOnly = true)
  public Connection connection() {
    return settings
        .findById(1L)
        .map(
            row ->
                new Connection(
                    row.isEnabled(),
                    row.getHost(),
                    row.getPort(),
                    row.getUsername(),
                    row.getFromAddress(),
                    row.getEncryptedPassword() == null ? "" : decrypt(row.getEncryptedPassword())))
        .orElseGet(() -> new Connection(false, DEFAULT_HOST, DEFAULT_PORT, "", "", ""));
  }

  @Transactional(readOnly = true)
  public String savedPassword() {
    return settings
        .findById(1L)
        .map(MailSettings::getEncryptedPassword)
        .filter(value -> !value.isBlank())
        .map(this::decrypt)
        .orElse("");
  }

  @Transactional
  public View save(Input input) {
    if (input.host() == null
        || !input.host().trim().matches("[A-Za-z0-9.-]{1,255}")
        || input.port() < 1
        || input.port() > 65535
        || !email(input.username())
        || !email(input.fromAddress()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cấu hình SMTP không hợp lệ.");
    MailSettings row = settings.findById(1L).orElseGet(MailSettings::new);
    String newPassword = input.password() == null ? "" : input.password().trim();
    if (newPassword.length() > 256)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu SMTP quá dài.");
    if (input.enabled() && newPassword.isEmpty() && row.getEncryptedPassword() == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cần nhập mật khẩu SMTP để bật gửi thư.");
    row.setEnabled(input.enabled());
    row.setHost(input.host().trim());
    row.setPort(input.port());
    row.setUsername(input.username().trim().toLowerCase());
    row.setFromAddress(input.fromAddress().trim().toLowerCase());
    if (!newPassword.isEmpty()) row.setEncryptedPassword(encrypt(newPassword));
    row.setUpdatedAt(TimeUtil.now());
    settings.saveAndFlush(row);
    if (input.enabled()) ticketMailJobs.resumePaused(TimeUtil.now());
    return view();
  }

  private boolean email(String value) {
    return value != null
        && value.length() <= 190
        && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  }

  private SecretKeySpec key() {
    try {
      String source = dedicatedKey.isBlank() ? ticketSecret : dedicatedKey;
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(("SEMS-mail-settings:" + source).getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(digest, "AES");
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể chuẩn bị khóa cấu hình thư.", ex);
    }
  }

  private String encrypt(String value) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể lưu mật khẩu SMTP.", ex);
    }
  }

  private String decrypt(String value) {
    try {
      byte[] data = Base64.getDecoder().decode(value);
      ByteBuffer buffer = ByteBuffer.wrap(data);
      byte[] iv = new byte[12];
      buffer.get(iv);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Không đọc được cấu hình SMTP. Kiểm tra MAIL_CONFIG_KEY.", ex);
    }
  }
}
