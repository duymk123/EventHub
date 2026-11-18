package com.eventhub.ticket.service;

import com.eventhub.ticket.dto.request.ConfirmRequest;
import com.eventhub.ticket.dto.request.ReserveRequest;
import com.eventhub.ticket.dto.response.ConfirmResponse;
import com.eventhub.ticket.dto.response.ReservationResponse;
import com.eventhub.ticket.dto.response.ReservationStatusResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Giữ vé, phát hành vé và trả quota (plan Ticket §2, §4). Caller là Order Service qua /internal/**.
 */
public interface ReservationService {

    /** Idempotent theo orderId: cùng payload trả reservation cũ (kể cả đã hết hạn), khác payload 409. */
    ReservationResponse reserve(ReserveRequest request);

    /**
     * CONFIRMED trả bộ vé cũ; RELEASED/EXPIRED trả 409. RESERVED quá hạn: commit EXPIRED + hoàn quota rồi mới
     * báo 409, không để rollback làm mất việc hoàn quota.
     */
    ConfirmResponse confirm(UUID reservationId, ConfirmRequest request);

    /** RESERVED → RELEASED hoàn quota một lần; RELEASED/EXPIRED trả trạng thái cũ; CONFIRMED 409. */
    ReservationStatusResponse release(UUID reservationId);

    /** Worker: chỉ RESERVED có expiresAt ≤ now; trả quota đúng một lần. true nếu vừa hết hạn. */
    boolean expire(UUID reservationId, Instant now);
}
