# ERD order-service — BC_32_V2 đã đối chiếu

Nguồn vật lý: [init.sql](init.sql). Đây là schema tham chiếu mục tiêu, chưa có migration nghiệp vụ trong module. [Quyết định nguồn](../../architecture/source-decisions.md) và [điểm cần chốt](../../project-clarifications.md).

```mermaid
erDiagram
  orders ||--o{ order_items : order_id
  order_items ||--o{ order_holders : order_item_id
  orders ||--o{ payments : order_id
  orders ||--o{ refund_requests : order_id
  orders ||--o{ payment_webhooks : order_id
  orders {
    char id PK
    char customer_id
    char event_id
    char reservation_id
    varchar status
    decimal subtotal
    decimal total_amount
    varchar currency
    timestamp expires_at
    timestamp paid_at
    timestamp created_at
    timestamp updated_at
  }
  order_items {
    char id PK
    char order_id
    char ticket_type_id
    varchar ticket_type_name
    int quantity
    decimal unit_price
    decimal line_total
  }
  order_holders {
    char id PK
    char order_item_id
    varchar holder_name
    varchar holder_email
    varchar holder_phone
  }
  payments {
    char id PK
    char order_id
    varchar provider
    varchar provider_transaction_id
    decimal amount
    varchar status
    text payment_url
    varchar idempotency_key
    timestamp created_at
    timestamp updated_at
  }
  refund_requests {
    char id PK
    char order_id
    char requested_by
    decimal amount
    text reason
    varchar status
    char approved_by
    text review_reason
    timestamp requested_at
    timestamp reviewed_at
    timestamp refunded_at
  }
  payment_webhooks {
    char id PK
    varchar provider
    varchar provider_event_id
    char order_id
    varchar payload_hash
    timestamp received_at
    timestamp processed_at
    varchar status
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

Chỉ các đường nối trên là FK nội bộ. ID tham chiếu service khác chỉ là UUID mềm; không truy cập DB khác. [Ảnh ERD](1.JPEG) đã được tạo lại từ DDL hiện hành với UUID `CHAR(36)`; chạy [script](../render-erd.ps1) để cập nhật khi DDL đổi. Cột outbox là bổ sung kỹ thuật chống mất message sau commit. Xem mapping/giới hạn ở [database README](../README.md).
