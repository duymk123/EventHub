package com.eventhub.analytics.messaging.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload chung cho ticket.checked-in và ticket.checked-out. eventType chọn thao tác, ticketId là
 * aggregateId/key còn scannedAt là thời điểm quét gốc.
 *
 * @param messageId UUID định danh message nguồn, giữ nguyên qua retry/replay để chống xử lý trùng.
 * @param eventType Tên loại sự kiện, cần trùng topic Kafka nhận được.
 * @param occurredAt Thời điểm nghiệp vụ gốc theo UTC.
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param ticketId UUID vé, dùng làm Kafka key của sự kiện quét.
 * @param ticketTypeId UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
 * @param gateId UUID cổng hoặc thiết bị gắn với lần quét.
 * @param scannedAt Thời điểm quét gốc do Ticket gửi.
 */
public record TicketScanEvent(
        @NotNull UUID messageId, @NotBlank String eventType, @NotNull Instant occurredAt, @NotNull UUID eventId,
        @NotNull UUID ticketId, @NotNull UUID ticketTypeId, @NotNull UUID gateId,
        @NotNull Instant scannedAt) implements BusinessEvent {
    /**
     * Trả ticketId để đối chiếu Kafka key của lần quét.
     */
    @Override public UUID aggregateId() { return ticketId; }
}
