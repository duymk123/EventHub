package com.eventhub.order.repository;

import com.eventhub.order.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /** Nạp item + holder của nhiều đơn bằng một truy vấn cho trang lịch sử/chi tiết. */
    @EntityGraph(attributePaths = "holders")
    List<OrderItem> findByOrderIdIn(Collection<UUID> orderIds);
}
