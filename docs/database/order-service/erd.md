# ERD order-service

Nguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất.

![ERD UUID CHAR(36)](erd.svg)

Ảnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service. [JPEG tương thích](1.JPEG).

```mermaid
erDiagram
  orders ||--o{ order_items : order_id
  order_items ||--o{ order_holders : order_item_id
  orders ||--o{ payments : order_id
  orders ||--o{ refund_requests : order_id
  orders ||--o{ payment_webhooks : order_id
  orders {
    CHAR(36) id PK
    CHAR(36) customer_id
    CHAR(36) event_id
    CHAR(36) reservation_id
    VARCHAR(20) status
    DECIMAL(14_2) subtotal
    DECIMAL(14_2) total_amount
    VARCHAR(3) currency
    TIMESTAMP expires_at
    TIMESTAMP paid_at
    TIMESTAMP created_at
    TIMESTAMP updated_at
  }
  order_items {
    CHAR(36) id PK
    CHAR(36) order_id FK
    CHAR(36) ticket_type_id
    VARCHAR(100) ticket_type_name
    INT quantity
    DECIMAL(14_2) unit_price
    DECIMAL(14_2) line_total
  }
  order_holders {
    CHAR(36) id PK
    CHAR(36) order_item_id FK
    VARCHAR(100) holder_name
    VARCHAR(255) holder_email
    VARCHAR(20) holder_phone
  }
  payments {
    CHAR(36) id PK
    CHAR(36) order_id FK
    VARCHAR(50) provider
    VARCHAR(100) provider_transaction_id
    DECIMAL(14_2) amount
    VARCHAR(20) status
    TEXT payment_url
    VARCHAR(100) idempotency_key
    TIMESTAMP created_at
    TIMESTAMP updated_at
  }
  refund_requests {
    CHAR(36) id PK
    CHAR(36) order_id FK
    CHAR(36) requested_by
    DECIMAL(14_2) amount
    TEXT reason
    VARCHAR(20) status
    CHAR(36) approved_by
    TEXT review_reason
    TIMESTAMP requested_at
    TIMESTAMP reviewed_at
    TIMESTAMP refunded_at
  }
  payment_webhooks {
    CHAR(36) id PK
    VARCHAR(50) provider
    VARCHAR(100) provider_event_id
    CHAR(36) order_id FK
    VARCHAR(255) payload_hash
    TIMESTAMP received_at
    TIMESTAMP processed_at
    VARCHAR(20) status
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
Chỉ các đường nối trên là FK nội bộ. ID tham chiếu service khác chỉ là UUID mềm; không truy cập DB khác. [Ảnh ERD](1.JPEG) đã được tạo lại từ DDL hiện hành với UUID `CHAR(36)`; chạy [script](../render-erd.ps1) để cập nhật khi DDL đổi. Cột outbox là bổ sung kỹ thuật chống mất message sau commit. Xem mapping/giới hạn ở [database README](../README.md).
=======
Tạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).
>>>>>>> 2d5e52cfa09fc1574dc9cd7ae930bf9373836f62
