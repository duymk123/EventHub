# ERD ticket-service

Nguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất.

![ERD UUID CHAR(36)](erd.svg)

Ảnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service. [JPEG tương thích](1.jpeg).

```mermaid
erDiagram
  ticket_types ||--o{ seats : ticket_type_id
  reservations ||--o{ seats : reservation_id
  reservations ||--o{ reservation_items : reservation_id
  ticket_types ||--o{ reservation_items : ticket_type_id
  reservations ||--o{ reservation_seats : reservation_id
  seats ||--o{ reservation_seats : seat_id
  reservations ||--o{ tickets : reservation_id
  ticket_types ||--o{ tickets : ticket_type_id
  seats ||--o{ tickets : seat_id
  tickets ||--o{ ticket_scans : ticket_id
  ticket_types {
    CHAR(36) id PK
    CHAR(36) event_id
    VARCHAR(100) name
    DECIMAL(14_2) price
    INT row_count
    INT seats_per_row
    INT total_quota
    VARCHAR(20) status
    TIMESTAMP created_at
  }
  reservations {
    CHAR(36) id PK
    CHAR(36) order_id
    VARCHAR(20) status
    TIMESTAMP expires_at
    TIMESTAMP created_at
    TIMESTAMP confirmed_at
    TIMESTAMP released_at
    TIMESTAMP event_start_time
  }
  seats {
    CHAR(36) id PK
    CHAR(36) ticket_type_id FK
    INT row_index
    VARCHAR(3) row_label
    INT seat_number
    VARCHAR(10) status
    CHAR(36) reservation_id FK
    TIMESTAMP updated_at
  }
  reservation_items {
    CHAR(36) id PK
    CHAR(36) reservation_id FK
    CHAR(36) ticket_type_id FK
    INT quantity
    DECIMAL(14_2) unit_price
  }
  reservation_seats {
    CHAR(36) id PK
    CHAR(36) reservation_id FK
    CHAR(36) seat_id FK
    VARCHAR(8) seat_label
  }
  tickets {
    CHAR(36) id PK
    CHAR(36) reservation_id FK
    CHAR(36) order_id
    CHAR(36) ticket_type_id FK
    CHAR(36) seat_id FK
    VARCHAR(8) seat_label
    CHAR(36) owner_user_id
    VARCHAR(100) holder_name
    VARCHAR(255) qr_code_value
    VARCHAR(20) status
    TIMESTAMP issued_at
    TIMESTAMP cancelled_at
  }
  ticket_scans {
    CHAR(36) id PK
    CHAR(36) ticket_id FK
    CHAR(36) gate_id
    VARCHAR(10) scan_type
    TIMESTAMP scanned_at
  }
  outbox_events {
    CHAR(36) id PK
    CHAR(36) aggregate_id
    VARCHAR(100) event_type
    JSON payload
    VARCHAR(16) status
    TIMESTAMP created_at
    TIMESTAMP published_at
  }
```

Tạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).
