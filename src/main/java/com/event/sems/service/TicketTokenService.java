package com.event.sems.service;

import com.event.sems.common.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TicketTokenService {
  private final SecretKeySpec aesKey;
  private final SecretKeySpec macKey;
  private final SecureRandom random = new SecureRandom();

  public TicketTokenService(@Value("${app.ticket-secret}") String secret) {
    if (secret.length() < 32)
      throw new IllegalArgumentException("TICKET_SECRET phải có ít nhất 32 ký tự");
    try {
      aesKey = new SecretKeySpec(sha256("aes:" + secret), "AES");
      macKey = new SecretKeySpec(sha256("hmac:" + secret), "HmacSHA256");
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String issue(long ticketId, long eventId, long epochSecond) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      byte[] plain =
          (ticketId + ":" + eventId + ":" + epochSecond).getBytes(StandardCharsets.UTF_8);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(plain);
      byte[] body =
          ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
      String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(body);
      String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(encoded));
      return "v1." + encoded + "." + signature;
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể tạo mã vé", ex);
    }
  }

  public Payload verify(String token) {
    try {
      if (token == null || token.length() > 700) throw new IllegalArgumentException();
      String[] parts = token.split("\\.");
      if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException();
      byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
      if (!MessageDigest.isEqual(hmac(parts[1]), signature)) throw new IllegalArgumentException();
      byte[] body = Base64.getUrlDecoder().decode(parts[1]);
      if (body.length < 29) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, body, 0, 12));
      String[] fields =
          new String(cipher.doFinal(body, 12, body.length - 12), StandardCharsets.UTF_8).split(":");
      if (fields.length != 3) throw new IllegalArgumentException();
      return new Payload(
          Long.parseLong(fields[0]), Long.parseLong(fields[1]), Long.parseLong(fields[2]));
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Mã QR không hợp lệ hoặc đã bị sửa đổi.");
    }
  }

  private byte[] hmac(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(macKey);
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] sha256(String text) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
  }

  public record Payload(long ticketId, long eventId, long epochSecond) {}
}
