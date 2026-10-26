# Danh mục service EventHub

Dùng làm tài liệu tra cứu: bảng DB, API, máy trạng thái, Kafka và quy tắc riêng của từng service. Kiểu dữ liệu: `Long`=BIGINT, `String`=VARCHAR, `Instant`=TIMESTAMP, `BigDecimal`=DECIMAL, `Enum`=lưu STRING. "Soft FK" = chỉ lưu id, không FK vật lý.

Đường dẫn công khai đều có tiền tố `/api/v1`. `/internal/**` chỉ service-to-service.

---

## 1. auth-service  (`auth_service_db`)

**Trách nhiệm**: tài khoản, đăng ký/đăng nhập, Access Token (JWT, 5 phút) + Refresh Token (7 ngày) với Rotation + Reuse Detection, đổi mật khẩu, vai trò, khóa/mở khóa.

**Bảng**
- `users`: id, email (UNIQUE), password_hash, full_name, status (`ACTIVE|LOCKED`), created_at, updated_at
- `roles`: id, name (UNIQUE: `CUSTOMER|ORGANIZER|ADMIN`), description
- `users_roles`: user_id (FK), role_id (FK), assigned_at — PK(user_id, role_id)
- `refresh_tokens`: id, user_id (FK), token (UNIQUE, lưu hash), family_id (INDEX), expires_at, revoked_at (NULL = còn hiệu lực), created_at

**API**
| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | public | Đăng ký, gán `CUSTOMER`, status `ACTIVE` |
| POST | `/auth/login` | public | Trả Access + Refresh Token |
| POST | `/auth/refresh` | Refresh Token | Xoay vòng token |
| POST | `/auth/logout` | Refresh Token | Thu hồi Refresh Token (204) |
| GET | `/auth/me` | JWT | Thông tin tài khoản hiện tại |
| POST | `/auth/change-password` | JWT | Đổi mật khẩu, thu hồi mọi Refresh Token (204) |
| PATCH | `/admin/users/{userId}/lock` | ADMIN | `LOCKED` + thu hồi Refresh Token (204) |
| PATCH | `/admin/users/{userId}/unlock` | ADMIN | `ACTIVE` (204) |
| PATCH | `/admin/users/{userId}/role` | ADMIN | Gán/chuyển vai trò (204) |

**Quy tắc**
- Đăng ký không cho client tự chọn vai trò. Chỉ ADMIN gán/chuyển vai trò.
- Access Token: `sub=userId`, `roles`, `iat`, `exp`. Logout không blacklist Access Token.
- Refresh: băm token → tìm → nếu đã `revoked` ⇒ **thu hồi cả `family_id`** và trả `REFRESH_TOKEN_REUSED`; hợp lệ ⇒ thu hồi token cũ, cấp token mới cùng `family_id`.
- Tài khoản `LOCKED`: không login, không refresh.
- Lỗi: 409 `EMAIL_ALREADY_EXISTS`, 401 `INVALID_CREDENTIALS`, 403 `ACCOUNT_LOCKED`, 401 `INVALID_REFRESH_TOKEN`, 401 `REFRESH_TOKEN_EXPIRED`, 401 `REFRESH_TOKEN_REUSED`.

**Máy trạng thái**: User `ACTIVE ⇄ LOCKED`. Refresh Token `ACTIVE → REVOKED | EXPIRED`.

---

## 2. event-service  (`event_service_db`)

**Trách nhiệm**: vòng đời sự kiện, danh mục, khu vực (zone), tìm kiếm công khai. Phát event qua `outbox_events`.

**Bảng**
- `events`: id, organizer_id (Soft FK→users), category_id (FK), title, description, venue, status, start_time, end_time, created_at, updated_at
- `event_categories`: id, name (UNIQUE), description, created_at
- `event_zones`: id, event_id (FK), name, capacity, created_at — UNIQUE(event_id, name)
- `outbox_events`: id, aggregate_type, aggregate_id, event_type, payload (JSON), status (`PENDING|PUBLISHED|FAILED`), created_at, published_at

**API**
| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/events` | ORGANIZER | Tạo Event `DRAFT` |
| PUT | `/events/{id}` | ORGANIZER (owner) | Cập nhật (chỉ khi trạng thái cho phép) |
| POST | `/events/{id}/submit` | ORGANIZER (owner) | `DRAFT→SUBMITTED` |
| PATCH | `/admin/events/{id}/review` | ADMIN | Duyệt/từ chối (`APPROVED`/`REJECTED`) |
| POST | `/events/{id}/publish` | ORGANIZER (owner) | `APPROVED→PUBLISHED` |
| POST | `/events/{id}/close` | ORGANIZER (owner) | `PUBLISHED→CLOSED` |
| GET | `/events?keyword=&category=&date=` | public | Tìm kiếm Event đã `PUBLISHED` |
| GET | `/events/{id}` | public | Chi tiết (404 nếu chưa `PUBLISHED`) |
| GET | `/organizer/events` | ORGANIZER | Event của mình |
| POST/GET/PUT/DELETE | `/categories[/{id}]` | ADMIN (GET public) | Quản lý danh mục |
| POST/PUT/DELETE | `/zones[/{id}]` | ADMIN | Quản lý khu vực |

**Máy trạng thái**: `DRAFT → SUBMITTED → APPROVED → PUBLISHED → CLOSED`; `SUBMITTED → REJECTED → DRAFT` (organizer sửa lại).

**Quy tắc**: chỉ owner (`organizer_id = userId`) được sửa/submit/publish/close; `start_time < end_time`; public chỉ thấy `PUBLISHED`. Loại vé **không** thuộc service này (xem Ticket Service).

---

## 3. ticket-service  (`ticket_service_db`)

**Trách nhiệm**: loại vé, tồn kho, giữ chỗ tạm 3 phút, phát hành vé + QR, check-in/out, hủy vé. **Sở hữu** dữ liệu vé và reservation.

**Bảng**
- `ticket_types`: id, event_id (Soft FK→events), name (Khán đài A|B|C|D), price, status (`ACTIVE|INACTIVE`), created_at, updated_at — UNIQUE(event_id, name)
- `ticket_inventory`: ticket_type_id (PK, FK 1-1), total_quota, available_quantity, reserved_quantity, sold_quantity, version, updated_at — ràng buộc `available + reserved + sold = total_quota`
- `reservations`: id, order_id (Soft FK→orders, **UNIQUE**), status (`RESERVED|CONFIRMED|RELEASED|EXPIRED`), expires_at, created_at, confirmed_at, released_at — INDEX(status, expires_at)
- `reservation_items`: id, reservation_id (FK), ticket_type_id (FK), quantity, unit_price
- `tickets`: id, reservation_id (FK), order_id (Soft FK), ticket_type_id (FK), owner_user_id (Soft FK→users), holder_name, holder_email, qr_code_value (UNIQUE), status (`ISSUED|CHECKED_IN|CHECKED_OUT|CANCELLED`), issued_at, checked_in_at, checked_out_at, cancelled_at
- `ticket_check_logs`: id, ticket_id (FK), event_id (Soft FK), gate_id, action (`CHECK_IN|CHECK_OUT`), scanned_at

**API**
| Method | Endpoint | Tác nhân | Mô tả |
|---|---|---|---|
| POST | `/ticket-types` | ORGANIZER | Khởi tạo 4 loại vé A–D cho Event (không cho tên khác) |
| PUT | `/ticket-types/{id}` | ORGANIZER | Sửa giá/quota/trạng thái; quota không nhỏ hơn đã bán + đang giữ |
| GET | `/events/{eventId}/ticket-types` | public | Loại vé + số còn lại |
| POST | `/internal/reservations` | Order Service | Reserve (idempotent theo `orderId`), trả `reservationId`, giá chính thức, `expiresAt` |
| POST | `/internal/reservations/{id}/confirm` | Order Service | `RESERVED→CONFIRMED`, phát hành vé + QR |
| POST | `/internal/reservations/{id}/release` | Order Service | `RESERVED→RELEASED`, trả quota |
| GET | `/tickets/me` | CUSTOMER | Vé của tôi |
| GET | `/tickets/{id}/qr` | CUSTOMER (owner) | QR của vé |
| POST | `/gate/check-in` | Máy quét | `ISSUED→CHECKED_IN` |
| POST | `/gate/check-out` | Máy quét | `CHECKED_IN→CHECKED_OUT` |
| POST | `/internal/tickets/cancel-by-order/{orderId}` | Order Service | Hủy vé sau Refund thành công |

**Máy trạng thái**: Reservation `RESERVED → CONFIRMED | RELEASED | EXPIRED`. Ticket `ISSUED → CHECKED_IN → CHECKED_OUT`; `ISSUED → CANCELLED`.

**Quy tắc**
- Reserve: kiểm tra loại vé A–D đang mở bán, quota đủ; giảm `available`, tăng `reserved`, tạo reservation trong **một transaction**; không đủ vé ⇒ rollback, `INSUFFICIENT_TICKETS`. `expiresAt = now + 3 phút`.
- Confirm: chỉ sau khi Order `PAID`; giảm `reserved`, tăng `sold`, tạo vé + QR duy nhất cùng transaction; sau commit publish `ticket.issued`. Gọi lặp trả kết quả cũ.
- Release/Expire: trả quota đúng một lần. **Ticket Scheduler** tìm reservation `RESERVED` có `expires_at < now` để chuyển `EXPIRED` (fail-safe khi Order Service không gọi Release).
- Confirm vs Release/Expire tranh nhau: dùng compare-and-set, chỉ một thao tác thắng.
- Cancel: chỉ khi vé `ISSUED`, chưa check-in, `eventStartTime − now ≥ 48 giờ`; giảm `sold`, tăng `available`; publish `ticket.cancelled`.
- Check-in/out ghi `ticket_check_logs`, publish event tương ứng; cùng một QR không được check-in đồng thời ở hai cổng.
- Chống bán vượt vé bằng UPDATE có điều kiện / khóa lạc quan (`version`).

---

## 4. order-service  (`order_service_db`)

**Trách nhiệm**: điều phối Khách hàng ↔ Ticket Service ↔ Payment Gateway; sở hữu Order, Payment, Refund.

**Bảng**
- `orders` (entity `PurchaseOrder`): id, customer_id (Soft FK), event_id (Soft FK), reservation_id (Soft FK→ticket), status (`PENDING_PAYMENT|PAID|FAILED|EXPIRED|REFUNDED`), subtotal, total_amount, currency (`VND`), expires_at, paid_at, created_at, updated_at — INDEX(status, expires_at)
- `order_items`: id, order_id (FK), ticket_type_id (Soft FK), ticket_type_name, quantity, unit_price, line_total, holder_name, holder_email, holder_phone (snapshot tên/giá lúc đặt)
- `payments`: id, order_id (FK), provider, provider_transaction_id (UNIQUE), amount, status (`CREATED|PENDING|SUCCEEDED|FAILED`), payment_url, idempotency_key (UNIQUE), created_at, updated_at
- `refund_requests`: id, order_id (FK), requested_by (Soft FK), amount, reason, status (`REQUESTED|APPROVED|REJECTED|PROCESSING|REFUNDED|FAILED`), reviewed_by (Soft FK), requested_at, reviewed_at, refunded_at

**API**
| Method | Endpoint | Tác nhân | Mô tả |
|---|---|---|---|
| POST | `/orders` | CUSTOMER | Tạo Order: lấy giá từ Ticket Service, reserve 3 phút, trả `PENDING_PAYMENT` + `expiresAt` |
| GET | `/orders/me` | CUSTOMER | Lịch sử đơn |
| GET | `/orders/{id}` | CUSTOMER (owner) | Chi tiết đơn |
| POST | `/orders/{id}/payment` | CUSTOMER | Khởi tạo thanh toán (chỉ khi còn `PENDING_PAYMENT` và chưa quá hạn) |
| POST | `/payments/webhook/{provider}` | Payment Gateway | Webhook, xác thực chữ ký, idempotent |
| POST | `/orders/{id}/refunds` | CUSTOMER | Yêu cầu hoàn tiền |
| GET | `/admin/refunds?status=REQUESTED` | ADMIN/ORGANIZER | Danh sách chờ duyệt |
| POST | `/admin/refunds/{id}/approve` | ADMIN/ORGANIZER | Duyệt + hoàn tiền |
| POST | `/admin/refunds/{id}/reject` | ADMIN/ORGANIZER | Từ chối |

**Máy trạng thái**: Order `PENDING_PAYMENT → PAID → REFUNDED`; `→ FAILED`; `→ EXPIRED`. Payment `CREATED → PENDING → SUCCEEDED`; `CREATED|PENDING → FAILED`. Refund `REQUESTED → APPROVED → PROCESSING → REFUNDED`; `REQUESTED → REJECTED`; `APPROVED|PROCESSING → FAILED`. **Không** cho `PAID → EXPIRED` hay `REFUNDED → PAID`.

**Luồng chính**: `Tạo Order → Reserve (3 phút) → Thanh toán → Webhook thành công → PAID → Confirm Reservation → phát hành vé`.

**Quy tắc**
- Tạo Order: client chỉ gửi loại vé + số lượng + người nhận; giá lấy từ Ticket Service. Nếu lưu Order lỗi sau khi reserve ⇒ gọi Release (bù trừ).
- Webhook: xác thực chữ ký, `provider_event_id` UNIQUE; `SUCCEEDED` + Order còn `PENDING_PAYMENT` ⇒ CAS sang `PAID` rồi Confirm. Confirm tạm thời lỗi ⇒ giữ `PAID`, retry; **chỉ publish `order.paid` sau khi vé phát hành thành công**. `FAILED` ⇒ Release + publish `order.failed`. Thanh toán thành công đến sau khi Order `EXPIRED` ⇒ không phát vé, đưa vào hoàn tiền/đối soát. Webhook lặp trả 2xx, không xử lý lại.
- Order Scheduler: tìm `PENDING_PAYMENT` có `expires_at < now`, CAS sang `EXPIRED`; thành công thì Release + publish `order.failed` (`reason=EXPIRED`); nếu đã `PAID` thì bỏ qua.
- Refund: chỉ khi Order `PAID`, vé chưa check-in, còn ≥ 48 giờ trước sự kiện, chưa có refund hợp lệ. Approve ⇒ `PROCESSING` + gọi Gateway (idempotency key riêng); chỉ khi Gateway xác nhận ⇒ Order/Refund `REFUNDED`, gọi Ticket Service hủy vé, publish `order.refunded`. Gateway lỗi ⇒ Refund `FAILED`, không hủy vé.

---

## 5. notification-service  (`notification_service_db`)

**Trách nhiệm**: Kafka Consumer gửi email/thông báo; quản lý thông báo in-app.

**Bảng**
- `notifications`: id (UUID CHAR(36)), recipient_user_id (nullable, Soft FK), recipient_email, channel (`EMAIL|IN_APP|PUSH`), template_code (FK), payload (JSON), status (`PENDING|SENT|FAILED`), sent_at, read_at, created_at
- `processed_events`: event_id (PK), processed_at
- `notification_templates`: code (PK: `TICKET_ISSUED|ORDER_FAILED|ORDER_REFUNDED|…`), channel, subject, body_template

**API**
| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/notifications/me?unreadOnly=` | CUSTOMER | Thông báo in-app |
| PUT | `/notifications/{id}/read` | CUSTOMER | Đánh dấu đã đọc (`read_at`) |
| POST | `/admin/notification-templates` | ADMIN | Tạo/sửa mẫu |

**Kafka consume**
- `ticket.issued` → render `TICKET_ISSUED`, sinh QR bằng ZXing từ `qr_code_value`, gửi email (SMTP/SES), ghi `notifications`; nếu có chủ vé thì thêm bản ghi `IN_APP`.
- `order.failed`, `order.refunded` → render `ORDER_FAILED`/`ORDER_REFUNDED`, gửi email, ghi log.

**Quy tắc**: kiểm tra `processed_events` trước khi xử lý; gửi lỗi thì Spring Retry (backoff), quá số lần ⇒ đẩy `notification.dlq`; không để một message lỗi chặn cả consumer group.

---

## 6. analytics-service  (`analytics_service_db`)

**Trách nhiệm**: tổng hợp thống kê (CQRS đơn giản: write-model log + read-model snapshot) và đẩy dữ liệu realtime cho Organizer.

**Bảng**
- `sales_metrics`: id, event_id, ticket_type_id, date, revenue, tickets_sold — UNIQUE(event_id, ticket_type_id, date), cập nhật bằng UPSERT
- `dashboard_snapshot` (read-model): event_id (PK), total_revenue, total_tickets_sold, current_attendance, last_updated
- `attendance_logs` (write-model): id, ticket_id, event_id, gate_id, action (`CHECK_IN|CHECK_OUT`), occurred_at — INDEX(event_id, occurred_at)

**API**
| Giao thức | Endpoint | Mô tả |
|---|---|---|
| GET | `/analytics/events/{id}/sales?from=&to=` | Doanh số theo khoảng thời gian (từ `sales_metrics`) |
| GET | `/analytics/events/{id}/attendance` | Số người hiện có mặt (từ `dashboard_snapshot`) |
| WebSocket | `/ws/dashboard` | Subscribe `/topic/event/{id}/dashboard` nhận JSON realtime |

**Kafka consume**
- `order.paid` → UPSERT `sales_metrics` theo từng loại vé/ngày, cập nhật `dashboard_snapshot`, push WebSocket.
- `ticket.issued` → cộng số vé đã phát hành thực tế.
- `ticket.checked-in` / `ticket.checked-out` → ghi `attendance_logs`, `current_attendance ±1`, push WebSocket.
- `order.refunded`, `ticket.cancelled` → điều chỉnh báo cáo nếu cần.

**Quy tắc**: idempotent theo `eventId` (tránh cộng dồn hai lần); endpoint chỉ cho ORGANIZER của sự kiện đó.

---

## 7. Bảng sự kiện Kafka

| Topic | Producer | Consumer | Khi phát |
|---|---|---|---|
| `ticket.issued` | ticket | notification, analytics | Phát hành vé thành công |
| `ticket.checked-in` | ticket | analytics | Check-in thành công |
| `ticket.checked-out` | ticket | analytics | Check-out thành công |
| `ticket.cancelled` | ticket | notification, analytics | Vé bị hủy hợp lệ |
| `order.paid` | order | analytics | Order `PAID` **và** vé đã phát hành |
| `order.failed` | order | notification | Thanh toán thất bại hoặc Order hết hạn (`reason`: `PAYMENT_FAILED`/`EXPIRED`) |
| `order.refunded` | order | notification, analytics | Hoàn tiền thành công |
| `notification.dlq` | notification | vận hành | Gửi thất bại quá số lần retry |

Event Service có bảng `outbox_events`; tên topic của event sự kiện (nếu phát) team sẽ bổ sung theo cùng quy ước `domain.action`.

## 8. Luồng mua vé end-to-end

```
Customer → POST /orders
  Order → Ticket: POST /internal/reservations        (giữ vé 3 phút, trả giá)
  Order lưu PENDING_PAYMENT
Customer → POST /orders/{id}/payment → Payment Gateway
Payment Gateway → POST /payments/webhook/{provider}
  SUCCEEDED: Order PAID → Ticket: POST /internal/reservations/{id}/confirm → phát vé + QR
             publish ticket.issued  → Notification gửi email QR, Analytics cập nhật
             publish order.paid     → Analytics cộng doanh số
  FAILED / hết 3 phút: Order FAILED|EXPIRED → Ticket: release → publish order.failed → Notification
Tại cổng: máy quét → POST /gate/check-in → publish ticket.checked-in → Analytics
Hoàn tiền: Customer yêu cầu → Admin duyệt → Gateway hoàn tiền → Ticket hủy vé → publish order.refunded
```
