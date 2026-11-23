package com.eventhub.order.service;

import com.eventhub.order.common.Actor;
import com.eventhub.order.dto.request.CreateOrderRequest;
import com.eventhub.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Tạo và tra cứu đơn của CUSTOMER (plan Order §2). Thanh toán VNPay nằm ở PaymentService.
 */
public interface OrderService {

    /**
     * Sinh orderId → Ticket reserve (ngoài transaction) → lưu orders/items/holders theo giá và hạn chính thức
     * của Ticket trong transaction ngắn. Lưu lỗi thì release bù trừ.
     */
    CreateResult create(Actor customer, CreateOrderRequest request, String idempotencyKey);

    /** created = false khi trả lại đơn cũ theo Idempotency-Key (HTTP 200 thay vì 201). */
    record CreateResult(OrderResponse order, boolean created) {
    }

    Page<OrderResponse> getMine(Actor customer, Pageable pageable);

    OrderResponse getDetail(Actor customer, UUID orderId);

    /** C19: hủy đơn PENDING_PAYMENT của chính khách; gọi lại khi đã CANCELLED trả đơn hiện tại. */
    OrderResponse cancel(Actor customer, UUID orderId);
}
