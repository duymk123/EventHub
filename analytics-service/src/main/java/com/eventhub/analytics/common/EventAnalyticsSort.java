package com.eventhub.analytics.common;

import com.eventhub.analytics.exception.BusinessException;

/**
 * Whitelist trường sắp xếp của GET /api/v1/analytics/events (C10). Mỗi giá trị gắn với một alias cột cố định
 * trong câu SQL, nên tham số request không bao giờ được ghép trực tiếp vào ORDER BY.
 */
public enum EventAnalyticsSort {
    NET_REVENUE("netRevenue", "net"),
    GROSS_REVENUE("grossRevenue", "gross"),
    TICKETS_SOLD("ticketsSold", "sold"),
    ATTENDEES("attendees", "att");

    private final String param;
    private final String column;

    EventAnalyticsSort(String param, String column) {
        this.param = param;
        this.column = column;
    }

    /**
     * Alias cột SQL đã định nghĩa sẵn trong truy vấn trang sự kiện.
     */
    public String column() {
        return column;
    }

    /**
     * Đổi tham số query (camelCase như OpenAPI) sang enum; null hoặc rỗng dùng mặc định netRevenue.
     * Giá trị lạ trả 400 VALIDATION_ERROR.
     */
    public static EventAnalyticsSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return NET_REVENUE;
        }
        for (EventAnalyticsSort sort : values()) {
            if (sort.param.equals(value)) {
                return sort;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported sort: " + value);
    }
}
