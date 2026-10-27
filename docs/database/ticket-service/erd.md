# ERD ticket-service — BC_32_V2 đã đối chiếu

Nguồn vật lý: [init.sql](init.sql). Đây là schema tham chiếu mục tiêu, chưa có migration nghiệp vụ trong module. [Quyết định nguồn](../../architecture/source-decisions.md) và [điểm cần chốt](../../project-clarifications.md).

```mermaid
erDiagram
  reservations ||--o{ reservation_items : reservation_id
  ticket_types ||--o{ reservation_items : ticket_type_id
  reservations ||--o{ tickets : reservation_id
  ticket_types ||--o{ tickets : ticket_type_id
  tickets ||--o{ ticket_scans : ticket_id
  ticket_types {
    char id PK
    char event_id
    varchar name
    decimal price
    int total_quota
    int available_quantity
    int reserved_quantity
    int sold_quantity
    varchar status
    timestamp created_at
  }
  reservations {
    char id PK
    char order_id
    varchar status
    timestamp expires_at
    timestamp created_at
    timestamp confirmed_at
    timestamp released_at
  }
  reservation_items {
    char id PK
    char reservation_id
    char ticket_type_id
    int quantity
    decimal unit_price
  }
  tickets {
    char id PK
    char reservation_id
    char order_id
    char ticket_type_id
    char owner_user_id
    varchar holder_name
    varchar qr_code_value
    varchar status
    timestamp issued_at
    timestamp cancelled_at
  }
  ticket_scans {
    char id PK
    char ticket_id
    char gate_id
    varchar scan_type
    timestamp scanned_at
  }
  outbox_events {
    char id PK
    char aggregate_id
    varchar event_type
    json payload
    varchar status
    timestamp created_at
    timestamp published_at
  }
```

Chỉ các đường nối trên là FK nội bộ. ID tham chiếu service khác chỉ là UUID mềm; không truy cập DB khác. Ảnh JPEG cũ trong thư mục là lịch sử, không phải ERD hiện hành. Cột outbox (nếu có) là bổ sung kỹ thuật chống mất message sau commit. Xem mapping/giới hạn ở [database README](../README.md).
