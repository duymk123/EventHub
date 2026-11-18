package com.eventhub.order.service;

import com.eventhub.order.common.Actor;
import com.eventhub.order.dto.request.CreateOrderRequest;
import com.eventhub.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Tạo và tra cứu đơn của CUSTOMER (plan Order §2). Thanh toán/webhook/hoàn tiền chờ quyết định provider C06.
 */
public interface OrderService {

    /**
     * Sinh orderId → Ticket reserve (ngoài transaction) → lưu orders/items/holders theo giá và hạn chính thức
     * của Ticket trong transaction ngắn. Lưu lỗi thì release bù trừ.
     */
    OrderResponse create(Actor customer, CreateOrderRequest request);

    Page<OrderResponse> getMine(Actor customer, Pageable pageable);

    OrderResponse getDetail(Actor customer, UUID orderId);
}
