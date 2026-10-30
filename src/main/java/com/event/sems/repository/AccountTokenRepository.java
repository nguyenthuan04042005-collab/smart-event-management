package com.event.sems.repository;

import com.event.sems.entity.AccountToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, String purpose);

  List<AccountToken> findByUserIdAndPurposeAndUsedAtIsNull(Long userId, String purpose);

  boolean existsByUserIdAndPurposeAndEmailAddressAndCreatedAtAfterAndUsedAtIsNull(
      Long userId, String purpose, String emailAddress, LocalDateTime createdAt);
}
