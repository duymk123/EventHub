-- C10: thống kê toàn hệ thống cho ADMIN (plan Analytics §8.5). Chỉ thêm index, không đổi bảng/cột.
-- Tên index trùng với @Index trên entity SalesMetric/AttendanceState.

-- Range theo ngày trên toàn hệ thống (/overview, /events không lọc event).
CREATE INDEX idx_sales_date_event ON sales_metrics (metric_date, event_id);
-- Range theo ngày của một sự kiện (/overview?eventId=). uk_metric có ticket_type_id chen giữa event_id và metric_date.
CREATE INDEX idx_sales_event_date ON sales_metrics (event_id, metric_date);
-- Đếm khán giả theo khoảng check-in.
CREATE INDEX idx_state_checkin_event ON attendance_state (checked_in_at, event_id);
CREATE INDEX idx_state_event_checkin ON attendance_state (event_id, checked_in_at);
