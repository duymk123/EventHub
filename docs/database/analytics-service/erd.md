# ERD analytics-service

Nguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất. Migration nền: [V1](../../../analytics-service/src/main/resources/db/migration/V1__init.sql).

![ERD UUID CHAR(36)](erd.svg)

Ảnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service.

```mermaid
erDiagram
  sales_metrics {
    CHAR(36) id PK
    CHAR(36) event_id
    CHAR(36) ticket_type_id
    VARCHAR(100) ticket_type_name
    DATE metric_date
    DECIMAL(14_2) revenue
    INT tickets_sold
    DECIMAL(14_2) refunded_amount
    INT tickets_refunded
    TIMESTAMP updated_at
  }
  dashboard_snapshot {
    CHAR(36) id PK
    CHAR(36) event_id
    DECIMAL(14_2) total_revenue
    INT total_tickets_sold
    INT current_attendance
    INT total_checked_in
    TIMESTAMP last_updated
  }
  attendance_logs {
    CHAR(36) id PK
    CHAR(36) ticket_id
    CHAR(36) event_id
    CHAR(36) gate_id
    VARCHAR(10) action
    TIMESTAMP occurred_at
  }
  processed_events {
    CHAR(36) id PK
    TIMESTAMP processed_at
  }
```

Tạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).
