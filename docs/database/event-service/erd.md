# ERD event-service — BC_32_V2 đã đối chiếu

Nguồn vật lý: [init.sql](init.sql). Đây là schema tham chiếu mục tiêu, chưa có migration nghiệp vụ trong module. [Quyết định nguồn](../../architecture/source-decisions.md) và [điểm cần chốt](../../project-clarifications.md).

```mermaid
erDiagram
  categories ||--o{ events : category_id
  categories {
    char id PK
    varchar name
    text description
    timestamp created_at
  }
  events {
    char id PK
    char organizer_id
    char category_id
    varchar title
    text description
    varchar status
    timestamp start_time
    timestamp end_time
    timestamp created_at
    timestamp updated_at
    text review_reason
  }
```

Chỉ các đường nối trên là FK nội bộ. ID tham chiếu service khác chỉ là UUID mềm; không truy cập DB khác. Ảnh JPEG cũ trong thư mục là lịch sử, không phải ERD hiện hành. Cột outbox (nếu có) là bổ sung kỹ thuật chống mất message sau commit. Xem mapping/giới hạn ở [database README](../README.md).
