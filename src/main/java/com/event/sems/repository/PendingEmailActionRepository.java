package com.event.sems.repository;

import com.event.sems.entity.PendingEmailAction;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PendingEmailActionRepository extends JpaRepository<PendingEmailAction, String> {
  boolean existsByEmailIgnoreCaseAndCreatedAtAfter(String email, LocalDateTime after);

  @Transactional
  void deleteByExpiresAtBefore(LocalDateTime time);
}
