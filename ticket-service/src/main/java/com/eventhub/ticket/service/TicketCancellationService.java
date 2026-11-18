package com.eventhub.ticket.service;

import com.eventhub.ticket.dto.response.CancelResponse;

import java.util.UUID;

/**
 * Hủy toàn bộ vé của đơn sau khi hoàn tiền thành công (Order gọi). Hold/token theo C05 chưa có trong hợp đồng
 * runtime (docs/architecture/payment-refund-recovery.md); bản này hủy trực tiếp, cả đơn hoặc không vé nào.
 */
public interface TicketCancellationService {

    /**
     * ISSUED → CANCELLED, sold → available một lần cho số vé vừa hủy. Vé đã CHECKED_IN/OUT làm cả đơn bị từ chối.
     * Gọi lặp trả cancelledTickets = 0, không hoàn quota hay phát ticket.cancelled lần hai.
     */
    CancelResponse cancelByOrder(UUID orderId);
}
