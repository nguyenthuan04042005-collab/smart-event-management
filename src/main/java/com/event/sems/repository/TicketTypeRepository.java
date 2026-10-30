package com.event.sems.repository;

import com.event.sems.entity.TicketType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
  Optional<TicketType> findByEventId(Long eventId);
}
