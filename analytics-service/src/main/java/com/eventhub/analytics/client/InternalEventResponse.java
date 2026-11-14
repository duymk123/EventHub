package com.eventhub.analytics.client;

import java.util.UUID;

/**
 * Dữ liệu sự kiện nội bộ tối thiểu, trả về bởi Event Service.
 *
 * @param id UUID sự kiện, được đối chiếu với eventId đang yêu cầu.
 * @param organizerId UUID người tổ chức sở hữu sự kiện lấy từ Event Service.
 * @param title Tiêu đề đã render và lưu cho lần gửi này.
 * @param status Trạng thái nghiệp vụ sự kiện từ Event Service.
 * @param startTime Thời điểm bắt đầu sự kiện từ Event Service.
 * @param endTime Thời điểm kết thúc sự kiện từ Event Service.
 */
public record InternalEventResponse(
        UUID id,
        UUID organizerId,
        String title,
        String status,
        java.time.Instant startTime,
        java.time.Instant endTime
) {}
