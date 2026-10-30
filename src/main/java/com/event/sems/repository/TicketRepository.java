package com.event.sems.repository;

import com.event.sems.entity.Ticket;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
  Optional<Ticket> findByCode(String code);
  boolean existsByEventId(Long eventId);
  long countByEventId(Long eventId);

  List<Ticket> findByQrToken(String qrToken);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Ticket t where t.id = :id")
  Optional<Ticket> findForUpdate(@Param("id") Long id);

  boolean existsByEventIdAndEmailKeyAndStatusNot(Long eventId, String emailKey, String status);

  boolean existsByEventIdAndPhoneKeyAndStatusNot(Long eventId, String phoneKey, String status);

  boolean existsByEventIdAndUserIdAndStatusNot(Long eventId, Long userId, String status);

  long countByEventIdAndStatusNot(Long eventId, String status);

  long countByEventIdAndStatus(Long eventId, String status);

  List<Ticket> findByEventIdAndStatusNotOrderByIdDesc(Long eventId, String status);
  List<Ticket> findByEventIdOrderByIdDesc(Long eventId);

  List<Ticket> findByUserIdOrderByIdDesc(Long userId);

  @Query("""
      select t from Ticket t join fetch t.event e join fetch t.user u
      where t.status = 'CHECKED_IN'
        and e.endsAt <= :now and e.lifecycleStatus <> 'CANCELLED'
        and not exists (select f.id from Feedback f where f.ticket.id = t.id)
        and not exists (select j.id from TicketMailJob j
          where j.ticket.id = t.id and j.messageType = 'SURVEY_INVITATION')
      order by t.id
      """)
  List<Ticket> findSurveyCandidates(@Param("now") LocalDateTime now, Pageable page);

  @Query(value =
      """
      select t from Ticket t join t.attendee a
      where t.event.id = :eventId
        and t.status <> 'CANCELLED'
        and (lower(a.fullName) like lower(concat('%', :query, '%'))
          or lower(a.email) like lower(concat('%', :query, '%')))
      order by t.id desc
       """, countQuery =
       """
       select count(t) from Ticket t join t.attendee a
       where t.event.id = :eventId and t.status <> 'CANCELLED'
         and (lower(a.fullName) like lower(concat('%', :query, '%'))
           or lower(a.email) like lower(concat('%', :query, '%')))
       """)
  Page<Ticket> searchForCheckin(
      @Param("eventId") Long eventId,
      @Param("query") String query,
      org.springframework.data.domain.Pageable page);
}
