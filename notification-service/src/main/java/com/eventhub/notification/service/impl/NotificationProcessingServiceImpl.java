package com.eventhub.notification.service.impl;

import com.eventhub.notification.messaging.InvalidEventException;
import com.eventhub.notification.messaging.event.*;
import com.eventhub.notification.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.*;

/**
 * Điều phối một sự kiện từ consumer tới batch và delivery. Các handler dựng model theo topic, process dùng
 * chung luồng prepare, deliver và complete.
 */
@Service
public class NotificationProcessingServiceImpl implements NotificationProcessingService {
    private final NotificationBatchService batches;
    private final NotificationDeliveryService delivery;
    private final ObjectMapper mapper;

    public NotificationProcessingServiceImpl(NotificationBatchService batches,
            NotificationDeliveryService delivery, ObjectMapper mapper) {
        this.batches = batches;
        this.delivery = delivery;
        this.mapper = mapper;
    }

    /**
     * Dựng model đơn hàng và lý do thất bại cho template ORDER_FAILED, chuyển sang process để tạo hai target
     * cho người mua.
     */
    @Override
    public void handleOrderFailed(OrderFailedEvent event) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("orderId", event.orderId().toString());
        model.put("reason", event.reason());
        model.put("totalAmount", event.totalAmount().toPlainString());
        // Luồng điều phối dùng chung cho ba topic. prepare trả các ID target đã lưu, deliver xử lý từng target,
        // complete chỉ chấp nhận batch khi tất cả target SENT. Batch đang chờ worker khác ném
        // DeliveryInProgressException để được retry.
        process(event, "ORDER_FAILED", event.customerId(), event.customerEmail(), model);
    }

    /**
     * Dựng nội dung số tiền đã hoàn theo snapshot trong event và chọn template ORDER_REFUNDED.
     */
    @Override
    public void handleOrderRefunded(OrderRefundedEvent event) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("orderId", event.orderId().toString());
        model.put("amount", event.amount().toPlainString());
        // Luồng điều phối dùng chung cho ba topic. prepare trả các ID target đã lưu, deliver xử lý từng target,
        // complete chỉ chấp nhận batch khi tất cả target SENT. Batch đang chờ worker khác ném
        // DeliveryInProgressException để được retry.
        process(event, "ORDER_REFUNDED", event.customerId(), event.customerEmail(), model);
    }

    /**
     * Kiểm tra email người mua và các ticketId duy nhất, dựng model gồm thông tin sự kiện, vé và Content-ID cho
     * ảnh QR, rồi gửi cả lô về người mua.
     */
    @Override
    public void handleTicketIssued(TicketIssuedEvent event) {
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            throw new InvalidEventException("Buyer email snapshot is required for ticket delivery");
        }
        Set<UUID> ids = new HashSet<>();
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (var ticket : event.tickets()) {
            if (!ids.add(ticket.ticketId())) {
                throw new InvalidEventException("Duplicate ticket in issued event");
            }
            tickets.add(Map.of("ticketId", ticket.ticketId().toString(), "holderName", ticket.holderName(),
                    "ticketTypeName", ticket.ticketTypeName(), "qrCodeValue", ticket.qrCodeValue(),
                    "cid", "ticket-" + ticket.ticketId()));
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("orderId", event.orderId().toString());
        model.put("eventTitle", event.eventTitle());
        model.put("eventStartTime", event.eventStartTime().toString());
        model.put("venue", event.venue() == null ? "" : event.venue());
        model.put("tickets", tickets);
        // Luồng điều phối dùng chung cho ba topic. prepare trả các ID target đã lưu, deliver xử lý từng target,
        // complete chỉ chấp nhận batch khi tất cả target SENT. Batch đang chờ worker khác ném
        // DeliveryInProgressException để được retry.
        process(event, "TICKET_ISSUED", event.ownerUserId(), event.customerEmail(), model);
    }

    /**
     * Luồng điều phối dùng chung cho ba topic. prepare trả các ID target đã lưu, deliver xử lý từng target,
     * complete chỉ chấp nhận batch khi tất cả target SENT. Batch đang chờ worker khác ném
     * DeliveryInProgressException để được retry.
     */
    private void process(BusinessEvent event, String code, UUID buyerId, String email, Map<String, Object> model) {
        // Bước 1 ghi batch và snapshot target vào DB, hoặc lấy lại đúng các target của lần trước.
        List<UUID> targets = batches.prepare(event.messageId(), fingerprint(event), code,
                buyerId, email.trim(), model);
        // Bước 2 thử giao từng target. Repository chỉ cho claim EMAIL chưa SENT và lease đã rảnh.
        for (UUID target : targets) {
            delivery.deliver(target);
        }
        // Bước 3 chỉ ghi dấu hoàn tất message sau khi toàn bộ target SENT.
        if (!batches.complete(event.messageId())) {
            throw new com.eventhub.notification.messaging.DeliveryInProgressException();
        }
    }

    /**
     * Chuẩn hóa event đã parse thành JSON và băm SHA-256. Batch so sánh dấu này để phát hiện một messageId bị
     * dùng lại với payload khác.
     */
    private String fingerprint(BusinessEvent event) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(event)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot fingerprint notification event");
        }
    }
}
