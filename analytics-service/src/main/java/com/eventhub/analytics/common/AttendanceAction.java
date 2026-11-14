package com.eventhub.analytics.common;

/**
 * Loại bằng chứng quét được lưu trong attendance_logs.
 * Projection giữ cả CHECK_IN và CHECK_OUT để tổng hợp trạng thái của từng vé.
 */
public enum AttendanceAction {
    CHECK_IN,
    CHECK_OUT
}
