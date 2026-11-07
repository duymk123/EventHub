package com.eventhub.order.repository;

import com.eventhub.order.entity.RefundRequest;
import com.eventhub.order.entity.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    List<RefundRequest> findByOrderIdAndStatus(UUID orderId, RefundStatus status);
}
