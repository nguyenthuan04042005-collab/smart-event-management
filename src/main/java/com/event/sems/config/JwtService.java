package com.event.sems.config;

import com.event.sems.entity.UserAccount;
import com.event.sems.entity.RevokedJwt;
import com.event.sems.repository.UserAccountRepository;
import com.event.sems.repository.RevokedJwtRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
  private static final String ISSUER = "sems";
  private final UserAccountRepository users;
  private final RevokedJwtRepository revoked;
  private final SecretKey key;
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final long hours;

  public JwtService(
      UserAccountRepository users,
      RevokedJwtRepository revoked,
      @Value("${app.jwt-secret}") String secret,
      @Value("${app.jwt-hours:8}") long hours) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || hours < 1 || hours > 24) {
      throw new IllegalArgumentException("JWT cần khóa tối thiểu 32 byte và thời hạn từ 1 đến 24 giờ.");
    }
    this.users = users;
    this.revoked = revoked;
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.hours = hours;
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256).build();
    var defaults = JwtValidators.createDefaultWithIssuer(ISSUER);
    jwtDecoder.setJwtValidator(jwt -> {
      var basic = defaults.validate(jwt);
      if (basic.hasErrors()) return basic;
      boolean current = jwt.getId() != null && !revoked.existsById(jwt.getId())
          && users.findByUsernameIgnoreCase(jwt.getSubject())
          .filter(UserAccount::isActive)
          .map(user -> MessageDigest.isEqual(
              version(user).getBytes(StandardCharsets.US_ASCII),
              String.valueOf((Object) jwt.getClaim("credentialVersion"))
                  .getBytes(StandardCharsets.US_ASCII)))
          .orElse(false);
      return current ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "Tài khoản hoặc mật khẩu đã thay đổi.", null));
    });
    this.decoder = jwtDecoder;
  }

  public JwtDecoder decoder() {
    return decoder;
  }

  public String issue(String username) {
    UserAccount user = users.findByUsernameIgnoreCase(username).orElseThrow();
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(ISSUER)
        .subject(user.getUsername())
        .issuedAt(now)
        .expiresAt(now.plus(hours, ChronoUnit.HOURS))
        .id(java.util.UUID.randomUUID().toString())
        .claim("credentialVersion", version(user))
        .build();
    return encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
  }

  public JwtAuthenticationToken authentication(Jwt jwt) {
    UserAccount user = users.findByUsernameIgnoreCase(jwt.getSubject())
        .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại."));
    List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName())).toList();
    return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
  }

  public void revoke(Jwt jwt) {
    RevokedJwt token = new RevokedJwt();
    token.setTokenId(jwt.getId());
    token.setExpiresAt(java.time.LocalDateTime.ofInstant(jwt.getExpiresAt(),
        java.time.ZoneOffset.UTC));
    revoked.save(token);
  }

  private String version(UserAccount user) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      return java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(user.getPasswordHash().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể xác thực phiên đăng nhập.", ex);
    }
  }
}
