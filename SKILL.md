---
name: eventhub-architecture
description: Quy ước kiến trúc và code EventHub; đọc quyết định BC_32_V2 và các phụ thuộc còn mở trước khi triển khai.
---

# EventHub — kiến trúc và quy ước code

Đọc [quyết định nguồn](docs/architecture/source-decisions.md), [điểm cần chốt](docs/project-clarifications.md) và [services.md](services.md). Xác nhận trực tiếp của chủ dự án cao hơn báo cáo; báo cáo cao hơn tài liệu phiên bản trước. File này không tự giải quyết câu hỏi đang mở.

## 1. Phạm vi

Hệ thống bán vé sự kiện gồm CUSTOMER, ORGANIZER, ADMIN. Keycloak quản lý đăng nhập/cấp token theo xác nhận mới nhất. Mỗi tài khoản có một vai trò nghiệp vụ trong phạm vi V2; Keycloak có thể chứa role kỹ thuật khác, không tự chuyển các role đó thành quyền EventHub. Chưa có code nghiệp vụ trong scaffold.

## 2. Ranh giới service

| Service | Sở hữu |
|---|---|
| Auth | Facade UI/API tài khoản riêng, gọi Keycloak. Profile DB chỉ thêm nếu cần; credentials/session/token thuộc Keycloak. |
| Event | events/categories; thông tin và máy trạng thái sự kiện. |
| Ticket | ticket_types gộp tồn kho, reservations/items, tickets, ticket_scans. |
| Order | orders/items/holders, payments, refund_requests, payment_webhooks. |
| Notification | templates, notifications, processed_events. |
| Analytics | sales_metrics, dashboard_snapshot, attendance_logs, processed_events. |

Database per service: không JOIN, FK vật lý hoặc JPA association xuyên service. Các ID bên ngoài chỉ là UUID mềm; gọi REST để xác minh. Keycloak có DB riêng do Keycloak quản lý, không truy cập SQL trực tiếp từ EventHub.

Order → Ticket dùng REST reserve/confirm/release/cancel. Ticket phát vé trong transaction confirm; Kafka phát sau commit để Notification/Analytics nhận. Không có consumer order.paid phát vé lần hai. Không dùng distributed transaction; cần idempotency, bù trừ và đối soát bền vững.

Client nghiệp vụ qua Gateway /api/v1/**. WS /ws/dashboard có route ws riêng. /internal/** không route công khai; chỉ cho service xác thực. Webhook xác minh chữ ký provider và thiết bị cổng có quyền riêng.

## 3. Phân lớp

Controller nhận @Valid Request DTO → Service interface → ServiceImpl chứa nghiệp vụ/transaction → Repository → Entity. Controller không gọi Repository, không trả Entity. DTO dùng record, mapper dùng MapStruct. Client REST và producer/consumer có interface riêng; consumer chỉ chuyển sự kiện vào service.

userId lấy từ Principal đã xác minh; không tin userId, giá hoặc kết quả thanh toán do client gửi. Role không thay kiểm tra sở hữu sự kiện/đơn/vé.

## 4. Package và migration

Package gốc com.eventhub.<service>: controller, service/impl, repository, entity/enums, dto/request, dto/response, mapper, exception, client/dto, messaging/event|producer|consumer, config, scheduler nếu có nhu cầu.

Migration Flyway ở src/main/resources/db/migration/V<n>__description.sql. Notification/Analytics đã có V1 và ddl-auto: validate. Service khác còn update trong scaffold; chuyển sang migration trước khi dùng môi trường chung. Không sửa migration đã áp dụng; dùng version tiếp theo. docs/database/*/init.sql là schema tham chiếu, không là migration triển khai.

## 5. Hợp đồng chung

**ID và dữ liệu:** UUID cho mọi PK/tham chiếu bảng EventHub, gồm gateId. Java UUID; JSON/OpenAPI string format uuid; MySQL CHAR(36). Không AUTO_INCREMENT, không nhận số thay UUID. Mã giao dịch/providerEventId bên ngoài là string của provider. UUID không phải QR; QR là token opaque.

Tên cột snake_case, Java camelCase, enum lưu string. Tiền dùng BigDecimal; DDL tham chiếu dùng DECIMAL(14,2) thống nhất với migration Analytics, không dùng double. UTC Instant/TIMESTAMP; ngày báo cáo đề xuất Asia/Ho_Chi_Minh, còn C09 xác nhận nghiệp vụ. Không tự bổ sung phí/voucher/đa tiền tệ.

**API:** OpenAPI trong docs/api là hợp đồng mục tiêu, không chứng minh endpoint đã tồn tại. Public /api/v1, internal /internal, admin /api/v1/admin; WS /ws/dashboard ngoại lệ có route riêng. Tạo mới 201, cập nhật không body 204, GET trả DTO hoặc page. Quy tắc ownership kiểm tra tại service. Giá/tổng tiền tính từ dữ liệu server.

**Lỗi REST:** {code, message, timestamp, path}; GlobalExceptionHandler chuẩn hóa cả validation và lỗi authentication/authorization của API EventHub. Keycloak giữ lỗi chuẩn OIDC riêng, không ép thành ErrorResponse.

**Auth:** Keycloak cấp token, EventHub không tự ký JWT hoặc lưu password_hash/refresh_tokens. Thuật toán, issuer/audience/JWK, TTL, role mapper, S2S còn chờ cấu hình. Không mặc định access 5/15 phút hoặc refresh 7 ngày. Đề xuất kỹ thuật dùng sub UUID trực tiếp, cần kiểm tra realm/mapping khi tích hợp; fail closed nếu thiếu role/subject hợp lệ.

**Kafka:** Mỗi record JSON có messageId UUID, eventType, occurredAt và eventId UUID nghiệp vụ. Retry/replay giữ nguyên messageId. processed_events.id chính là messageId. Hợp đồng tại [events.md](docs/kafka/events.md). Producer JsonSerializer tắt spring.json.add.type.headers; consumer không phụ thuộc Java type header. Có notification.dlq và analytics.dlq.

Ghi outbox/delivery log cùng transaction nghiệp vụ, publish có retry và đánh dấu sau ACK. AFTER_COMMIT đơn lẻ không chống mất message. Claim dedup và projection cùng transaction. SMTP ngoài transaction DB, dedup từng recipient; không hứa exactly-once email. Key ticketId không giữ thứ tự giữa hai topic check-in/out; cần projection chịu đảo thứ tự.

## 6. Invariant và trạng thái

- TicketTypeName chỉ VIP/PREMIUM/STANDARD. ticket_types giữ total_quota = available_quantity + reserved_quantity + sold_quantity, các lượng không âm; SELECT FOR UPDATE hoặc UPDATE điều kiện trong transaction, không ticket_inventory riêng.
- Reservation UNIQUE(order_id); RESERVED → CONFIRMED|RELEASED|EXPIRED. Confirm phát vé một lần, lặp trả vé cũ. Release CONFIRMED trả 409; release/expire chỉ trả quota một lần.
- Ticket ISSUED → CHECKED_IN → CHECKED_OUT, ISSUED → CANCELLED. Không có tái nhập trong V2. CAS trạng thái và ghi ticket_scans cùng transaction, gateId UUID.
- Order PENDING_PAYMENT → PAID|FAILED|EXPIRED, PAID → REFUNDED. order.paid chỉ publish sau confirm thành công; confirm retry phải tồn tại bền vững sau ACK webhook.
- Webhook xác minh chữ ký; hash chỉ phát hiện payload thay đổi, không thay thế chữ ký. UNIQUE(provider,provider_event_id) cho chống trùng; hai payload khác nhau cùng key phải báo lỗi/đối soát.
- Refund kiểm tra điều kiện ít nhất 48 giờ lúc yêu cầu và duyệt, vé chưa dùng. Các khoảng trống thanh toán muộn/hủy vé sau refund nằm ở C04–C06; không lấp bằng mặc định kinh doanh.

## 7. Hoàn thành một thay đổi

Đọc source-decisions/câu hỏi → migration/entity → repository/CAS → DTO/mapper → service → controller/client/messaging → test cần thiết. Kiểm tra role+sở hữu, invariant, retry, crash và cạnh tranh với luồng tiền/vé. Không viết hàng loạt test phản chiếu implementation cho sửa tài liệu. Context test scaffold chưa kiểm tra DB, Kafka, email hoặc Keycloak.

## 8. Workflow

Theo [WORKFLOW.md](WORKFLOW.md). Lượt sửa tài liệu này thực hiện trực tiếp main theo yêu cầu; chưa được coi là yêu cầu push. Những phần còn mở ở docs/project-clarifications.md cần được giải quyết trước khi triển khai nhánh nghiệp vụ phụ thuộc.
