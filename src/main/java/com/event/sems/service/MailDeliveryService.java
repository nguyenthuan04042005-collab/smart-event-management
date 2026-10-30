package com.event.sems.service;

import com.event.sems.common.TimeUtil;
import com.event.sems.entity.MailDelivery;
import com.event.sems.repository.MailDeliveryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MailDeliveryService {
  private final MailDeliveryRepository deliveries;

  public record View(Long id, String messageType, String recipient, String status,
      String errorCode, LocalDateTime createdAt, LocalDateTime completedAt) {}


  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long begin(String type, String recipient) {
    MailDelivery delivery = new MailDelivery();
    delivery.setMessageType(type);
    delivery.setRecipient(recipient);
    delivery.setCreatedAt(TimeUtil.now());
    return deliveries.saveAndFlush(delivery).getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(long id, String status, String errorCode) {
    MailDelivery delivery = deliveries.findById(id).orElseThrow();
    delivery.setStatus(status);
    delivery.setErrorCode(errorCode);
    delivery.setCompletedAt(TimeUtil.now());
  }

  @Transactional(readOnly = true)
  public List<View> latest() {
    return deliveries.findByOrderByIdDesc(PageRequest.of(0, 50)).stream()
        .map(item -> new View(item.getId(), item.getMessageType(), item.getRecipient(),
            item.getStatus(), item.getErrorCode(), item.getCreatedAt(), item.getCompletedAt()))
        .toList();
  }

}
