# Kế hoạch triển khai sáu service và API Gateway EventHub

Cập nhật 2026-10-28, viết trực tiếp trên `main` theo yêu cầu chủ dự án. Đây là kế hoạch code có thể chia việc, chưa phải xác nhận các lớp/hàm bên dưới đã tồn tại. Không tự commit/push từ yêu cầu viết plan.

Bổ sung 2026-10-31: [plan API Gateway](api-gateway.md) và [tài liệu nhập môn kèm luồng request](../architecture/api-gateway.md). Gateway là thành phần hạ tầng bên cạnh sáu service nghiệp vụ.

## Đọc theo thứ tự

| Tài liệu | Phạm vi |
|---|---|
| [Quy ước và phối hợp](00-cross-service-contracts.md) | UUID, transaction, outbox, task retry, API nội bộ cần bổ sung, trình tự triển khai |
| [API Gateway](api-gateway.md) | Route 47 REST operations public, chặn internal, JWT/CORS, webhook, WS, timeout và triển khai từng bước |
| [Auth](auth-service.md) | UI/API riêng, adapter Keycloak; không tự tạo DB mật khẩu/token |
| [Event](event-service.md) | Danh mục, CRUD/vòng đời sự kiện, internal response và ownership |
| [Ticket](ticket-service.md) | Hạng vé/tồn kho, reserve/confirm/release/expire, QR, check-in/out/cancel |
| [Order](order-service.md) | Chỉnh model hiện có, order/items/holders, payment/webhook/refund, phục hồi saga |
| [Notification](notification-service.md) | Template/in-app, email/QR, dedup, lease từng lần gửi và DLQ |
| [Analytics](analytics-service.md) | Sales/refund theo hạng, attendance chịu đảo thứ tự, API và STOMP |

Mỗi plan chỉ rõ interface/service methods, repository methods, kiểu dữ liệu và DTO, API → service, transaction/lock, migration còn thiếu, lỗi và test. Tên hàm là thiết kế đề xuất để bắt đầu code; khi đổi phải sửa API/plan/DDL liên quan cùng thay đổi.

Quy ước package: `entity` chỉ chứa entity/mapped superclass, enum nằm trực tiếp ở `com.eventhub.<service>.common` của từng service. Không để enum ở entity hoặc entity/enums.

**Cập nhật theo yêu cầu chủ dự án ngày 2026-10-28:** đã bỏ toàn bộ file/profile/dependency test. Không tạo lại test khi triển khai các plan này. Các mục test bên dưới và trong sáu plan là danh sách tình huống tham khảo để chạy thử/kiểm tra thủ công; không tự thêm JUnit/Mockito/Testcontainers/GreenMail.

## Các nguồn không được bỏ qua

Xác nhận trực tiếp → [đối chiếu nguồn](../architecture/source-decisions.md) → [quyết định hiện hành](../project-clarifications.md) → [SKILL](../../SKILL.md), [services](../../services.md), [OpenAPI](../api/README.md), [Kafka](../kafka/events.md), [database](../database/README.md). C02–C05/C08 đã có quyết định ngày 2026-11-05, C09 giữ xác nhận trước đó; C06/C07 để sau. [Thiết kế phục hồi C05](../architecture/payment-refund-recovery.md) mô tả hold/fence/verify và bù 100%, chưa phải code đã chạy.

UUID là quyết định đã chốt: Java `UUID`, MySQL `CHAR(36)`, JSON `string/uuid` dài 36. PK, FK nội bộ và ID mềm xuyên service đều theo quy tắc này. `messageId` là UUID message, `eventId` là UUID sự kiện. Hạng vé chỉ VIP/PREMIUM/STANDARD.

## Hiện trạng cần nhớ trước khi chia việc

Tiến độ riêng Notification/Analytics ngày 2026-11-05 đã có REST security, consumer, email/QR, projection và WebSocket, kèm migration V2. Bảng scaffold bên dưới là mốc ban đầu. Xem [cấu hình và hợp đồng nối nhóm](../notification-analytics-implementation.md).

| Service | Đã có | Cần xây |
|---|---|---|
| Auth | Application | Facade + Keycloak clients, mapping identity/role và security |
| Event | Application | Toàn bộ nghiệp vụ, entity/repository/Flyway |
| Ticket | Application | Toàn bộ nghiệp vụ, entity/repository/Flyway/outbox |
| Order | BaseEntity UUID, Order/OrderItem/Payment/Refund | Refactor model còn lệch hợp đồng; các tầng nghiệp vụ/worker/migration |
| Notification | Application, Kafka/mail, Flyway V1, ba entity và enum trong common | Repository/API/template/consumer/delivery |
| Analytics | Application, Kafka, Flyway V1, bốn entity/enum | Repository/projection/API/EventClient/WS |

API Gateway là phần phụ thuộc chung, có [plan riêng](api-gateway.md), không tính thành service nghiệp vụ thứ bảy. Module hiện vẫn là scaffold MVC/JPA/MySQL, chưa có routing/security; plan đề xuất chuyển sang Gateway WebFlux và bỏ DB riêng khi triển khai. Compose có Kafka/UI/Mailpit, chưa có Keycloak/Redis trong file Compose chính; hạ tầng local riêng xem [hướng dẫn local](../local-development.md).

## Trình tự triển khai

1. Chuẩn hóa dependency/config, cơ chế identity/S2S, schema/migration và đọc được ERD UUID. Bắt đầu Gateway G1–G2: runtime/routing và chặn internal; G3 security đi cùng Auth. Chốt các hợp đồng nội bộ còn thiếu trước khi hai nhóm code client/server độc lập.
2. Auth adapter và Event core/internal API. Có thể làm phần Event với Principal giả trong test, không thêm Auth tự cấp JWT để chạy tạm.
3. Ticket type/inventory/reservation và Order create/query. Kiểm tra reserve/rollback/expire trước nối payment.
4. Payment/webhook + durable confirm/release/compensation + Ticket issue/fence/outbox theo C05; chọn adapter khi C06 được bổ sung.
5. Notification theo C02 holder/fallback buyer và C03 text chung; Analytics theo chỉ số C09 đã chốt, dùng fixture producer đủ hợp đồng.
6. Refund C04 cả đơn 70%/ADMIN, C05 prepare hold trước provider; cổng C07 để sau. Hoàn thiện Gateway G4–G6 và kiểm tra thủ công xuyên luồng, CONNECT/SUBSCRIBE cùng Analytics.

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
