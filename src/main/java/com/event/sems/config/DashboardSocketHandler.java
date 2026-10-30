package com.event.sems.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.event.sems.repository.EventRepository;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.SubProtocolCapable;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DashboardSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
  private static final Logger log = LoggerFactory.getLogger(DashboardSocketHandler.class);
  private final ObjectMapper mapper;
  private final EventRepository events;
  private final JwtService jwt;
  private final ConcurrentHashMap<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

  @Override
  public List<String> getSubProtocols() {
    return List.of("sems-jwt");
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Long eventId = eventId(session);
    boolean admin = session.getPrincipal() instanceof org.springframework.security.core.Authentication auth
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    boolean owner = session.getPrincipal() != null
        && events.existsByIdAndOrganizerUsernameIgnoreCase(eventId, session.getPrincipal().getName());
    if (!admin && !owner) {
      try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
      return;
    }
    rooms.computeIfAbsent(eventId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Set<WebSocketSession> room = rooms.get(eventId(session));
    if (room != null) room.remove(session);
  }

  public void broadcast(long eventId, Object view) {
    Set<WebSocketSession> room = rooms.get(eventId);
    if (room == null) return;
    try {
      TextMessage message = new TextMessage(mapper.writeValueAsString(view));
      for (WebSocketSession session : room) {
        if (!session.isOpen()) {
          room.remove(session);
          continue;
        }
        if (session.getPrincipal() instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken auth) {
          try {
            jwt.decoder().decode(auth.getToken().getTokenValue());
          } catch (RuntimeException ex) {
            room.remove(session);
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
            continue;
          }
        }
        try {
          synchronized (session) {
            session.sendMessage(message);
          }
        } catch (IOException ex) {
          room.remove(session);
        }
      }
    } catch (Exception ex) {
      log.warn("Không thể gửi số liệu Dashboard cho sự kiện {}", eventId, ex);
    }
  }

  private Long eventId(WebSocketSession session) {
    String path = session.getUri().getPath();
    return Long.valueOf(path.substring(path.lastIndexOf('/') + 1));
  }
}
