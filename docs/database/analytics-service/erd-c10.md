# ERD thống kê gộp cho ADMIN và ORGANIZER (C10)

Đề xuất 2026-11-13; analytics-service đã triển khai và có migration V3 ngày 2026-11-14 (Event INT-02, INT-03 và Ticket capacity chưa có). Sơ đồ này bổ sung cho [ERD Analytics](erd.md) (sinh tự động từ [init.sql](init.sql)) để phục vụ thiết kế và báo cáo: C10 **không thêm bảng hay cột** vào Analytics, chỉ thêm index V3 và đọc metadata từ Event/Ticket qua API nội bộ. `init.sql` đã gồm 4 index V3 và `erd.svg` đã được sinh lại, nhưng ERD tự động không vẽ index nên hình không đổi; sơ đồ dưới đây là nơi thể hiện index và quan hệ C10.

![ERD C10](erd-c10.svg)

Nét liền là bảng thuộc `eventhub_analytics`. `events` (Event Service) và `ticket_types` (Ticket Service) là bảng của service khác, chỉ hiện các cột C10 dùng; quan hệ là **ID mềm** (không có FK, không JOIN xuyên database). Analytics lấy dữ liệu của hai bảng này qua `GET /internal/events?ids=` (INT-02), `GET /internal/events/ids?organizerId=` (INT-03: danh sách sự kiện của ORGANIZER, dùng làm điều kiện `event_id IN (...)`) và `GET /internal/ticket-types/capacity?eventIds=`.

```mermaid
erDiagram
  events ||--o{ sales_metrics : "event_id (ID mềm)"
  events ||--o| dashboard_snapshot : "event_id (ID mềm)"
  events ||--o{ attendance_state : "event_id (ID mềm)"
  events ||--o{ ticket_types : "event_id (ID mềm)"
  ticket_types ||--o{ sales_metrics : "ticket_type_id (ID mềm)"
  events {
    CHAR(36) id PK "Event Service, đọc qua INT-02"
    CHAR(36) organizer_id "phạm vi ORGANIZER, đọc qua INT-03"
    VARCHAR(255) title
    VARCHAR(20) status
    TIMESTAMP start_time
  }
  ticket_types {
    CHAR(36) id PK "Ticket Service"
    CHAR(36) event_id "capacity = SUM(total_quota)"
    VARCHAR(100) name "VIP/PREMIUM/STANDARD"
    INT total_quota
  }
  sales_metrics {
    CHAR(36) id PK
    CHAR(36) event_id "V3 idx (event_id, metric_date)"
    CHAR(36) ticket_type_id
    VARCHAR(100) ticket_type_name "nhóm theo hạng vé"
    DATE metric_date "V3 idx (metric_date, event_id)"
    DECIMAL(14_2) revenue "doanh thu gộp"
    INT tickets_sold
    DECIMAL(14_2) refunded_amount
    INT tickets_refunded
    TIMESTAMP updated_at
  }
  dashboard_snapshot {
    CHAR(36) id PK
    CHAR(36) event_id "UNIQUE"
    DECIMAL(14_2) total_revenue
    INT total_tickets_sold "lifetime, mẫu số check-in"
    INT current_attendance
    INT total_checked_in "attendeesLifetime"
    TIMESTAMP last_updated
  }
  attendance_state {
    CHAR(36) id PK
    CHAR(36) ticket_id "UNIQUE, mỗi vé một dòng"
    CHAR(36) event_id "V3 idx (event_id, checked_in_at)"
    TIMESTAMP checked_in_at "khán giả theo ngày, V3 idx (checked_in_at, event_id)"
    TIMESTAMP checked_out_at
  }
```

`attendance_logs` và `processed_events` không tham gia C10 nên không vẽ ở đây.

## Bảng nào phục vụ số liệu nào

| Số liệu trên trang Phân tích | Bảng / nguồn | API |
|---|---|---|
| Doanh thu gộp, hoàn, thuần; vé bán, vé hoàn theo ngày/tháng | `sales_metrics` (`metric_date`) | `/analytics/overview` |
| Khán giả tham gia theo ngày/tháng | `attendance_state.checked_in_at` | `/analytics/overview` |
| Phân bổ theo hạng vé | `sales_metrics.ticket_type_name` | `/analytics/overview` |
| Xếp hạng sự kiện theo doanh thu/vé/khán giả | `sales_metrics` + `attendance_state` nhóm theo `event_id` | `/analytics/events` |
| Tỉ lệ check-in (toàn thời gian) | `dashboard_snapshot.total_tickets_sold`, `total_checked_in` − `SUM(sales_metrics.tickets_refunded)` | `/analytics/events` |
| Tên, trạng thái, ngày diễn ra | `events` (Event Service) | INT-02 `GET /internal/events?ids=` |
| Sức chứa | `ticket_types.total_quota` (Ticket Service) | `GET /internal/ticket-types/capacity` |
| Phạm vi của ORGANIZER (chỉ sự kiện của mình) | `events.organizer_id` (Event Service) | INT-03 `GET /internal/events/ids?organizerId=` |

## Index đề xuất V3

| Index | Bảng | Truy vấn dùng |
|---|---|---|
| `idx_sales_date_event (metric_date, event_id)` | sales_metrics | Lọc khoảng ngày trên toàn hệ thống |
| `idx_sales_event_date (event_id, metric_date)` | sales_metrics | Lọc khoảng ngày của một sự kiện |
| `idx_state_checkin_event (checked_in_at, event_id)` | attendance_state | Đếm khán giả theo khoảng thời gian |
| `idx_state_event_checkin (event_id, checked_in_at)` | attendance_state | Đếm khán giả của một sự kiện |

Nguồn thiết kế: [plan Analytics §8](../../plans/analytics-service.md), quyết định [C10](../../project-clarifications.md), từ điển trường [§7](fields.md). Tạo lại ảnh: dán khối Mermaid trên vào công cụ render Mermaid và xuất SVG ra `erd-c10.svg`.
