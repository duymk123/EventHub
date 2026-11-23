package com.eventhub.order.service;

import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.OrderItem;
import com.eventhub.order.entity.RefundRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trường nghiệp vụ của order.paid / order.failed / order.refunded theo docs/kafka/events.md §3 (envelope do OutboxWriter thêm).
 */
public final class OrderEvents {
    public static final String PAID = "order.paid";
    public static final String FAILED = "order.failed";
    public static final String REFUNDED = "order.refunded";

    private OrderEvents() {
    }

    /** reason: PAYMENT_FAILED hoặc EXPIRED. customerEmail là snapshot C02. */
    public static Map<String, Object> failed(Order order, String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", order.getId());
        fields.put("customerId", order.getCustomerId());
        fields.put("customerEmail", order.getCustomerEmail());
        fields.put("reason", reason);
        fields.put("totalAmount", order.getTotalAmount());
        return fields;
    }

    /** Chỉ phát sau khi Ticket đã phát vé; paidAt giữ thời điểm thanh toán dù confirm xong muộn hơn. */
    public static Map<String, Object> paid(Order order) {
        List<Map<String, Object>> items = order.getItems().stream().map(OrderEvents::item).toList();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", order.getId());
        fields.put("customerId", order.getCustomerId());
        fields.put("currency", order.getCurrency());
        fields.put("totalAmount", order.getTotalAmount());
        fields.put("paidAt", order.getPaidAt());
        fields.put("items", items);
        return fields;
    }

    /** C04/C05: phát khi VNPay xác nhận hoàn tiền; items[].refundedAmount cộng lại bằng amount. */
    public static Map<String, Object> refunded(Order order, RefundRequest refund, List<RefundAllocation.Line> lines) {
        List<Map<String, Object>> items = lines.stream().map(line -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ticketTypeId", line.ticketTypeId());
            entry.put("ticketTypeName", line.ticketTypeName());
            entry.put("quantity", line.quantity());
            entry.put("refundedAmount", line.refundedAmount());
            return entry;
        }).toList();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", order.getId());
        fields.put("refundId", refund.getId());
        fields.put("customerId", order.getCustomerId());
        fields.put("customerEmail", order.getCustomerEmail());
        fields.put("currency", order.getCurrency());
        fields.put("amount", refund.getAmount());
        fields.put("refundedAt", refund.getRefundedAt());
        fields.put("items", items);
        return fields;
    }

    private static Map<String, Object> item(OrderItem item) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ticketTypeId", item.getTicketTypeId());
        fields.put("ticketTypeName", item.getTicketTypeName());
        fields.put("quantity", item.getQuantity());
        fields.put("unitPrice", item.getUnitPrice());
        fields.put("lineTotal", item.getLineTotal());
        return fields;
    }
}
