---
name: eventhub-architecture
description: Kiến trúc hệ thống và quy ước code của dự án EventHub (microservices Java Spring Boot - auth, event, ticket, order, notification, analytics). Dùng skill này mỗi khi viết, sửa, review hoặc sinh code cho bất kỳ service nào của EventHub, thêm API/entity/DTO/Kafka event, hoặc khi cần hiểu ranh giới giữa các service. Use whenever generating or reviewing code in the EventHub backend.
---

# EventHub – Kiến trúc & Quy ước code

Tài liệu này là **nguồn sự thật** cho AI agent và các thành viên khi code EventHub. Đọc hết mục 1–6 trước khi viết code. Chi tiết từng service nằm ở `references/services.md`; mẫu code đầy đủ nằm ở `references/code-templates.md`.

## 1. EventHub là gì

Hệ thống bán vé sự kiện theo kiến trúc **Microservices**. Khách xem sự kiện, đặt vé, thanh toán, nhận vé QR; Ban tổ chức tạo và quản lý sự kiện, xem thống kê; Admin duyệt sự kiện và quản trị người dùng.

Tác nhân: `CUSTOMER`, `ORGANIZER`, `ADMIN` (vai trò trong Auth Service), cùng các tác nhân hệ thống: API Gateway, Payment Gateway, máy quét QR tại cổng, các Scheduler.

## 2. Kiến trúc tổng quan

```
Web/Mobile Client
      │
 API Gateway  (điểm vào duy nhất, xác thực JWT, định tuyến /api/v1/**)
      │
 ┌────┬────┬────┬────┬────────────┬───────────┐
 Auth Event Ticket Order Notification Analytics     ← mỗi service 1 MySQL riêng
                │     │       ▲            ▲
                │     └───REST (đồng bộ)───┘ (Order → Ticket)
                └────── Kafka Event Bus ───────────────┘ (bất đồng bộ)
                          Order ──► Payment Gateway (ngoài)
```

| Service | Trách nhiệm | Database |
|---|---|---|
| auth-service | Tài khoản, đăng ký/đăng nhập, JWT + Refresh Token Rotation, vai trò, khóa/mở khóa | `auth_service_db` |
| event-service | Vòng đời sự kiện (DRAFT→…→CLOSED), danh mục, khu vực, tìm kiếm | `event_service_db` |
| ticket-service | Loại vé A–D, tồn kho, giữ chỗ 3 phút, phát hành vé + QR, check-in/out, hủy vé | `ticket_service_db` |
| order-service | Đơn hàng, thanh toán, webhook, hết hạn đơn, hoàn tiền | `order_service_db` |
| notification-service | Consumer Kafka → gửi email QR/thông báo, thông báo in-app | `notification_service_db` |
| analytics-service | Consumer Kafka → thống kê doanh số, điểm danh, dashboard realtime (WebSocket) | `analytics_service_db` |

Công nghệ: Java + Spring Boot 3 (Spring Security, Spring Data JPA, Spring Kafka), MySQL, Redis (cache / khóa có TTL), Apache Kafka, Docker, ReactJS (frontend). Xác thực bằng JWT do Auth Service cấp (Access Token 5 phút, Refresh Token 7 ngày).

### Nguyên tắc kiến trúc bất biến

1. **Database per Service.** Không service nào đọc/ghi DB của service khác, không JOIN xuyên service, không `@ManyToOne` sang entity của service khác.
2. **Tham chiếu chéo service = khóa ngoại mềm**: chỉ lưu `Long xxxId` (ví dụ `organizerId`, `eventId`, `orderId`), không FK vật lý. Tính toàn vẹn do tầng service đảm bảo.
3. **Giao tiếp đồng bộ = REST** (cần phản hồi ngay: Order → Ticket reserve/confirm/release/cancel). **Bất đồng bộ = Kafka** (phát vé, gửi mail, thống kê).
4. **Không dùng distributed transaction.** Dùng idempotency + bù trừ (compensation) để đạt eventual consistency.
5. **Mọi request từ client đi qua API Gateway.** Endpoint `/internal/**` chỉ cho service-to-service, Gateway không route ra ngoài.
6. **Không tin client**: giá, tổng tiền, kết quả thanh toán luôn lấy từ server/provider, không lấy từ request của client.

## 3. Luồng code bắt buộc trong mỗi service

```
HTTP request
   ▼
Controller          ← nhận Request DTO, validate, gọi Service, trả Response DTO
   ▼
Service (interface) ← hợp đồng nghiệp vụ
   ▼
ServiceImpl         ← TOÀN BỘ logic nghiệp vụ + @Transactional (implements Service)
   ▼
Repository          ← Spring Data JPA, chỉ truy cập dữ liệu
   ▼
Entity              ← ánh xạ bảng DB
```

DTO tách thành hai nhóm: `dto/request` (dữ liệu vào) và `dto/response` (dữ liệu ra). Entity **không bao giờ** đi ra khỏi tầng service.

### Quy tắc từng tầng

**Controller** (`controller/`)
- Chỉ: nhận request → `@Valid` → gọi **interface** Service → trả `ResponseEntity<ResponseDTO>`.
- Cấm: chứa logic nghiệp vụ, gọi Repository, nhận/trả Entity, bắt exception bằng try/catch (đã có `GlobalExceptionHandler`).
- Phân quyền bằng `@PreAuthorize("hasRole('ORGANIZER')")`; lấy `userId` từ JWT (`sub`), **không** nhận `userId` từ body/query.

**Service** (`service/`) – chỉ là **interface**, khai báo method nghiệp vụ. Tham số/kết quả là DTO hoặc kiểu cơ bản, không phải Entity.

**ServiceImpl** (`service/impl/`)
- Tên `XxxServiceImpl implements XxxService`, gắn `@Service`, `@RequiredArgsConstructor`.
- Nơi duy nhất chứa nghiệp vụ: kiểm tra quyền sở hữu, máy trạng thái, quy tắc giá/quota, gọi Repository, gọi client service khác, phát Kafka event.
- `@Transactional` đặt ở đây (method ghi), `@Transactional(readOnly = true)` cho method đọc.
- Ném `BusinessException(ErrorCode.X)` khi vi phạm nghiệp vụ.
- Gọi service khác **qua interface**, không `new` ServiceImpl.

**Repository** (`repository/`) – `interface XxxRepository extends JpaRepository<Xxx, Long>`. Chỉ query dữ liệu (derived query / `@Query`). Không chứa nghiệp vụ. Chuyển trạng thái có cạnh tranh dùng `@Modifying @Query("update … where id=:id and status=:from")` và kiểm tra số dòng cập nhật (compare-and-set).

**Entity** (`entity/`) – ánh xạ 1-1 với bảng trong `references/services.md`. Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. Không đặt logic nghiệp vụ nặng, không expose ra ngoài service.

**DTO** (`dto/request`, `dto/response`)
- Dùng Java `record`. Request có Jakarta Validation (`@NotBlank`, `@Size`, `@NotNull`, `@Positive`…). Response chỉ chứa field client cần, **không** chứa `passwordHash`, `qrCodeValue` nội bộ, `idempotencyKey`…
- Đặt tên: `CreateEventRequest`, `UpdateEventRequest`, `EventResponse`, `EventSummaryResponse`, `PageResponse<T>`.

**Mapper** (`mapper/`) – chuyển Entity ↔ DTO bằng MapStruct (`@Mapper(componentModel = "spring")`). Không map trong Controller.

## 4. Cấu trúc package mỗi service

Package gốc: `com.eventhub.<service>` (ví dụ `com.eventhub.ticket`).

```
com.eventhub.<service>
├── <Service>Application.java
├── controller/           XxxController          (+ InternalXxxController cho /internal/**)
├── service/              XxxService             (interface)
│   └── impl/             XxxServiceImpl
├── repository/           XxxRepository
├── entity/               Xxx                    (+ enums/ XxxStatus)
├── dto/
│   ├── request/          CreateXxxRequest ...
│   └── response/         XxxResponse ...
├── mapper/               XxxMapper
├── exception/            BusinessException, ErrorCode, GlobalExceptionHandler
├── client/               XxxClient (+ impl)     gọi REST service khác
│   └── dto/              DTO riêng của client call
├── messaging/
│   ├── event/            XxxEvent (record payload Kafka)
│   ├── producer/         XxxEventPublisher
│   └── consumer/         XxxEventConsumer
├── scheduler/            XxxScheduler           (job định kỳ)
├── config/               SecurityConfig, KafkaConfig, ...
└── util/
```

Chỉ tạo package khi service thực sự cần (ví dụ auth-service không có `messaging`, notification-service không có `scheduler`).

Migration DB: Flyway tại `src/main/resources/db/migration/V<n>__<mô_tả>.sql`; `spring.jpa.hibernate.ddl-auto=validate`. Không dùng `update/create` ở môi trường chung.

## 5. Quy ước chung

**API**
- Endpoint công khai: `/api/v1/<resource>` (danh từ số nhiều, kebab-case). Endpoint nội bộ: `/internal/<resource>`. Admin: `/api/v1/admin/...`. Gateway chỉ route `/api/v1/**`.
- Động từ HTTP đúng nghĩa; tạo mới trả `201`, xóa/cập nhật không trả body trả `204`.
- Thao tác chuyển trạng thái dùng sub-resource: `POST /events/{id}/submit`, `POST /events/{id}/publish`.
- Lưu ý: spec Ticket/Order trong Notion ghi đường dẫn không có `/api/v1` (ví dụ `/orders`); khi code, thêm tiền tố `/api/v1` cho endpoint công khai.

**Lỗi** – mọi lỗi trả về cùng một định dạng, do `GlobalExceptionHandler` tạo:
```json
{ "code": "INSUFFICIENT_TICKETS", "message": "Không đủ vé", "timestamp": "2026-10-26T10:00:00Z", "path": "/api/v1/orders" }
```
`code` là hằng số `UPPER_SNAKE_CASE` trong enum `ErrorCode` (kèm HTTP status). Ví dụ đã dùng: `EMAIL_ALREADY_EXISTS`(409), `INVALID_CREDENTIALS`(401), `ACCOUNT_LOCKED`(403), `INVALID_REFRESH_TOKEN`(401), `REFRESH_TOKEN_EXPIRED`(401), `REFRESH_TOKEN_REUSED`(401), `INSUFFICIENT_TICKETS`(409).

**Dữ liệu**
- Khóa chính `Long` `IDENTITY` (ngoại lệ: `notifications.id` là UUID `CHAR(36)`; `processed_events.event_id`, `notification_templates.code` là String).
- Cột `snake_case`, field Java `camelCase`. Enum luôn `@Enumerated(EnumType.STRING)`.
- Tiền: `BigDecimal` / `DECIMAL(12,2)`, tiền tệ mặc định `VND`. Thời gian: `Instant` (UTC) / `TIMESTAMP`.
- Entity của bảng `orders` đặt tên class `PurchaseOrder` (tránh trùng từ khóa `ORDER` của JPQL).
- Không xóa cứng dữ liệu nghiệp vụ; dùng trạng thái (`CANCELLED`, `REVOKED`, `LOCKED`…).

**Bảo mật**
- Mật khẩu băm Bcrypt/Argon2, không log mật khẩu/token. JWT: `sub = userId`, claim `roles`.
- Refresh Token lưu **hash**, xoay vòng mỗi lần refresh, phát hiện dùng lại thì thu hồi cả `family_id`.
- QR là token ngẫu nhiên (opaque), luôn kiểm tra trạng thái vé ở server khi quét.
- `/internal/**` yêu cầu xác thực service-to-service; `/gate/**` chỉ cho thiết bị cổng hợp lệ; webhook thanh toán phải xác thực chữ ký provider.

**Kafka**
- Tên topic = tên sự kiện dạng `domain.action`: `order.paid`, `order.failed`, `order.refunded`, `ticket.issued`, `ticket.checked-in`, `ticket.checked-out`, `ticket.cancelled`; DLQ: `notification.dlq`.
- Mỗi message là `record` có `eventId` (UUID), `eventType`, `occurredAt` + dữ liệu nghiệp vụ. Key = id của aggregate.
- **Producer**: chỉ publish sau khi transaction DB commit (Outbox Pattern, hoặc `@TransactionalEventListener(AFTER_COMMIT)`). Event Service dùng bảng `outbox_events`.
- **Consumer**: luôn idempotent – kiểm tra `processed_events` theo `eventId` trước khi xử lý, ghi nhận trong cùng transaction. Lỗi tạm thời thì retry có backoff, quá số lần thì đẩy DLQ.

**Idempotency & đồng thời** (bắt buộc cho luồng tiền/vé)
- Reserve theo `orderId` (UNIQUE); Confirm/Release/Expire/Cancel gọi lặp không được phát vé/trả quota lần hai.
- Chuyển trạng thái bằng compare-and-set (`UPDATE … WHERE status = :from`), chỉ một thao tác thắng.
- Chống bán vượt vé: cập nhật tồn kho bằng UPDATE có điều kiện hoặc khóa lạc quan (`ticket_inventory.version`).
- Webhook lặp trả `2xx` nhưng không xử lý lần hai (`provider_event_id` UNIQUE).

**Scheduler** – job chạy định kỳ phải idempotent và dùng compare-and-set để không đua với luồng online (ví dụ hết hạn Reservation, hết hạn Order sau 3 phút).

**Test** – JUnit 5 + Mockito cho `ServiceImpl` (mock Repository/Client); `@WebMvcTest` cho Controller; Testcontainers (MySQL, Kafka) cho tích hợp. Mỗi use case có ít nhất test đường thành công và các nhánh lỗi chính.

## 6. Quy trình thêm một chức năng (làm theo đúng thứ tự)

1. Đọc use case/API/trạng thái của service trong `references/services.md`. Chức năng thuộc service nào thì chỉ code trong service đó.
2. **Migration + Entity** (+ enum nếu có trạng thái).
3. **Repository** (query cần thiết, CAS nếu có cạnh tranh).
4. **DTO**: `dto/request` rồi `dto/response`.
5. **Mapper**.
6. **Service interface** → **ServiceImpl** (nghiệp vụ, transaction, kiểm tra quyền/trạng thái, ném `BusinessException`).
7. **Controller** (phân quyền, validate, gọi interface).
8. Nếu cần gọi service khác: thêm `client/`; nếu cần phát/nhận sự kiện: thêm `messaging/`.
9. Thêm `ErrorCode` mới nếu có lỗi nghiệp vụ mới.
10. Viết test, chạy `build` và test trước khi báo hoàn thành.

## 7. Checklist trước khi hoàn thành code

- [ ] Controller không có logic nghiệp vụ, không gọi Repository, không trả Entity.
- [ ] Service là interface; logic nằm trong `XxxServiceImpl`; `@Transactional` đúng chỗ.
- [ ] DTO request/response tách riêng, request có validation, response không lộ dữ liệu nhạy cảm.
- [ ] Không có truy cập DB/Entity của service khác; tham chiếu chéo chỉ là `Long id`.
- [ ] Quyền được kiểm tra (role + sở hữu); `userId` lấy từ JWT.
- [ ] Chuyển trạng thái đúng máy trạng thái trong `references/services.md`.
- [ ] Luồng tiền/vé/Kafka idempotent; Kafka publish sau commit.
- [ ] Lỗi dùng `BusinessException` + `ErrorCode`, không `catch` rồi nuốt.
- [ ] Có test và build qua.

## 8. Điểm cần team chốt (tài liệu gốc chưa thống nhất)

Khi gặp các điểm dưới đây, **làm theo quy ước đã ghi ở đây** và hỏi lại người phụ trách nếu cần đổi:

- **Auth**: báo cáo chương 1 nhắc Keycloak, nhưng spec và DB (`users`, `refresh_tokens`) là Auth Service tự cấp JWT → code theo Auth Service tự cấp.
- **Ticket Type & Zone**: spec Event cũ nhắc `VIP/PREMIUM/STANDARD` và TicketType nằm trong Event Service; spec Ticket mới chốt 4 loại **Khán đài A, B, C, D** do **Ticket Service sở hữu**. Event Service chỉ giữ `event_zones` (khu vực/sức chứa). Code theo Ticket Service.
- **Giữ vé**: nguồn sự thật của Reservation là bảng `reservations` trong DB kèm Scheduler hết hạn; Redis chỉ dùng cache/khóa TTL hỗ trợ, không thay thế DB.
- **Cổng service-to-service & JWT**: Gateway xác thực JWT; mỗi service vẫn kiểm tra lại JWT (resource server) để tự bảo vệ endpoint.
