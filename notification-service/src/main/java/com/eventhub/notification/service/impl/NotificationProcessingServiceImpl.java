package com.eventhub.notification.service.impl;

import com.eventhub.notification.messaging.InvalidEventException;
import com.eventhub.notification.messaging.event.*;
import com.eventhub.notification.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final String venueText;
    /** Giờ hiển thị trong email theo giờ Việt Nam, ví dụ 19:30 28/11/2026. */
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public NotificationProcessingServiceImpl(NotificationBatchService batches,
            NotificationDeliveryService delivery, ObjectMapper mapper,
            @Value("${eventhub.notification.venue-text:141 Đường Chiến Thắng, Hà Nội}") String venueText) {
        this.batches = batches;
        this.delivery = delivery;
        this.mapper = mapper;
        this.venueText = venueText;
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
        process(event, "ORDER_FAILED", event.customerId(), model, buyerOnly(event.customerEmail(), model));
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
        process(event, "ORDER_REFUNDED", event.customerId(), model, buyerOnly(event.customerEmail(), model));
    }

    /**
     * Kiểm tra email người mua và các ticketId duy nhất, dựng model gồm thông tin sự kiện, vé (kèm ghế) và
     * Content-ID cho ảnh QR. C02: vé có holderEmail gửi tới địa chỉ đó, thiếu thì gửi người mua; vé cùng địa chỉ
     * gom một email và mỗi địa chỉ chỉ nhận QR của vé mình. IN_APP cho người mua liệt kê đủ vé. C03: venue trống
     * thì dùng text địa điểm cấu hình, snapshot vào model nên retry không đổi.
     */
    @Override
    public void handleTicketIssued(TicketIssuedEvent event) {
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            throw new InvalidEventException("Buyer email snapshot is required for ticket delivery");
        }
        String buyerEmail = event.customerEmail().trim();
        Set<UUID> ids = new HashSet<>();
        List<Map<String, Object>> allTickets = new ArrayList<>();
        // Khóa nhóm là email viết thường; giữ cách viết của lần xuất hiện đầu để gửi.
        Map<String, String> addressOf = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> byAddress = new LinkedHashMap<>();
        for (var ticket : event.tickets()) {
            if (!ids.add(ticket.ticketId())) {
                throw new InvalidEventException("Duplicate ticket in issued event");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ticketId", ticket.ticketId().toString());
            item.put("holderName", ticket.holderName());
            item.put("ticketTypeName", ticket.ticketTypeName());
            item.put("seatLabel", ticket.seatLabel() == null ? "" : ticket.seatLabel());
            item.put("qrCodeValue", ticket.qrCodeValue());
            item.put("cid", "ticket-" + ticket.ticketId());
            allTickets.add(item);
            String recipient = ticket.holderEmail() == null || ticket.holderEmail().isBlank()
                    ? buyerEmail : ticket.holderEmail().trim();
            String key = recipient.toLowerCase(Locale.ROOT);
            addressOf.putIfAbsent(key, recipient);
            byAddress.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("orderId", event.orderId().toString());
        base.put("eventTitle", event.eventTitle());
        base.put("eventStartTime", DISPLAY_TIME.format(event.eventStartTime()));
        base.put("venue", event.venue() == null || event.venue().isBlank() ? venueText : event.venue());

        Map<String, Object> inApp = new LinkedHashMap<>(base);
        inApp.put("tickets", allTickets);
        List<NotificationBatchService.EmailTarget> emails = new ArrayList<>();
        byAddress.forEach((key, tickets) -> {
            Map<String, Object> model = new LinkedHashMap<>(base);
            model.put("tickets", tickets);
            emails.add(new NotificationBatchService.EmailTarget(addressOf.get(key), model));
        });
        process(event, "TICKET_ISSUED", event.ownerUserId(), inApp, emails);
    }

    /**
     * Luồng điều phối dùng chung cho ba topic. prepare trả các ID target đã lưu, deliver xử lý từng target,
     * complete chỉ chấp nhận batch khi tất cả target SENT. Batch đang chờ worker khác ném
     * DeliveryInProgressException để được retry.
     */
    private void process(BusinessEvent event, String code, UUID buyerId, Map<String, Object> inAppModel,
                         List<NotificationBatchService.EmailTarget> emails) {
        // Bước 1 ghi batch và snapshot target vào DB, hoặc lấy lại đúng các target của lần trước.
        List<UUID> targets = batches.prepare(event.messageId(), fingerprint(event), code, buyerId, inAppModel, emails);
        // Bước 2 thử giao từng target. Repository chỉ cho claim EMAIL chưa SENT và lease đã rảnh.
        for (UUID target : targets) {
            delivery.deliver(target);
        }
        // Bước 3 chỉ ghi dấu hoàn tất message sau khi toàn bộ target SENT.
        if (!batches.complete(event.messageId())) {
            throw new com.eventhub.notification.messaging.DeliveryInProgressException();
        }
    }

    private static List<NotificationBatchService.EmailTarget> buyerOnly(String email, Map<String, Object> model) {
        return List.of(new NotificationBatchService.EmailTarget(email.trim(), model));
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
