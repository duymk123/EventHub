# ERD analytics-service

Schema nền theo [migration V1](../../../analytics-service/src/main/resources/db/migration/V1__init.sql); bản đọc tại [init.sql](init.sql). Các bảng: `sales_metrics`, `dashboard_snapshot`, `attendance_logs`, `processed_events`. Không FK vật lý xuyên service.

```mermaid
erDiagram
  sales_metrics {
    char36 id PK
  }
  dashboard_snapshot {
    char36 id PK
  }
  attendance_logs {
    char36 id PK
  }
  processed_events {
    char36 id PK
  }
```

Đây là ERD mức bảng, không phải toàn bộ cột; xem DDL để biết unique/index. Các bảng chỉ liên hệ theo khóa nghiệp vụ/UUID mềm, không thêm FK vào các DB nguồn. [Giới hạn schema và mapping V2](../README.md).
