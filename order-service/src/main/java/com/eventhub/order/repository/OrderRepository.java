package com.eventhub.order.repository;

import com.eventhub.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"items", "items.holders"})
    Page<Order> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.holders"})
    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);
}
