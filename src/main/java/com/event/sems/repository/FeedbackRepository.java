package com.event.sems.repository;

import com.event.sems.entity.Feedback;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
  boolean existsByTicketId(Long ticketId);

  List<Feedback> findByEventIdOrderByIdDesc(Long eventId);
  List<Feedback> findByTicketUserIdOrderByIdDesc(Long userId);

  @Query("select f.rating, count(f) from Feedback f where f.event.id = :eventId group by f.rating")
  List<Object[]> ratingCounts(@Param("eventId") Long eventId);

  @Query("select f.createdAt, f.rating from Feedback f where f.event.id = :eventId order by f.createdAt")
  List<Object[]> trendValues(@Param("eventId") Long eventId);

  @Query(value = """
      select f from Feedback f join f.ticket t join t.attendee a
      where f.event.id = :eventId and (:rating = 0 or f.rating = :rating)
        and (:term = '' or lower(a.fullName) like lower(concat('%', :term, '%'))
          or lower(f.commentText) like lower(concat('%', :term, '%')))
      order by f.createdAt desc, f.id desc
      """, countQuery = """
      select count(f) from Feedback f join f.ticket t join t.attendee a
      where f.event.id = :eventId and (:rating = 0 or f.rating = :rating)
        and (:term = '' or lower(a.fullName) like lower(concat('%', :term, '%'))
          or lower(f.commentText) like lower(concat('%', :term, '%')))
      """)
  Page<Feedback> search(
      @Param("eventId") Long eventId,
      @Param("rating") int rating,
      @Param("term") String term,
      Pageable pageable);
}
