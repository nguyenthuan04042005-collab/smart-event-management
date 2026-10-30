package com.event.sems.repository;

import com.event.sems.entity.Venue;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {
  List<Venue> findByOrganizerIdOrderByNameAsc(Long organizerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Venue v where v.id = :id")
  Optional<Venue> findForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Venue v where lower(v.name) = lower(:name)"
      + " and lower(v.address) = lower(:address) order by v.id")
  List<Venue> lockPhysicalVenue(@Param("name") String name,
      @Param("address") String address);
  boolean existsByOrganizerIdAndNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(
      Long organizerId, String name, String address, Long id);
}
