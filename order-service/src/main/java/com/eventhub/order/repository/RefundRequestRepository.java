package com.eventhub.order.repository;

import java.util.Optional;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.eventhub.order.entity.RefundRequest;
import com.eventhub.order.common.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    List<RefundRequest> findByOrderIdAndStatus(UUID orderId, RefundStatus status);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<RefundStatus> statuses);

    List<RefundRequest> findByOrderIdOrderByRequestedAtDesc(UUID orderId);

    List<RefundRequest> findAllByOrderByRequestedAtDesc();

    List<RefundRequest> findByStatusOrderByRequestedAtDesc(RefundStatus status);

    @Query("select r.order.id from RefundRequest r where r.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    @Query("select r from RefundRequest r join fetch r.order where r.id = :id")
    Optional<RefundRequest> findWithOrderById(@Param("id") UUID id);

    /** Khóa sau khi đã khóa đơn (thứ tự khóa: order rồi refund). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") UUID id);
}
