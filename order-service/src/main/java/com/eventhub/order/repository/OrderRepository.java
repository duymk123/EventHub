package com.eventhub.order.repository;

import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Trang đơn của khách, không fetch collection (tránh phân trang trong bộ nhớ); item/holder nạp riêng. */
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    /** Tuần tự hóa expiry/webhook trên cùng đơn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    /** ID đơn quá hạn thanh toán (index status, expires_at), xử lý từng đơn trong transaction riêng. */
    @Query("select o.id from Order o where o.status = :status and o.expiresAt <= :now order by o.expiresAt")
    List<UUID> findExpiredIds(@Param("status") OrderStatus status, @Param("now") Instant now, Pageable page);
}
