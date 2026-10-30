package com.event.sems.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      ObjectMapper json,
      JwtService jwt)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/index.html",
                        "/about.html",
                        "/event.html",
                        "/login.html",
                        "/signup.html",
                        "/forgot-password.html",
                        "/reset-password.html",
                        "/verify-email.html",
                        "/pages/**",
                        "/css/**",
                        "/js/**",
                        "/vendor/**",
                        "/favicon.ico",
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/register",
                        "/api/v1/auth/confirm-registration",
                        "/api/v1/auth/resend-registration-code",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/public/events", "/api/v1/public/events/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/public/banners/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/venues")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/venues")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/venues/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/venues/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/venues/*/active")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/events/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/banner")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*/banner")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/submit")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/withdraw")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/cancel")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/events/*/review")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/events/*/reconsider")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/reviews")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(
                        "/api/v1/admin/events/*/staff", "/api/v1/admin/events/*/staff/*")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/admin/mail-settings", "/api/v1/admin/mail-settings/*")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/tickets/mine")
                    .hasRole("ATTENDEE")
                    .requestMatchers(HttpMethod.GET, "/api/v1/feedback/mine")
                    .hasRole("ATTENDEE")
                    .requestMatchers(HttpMethod.GET, "/api/v1/tickets/*", "/api/v1/tickets/*/qr")
                    .hasAnyRole("ADMIN", "ATTENDEE")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/events/*/registrations",
                        "/api/v1/tickets/*/feedback",
                        "/api/v1/tickets/*/cancel")
                    .hasRole("ATTENDEE")
                    .requestMatchers("/ws/dashboard/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/check-ins")
                    .hasRole("STAFF")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/check-ins/search")
                    .hasRole("STAFF")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/reports/*")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/events/*/dashboard",
                        "/api/v1/events/*/feedback",
                        "/api/v1/events/*/feedback/items",
                        "/api/v1/events/*/tickets")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/me")
                    .authenticated()
                    .requestMatchers("/api/v1/account/**")
                    .authenticated()
                    .requestMatchers("/api/v1/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .oauth2ResourceServer(oauth -> oauth.jwt(config -> config
            .decoder(jwt.decoder())
            .jwtAuthenticationConverter(jwt::authentication)))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeError(
                                response,
                                json,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Cần đăng nhập."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeError(
                                response,
                                json,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Bạn không có quyền thực hiện thao tác này.")));
    return http.build();
  }

  private static void writeError(
      HttpServletResponse response, ObjectMapper json, int status, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    json.writeValue(response.getWriter(), Map.of("message", message));
  }
}
