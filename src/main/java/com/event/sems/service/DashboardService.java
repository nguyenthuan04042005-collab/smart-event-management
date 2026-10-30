package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.dto.ApiDtos.DashboardView;
import com.event.sems.entity.Event;
import com.event.sems.repository.EventRepository;
import com.event.sems.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
  private final EventRepository events;
  private final TicketRepository tickets;

  @Transactional(readOnly = true)
  public DashboardView get(long eventId) {
    Event event =
        events
            .findById(eventId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    long registered = tickets.countByEventIdAndStatusNot(eventId, "CANCELLED");
    long checkedIn = tickets.countByEventIdAndStatus(eventId, "CHECKED_IN");
    double attendance = registered == 0 ? 0 : 100.0 * checkedIn / registered;
    double occupancy = 100.0 * checkedIn / event.getCapacity();
    String alert = occupancy >= 95 ? "RED" : occupancy >= 85 ? "YELLOW" : "NORMAL";
    return new DashboardView(
        eventId,
        event.getTitle(),
        event.getCapacity(),
        registered,
        checkedIn,
        Math.round(attendance * 10) / 10.0,
        Math.round(occupancy * 10) / 10.0,
        alert,
        event.getLifecycleStatus(),
        event.getCancelReason());
  }
}
