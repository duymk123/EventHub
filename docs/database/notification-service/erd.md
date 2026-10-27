# ERD notification-service

Nguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất. Migration nền: [V1](../../../notification-service/src/main/resources/db/migration/V1__init.sql).

![ERD UUID CHAR(36)](erd.svg)

Ảnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service.

```mermaid
erDiagram
  notification_templates {
    CHAR(36) id PK
    VARCHAR(50) code
    VARCHAR(10) channel
    VARCHAR(255) subject
    TEXT body_template
    TIMESTAMP updated_at
  }
  notifications {
    CHAR(36) id PK
    VARCHAR(400) dedup_key
    CHAR(36) source_message_id
    CHAR(36) recipient_user_id
    VARCHAR(255) recipient_email
    VARCHAR(10) channel
    VARCHAR(50) template_code
    VARCHAR(255) title
    TEXT content
    JSON payload
    VARCHAR(10) status
    INT attempts
    VARCHAR(500) last_error
    TIMESTAMP sent_at
    TIMESTAMP read_at
    TIMESTAMP created_at
  }
  processed_events {
    CHAR(36) id PK
    TIMESTAMP processed_at
  }
```

Tạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).
