# ERD ticket-service

Nguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất.

![ERD UUID CHAR(36)](erd.svg)

Ảnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service. [JPEG tương thích](1.jpeg).

```mermaid
erDiagram
  reservations ||--o{ reservation_items : reservation_id
  ticket_types ||--o{ reservation_items : ticket_type_id
  reservations ||--o{ tickets : reservation_id
  ticket_types ||--o{ tickets : ticket_type_id
  tickets ||--o{ ticket_scans : ticket_id
  ticket_types {
    CHAR(36) id PK
    CHAR(36) event_id
    VARCHAR(100) name
    DECIMAL(14_2) price
    INT total_quota
    INT available_quantity
    INT reserved_quantity
    INT sold_quantity
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
  }
  reservation_items {
    CHAR(36) id PK
    CHAR(36) reservation_id FK
    CHAR(36) ticket_type_id FK
    INT quantity
    DECIMAL(14_2) unit_price
  }
  tickets {
    CHAR(36) id PK
    CHAR(36) reservation_id FK
    CHAR(36) order_id
    CHAR(36) ticket_type_id FK
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

<<<<<<< HEAD
Chỉ các đường nối trên là FK nội bộ. ID tham chiếu service khác chỉ là UUID mềm; không truy cập DB khác. [Ảnh ERD](1.jpeg) đã được tạo lại từ DDL hiện hành với UUID `CHAR(36)`; chạy [script](../render-erd.ps1) để cập nhật khi DDL đổi. Cột outbox là bổ sung kỹ thuật chống mất message sau commit. Xem mapping/giới hạn ở [database README](../README.md).
=======
Tạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).
>>>>>>> 2d5e52cfa09fc1574dc9cd7ae930bf9373836f62
