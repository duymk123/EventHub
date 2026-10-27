# EventHub

Hệ thống bán vé sự kiện với sáu service nghiệp vụ và API Gateway. Tài liệu đã đối chiếu BC_32_V2 và các xác nhận ngày 2026-10-27. Repo hiện là backend scaffold, frontend chưa triển khai; chưa có API nghiệp vụ, route Gateway hoặc tích hợp Keycloak chạy được.

## Bắt đầu đọc

| Tài liệu | Nội dung |
|---|---|
| [Sơ đồ và ví dụ](docs/architecture/README.md) | Luồng mua ba vé, ranh giới DB và dữ liệu giữa các service |
| [Quyết định và nguồn](docs/architecture/source-decisions.md) | V2 đã làm rõ gì, xung đột trong báo cáo, xác nhận nào thay thế thiết kế cũ |
| [Các điểm cần chốt](docs/project-clarifications.md) | C01–C09 và phần nào có thể làm trước |
| [Database](docs/database/README.md) | Quyền sở hữu, DDL/ERD từng service, mapping tên và giới hạn schema |
| [Service](services.md) | Trách nhiệm, API, trạng thái, quy tắc phối hợp |
| [OpenAPI](docs/api/README.md) | Hợp đồng mục tiêu sáu service; phần draft chưa phải chức năng đã có |
| [Kafka](docs/kafka/events.md) | Envelope messageId, payload, key, idempotency, retry/DLQ |
| [SKILL](SKILL.md), [mẫu code](code-templates.md) | Quy tắc triển khai và ví dụ nhất quán với schema |
| [Workflow](WORKFLOW.md), [Git](huong-dan-push-code.md) | Lượt tài liệu này sửa main theo yêu cầu; chưa tự commit/push |
| [Docker](Docker.md), [seed](infrastructure/mysql/seed/README.md) | Port, cấu hình local, Flyway và dữ liệu mẫu |
| [Kế hoạch hai service mới](docs/notification-analytics-plan.md) | Phạm vi/điều kiện trước triển khai Notification và Analytics |

## Các xác nhận quan trọng

UUID cho mọi bảng/tham chiếu của ứng dụng (gồm gateId); ba hạng VIP/PREMIUM/STANDARD do Ticket sở hữu; tồn kho gộp ticket_types. Kafka dùng messageId cho message, eventId cho sự kiện. Keycloak quản lý đăng nhập và cấp token; giữ UI/API riêng, Auth gọi Keycloak, không tự cấp JWT. Cấu hình realm/claim/issuer/audience/JWK/S2S còn chờ người phụ trách. ErrorResponse REST EventHub có code/message/timestamp/path.

## Chạy local

Java 21+, Maven Wrapper từ root; Docker Desktop/Compose. Tạo .env local theo [Docker.md](Docker.md), không commit secret.

~~~powershell
docker compose up --build -d
docker compose ps
.\mvnw.cmd verify
~~~

Gateway 8080, Auth 8081, Event 8082, Ticket 8083, Order 8084, Notification 8085, Analytics 8086. Kafka localhost:29092, Kafka UI 8089, Mailpit SMTP 1025/web 8025. MySQL host 33101–33107 là cấu hình scaffold, **không có nghĩa Gateway cần DB nghiệp vụ**.

Compose có Kafka KRaft/UI/Mailpit; chưa có Keycloak/Redis. Notification/Analytics có migration nền V1 + validate; các module khác còn Hibernate update. DB/Auth/API chưa hoàn thiện nên khởi động container không có nghĩa luồng mua vé đã chạy được. Context test không thay thế test tích hợp. Nếu Maven Wrapper lỗi khâu cache trên máy Windows, dùng Maven 3.9.x đã cài cùng tham số và giữ lỗi Wrapper thành công việc riêng.
