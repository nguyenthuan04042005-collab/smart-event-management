package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.dto.ApiDtos.AssignedStaffView;
import com.event.sems.entity.EventStaff;
import com.event.sems.entity.UserAccount;
import com.event.sems.repository.EventRepository;
import com.event.sems.repository.EventStaffRepository;
import com.event.sems.repository.UserAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventStaffService {
  private final EventRepository events;
  private final EventStaffRepository assignments;
  private final UserAccountRepository users;

  @Transactional(readOnly = true)
  public List<AssignedStaffView> list(long eventId) {
    requireEvent(eventId);
    return assignments.findByEventIdOrderByStaffUsernameAsc(eventId).stream()
        .map(item -> new AssignedStaffView(item.getStaff().getId(),
            item.getStaff().getUsername(), item.getStaff().getDisplayName()))
        .toList();
  }

  @Transactional
  public List<AssignedStaffView> assign(long eventId, long staffId) {
    var event = requireEvent(eventId);
    if ("CANCELLED".equals(event.getLifecycleStatus()))
      throw new ApiException(HttpStatus.CONFLICT, "Sự kiện đã hủy, không thể phân công thêm nhân viên.");
    UserAccount staff = users.findWithRolesById(staffId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên."));
    if (!staff.isActive() || staff.getRoles().stream().noneMatch(role -> "STAFF".equals(role.getName())))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ phân công tài khoản staff đang hoạt động.");
    if (!assignments.existsByEventIdAndStaffUsernameIgnoreCase(eventId, staff.getUsername())) {
      EventStaff assignment = new EventStaff();
      assignment.setEvent(event);
      assignment.setStaff(staff);
      assignments.save(assignment);
    }
    return list(eventId);
  }

  @Transactional
  public void unassign(long eventId, long staffId) {
    EventStaff assignment = assignments.findByEventIdAndStaffId(eventId, staffId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Nhân viên chưa được phân công."));
    assignments.delete(assignment);
  }

  @Transactional(readOnly = true)
  public void requireAssigned(long eventId, String username) {
    if (!assignments.existsByEventIdAndStaffUsernameIgnoreCase(eventId, username))
      throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chưa được phân công check-in sự kiện này.");
  }

  private com.event.sems.entity.Event requireEvent(long eventId) {
    return events.findById(eventId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
  }
}
