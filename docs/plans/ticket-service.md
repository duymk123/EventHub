# Plan code ticket-service

Ngày 2026-10-28. Nguồn: [quy ước chung](00-cross-service-contracts.md), [OpenAPI Ticket](../api/ticket-service-openapi.yaml), [DDL](../database/ticket-service/init.sql), [ERD UUID](../database/ticket-service/erd.md), [Kafka](../kafka/events.md). Đây là owner vé, reservation và tồn kho; trạng thái code ở [mục 7](#7-trạng-thái-triển-khai-2026-11-23).

Plan viết lại ngày 2026-11-23 theo code hiện tại: bán vé theo **ghế cụ thể** ([C12](../project-clarifications.md#c12--đặt-vé-theo-ghế-cụ-thể)), giữ ghế 10 phút (C16), ngừng bán khi sự kiện bắt đầu (C17), Flyway (C15). Thiết kế bán theo số lượng với bộ đếm `available/reserved/sold` đã bỏ; chi tiết lần chuyển sang ghế ở §8, các việc đã chốt còn chưa code ở §9.

## 1. Entity và invariant

`TicketType` (zone = hạng VIP/PREMIUM/STANDARD, giá, `rowCount`, `seatsPerRow`, `totalQuota = rowCount × seatsPerRow`), `Seat`, `Reservation`, `ReservationItem`, `ReservationSeat`, `Ticket`, `TicketScan`, `OutboxEvent`. Mọi PK/FK/tham chiếu mềm là UUID CHAR(36); `event_id` là tham chiếu mềm sang Event, không có entity Event local.

Invariant: mỗi ghế đúng một trạng thái AVAILABLE/HELD/SOLD; HELD/SOLD luôn có `reservation_id`, AVAILABLE thì không. Reservation UNIQUE(order_id); `reservation_items` unique (reservation, type) với giá snapshot; `reservation_seats` giữ lịch sử ghế kể cả khi ghế đã được trả. Ticket có reservation, type, ghế, `owner_user_id` (người mua, C02), `qr_code_value` opaque UNIQUE. C08 cho phép zone 0 ghế.

Trạng thái: Reservation RESERVED → CONFIRMED/RELEASED/EXPIRED; Ticket ISSUED → CHECKED_IN → CHECKED_OUT hoặc ISSUED → CANCELLED. Không tái nhập. Event CLOSED hoặc đã tới `startTime` chỉ ngừng bán (C17), vé đã phát vẫn dùng được.

## 2. Service

| Interface / method | Logic / transaction |
|---|---|
| `TicketTypeService.createTypes(Actor, CreateTicketTypesRequest)` | Xác minh owner qua `EventClient`; tên hợp lệ, không trùng; sinh `rowCount × seatsPerRow` ghế trong cùng transaction. |
| `updateType(Actor, UUID, UpdateTicketTypeRequest)` | Khóa type. Thêm hàng tự do; bớt hàng hoặc đổi `seatsPerRow` chỉ khi các ghế bị ảnh hưởng chưa từng thuộc reservation nào (`reservation_seats` giữ lịch sử nên không xóa được). Vi phạm → 409 `SEAT_LAYOUT_LOCKED`. Không đổi giá đã snapshot. |
| `listTypes(UUID eventId, Actor viewer)` | Event công khai (PUBLISHED/CLOSED), hoặc ORGANIZER chủ sự kiện / ADMIN khi còn nháp; số ghế theo trạng thái bằng một query GROUP BY. Chỉ để tham khảo, giữ ghế mới là bước quyết định. |
| `getSeatMap(UUID typeId)` | Public, read-only, ghế sắp theo hàng/số ghế. |
| `ReservationService.reserve(ReserveRequest)` | 1–10 ghế không trùng, đúng hạng/sự kiện (404 `SEAT_NOT_FOUND`), hạng ACTIVE, Event PUBLISHED và chưa tới `startTime` (C17), nếu không → 409 `TICKET_TYPE_NOT_ON_SALE`. Hạn = min(đề xuất của Order, now + 10 phút) (C16). Một transaction: insert reservation → `holdSeats` (UPDATE có điều kiện `status = 'AVAILABLE'`) → thiếu dòng thì rollback, trả 409 `SEAT_TAKEN` kèm `takenSeatIds`; đủ thì insert items và `reservation_seats`. Idempotent theo orderId (cùng sự kiện + cùng bộ ghế trả reservation cũ, khác → 409 `RESERVATION_PAYLOAD_MISMATCH`). |
| `confirm(UUID, ConfirmRequest)` | Khóa reservation. CONFIRMED trả bộ vé cũ; RELEASED/EXPIRED hoặc đã quá `expiresAt` → 409 `RESERVATION_RELEASED_OR_EXPIRED`. Hợp lệ: HELD → SOLD, mỗi ghế một vé/QR (holder ghép theo `seatId`), outbox `ticket.issued` cùng transaction. |
| `release(UUID)` / `expire(UUID, Instant)` | Khóa reservation; chỉ RESERVED mới trả ghế (HELD → AVAILABLE) đúng một lần. CONFIRMED khi release → 409. Worker hết hạn xử lý từng reservation trong transaction riêng. |
| `TicketQueryService.getMine(Actor)` / `getQr(Actor, UUID)` | Vé theo `owner_user_id`; QR chỉ cho chủ vé, không sinh lại mỗi lần GET. |
| `GateService.checkIn/checkOut` | ORGANIZER chủ sự kiện hoặc ADMIN; CAS trạng thái vé + `ticket_scans` + outbox cùng transaction; hai cổng quét cùng QR chỉ một bên thắng. Khung giờ quét C07 ở §9. |
| `TicketCancellationService.cancelByOrder(UUID)` | Khóa vé của đơn theo thứ tự id; ISSUED → CANCELLED, ghế SOLD → AVAILABLE, outbox `ticket.cancelled`. Bản rút gọn C05 ở §9. |

## 3. Repository chính

| Method | Cách dùng |
|---|---|
| `SeatRepository.holdSeats / sellByReservation / releaseByReservation / freeSeats` | UPDATE có điều kiện theo trạng thái, trả số dòng đổi để phát hiện tranh chấp (§4). |
| `SeatRepository.countByTypeAndStatus`, `findByTicketTypeIdOrderByRowIndexAscSeatNumberAsc` | Danh sách zone và sơ đồ ghế. |
| `ReservationRepository.findByOrderId`, `findByIdForUpdate`, `findDueIds` | Idempotency (UNIQUE là chốt cuối), tuần tự confirm/release/expire, lô reservation quá hạn. |
| `ReservationSeatRepository.findByReservationIdOrderBySeatLabelAsc` | Confirm và trả bộ vé cũ khi confirm lặp. |
| `TicketRepository.findByOwnerUserIdOrderByIssuedAtDescIdAsc`, `findByQrCodeValue`, `findByOrderIdForUpdate`, `compareAndSetStatus` | Ví vé, quét QR, hủy theo đơn, CAS check-in/out. |
| `OutboxEventRepository` | Theo [plan chung](00-cross-service-contracts.md); id message UUID ổn định. |

## 4. Đồng thời và idempotency

- **Giữ ghế khi nhiều người mua cùng lúc:** không khóa `ticket_types`, không đọc rồi ghi. Mỗi lần giữ là một câu `UPDATE seats … WHERE id IN (:ids) AND status = 'AVAILABLE'`; InnoDB khóa từng dòng ghế nên hai đơn chỉ chặn nhau khi chọn trùng ghế. Số dòng đổi < số ghế → rollback cả reservation, trả `SEAT_TAKEN`. Đây là kiểm soát đồng thời kiểu lạc quan theo trạng thái (compare-and-set), không cần cột `version` và không cần retry: ghế đã bị lấy thì thử lại cũng không được.
- **Reserve lặp cùng orderId:** trả reservation cũ, không gia hạn. Thua UNIQUE(order_id) thì đọc bản thắng ở transaction mới, không tiếp tục trong transaction đã rollback-only.
- **Confirm/release/expire:** khóa dòng reservation (`SELECT … FOR UPDATE`) rồi mới đổi ghế, nên ghế không bị trả hai lần hay vừa bán vừa trả.
- **Quét vé:** CAS trên trạng thái vé.

## 5. API

| Operation | Method |
|---|---|
| POST `/api/v1/ticket-types` | createTypes |
| PUT `/api/v1/ticket-types/{id}` | updateType |
| GET `/api/v1/events/{eventId}/ticket-types` | listTypes |
| GET `/api/v1/ticket-types/{id}/seats` | getSeatMap |
| POST `/internal/reservations` | reserve |
| POST `/internal/reservations/{id}/confirm` | confirm |
| POST `/internal/reservations/{id}/release` | release |
| GET `/api/v1/tickets/me` | getMine |
| GET `/api/v1/tickets/{id}/qr` | getQr |
| POST `/api/v1/gate/check-in` | checkIn |
| POST `/api/v1/gate/check-out` | checkOut |
| POST `/internal/tickets/cancel-by-order/{orderId}` | cancelByOrder |
| GET `/internal/ticket-types/capacity?eventIds=` | Tổng số ghế theo sự kiện cho Analytics (C10). |

`EventClient.getInternal` (INT-01 của event-service, token `client_credentials`) lấy owner, trạng thái, `startTime`/`endTime`; timeout/5xx → 503 `EVENT_SERVICE_UNAVAILABLE`, 404 → `EVENT_NOT_FOUND`. `QrTokenGenerator`: 32 byte `SecureRandom`, base64url. `OutboxPublisher` gửi Kafka sau commit, không có consumer `order.paid` để phát vé.

## 6. Mã lỗi

`VALIDATION_ERROR` 400; `FORBIDDEN`/`FORBIDDEN_NOT_OWNER` 403; `EVENT_NOT_FOUND`, `TICKET_TYPE_NOT_FOUND`, `SEAT_NOT_FOUND`, `RESERVATION_NOT_FOUND`, `TICKET_NOT_FOUND` 404; `SEAT_TAKEN` (kèm `takenSeatIds`), `SEAT_LAYOUT_LOCKED`, `TICKET_TYPE_NOT_ON_SALE`, `RESERVATION_PAYLOAD_MISMATCH`, `RESERVATION_RELEASED_OR_EXPIRED`, `RESERVATION_ALREADY_CONFIRMED`, `INVALID_TICKET_STATE`, `TICKET_ALREADY_CHECKED_IN`, `CONCURRENT_UPDATE` 409; `EVENT_SERVICE_UNAVAILABLE` 503. Lỗi mạng không bao giờ đổi thành lỗi nghiệp vụ vĩnh viễn.

## 7. Trạng thái triển khai (2026-11-23)

| Mục | Trạng thái |
|---|---|
| Actor/ServiceActor | JWT Keycloak (issuer + JWK; audience chỉ kiểm khi cấu hình). Token người dùng → `Actor` một business role; token `client_credentials` có `azp` trong `eventhub.security.internal-clients` chỉ vào được `/internal/**`. |
| Bán theo ghế (C12) | Đã làm (§8). |
| Flyway (C15) | Đã làm: `V1__init.sql`, `ddl-auto: validate`. |
| Giữ ghế 10 phút (C16), ngừng bán khi bắt đầu (C17) | Đã làm 2026-11-23 (§10). |
| Khung giờ quét (C07) | Đã làm 2026-11-23: `GateServiceImpl` lấy `startTime`/`endTime` qua `EventClient` cho cả ORGANIZER và ADMIN; ngoài `[startTime − open-before, endTime]` (mặc định `PT1H`) trả 409 `SCAN_OUTSIDE_EVENT_TIME`, áp cho cả check-in và check-out. |
| Hủy vé theo đơn (C05 rút gọn) | Đã có: khóa vé theo id, có vé đã quét thì 409 `TICKET_ALREADY_CHECKED_IN` cho cả đơn, còn lại ISSUED → CANCELLED, ghế SOLD → AVAILABLE, outbox `ticket.cancelled`; gọi lại trả 0 vé. Không kiểm 48 giờ ở Ticket (Order kiểm). |
| `eventStartTime` khi giữ ghế | Đã làm 2026-11-23: `reservations.event_start_time` lưu giờ bắt đầu lúc reserve, trả trong `ReservationResponse` cho mốc 48 giờ của Order. |
| Kiểm tra | Không thêm file test theo yêu cầu chủ dự án; kiểm bằng script HTTP trên MySQL thật (phân quyền, idempotency, nhiều đơn tranh một ghế chỉ một thắng, nhiều cổng quét cùng QR chỉ một thắng, hết hạn trả ghế đúng một lần, hủy lặp). |

## 8. Bán theo ghế (C12, đã làm 2026-11-23)

Đã thay việc bán theo số lượng (2026-11-23); làm cùng [plan Order §9](order-service.md#9-bán-theo-ghế-c12-đã-làm-2026-11-23).

### 8.1 Entity và schema

| Thành phần | Thay đổi |
|---|---|
| `TicketType` | Thêm `rowCount`, `seatsPerRow`; `totalQuota = rowCount × seatsPerRow`. Bỏ `availableQuantity`, `reservedQuantity`, `soldQuantity`. |
| `Seat` (mới) | `ticketType`, `rowIndex`, `rowLabel`, `seatNumber`, `status` (enum `SeatStatus` AVAILABLE/HELD/SOLD ở `common`), `reservation` (nullable), `updatedAt`. UNIQUE (ticket_type_id, row_index, seat_number). |
| `ReservationSeat` (mới) | `reservation`, `seat`, `seatLabel` snapshot. Giữ lịch sử ghế của reservation sau khi ghế được trả. |
| `Ticket` | Thêm `seat`, `seatLabel`. |
| Migration | DB đang `ddl-auto: update` không tự xóa cột bộ đếm hay thêm CHECK: viết script ALTER/Flyway; sinh ghế cho các hạng vé đã có từ quota cũ (ví dụ 1 hàng × quota) hoặc yêu cầu organizer nhập lại trước khi mở bán. |

Nhãn hàng: 1 → A … 26 → Z, 27 → AA. Nhãn ghế `B-07` (số 2 chữ số). Giới hạn 30 hàng × 60 ghế mỗi zone, tối đa 10 ghế mỗi đơn.

### 8.2 Service

| Method | Logic |
|---|---|
| `TicketTypeService.createTypes` | Như cũ, thêm sinh `rowCount × seatsPerRow` dòng `seats` trong cùng transaction (batch insert). |
| `updateType` | Khóa type. Tăng rowCount: thêm hàng mới. Giảm: chỉ khi mọi ghế ở các hàng bị bỏ còn AVAILABLE rồi xóa chúng. Đổi seatsPerRow: chỉ khi zone chưa có ghế HELD/SOLD (xóa và sinh lại). Vi phạm → 409 `SEAT_LAYOUT_LOCKED`. |
| `listTypes` | Thêm `availableSeats/heldSeats/soldSeats` bằng một query `GROUP BY ticket_type_id, status` cho cả sự kiện. |
| `SeatQueryService.getSeatMap(UUID typeId)` (mới) | Public, read-only: mọi ghế của zone sắp theo row_index, seat_number, nhóm theo hàng. |
| `ReservationService.reserve` | Validate: tổng ghế 1–10, không trùng, mọi ghế thuộc đúng hạng/sự kiện (404 `SEAT_NOT_FOUND`), hạng ACTIVE, Event PUBLISHED. Một transaction: insert reservation; `holdSeats(seatIds, reservationId)`; số dòng < số ghế → rollback, đọc lại ghế không AVAILABLE và trả 409 `SEAT_TAKEN` kèm `takenSeatIds`. Đủ → insert reservation_items (giá snapshot) và reservation_seats. Idempotent theo orderId như cũ, so danh sách ghế thay vì số lượng. |
| `confirm` | Như cũ nhưng: `sellByReservation` (HELD → SOLD), mỗi `reservation_seat` một vé có `seat`/`seatLabel`, holder ghép theo `seatId`. Outbox `ticket.issued` thêm `seatId`, `seatLabel`. |
| `release` / `expire` | `releaseByReservation` (HELD → AVAILABLE, reservation_id NULL) cùng transaction đổi trạng thái reservation, đúng một lần. |
| `TicketCancellationService.cancelByOrder` | Vé → CANCELLED và ghế của các vé đó SOLD → AVAILABLE trong cùng transaction. |

Không khóa `ticket_types` trong luồng giữ ghế: hai đơn chỉ chặn nhau khi chọn trùng ghế. Câu UPDATE dùng `WHERE id IN (...)` với danh sách đã sắp xếp để thứ tự khóa ổn định, tránh deadlock giữa hai đơn có ghế giao nhau.

### 8.3 Repository

| Method | Mục đích |
|---|---|
| `SeatRepository.holdSeats(Collection<UUID> ids, UUID reservationId, Instant now): int` | `UPDATE seats SET status='HELD', reservation_id=:r, updated_at=:now WHERE id IN (:ids) AND status='AVAILABLE'`; trả số dòng. |
| `findByIdInAndStatusNot(Collection<UUID>, SeatStatus.AVAILABLE)` | Lấy ghế đã bị lấy để trả `takenSeatIds`. |
| `sellByReservation(UUID reservationId, Instant now): int` | HELD → SOLD cho confirm. |
| `releaseByReservation(UUID reservationId, Instant now): int` | HELD → AVAILABLE cho release/expire. |
| `freeSeats(Collection<UUID> seatIds, Instant now): int` | SOLD → AVAILABLE khi hủy vé. |
| `countByEventGroupByTypeAndStatus(UUID eventId)` | Số ghế theo zone/trạng thái cho danh sách zone. |
| `findByTicketTypeIdOrderByRowIndexAscSeatNumberAsc(UUID)` | Vẽ sơ đồ ghế. |
| `ReservationSeatRepository.findByReservationIdOrderBySeatLabelAsc(UUID)` | Confirm và trả bộ vé cũ khi confirm lặp. |

### 8.4 API, lỗi, Kafka

- Mới: `GET /api/v1/ticket-types/{id}/seats` (public, cần route ở Gateway). Đổi: request tạo/sửa hạng vé nhận `rowCount`, `seatsPerRow`; `ReserveItem` nhận `seatIds`; `Holder` của confirm có `seatId`; `TicketResponse` có `seatId`, `seatLabel`. Xem [OpenAPI Ticket](../api/ticket-service-openapi.yaml).
- `ErrorCode` thêm `SEAT_TAKEN` (409), `SEAT_NOT_FOUND` (404), `SEAT_LAYOUT_LOCKED` (409); bỏ `INSUFFICIENT_TICKETS`. `ErrorResponse` có thêm `takenSeatIds` khi SEAT_TAKEN.
- Kafka không thêm topic; `ticket.issued` thêm `seatId`, `seatLabel` cho từng vé ([hợp đồng](../kafka/events.md)).

### 8.5 Kiểm tra

MySQL thật: 50 đơn cùng giữ một ghế chỉ 1 thành công, 49 nhận SEAT_TAKEN đúng ghế; hai đơn giữ hai bộ ghế giao nhau không deadlock; đơn 3 ghế trong đó 1 ghế đã bị lấy thì không giữ ghế nào; hết hạn trả ghế đúng một lần; confirm lặp không tạo vé mới; hủy vé trả ghế và bán lại được; giảm hàng có ghế đã bán bị chặn.

## 9. Chốt 2026-11-22: Flyway, khung giờ quét, hủy vé rút gọn (Flyway đã làm 2026-11-23)

**Flyway (C15).** Xóa database `eventhub_ticket` (chỉ có dữ liệu thử), thêm `flyway-core` + `flyway-mysql` (Spring Boot 3.5), `db/migration/V1__init.sql` từ [DDL tham chiếu](../database/ticket-service/init.sql) (đã có bảng ghế C12), `spring.flyway.enabled: true`, `ddl-auto: validate`. Làm cùng Order ([plan Order §10.1](order-service.md#101-flyway-c15)). Đã làm 2026-11-23; khung giờ quét và hủy vé theo đơn bên dưới vẫn chưa làm. Seed dữ liệu dev (nếu cần) để ở file `V2__dev_seed.sql` chỉ chạy với profile dev, hoặc tạo qua API.

**Khung giờ quét (C07).** `GateService.checkIn/checkOut`: lấy `startTime`/`endTime` qua `EventClient.getInternal` (đã gọi để kiểm owner). Cho quét khi `startTime − open-before ≤ now ≤ endTime`, `eventhub.ticket.gate.open-before` mặc định `PT1H`; ngoài khung 409 `SCAN_OUTSIDE_EVENT_TIME`. Quyền: ORGANIZER chủ sự kiện hoặc ADMIN bằng Bearer JWT (như code hiện tại). Không làm bảng thiết bị; `gateId` là UUID chọn trên trang quét.

**Hủy vé theo đơn (C05 rút gọn).** `cancelByOrder` giữ một transaction: khóa vé của đơn theo thứ tự id; có vé không ISSUED → 409 `TICKET_ALREADY_CHECKED_IN`, không hủy vé nào; còn lại ISSUED → CANCELLED, ghế SOLD → AVAILABLE (`SeatRepository.freeSeats`), outbox `ticket.cancelled`. Gọi lại khi vé đã CANCELLED trả kết quả cũ. Không kiểm 48 giờ ở Ticket (Order kiểm). `RefundHoldService` và endpoint hold ở §2 là hướng phát triển.

**Text địa điểm (C03).** `ticket.issued.venue` để null; Notification điền `141 Đường Chiến Thắng, Hà Nội` từ cấu hình.

**Mã lỗi mới.** `SCAN_OUTSIDE_EVENT_TIME` 409, cùng `SEAT_TAKEN`, `SEAT_NOT_FOUND`, `SEAT_LAYOUT_LOCKED` của C12.

## 10. Chốt 2026-11-23: giữ ghế 10 phút, giờ ngừng bán (đã làm 2026-11-23)

- **C16:** `eventhub.ticket.reservation-ttl: PT10M`; Ticket kẹp hạn Order đề xuất về tối đa 10 phút và trả hạn chính thức.
- **C17:** `reserve` từ chối 409 `TICKET_TYPE_NOT_ON_SALE` ("Event has already started") khi `now ≥ startTime` lấy từ `EventClient.getInternal`, kể cả khi organizer chưa chuyển CLOSED. Confirm cho đơn đã giữ ghế trước giờ bắt đầu vẫn được, vì khách thanh toán trong hạn giữ ghế.
