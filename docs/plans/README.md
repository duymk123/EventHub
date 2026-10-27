# Kế hoạch triển khai sáu service EventHub

Cập nhật 2026-10-28, viết trực tiếp trên `main` theo yêu cầu chủ dự án. Đây là kế hoạch code có thể chia việc, chưa phải xác nhận các lớp/hàm bên dưới đã tồn tại. Không tự commit/push từ yêu cầu viết plan.

## Đọc theo thứ tự

| Tài liệu | Phạm vi |
|---|---|
| [Quy ước và phối hợp](00-cross-service-contracts.md) | UUID, transaction, outbox, task retry, API nội bộ cần bổ sung, trình tự triển khai |
| [Auth](auth-service.md) | UI/API riêng, adapter Keycloak; không tự tạo DB mật khẩu/token |
| [Event](event-service.md) | Danh mục, CRUD/vòng đời sự kiện, internal response và ownership |
| [Ticket](ticket-service.md) | Hạng vé/tồn kho, reserve/confirm/release/expire, QR, check-in/out/cancel |
| [Order](order-service.md) | Chỉnh model hiện có, order/items/holders, payment/webhook/refund, phục hồi saga |
| [Notification](notification-service.md) | Template/in-app, email/QR, dedup, lease từng lần gửi và DLQ |
| [Analytics](analytics-service.md) | Sales/refund theo hạng, attendance chịu đảo thứ tự, API và STOMP |

Mỗi plan chỉ rõ interface/service methods, repository methods, kiểu dữ liệu và DTO, API → service, transaction/lock, migration còn thiếu, lỗi và test. Tên hàm là thiết kế đề xuất để bắt đầu code; khi đổi phải sửa API/plan/DDL liên quan cùng thay đổi.

## Các nguồn không được bỏ qua

Xác nhận trực tiếp của chủ dự án → [đối chiếu BC_32_V2](../architecture/source-decisions.md) → [câu hỏi còn mở](../project-clarifications.md) → [SKILL](../../SKILL.md), [services](../../services.md), [OpenAPI](../api/README.md), [Kafka](../kafka/events.md), [database](../database/README.md). Plan không tự phê duyệt phương án còn C02–C09.

UUID là quyết định đã chốt: Java `UUID`, MySQL `CHAR(36)`, JSON `string/uuid` dài 36. PK, FK nội bộ và ID mềm xuyên service đều theo quy tắc này. `messageId` là UUID message, `eventId` là UUID sự kiện. Hạng vé chỉ VIP/PREMIUM/STANDARD.

## Hiện trạng cần nhớ trước khi chia việc

| Service | Đã có | Cần xây |
|---|---|---|
| Auth | Application/context test | Facade + Keycloak clients, mapping identity/role và security |
| Event | Application/context test | Toàn bộ nghiệp vụ, entity/repository/Flyway |
| Ticket | Application/context test | Toàn bộ nghiệp vụ, entity/repository/Flyway/outbox |
| Order | BaseEntity UUID, Order/OrderItem/Payment/Refund, test UUID | Refactor model còn lệch hợp đồng; các tầng nghiệp vụ/worker/migration |
| Notification | Application, cấu hình Kafka/mail, Flyway V1 | Entity/repository/API/template/consumer/delivery |
| Analytics | Application, cấu hình Kafka, Flyway V1 | Entity/repository/projection/API/EventClient/WS |

API Gateway là phần phụ thuộc chung, không tính thành service nghiệp vụ thứ bảy trong bộ plan này. Nó vẫn cần route REST, route WS riêng, xác thực và chặn public `/internal/**`. Compose có Kafka/UI/Mailpit, chưa có Keycloak/Redis.

## Trình tự triển khai

1. Chuẩn hóa dependency/config, cơ chế identity/S2S, schema/migration và đọc được ERD UUID. Chốt các hợp đồng nội bộ còn thiếu trước khi hai nhóm code client/server độc lập.
2. Auth adapter và Event core/internal API. Có thể làm phần Event với Principal giả trong test, không thêm Auth tự cấp JWT để chạy tạm.
3. Ticket type/inventory/reservation và Order create/query. Kiểm tra reserve/rollback/expire trước nối payment.
4. Payment/webhook + durable confirm/release tasks + Ticket issue + outbox. C05/C06 quyết định nhánh thu tiền nhưng hết giữ chỗ.
5. Notification in-app/template, rồi email khi có nguồn người nhận C02/C03. Analytics sales/attendance có thể làm bằng fixture Kafka đã ký hợp đồng.
6. Refund/cancel sau C04/C05, chỉ số sau C09, cổng sau C07; Gateway WS và test xuyên luồng.

## ERD dùng khi code

| Service | Ảnh đọc được bằng mã | Mô tả |
|---|---|---|
| Auth | [Sơ đồ ranh giới](../database/auth-service/erd.svg) | Chưa có bảng profile local được yêu cầu; không bịa schema nội bộ Keycloak |
| Event | [ERD](../database/event-service/erd.svg) | categories/events |
| Ticket | [ERD](../database/ticket-service/erd.svg) | Sáu bảng, tồn kho gộp ticket_types |
| Order | [ERD](../database/order-service/erd.svg) | Bảy bảng tham chiếu, gồm holders/webhooks/outbox |
| Notification | [ERD](../database/notification-service/erd.svg) | Ba bảng V1; lease/batch ở plan là bổ sung tương lai |
| Analytics | [ERD](../database/analytics-service/erd.svg) | Bốn bảng V1; attendance_state ở plan là bổ sung tương lai |

DDL tham chiếu là nguồn sinh SVG/Mermaid; JPEG Order/Ticket là bản tương thích cùng nguồn. Chạy `node docs/database/render-erd.mjs --check` để phát hiện ảnh/SVG/Mermaid lệch DDL. Khi thêm bảng phục vụ plan, tạo migration/DDL trước, bổ sung layout renderer rồi tạo lại ERD; không vẽ ảnh trước rồi suy ngược thành entity.
