package com.eventhub.order.repository;

import com.eventhub.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"items", "items.holders"})
    Page<Order> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.holders"})
    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    @Modifying
    @Query("""
            update Order o
               set o.status = :expiredStatus
             where o.status = :pendingStatus
               and o.expiresAt <= :now
            """)
    int expirePendingOrders(
            java.time.Instant now,
            com.eventhub.order.entity.enums.OrderStatus pendingStatus,
            com.eventhub.order.entity.enums.OrderStatus expiredStatus
    );
}
