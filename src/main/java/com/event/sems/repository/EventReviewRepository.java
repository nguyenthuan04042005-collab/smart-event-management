package com.event.sems.repository;

import com.event.sems.entity.EventReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventReviewRepository extends JpaRepository<EventReview, Long> {
  List<EventReview> findByEventIdOrderByIdDesc(Long eventId);
}
