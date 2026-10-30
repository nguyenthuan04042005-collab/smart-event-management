package com.event.sems.repository;

import com.event.sems.entity.MailDelivery;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailDeliveryRepository extends JpaRepository<MailDelivery, Long> {
  List<MailDelivery> findByOrderByIdDesc(Pageable page);
}
