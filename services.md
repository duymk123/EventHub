# Danh mục service EventHub — sau đối chiếu BC_32_V2

Cập nhật 2026-10-27. Đây là thiết kế mục tiêu, mã nguồn hiện là scaffold. [Quyết định nguồn](docs/architecture/source-decisions.md), [database](docs/database/README.md), [câu hỏi còn mở](docs/project-clarifications.md).

Kế hoạch triển khai chi tiết ngày 2026-10-28: [docs/plans](docs/plans/README.md), gồm từng hàm service/repository, API, transaction, worker, migration và test cho cả sáu service.

Public API có prefix /api/v1 ở server URL OpenAPI; /internal/** là đường trực tiếp service, WS /ws/dashboard là ngoại lệ có route Gateway riêng. UUID cho mọi PK/tham chiếu bảng. Không FK/JOIN xuyên DB.

## auth-service

Đã chốt giữ UI/API tài khoản riêng, Auth gọi Keycloak. Identity, credentials, session, token và role thuộc Keycloak; không tự ký JWT hoặc tạo password_hash/refresh_tokens local. eventhub_auth chỉ là datasource scaffold, profile DB chỉ bổ sung nếu có nhu cầu nghiệp vụ riêng.

Role CUSTOMER/ORGANIZER/ADMIN và ACTIVE/LOCKED trong DTO là mapping ứng dụng. Grant flow, role/sub mapping, quyền gọi Admin API/service account, TTL và S2S cần cấu hình; không tự bật password grant hoặc cho rằng khóa user làm mọi JWT mất hiệu lực ngay.

[Auth API](docs/api/auth-service-openapi.yaml), [database identity](docs/database/auth-service/README.md). Các shape login/password là draft tùy cơ chế Keycloak, không phải code đã có.

| Method | Đường dẫn (chưa gồm /api/v1) | Mục đích | Kiến trúc |
|---|---|---|---|
| POST | `/auth/register` | AUTH-UC01 Đăng ký | Facade Auth → Keycloak |
| POST | `/auth/login` | AUTH-UC02 Đăng nhập | Facade Auth → Keycloak |
| POST | `/auth/refresh` | AUTH-UC03 Refresh token (rotation) | Facade Auth → Keycloak |
| POST | `/auth/logout` | AUTH-UC04 Đăng xuất | Facade Auth → Keycloak |
| GET | `/auth/me` | AUTH-UC05 Thông tin tài khoản hiện tại | Facade Auth → Keycloak |
| POST | `/auth/change-password` | AUTH-UC06 Đổi mật khẩu | Facade Auth → Keycloak |
| PATCH | `/admin/users/{userId}/lock` | AUTH-UC08 Khóa tài khoản | Facade Auth → Keycloak |
| PATCH | `/admin/users/{userId}/unlock` | AUTH-UC09 Mở khóa tài khoản | Facade Auth → Keycloak |
| PATCH | `/admin/users/{userId}/role` | AUTH-UC07 Gán / chuyển role | Facade Auth → Keycloak |

## event-service

Database: eventhub_event. Nguồn: Bảng 2.3–2.4, Hình 2.36–2.43.

Chỉ owner ORGANIZER được sửa/submit/publish/close; ADMIN review. Chỉ DRAFT/REJECTED được sửa, sửa REJECTED đưa về DRAFT. Public chỉ PUBLISHED. CLOSED ngừng bán, không tự hủy vé. Loại vé/quota thuộc Ticket, không zones A–D. venue chưa có trong baseline, chờ C03; review_reason là bổ sung kỹ thuật lưu lý do review.

**Trạng thái:** DRAFT → SUBMITTED → APPROVED → PUBLISHED → CLOSED; SUBMITTED → REJECTED → DRAFT khi sửa.

Schema/kiểu/NULL/unique: [DDL](docs/database/event-service/init.sql), [ERD](docs/database/event-service/erd.md). OpenAPI: [event](docs/api/event-service-openapi.yaml).

| Method | Đường dẫn (chưa gồm prefix public) | Mục đích | Trạng thái |
|---|---|---|---|
| POST | `/events` | EVT-01 Tạo Event (DRAFT) | Mục tiêu V2 |
| GET | `/events` | EVT-07 Tìm kiếm Event (public) | Mục tiêu V2 |
| GET | `/events/{id}` | EVT-08 Xem chi tiết Event (public) | Mục tiêu V2 |
| PUT | `/events/{id}` | EVT-02 Cập nhật Event | Mục tiêu V2 |
| POST | `/events/{id}/submit` | EVT-03 Nộp duyệt | Mục tiêu V2 |
| POST | `/events/{id}/publish` | EVT-05 Publish Event | Mục tiêu V2 |
| POST | `/events/{id}/close` | EVT-06 Đóng bán vé | Mục tiêu V2 |
| PATCH | `/admin/events/{id}/review` | EVT-04 Admin duyệt / từ chối Event | Mục tiêu V2 |
| GET | `/organizer/events` | EVT-09 Event của tôi | Mục tiêu V2 |
| GET | `/categories` | CAT-02 Danh sách Category (public) | Mục tiêu V2 |
| POST | `/categories` | CAT-01 Tạo Category | Mục tiêu V2 |
| PUT | `/categories/{id}` | CAT-03 Cập nhật Category | Mục tiêu V2 |
| DELETE | `/categories/{id}` | CAT-04 Xóa Category | Mục tiêu V2 |
| GET | `/internal/events/{id}` | INT-01 Thông tin Event cho service khác | Mục tiêu V2 |

## ticket-service

Database: eventhub_ticket. Nguồn: Bảng 2.5–2.9, Hình 2.44–2.56.

ticket_types gộp quota/counters; ba tên VIP/PREMIUM/STANDARD. Reserve 3 phút trong DB transaction với khóa hàng/UPDATE điều kiện, UNIQUE(order_id), giá snapshot. Confirm CAS và phát vé/QR trong cùng transaction, lặp trả kết quả cũ. Release CONFIRMED trả 409; RELEASED/EXPIRED gọi lại không trả quota hai lần. Check-in/out CAS và ticket_scans cùng transaction. Không tái nhập. gateId UUID nhưng owner/quyền thiết bị chờ C07. Cancel sau refund chỉ vé chưa dùng và >=48 giờ; khoảng trống sau provider refund chờ C05. Redis hỗ trợ khóa/cache, DB là nguồn chính.

**Trạng thái:** Reservation RESERVED → CONFIRMED|RELEASED|EXPIRED. Ticket ISSUED → CHECKED_IN → CHECKED_OUT; ISSUED → CANCELLED.

Schema/kiểu/NULL/unique: [DDL](docs/database/ticket-service/init.sql), [ERD](docs/database/ticket-service/erd.md). OpenAPI: [ticket](docs/api/ticket-service-openapi.yaml).

| Method | Đường dẫn (chưa gồm prefix public) | Mục đích | Trạng thái |
|---|---|---|---|
| POST | `/ticket-types` | Khởi tạo 3 Ticket Type cho Event | Mục tiêu V2 |
| PUT | `/ticket-types/{id}` | Cập nhật giá, quota, trạng thái | Mục tiêu V2 |
| GET | `/events/{eventId}/ticket-types` | Xem Ticket Type, giá và số vé còn lại | Mục tiêu V2 |
| GET | `/tickets/me` | Danh sách vé của tôi | Mục tiêu V2 |
| GET | `/tickets/{id}/qr` | Lấy QR của vé | Mục tiêu V2 |
| POST | `/gate/check-in` | Check-in bằng QR | Mục tiêu V2 |
| POST | `/gate/check-out` | Check-out bằng QR | Mục tiêu V2 |
| POST | `/internal/reservations` | Reserve Ticket (Order Service gọi) | Mục tiêu V2 |
| POST | `/internal/reservations/{id}/confirm` | Confirm Reservation và phát hành vé | Mục tiêu V2 |
| POST | `/internal/reservations/{id}/release` | Release Reservation | Mục tiêu V2 |
| POST | `/internal/tickets/cancel-by-order/{orderId}` | Hủy vé sau Refund thành công | Mục tiêu V2 |

## order-service

Database: eventhub_order. Nguồn: Bảng 2.10–2.15, Hình 2.57–2.72.

Sinh orderId → reserve → lưu Order/items/holders; một holder mỗi vé. Giá/hạn từ Ticket, không từ client. Payment ID provider có thể NULL trước phản hồi. Webhook phải xác minh chữ ký và đối chiếu amount/currency/transaction, dedup theo provider + provider_event_id; payload_hash không thay chữ ký. CAS với scheduler, chỉ một nhánh được hoàn quota. PAID rồi confirm tạm lỗi: job retry bền vững, chỉ order.paid sau phát vé. PAID nhưng reservation EXPIRED cần quy tắc C05/C06, không tự phát vé. Refund kiểm tra điều kiện >=48h lúc yêu cầu và duyệt; provider lỗi không hủy vé. Chính sách amount/từng vé/quyền duyệt C04 chưa chốt.

**Trạng thái:** Order PENDING_PAYMENT → PAID|FAILED|EXPIRED; PAID → REFUNDED. Payment CREATED → PENDING → SUCCEEDED|FAILED. Refund REQUESTED → APPROVED → PROCESSING → REFUNDED; REQUESTED → REJECTED; APPROVED|PROCESSING → FAILED.

Schema/kiểu/NULL/unique: [DDL](docs/database/order-service/init.sql), [ERD](docs/database/order-service/erd.md). OpenAPI: [order](docs/api/order-service-openapi.yaml).

| Method | Đường dẫn (chưa gồm prefix public) | Mục đích | Trạng thái |
|---|---|---|---|
| POST | `/orders` | Tạo Order và reserve vé | Mục tiêu V2 |
| GET | `/orders/me` | Lịch sử Order của tôi | Mục tiêu V2 |
| GET | `/orders/{id}` | Chi tiết Order | Mục tiêu V2 |
| POST | `/orders/{id}/payment` | Khởi tạo thanh toán | Mục tiêu V2 |
| POST | `/payments/webhook/{provider}` | Payment webhook (gateway gọi) | Mục tiêu V2 |
| POST | `/orders/{id}/refunds` | Yêu cầu Refund | Mục tiêu V2 |
| GET | `/admin/refunds` | Danh sách Refund | Mục tiêu V2 |
| POST | `/admin/refunds/{id}/approve` | Duyệt Refund | Mục tiêu V2 |
| POST | `/admin/refunds/{id}/reject` | Từ chối Refund | Mục tiêu V2 |

## notification-service

Database: eventhub_notification. Nguồn: Bảng 2.16–2.18, Hình 2.73–2.77; Flyway V1 bổ sung để thực hiện in-app và retry.

Consume ticket.issued, order.failed, order.refunded. EMAIL và IN_APP; PUSH/reminder để giai đoạn sau. Render title/content và lưu snapshot; QR PNG nhúng cid. Không lộ email/QR/payload ra GET notifications. Dedup từng messageId:channel:recipient, transaction ngắn, SMTP ngoài transaction. Chỉ processed khi mọi target thành công. SMTP có thể gửi trùng nếu crash giữa send và markSent. Nguồn customerEmail/holder fallback C02, venue C03. API page/unread-count/PUT template là bổ sung kỹ thuật có nhãn trong OpenAPI.

**Trạng thái:** Delivery PENDING → SENT|FAILED; FAILED có thể retry → SENT. Đọc IN_APP chỉ set read_at lần đầu. Template UUID PK, UNIQUE(code,channel).

Schema/kiểu/NULL/unique: [DDL](docs/database/notification-service/init.sql), [ERD](docs/database/notification-service/erd.md). OpenAPI: [notification](docs/api/notification-service-openapi.yaml).

| Method | Đường dẫn (chưa gồm prefix public) | Mục đích | Trạng thái |
|---|---|---|---|
| GET | `/notifications/me` | Danh sách thông báo in-app của tôi | Mục tiêu V2 |
| PUT | `/notifications/{id}/read` | Đánh dấu đã đọc | Mục tiêu V2 |
| GET | `/admin/notification-templates` | Danh sách template, chỉ ADMIN | Mục tiêu V2 |
| GET | `/notifications/me/unread-count` | Số thông báo IN_APP chưa đọc | Mục tiêu V2 |
| PUT | `/admin/notification-templates/{code}` | Upsert theo code và channel, chỉ ADMIN | Mục tiêu V2 |
| GET | `/admin/notification-templates/{code}` | Chi tiết template, chỉ ADMIN | Mục tiêu V2 |

## analytics-service

Database: eventhub_analytics. Nguồn: Bảng 2.19–2.21, Hình 2.78–2.81; Flyway V1 là nền, chưa là projection hoàn chỉnh.

Chỉ order.paid cộng sales, order.refunded ghi refund; không cộng ticket.issued hoặc trừ ticket.cancelled lần hai. Phân hạng vé cần ticketTypeId/name và tiền hoàn từng hạng. Claim messageId + UPSERT + log/snapshot cùng transaction. Attendance phải chịu check-out đến trước check-in, không giảm/clamp đơn thuần. GET dashboard tải ban đầu, WS /ws/dashboard có Gateway route riêng. CONNECT xác minh JWT, SUBSCRIBE kiểm tra owner/Admin, chặn client SEND vào topic server; session cần hết hạn theo token. Gọi Event Service để phân quyền và xác minh tồn tại; cache ownership chỉ ngắn hạn có invalidation. Quy tắc ngày báo cáo/thuần-gộp còn C09.

**Trạng thái:** Read model một snapshot/event, log nghiệp vụ bất biến. Chưa có dữ liệu trả 0 và updatedAt=null sau kiểm tra sự kiện; provider nội bộ lỗi trả 503, không giả số 0.

Schema/kiểu/NULL/unique: [DDL](docs/database/analytics-service/init.sql), [ERD](docs/database/analytics-service/erd.md). OpenAPI: [analytics](docs/api/analytics-service-openapi.yaml).

| Method | Đường dẫn (chưa gồm prefix public) | Mục đích | Trạng thái |
|---|---|---|---|
| GET | `/analytics/events/{id}/sales` | Báo cáo doanh số theo khoảng thời gian | Mục tiêu V2 |
| GET | `/analytics/events/{id}/attendance` | Số người đang có mặt | Mục tiêu V2 |
| GET | `/analytics/events/{id}/dashboard` | Snapshot ban đầu trước khi subscribe WS | Mục tiêu V2 |

## Kafka và luồng phối hợp

Hợp đồng đầy đủ tại [docs/kafka/events.md](docs/kafka/events.md). Topic order.paid → Analytics; order.failed → Notification; order.refunded → Notification + Analytics; ticket.issued → Notification; ticket.checked-in/out → Analytics; ticket.cancelled chưa có consumer giai đoạn này. Có notification.dlq và analytics.dlq.

Order reserve → thanh toán → webhook đã xác minh → CAS PAID → REST Ticket confirm → Ticket commit vé/QR và outbox ticket.issued → Order ghi outbox order.paid. Không phát vé qua hai đường. Consumer idempotent; lỗi đồng bộ phải có retry task bền vững và đối soát.

Sự kiện hủy/hoàn qua các topic có thể đến khác thứ tự; cùng key không bảo đảm thứ tự xuyên topic. Các điểm late payment/refund sau cancel conflict chưa được lấp bằng mặc định: xem C04–C06.
