package com.event.sems.repository;

import com.event.sems.entity.Event;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {
  long countByVenueId(Long venueId);
  long countByVenueIdAndApprovalStatus(Long venueId, String approvalStatus);
  boolean existsByIdAndOrganizerUsernameIgnoreCase(Long id, String username);

  @Query("select max(e.capacity) from Event e where e.venue.id = :venueId")
  Integer maxCapacityByVenueId(@Param("venueId") Long venueId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Event e where e.id = :id")
  Optional<Event> findForUpdate(@Param("id") Long id);

  @Query("select count(e) from Event e join e.venue v where e.id <> :excludeId"
      + " and e.approvalStatus = 'APPROVED' and e.lifecycleStatus = 'SCHEDULED'"
      + " and e.deletedAt is null and lower(v.name) = lower(:name)"
      + " and lower(v.address) = lower(:address)"
      + " and e.startsAt < :endsAt and e.endsAt > :startsAt")
  long countPhysicalVenueConflicts(
      @Param("name") String name, @Param("address") String address,
      @Param("excludeId") Long excludeId,
      @Param("startsAt") java.time.LocalDateTime startsAt,
      @Param("endsAt") java.time.LocalDateTime endsAt);
}
