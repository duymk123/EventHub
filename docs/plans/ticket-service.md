# Plan code ticket-service

Ngày 2026-10-28. Nguồn: [quy ước chung](00-cross-service-contracts.md), [OpenAPI Ticket](../api/ticket-service-openapi.yaml), [DDL](../database/ticket-service/init.sql), [ERD UUID](../database/ticket-service/erd.md), [Kafka](../kafka/events.md). Đây là owner vé, reservation và tồn kho; hiện chưa có code nghiệp vụ.

## 1. Entity và invariant

`TicketType` gộp price/quota/counters; enum name VIP/PREMIUM/STANDARD. `Reservation`, `ReservationItem`, `Ticket`, `TicketScan`, `OutboxEvent`. Mọi PK/FK/soft reference UUID CHAR(36). `event_id` mềm sang Event, không tạo entity Event local.

Invariant: available + reserved + sold = totalQuota, không âm; C08 cho quota 0, không buộc ba hạng dương. Reservation UNIQUE(order_id); reservation_items unique reservation/type, giá snapshot. Ticket có reservation_id, ticket_type_id, owner_user_id bắt buộc, qr_code_value opaque UNIQUE. C02 chốt owner là buyer; holderEmail có thì nhận vé, thiếu fallback buyer snapshot. Gate ownership C07 để sau.

Trạng thái: RESERVED → CONFIRMED/RELEASED/EXPIRED; ISSUED → CHECKED_IN → CHECKED_OUT hoặc ISSUED → CANCELLED. Không tái nhập. CLOSED của Event chỉ ngừng bán, không tự làm vé đã phát hành mất hiệu lực.

## 2. Service methods

| Interface / signature | Logic / transaction |
|---|---|
| `TicketTypeService.createTypes(Actor organizer, CreateTicketTypesRequest request): List<TicketTypeResponse>` | EventClient xác minh owner trước; tên cho phép, không trùng, giá/quota không âm; insert cùng transaction. C08 cho quota 0 và không buộc đủ ba hạng; publish cần ít nhất một ACTIVE có total_quota > 0. |
| `updateType(Actor organizer, UUID typeId, UpdateTicketTypeRequest request): TicketTypeResponse` | Đọc eventId, xác minh owner, lock lại type và kiểm tra quota mới ≥ reserved+sold; available = quota mới - reserved - sold. Không sửa giá snapshot của reservation/order cũ. |
| `listPublicTypes(UUID eventId): List<TicketTypeResponse>` | Xác minh trạng thái công khai của event phù hợp API, trả tồn kho hiện tại; số lượng chỉ tham khảo, reserve mới là bước quyết định. |
| `ReservationService.reserve(ServiceActor orderCaller, ReserveRequest request): ReservationResponse` | Event hợp lệ/PUBLISHED và các type cùng event, normalize giỏ; lock type theo thứ tự UUID; giảm available/tăng reserved, tạo reservation/items trong một transaction. Deadline Ticket cấp tối đa 3 phút, trả giá/hạn chính thức. |
| `confirm(ServiceActor orderCaller, UUID reservationId, ConfirmRequest request): ConfirmResponse` | Lock reservation, CONFIRMED trả vé cũ; RELEASED/EXPIRED trả 409. RESERVED hết hạn: hoàn quota/đổi EXPIRED rồi trả lỗi theo ranh giới commit tách rõ, không throw làm rollback hoàn quota. Hợp lệ: CAS confirm, reserved→sold, tạo vé/QR và outbox trong cùng transaction. |
| `release(ServiceActor orderCaller, UUID reservationId): ReservationStatusResponse` | Lock; RESERVED → RELEASED + hoàn available một lần; RELEASED/EXPIRED trả trạng thái cũ; CONFIRMED 409. Cùng transaction counters/status. |
| `expire(UUID reservationId, Instant now): boolean` | Internal worker một reservation/transaction; chỉ RESERVED có expiresAt ≤ now; trả quota đúng một lần, thua CAS bỏ qua. Không gọi Order khi giữ lock DB. |
| `TicketQueryService.getMine(Actor customer): List<TicketResponse>` | Query owner_user_id=Principal, read-only, array hiện tại. C02 chốt owner là người mua; holder email không cấp quyền xem vé. |
| `getQr(Actor customer, UUID ticketId): QrResponse` | Ticket thuộc owner, trạng thái được phép hiển thị theo API; không lộ QR qua API danh sách hoặc nội bộ refund. Không sinh QR mới mỗi GET. |
| `GateService.checkIn(GateActor gate, GateScanRequest request): GateScanResponse` | Xác minh thiết bị được quét event/gate, QR/event khớp, CAS ISSUED→CHECKED_IN; log và outbox cùng transaction. Retry đã CHECKED_IN trả lỗi trạng thái theo API, không thêm lượt. |
| `checkOut(GateActor gate, GateScanRequest request): GateScanResponse` | CAS CHECKED_IN→CHECKED_OUT, log/outbox; không có check-in lại từ CHECKED_OUT. |
| `TicketCancellationService.cancelByOrder(ServiceActor caller, UUID orderId): CancelResponse` | C05 cần hold PREPARED trước provider; sau success commit hủy cả đơn theo token, sold→available một lần và outbox ticket.cancelled. Không recheck mốc 48h theo giờ mới. Chữ ký/endpoint cũ cần bổ sung token trước dùng. |
| `RefundEligibilityService.getByOrder(ServiceActor caller, UUID orderId, Instant at): RefundEligibilityResponse` | API read nội bộ đề xuất, không QR. Không thay prepare hold: C05 đã chọn giữ quyền hủy có khóa trước khi gọi provider. |
| `RefundHoldService.prepare/commit/release/query(...)` | Thiết kế mới theo C05: prepare toàn bộ ISSUED/>=48h, chặn scan/cancel cạnh tranh; commit theo token hoặc release khi chắc chắn không hoàn. Không tự mở hold vì TTL khi tiền UNKNOWN. |

Với confirm hết hạn, có thể gọi `expireInTransaction` commit rồi outer layer ném RESERVATION_EXPIRED, hoặc trả outcome DTO để controller map 409 sau commit. Không dùng catch/throw khiến trạng thái EXPIRED và trả quota bị rollback vô tình.

## 3. Repository methods

| Repository / signature | Cách dùng |
|---|---|
| `TicketTypeRepository.findByEventIdOrderByNameAsc(UUID): List<TicketType>` | Type list; UNIQUE(event_id,name) chống trùng. |
| `findAllByIdsForUpdate(List<UUID> ids): List<TicketType>` | JPQL ORDER BY id + PESSIMISTIC_WRITE; kiểm tra đủ ID và cùng event, không khóa theo thứ tự client. |
| `findByIdForUpdate(UUID): Optional<TicketType>` | Update quota. |
| `reserveQuantity(UUID typeId, int qty): int` | Native UPDATE available-=qty/reserved+=qty WHERE available>=qty AND status=ACTIVE; phương án thay mutation dưới lock, check affected rows. |
| `confirmQuantity(UUID typeId, int qty): int` | reserved-=qty/sold+=qty với điều kiện reserved>=qty; cùng transaction confirm. |
| `releaseQuantity(UUID typeId, int qty): int` | reserved-=qty/available+=qty; chỉ sau thắng transition reservation. |
| `returnSoldQuantity(UUID typeId, int qty): int` | sold-=qty/available+=qty; chỉ cho số vé vừa cancel lần đầu, không tổng cả vé đã cancel trước. |
| `ReservationRepository.findByOrderId(UUID): Optional<Reservation>` | Idempotency reserve, UNIQUE là chốt cạnh tranh. |
| `findByIdForUpdate(UUID): Optional<Reservation>` | Serial confirm/release/expire. |
| `findDueIds(Instant now, Pageable page): List<UUID>` | WHERE RESERVED AND expires_at≤now, index status/deadline; batch ID rồi xử lý từng transaction. |
| `compareAndSetState(UUID id, ReservationStatus from, ReservationStatus to, Instant at): int` | Nếu chọn CAS thay lock; kiểm tra số dòng trước counters. |
| `ReservationItemRepository.findByReservationIdOrderByTicketTypeIdAsc(UUID): List<ReservationItem>` | Frozen price/quantity, dùng trong confirm/release. |
| `TicketRepository.findByReservationIdOrderByIdAsc(UUID): List<Ticket>` | Confirm lặp trả đúng bộ vé/QR cũ; cần internal DTO đúng quyền. |
| `findByOwnerUserIdOrderByIssuedAtDescIdAsc(UUID): List<Ticket>` | /tickets/me trả array theo API, index owner/time. Nếu thêm phân trang sau này phải sửa OpenAPI/DTO/plan cùng thay đổi. |
| `findByIdAndOwnerUserId(UUID, UUID): Optional<Ticket>` | QR ownership. |
| `findByQrCodeValue(String): Optional<Ticket>` | QR lookup UNIQUE, đây là opaque string, không ép UUID. |
| `findByOrderIdForUpdate(UUID): List<Ticket>` | Khóa vé theo thứ tự ID trước cancel; query phục vụ eligibility dùng bản read-only riêng. |
| `compareAndSetStatus(UUID id, TicketStatus from, TicketStatus to): int` | Check-in/out/cancel tránh hai cổng thắng cùng lúc. |
| `TicketScanRepository.save(TicketScan)` | Log action/gate/scannedAt trong transaction CAS; không có update log cũ. |
| `OutboxEventRepository.append/claimDue/markPublished/reschedule` | Theo [plan chung](00-cross-service-contracts.md); id message UUID ổn định. |

Các phương thức counter là lựa chọn SQL bổ sung; đừng vừa mutate entity vừa gọi UPDATE cho cùng lượng. `saveAll` reservation items/tickets dùng một transaction, UUID được sinh trước khi ghi outbox cần ID vé.

## 4. Đồng thời và idempotency cụ thể

Reserve cùng orderId: so fingerprint của event/items/deadline gốc; cùng payload trả reservation cũ kể cả đã hết hạn, không tự gia hạn. Khác payload 409. Cạnh tranh insert unique phải rollback cả transaction thua, sau đó đọc bản thắng ở transaction mới; không catch rồi tiếp tục trong transaction đã rollback-only.

Confirm holders phải đúng số quantity từng type, owner lấy từ caller đáng tin cậy và mapping C02. Snapshot tên/email/eventTitle/startTime/QR vào outbox; đọc template/email sau này không được phải gọi lại Order để dựng dữ liệu vé cũ. Redis lock TTL không thay DB lock/unique/counters.

Prepare phải toàn bộ đơn hoặc rollback nếu một vé không hợp lệ. C05 giữ quyền trước provider, commit sau success không từ chối do thời gian trôi; hold chặn scan và không tự mở khi tiền UNKNOWN. Bổ sung storage/API hold và fence reservation theo [thiết kế phục hồi](../architecture/payment-refund-recovery.md); chưa coi hợp đồng mới đã có trong runtime.

## 5. API và client/worker

| Operation | Method |
|---|---|
| POST `/api/v1/ticket-types` | createTypes |
| PUT `/api/v1/ticket-types/{id}` | updateType |
| GET `/api/v1/events/{eventId}/ticket-types` | listPublicTypes |
| POST `/internal/reservations` | reserve |
| POST `/internal/reservations/{id}/confirm` | confirm |
| POST `/internal/reservations/{id}/release` | release |
| GET `/api/v1/tickets/me` | getMine |
| GET `/api/v1/tickets/{id}/qr` | getQr |
| POST `/api/v1/gate/check-in` | checkIn |
| POST `/api/v1/gate/check-out` | checkOut |
| POST `/internal/tickets/cancel-by-order/{orderId}` | cancelByOrder |

`EventClient.getInternal(UUID): InternalEventResponse` cho owner/start/status; timeout map 503. Internal eligibility mới phải bổ sung OpenAPI trước client Order. `ReservationExpiryScheduler.expireDue(int batchSize)` chỉ gọi expire theo từng ID. `QrTokenGenerator.generate(): String` dùng nguồn ngẫu nhiên đủ mạnh, không dựa ticketId/timestamp; sinh PNG gửi mail thuộc Notification. `OutboxPublisher.publishDue` xử lý Kafka sau commit, không consumer order.paid để phát vé.

## 6. Migration và test

Tạo Flyway initial schema theo DDL, entity đầy đủ, ddl-auto validate. Thêm request fingerprint/outbox lease bằng migration cần thiết; không giả cột đã tồn tại trong ảnh. Bộ DTO/request/response đúng OpenAPI: Reserve/ReservedItem/Reservation/Holder/Confirm/Ticket/Qr/GateScan/Cancel.

ErrorCode: INSUFFICIENT_TICKETS 409, RESERVATION_EXPIRED/RESERVATION_ALREADY_CONFIRMED/INVALID_TICKET_STATE 409, TICKET_NOT_FOUND/EVENT_NOT_FOUND 404, FORBIDDEN_NOT_OWNER 403, EVENT_SERVICE_UNAVAILABLE 503, VALIDATION_ERROR 400; code không được thay cả lỗi mạng bằng business permanent error.

MySQL integration test: 100 reserve tranh quota không oversell; reserve lặp/khác payload; giá đổi không đổi snapshot; confirm đua expire/release; confirm quá hạn vẫn trả quota đã commit; confirm lặp không thêm QR; hai cổng cùng QR chỉ một scan/outbox; checkout trước checkin ở Ticket bị chặn; cancel lặp/đã dùng/48h biên và rollback cả đơn; outbox crash/retry giữ messageId. WebMvcTest cho owner/service/gate. C02/C05/C08 đã có quyết định; cần triển khai buyer ownership, hold/fence và quota 0/publish preflight. C07 thiết bị để sau.
