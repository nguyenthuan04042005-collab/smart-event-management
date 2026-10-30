package com.event.sems.config;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
@RequiredArgsConstructor
public class DashboardJwtHandshake extends DefaultHandshakeHandler
    implements HandshakeInterceptor {
  private final JwtService jwt;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler handler, Map<String, Object> attributes) {
    String requested = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
    if (requested == null) return false;
    List<String> protocols = java.util.Arrays.stream(requested.split(","))
        .map(String::trim).toList();
    if (protocols.size() != 2 || !"sems-jwt".equals(protocols.get(0))) return false;
    try {
      JwtAuthenticationToken authentication = jwt.authentication(jwt.decoder().decode(protocols.get(1)));
      attributes.put("jwtPrincipal", authentication);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler handler, Exception exception) { }

  @Override
  protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler,
      Map<String, Object> attributes) {
    return (Principal) attributes.get("jwtPrincipal");
  }
}
