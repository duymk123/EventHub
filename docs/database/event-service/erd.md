# ERD event-service

Nguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất.

![ERD UUID CHAR(36)](erd.svg)

Ảnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service.

```mermaid
erDiagram
  categories ||--o{ events : category_id
  categories {
    CHAR(36) id PK
    VARCHAR(100) name
    TEXT description
    TIMESTAMP created_at
  }
  events {
    CHAR(36) id PK
    CHAR(36) organizer_id
    CHAR(36) category_id FK
    VARCHAR(255) title
    TEXT description
    VARCHAR(20) status
    TIMESTAMP start_time
    TIMESTAMP end_time
    TIMESTAMP created_at
    TIMESTAMP updated_at
    TEXT review_reason
  }
```

Tạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).
