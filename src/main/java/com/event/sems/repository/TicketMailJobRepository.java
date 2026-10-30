package com.event.sems.repository;

import com.event.sems.entity.TicketMailJob;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketMailJobRepository extends JpaRepository<TicketMailJob, Long> {
  boolean existsByTicketIdAndMessageType(Long ticketId, String messageType);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from TicketMailJob j where j.id = :id")
  Optional<TicketMailJob> findForUpdate(@Param("id") Long id);

  @Query("select j.id from TicketMailJob j where j.status = 'PENDING' "
      + "and j.nextAttemptAt <= :now order by j.nextAttemptAt, j.id")
  List<Long> dueIds(@Param("now") LocalDateTime now, Pageable page);

  List<TicketMailJob> findByOrderByIdDesc(Pageable page);

  @Modifying
  @Query("update TicketMailJob j set j.status = 'PENDING', j.nextAttemptAt = :now, "
      + "j.lastErrorCode = null "
      + "where j.status = 'PAUSED'")
  int resumePaused(@Param("now") LocalDateTime now);
}
