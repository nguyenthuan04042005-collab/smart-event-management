package com.event.sems.repository;

import com.event.sems.entity.CheckinLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinLogRepository extends JpaRepository<CheckinLog, Long> {
  List<CheckinLog> findByEventIdOrderByCheckedInAtAsc(Long eventId);
}
