package com.event.sems.repository;

import com.event.sems.entity.EventStaff;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStaffRepository extends JpaRepository<EventStaff, Long> {
  boolean existsByEventIdAndStaffUsernameIgnoreCase(Long eventId, String username);
  List<EventStaff> findByEventIdOrderByStaffUsernameAsc(Long eventId);
  List<EventStaff> findByStaffUsernameIgnoreCase(String username);
  Optional<EventStaff> findByEventIdAndStaffId(Long eventId, Long staffId);
}
