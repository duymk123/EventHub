# Plan code order-service

Ngày 2026-10-28. Đọc [plan chung](00-cross-service-contracts.md), [OpenAPI Order](../api/order-service-openapi.yaml), [DDL](../database/order-service/init.sql), [ERD UUID](../database/order-service/erd.md), [Kafka](../kafka/events.md). Order điều phối tiền/đơn, không sở hữu quota/QR và không đọc Ticket DB.

Bổ sung quyết định 2026-11-05: C02 snapshot buyer email đã xác minh, owner=buyer; C04 hoàn cả đơn 70% giá mua, giữ 30%, ADMIN duyệt theo phương án giai đoạn đầu; C05 hold trước provider, verify UNKNOWN và bù lỗi phát vé 100%. [Thiết kế phục hồi](../architecture/payment-refund-recovery.md) thay mọi mô tả chờ C04/C05 trước đó. Provider C06 chốt VNPay sandbox ngày 2026-11-22; phạm vi hoàn tiền theo C05 rút gọn ở §10, operation ledger và hold/fence là hướng phát triển.

Bổ sung quyết định 2026-11-22 [C12](../project-clarifications.md#c12--đặt-vé-theo-ghế-cụ-thể): khách chọn ghế cụ thể. Mỗi holder trong request có `seatId`, Order gọi Ticket giữ đúng các ghế đó (đồng bộ như cũ), ghế đã bị lấy trả 409 `SEAT_TAKEN` kèm `takenSeatIds`. `order_holders` lưu `seat_id`, `seat_label`. Các bước chuyển code ở [§9](#9-bán-theo-ghế-c12-đã-làm-2026-11-23).

Bổ sung quyết định 2026-11-22 (đợt 2): [C06 VNPay sandbox](../project-clarifications.md#c06--vnpay-sandbox), [C05 rút gọn](../project-clarifications.md#c05-rút-gọn--luồng-hoàn-tiền-giai-đoạn-này), C14 Idempotency-Key, C15 Flyway. **§10 là thiết kế áp dụng cho thanh toán, IPN, hoàn tiền và migration; §3–§4 chỉ còn giá trị tham khảo cho phần "hướng phát triển"** (hold, verify UNKNOWN, task bền vững, hoàn bù 100%).

## 1. Refactor model hiện có trước khi thêm service

| Hiện có trong code | Mục tiêu và công việc |
|---|---|
| BaseEntity dùng UUID CHAR(36) | Giữ quyết định này khi kiểm tra mapping PK/FK; không chuyển lại IDENTITY/Long. |
| `model/Order`, user_id, PENDING, payment_status, expired_at | Đổi thành entity `PurchaseOrder` bảng orders, customer_id/event_id/reservation_id, PENDING_PAYMENT, subtotal/total_amount/currency/paid_at/expires_at theo DDL. Payment status thuộc payments; không duy trì hai nguồn authoritative. |
| OrderItem có ticketTypeId/quantity/unitPrice/subtotal | Bổ sung ticket_type_name snapshot, line_total đúng tên và order_holders riêng; không một holder cho cả item nhiều vé. |
| Payment có transaction_id NOT NULL | Provider + provider_transaction_id nullable trước phản hồi, CREATED/PENDING/SUCCEEDED/FAILED, payment_url và idempotency_key; mã provider là string ngoài hệ thống. |
| Refund bảng refunds | Chuyển mô hình RefundRequest/refund_requests, requested_by/approved_by/review_reason/time và state theo hợp đồng; không mặc định PENDING. |
| LocalDateTime, DECIMAL(12,2) | Chuẩn UTC Instant và DECIMAL(14,2) theo schema tham chiếu; kiểm tra dữ liệu/migration khi DB đã tồn tại. |

Thêm OrderHolder, PaymentWebhook, OutboxEvent và PendingOperation kỹ thuật. Không rename/drop bảng có dữ liệu một cách tự động. Migration chuyển dữ liệu/mapping và compatibility phải được chuẩn bị trước `ddl-auto=validate`. Tổ chức lại model→entity và test imports trong cùng thay đổi, không làm gãy module rồi thêm controller.

## 2. OrderService và query

| Signature | Logic / ranh giới |
|---|---|
| `OrderService.create(Actor customer, CreateOrderRequest request, IdempotencyContext context): OrderResponse` | Validate type/quantity/holders; lấy customer từ Principal, sinh orderId UUID; Ticket.reserve trước → dùng giá/hạn server → lưu orders/items/holders transaction ngắn. Nếu lưu lỗi thì release bù trừ; fail-safe Ticket expiry khi không lưu được task do DB lỗi. C12: mỗi holder có seatId, tổng tối đa 10 ghế, không trùng ghế; gửi seatIds theo từng hạng sang Ticket. |
| `OrderQueryService.getMine(Actor customer, Pageable pageable): Page<OrderResponse>` | CustomerId owner, sort mặc định createdAt/id qua @PageableDefault; map items/holders tránh N+1, read-only. Trả Page DTO, JSON content/page theo cấu hình via-dto chung. |
| `getDetail(Actor customer, UUID orderId): OrderResponse` | Order thuộc customer, trả snapshot names/prices/deadline đúng API; không tính lại giá hiện tại từ Ticket. |
| `OrderExpiryService.expire(UUID orderId, Instant now): boolean` | Lock/CAS PENDING_PAYMENT có expiresAt≤now → EXPIRED; ghi task RELEASE + outbox order.failed reason=EXPIRED cùng transaction. Không chạm PAID/FAILED/REFUNDED. |

IdempotencyContext cho POST /orders là bổ sung đề xuất, cần hợp đồng/fingerprint/storage trước triển khai. Retry giữ orderId, khác payload trả 409; không reserve bằng ID mới chỉ vì timeout. Holders count bằng quantity từng item. C02 owner=customerId, snapshot buyer email đã xác minh khi tạo đơn; holderEmail thiếu fallback buyer.

## 3. PaymentService và webhook (thiết kế đầy đủ — xem §10 cho phạm vi hiện tại)

| Signature | Logic / ranh giới |
|---|---|
| `PaymentService.initiate(Actor customer, UUID orderId, String idempotencyKey, InitPaymentRequest request): PaymentResponse` | Owner, PENDING_PAYMENT chưa hết hạn; lưu/đọc Payment CREATED theo key, amount từ Order; gọi provider ngoài transaction → update PENDING/url/transactionId bằng điều kiện. Timeout truy vấn lại/retry cùng key, không tạo payment thứ hai. |
| `PaymentWebhookService.handle(String provider, RawWebhook request): ProviderWebhookAck` | Verify chữ ký trên raw bytes/query/form đúng provider trước → adapter normalize → đối chiếu transaction/order/amount/currency → helper transaction dedup/CAS/task/outbox → ACK đúng protocol. Wire contract VNPay ở §10.3. |
| `WebhookTransactionService.applySuccess(VerifiedPaymentEvent event): WebhookOutcome` | Claim provider/eventId, lock Order/Payment; PENDING_PAYMENT→PAID, SUCCEEDED/paidAt và CONFIRM task cùng transaction trước ACK. Lặp không tạo hiệu ứng mới; EXPIRED/FAILED nhận tiền muộn giữ bằng chứng, query/fence Ticket rồi bù 100% thực thu chưa hoàn theo C05. |
| `applyFailure(VerifiedPaymentEvent event): WebhookOutcome` | **C18 thay:** chỉ Payment FAILED, đơn giữ PENDING_PAYMENT để khách thanh toán lại, không release, không phát order.failed. Failure đến sau PAID/EXPIRED không đổi ngược trạng thái. |
| `FulfillmentService.confirmOrder(UUID orderId): FulfillmentOutcome` | Confirm cùng reservation/payload, success ghi DONE + outbox order.paid. Timeout query/retry; EXPIRED/RELEASED hoặc lỗi không thể phát vé phải fence Ticket trước operation bù 100%, không vừa phát vé vừa bù. |
| `releaseOrder(UUID orderId): void` | Worker gọi Ticket.release sau Order FAILED/EXPIRED, retry cùng reservationId; CONFIRMED 409 cần đối soát, không gọi cancel nếu chưa có refund hợp lệ. |

`RawWebhook` giữ raw body/headers/query để xác minh; không parse thành WebhookPayload JSON ứng dụng rồi mất dữ liệu chữ ký. `payload_hash` chỉ phát hiện cùng eventId khác payload, không xác thực provider. Claim webhook cùng transition/job trong một transaction; chỉ đánh PROCESSED khi đã ghi bền vững quyết định local, không đòi REST confirm đã xong để ACK.

`order.paid` chỉ phát sau Ticket đã phát vé. Tiền đã thu nhưng confirm pending vẫn giữ PAID theo báo cáo; trạng thái fulfillment kỹ thuật/task phải cho phép truy vết riêng, không phát order.paid sớm để làm analytics chạy. `paidAt` là thời điểm thu tiền, không thời điểm worker retry thành công.

## 4. RefundService và retry (thiết kế đầy đủ — xem §10 cho phạm vi hiện tại)

| Signature | Logic / ranh giới |
|---|---|
| `RefundService.request(Actor customer, UUID orderId, RefundRequest request): RefundResponse` | Owner/PAID, mọi vé chưa dùng/>=48h, không có refund/compensation tranh cùng tiền; quote=70% snapshot giá cả đơn, lưu allocation/version. Lock Order chặn lặp. Read eligibility chưa giữ quyền, C05 cần prepare hold trước provider. |
| `listForReview(Actor reviewer, RefundFilter filter): List<RefundResponse>` | Array hiện tại, chỉ ADMIN theo C04 giai đoạn đầu. Không cấp quyền reviewer cho ORGANIZER. |
| `approve(Actor reviewer, UUID refundId): RefundResponse` | ADMIN, REQUESTED, kiểm tra lại >=48h/vé; lưu APPROVED và task PREPARE_REFUND. Chỉ gọi provider sau Ticket hold PREPARED; không giữ DB lock khi gọi HTTP. |
| `reject(Actor reviewer, UUID refundId, RejectRefundRequest request): RefundResponse` | Chỉ REQUESTED→REJECTED, review_reason/reviewedAt cùng transaction; không sửa amount hoặc Order PAID. approvedBy chỉ ghi khi duyệt. |
| `RefundExecutionService.execute(UUID refundId): RefundExecutionOutcome` | Sau hold: refund cùng key, UNKNOWN thì verify và giữ hold. Success lưu receipt/thực hoàn và CANCEL task; chỉ khi xác nhận không hoàn và không thể success muộn mới enqueue RELEASE_HOLD, không hủy vé. |
| `completeCancellation(UUID refundId): void` | Commit hold hủy cả đơn, không recheck 48h theo giờ mới. Thành công ghi REFUNDED và outbox một lần; lỗi giữ WAIT_CANCEL/MANUAL_REVIEW/hold, không mở vé hoặc phát event sớm. |
| `RefundPolicy.evaluate(RefundContext context): RefundQuote` | C04: 70% sum(line_total), HALF_UP scale 2, giữ 30% không thêm phí; hủy toàn bộ quantity. Phân bổ phần lẻ theo phần dư lớn nhất/ticketTypeId và lưu quote/version. Bù kỹ thuật 100% dùng policy riêng. |

API chỉ nhận reason, không ticketIds hoặc amount tự chọn; C04 chốt cả đơn nên chưa thêm hoàn từng vé. Vẫn cần storage allocation/quote để giữ nguyên tiền giữa retry. order.refunded chứa mọi hạng và toàn bộ quantity, refundedAmount thực hoàn phân bổ tổng bằng amount. Thu tiền nhưng không phát vé dùng compensation operation và message riêng dự kiến, không phát order.refunded vào sale chưa có.

## 5. Repository methods

| Repository / method đề xuất | Mục đích |
|---|---|
| `PurchaseOrderRepository.findByIdAndCustomerId(UUID, UUID): Optional<PurchaseOrder>` | Query owner; hoặc projection DTO kèm children bằng query riêng. |
| `findByCustomerId(UUID, Pageable): Page<PurchaseOrder>` | Lấy page IDs trước, rồi batch items/holders; không page collection join fetch gây nhân bản dòng. |
| `findByIdForUpdate(UUID): Optional<PurchaseOrder>` | Serial webhook/expiry/refund creation. |
| `compareAndSetStatus(UUID, OrderStatus from, OrderStatus to, Instant at): int` | Phương án CAS thay lock, WHERE deadline khi expiry; chỉ thao tác thắng được ghi effects. |
| `findExpiredIds(Instant now, Pageable): List<UUID>` | PENDING_PAYMENT/expires_at index cho scheduler. |
| `OrderItemRepository.findByOrderIdOrderByIdAsc(UUID): List<OrderItem>` | Giá/type snapshot, không gọi Ticket lại để tính tiền. |
| `findByOrderIdIn(Collection<UUID>): List<OrderItem>` | Batch query cho page. |
| `OrderHolderRepository.findByOrderItemIdInOrderByIdAsc(Collection<UUID>): List<OrderHolder>` | Holder từng vé, thứ tự ổn định để dựng confirm payload. |
| `PaymentRepository.findByIdempotencyKey(String): Optional<Payment>` | UNIQUE chống lặp; compare orderId/provider/payload trước trả payment cũ. |
| `findByProviderAndProviderTransactionId(String, String): Optional<Payment>` | Match webhook đã xác minh; external IDs không ép UUID. |
| `findByOrderIdForUpdate(UUID): List<Payment>` | Chọn payment hợp lệ khi reconcile; không cộng tiền hai payment thành một thành công mơ hồ. |
| `compareAndSetStatus(UUID, PaymentStatus from, PaymentStatus to, Instant at): int` | Ghi kết quả không làm SUCCEEDED bị FAILED webhook muộn ghi đè. |
| `PaymentWebhookRepository.findByProviderAndProviderEventId(String, String): Optional<PaymentWebhook>` | Dedup/hash so sánh; unique insert trong transaction cùng state/task. |
| `markProcessed(UUID webhookId, Instant at): int` | Sau local effects bền vững; cùng transaction, không mark thành công trong finally. |
| `RefundRequestRepository.findByIdForUpdate(UUID): Optional<RefundRequest>` | Review/worker chuyển state đúng một lần. |
| `existsByOrderIdAndStatusIn(UUID, Set<RefundStatus>): boolean` | Kiểm tra active refund dưới Order lock; status hợp lệ chờ policy C04. |
| `findAll(Specification<RefundRequest> scope): List<RefundRequest>` | Status + authorization scope trong DB query. Nếu scope organizer cần eventIds, thiết kế internal Event lookup/filter trước, không JOIN Event DB. API array chưa có paging; bổ sung paging phải đi cùng OpenAPI. |
| `PendingOperationRepository.enqueueIfAbsent(OperationCommand): UUID` | Unique operation_id/kind, vì cùng đơn có thể nhiều payment/refund; trùng DONE không revive. UUID/payload/fingerprint ổn định. |
| `claimDue(Instant now, int limit, UUID leaseToken): List<OperationWork>` | Khóa/claim ngắn với lease, index status/next_attempt_at. |
| `markDone(UUID id, UUID leaseToken, Instant at): int` | Chỉ owner lease được hoàn tất, return affected rows. |
| `reschedule(UUID id, UUID leaseToken, Instant nextAt, String safeError): int` | Backoff lỗi tạm thời; không log payload/token. |
| `markNeedsReconciliation(UUID id, UUID leaseToken, String reason): int` | Lỗi permanent/unknown-money-outcome cần quyết định C05/C06, không vô hạn retry. |
| `OutboxEventRepository.append/claimDue/markPublished/reschedule` | Durable Kafka publish, cùng nguyên tắc [plan chung](00-cross-service-contracts.md). |

`save/saveAll/findById` kế thừa JpaRepository<Entity,UUID>. Idempotency key của provider/client là string opaque, không phải PK; không đổi sang UUID bắt buộc nếu provider dùng định dạng khác.

## 6. API → service và client

| Operation public `/api/v1` | Method |
|---|---|
| POST `/orders` | create |
| GET `/orders/me` | getMine |
| GET `/orders/{id}` | getDetail |
| POST `/orders/{id}/payment` | initiate |
| GET `/payments/vnpay/ipn` (C06) | `VnpayIpnService.handle` |
| POST `/orders/{id}/payment/verify` (C06) | `PaymentService.verify` |
| POST `/orders/{id}/refunds` | request |
| GET `/admin/refunds` | listForReview |
| POST `/admin/refunds/{id}/approve` | approve |
| POST `/admin/refunds/{id}/reject` | reject |
| POST `/admin/refunds/{id}/retry` (C05 rút gọn) | retry |

`TicketClient.reserve/confirm/release/cancelByOrder` dùng DTO theo Ticket OpenAPI; `getRefundEligibility` là contract cần thêm theo plan chung. `EventClient.getInternal` kiểm tra owner/startTime khi cần. `PaymentGatewayClient.createPayment/verifyPayment/refund/verifyRefund` và `WebhookVerifier.verify` tách adapter VNPay (C06, §10.3), mock không được làm implementation live mặc định.

`OrderExpiryScheduler.expireDue`, `OperationWorker.runDue`, `OutboxPublisher.publishDue` xử lý batch ngoài một transaction lớn. Response DTO: OrderResponse/OrderItemResponse/HolderResponse/PaymentResponse/RefundResponse; Order history trả page, list refunds trả array theo OpenAPI hiện hành. Nếu bổ sung phân trang refunds thì cập nhật contract trước code.

## 7. Kiểm tra và thứ tự code

1. Refactor entity + migration, đối chiếu DDL/metadata cho class/table mới để tránh quay lại Long/IDENTITY. Không tạo lại file test đã xóa theo yêu cầu chủ dự án.
2. Query/create/reserve + holders/fingerprint; fake Ticket client và test bù trừ; sau đó MySQL lock/unique.
3. Payment adapter/verifier khi C06 có câu trả lời; webhook local transaction/task/outbox; worker integration với Ticket.
4. RefundPolicy/review theo C04 và C05 rút gọn (§10.4); hold/fence/worker là hướng phát triển.

Test bắt buộc: price tamper; quantity/holders mismatch; reserve thành công rồi DB save lỗi; payment request lặp và timeout sau provider đã nhận; webhook duplicate/cùng key khác hash/sai chữ ký/sai amount; webhook đua expiry chỉ một nhánh thắng; crash sau PAID/task commit trước confirm; PAID nhưng reservation expired đi reconcile; confirm thành công rồi worker crash không thêm vé/order.paid; refund/reject/approve races; provider refund thành công nhưng cancel 409; hoàn lặp không gọi provider/cộng analytics hai lần. Chỉ business error đúng code mới là non-retryable; timeout/503 còn retry/verify.

## 8. Trạng thái triển khai (2026-11-19)

Đã có `POST /api/v1/orders`, `GET /api/v1/orders/me`, `GET /api/v1/orders/{id}` và worker hết hạn đơn. Cập nhật 2026-11-23: bán theo ghế (§9), Flyway (§10.1) và thanh toán VNPay + IPN + verify + confirm Ticket (§10.3) đã làm. Còn lại: Idempotency-Key cho `POST /orders` (§10.2, cột đã có trong V1) và hoàn tiền (§10.4).

| Mục plan | Cách làm |
|---|---|
| Actor | JWT Keycloak, CUSTOMER; `customerId` từ `sub`, email chỉ lấy khi `email_verified` (C02). Thiếu email đã xác minh → 403 `EMAIL_NOT_VERIFIED`. |
| create | Validate holders = quantity, sinh orderId → `TicketClient.reserve` **ngoài** transaction (lỗi tạm thời thử lại một lần cùng orderId) → lưu orders/items/holders bằng giá và hạn Ticket trả về trong transaction ngắn; lưu lỗi thì release bù trừ. `Order.id` gán sẵn (bỏ `@GeneratedValue`). |
| Snapshot email | Cột mới `orders.customer_email` (DDL đã cập nhật). |
| getMine | Page đơn không fetch collection, rồi nạp item + holder của cả trang bằng một truy vấn; JSON `{content, page}` theo via-dto; sort chỉ nhận createdAt, id, totalAmount, status, expiresAt. |
| Expiry | Lô ID quá hạn, mỗi đơn một transaction: khóa đơn, PENDING_PAYMENT → EXPIRED + outbox `order.failed` reason EXPIRED; sau commit gọi Ticket release (lỗi thì reservation cùng hạn tự hết ở Ticket). |
| TicketClient | Token `client_credentials` của `eventhub-order-service`; giữ nguyên code nghiệp vụ của Ticket (SEAT_TAKEN, SEAT_NOT_FOUND, TICKET_TYPE_NOT_ON_SALE…), timeout/5xx → 503 `TICKET_SERVICE_UNAVAILABLE`. |
| Outbox | Giống Ticket (`OutboxWriter`, `OutboxPublisher`). |

Chưa làm: IdempotencyContext cho POST /orders (retry của client tạo đơn mới), bảng `PendingOperation` cho task RELEASE/CONFIRM bền vững, PaymentService/Webhook/Refund (§3–§4), Flyway. Đã bỏ code thừa của PR #25 (`markPaid` cho khách tự đánh dấu đã trả tiền, `createPendingOrder`).

## 9. Bán theo ghế (C12, đã làm 2026-11-23)

Làm cùng [plan Ticket §8](ticket-service.md#8-bán-theo-ghế-c12-đã-làm-2026-11-23).

| Phần | Thay đổi |
|---|---|
| DTO | `HolderInput` thêm `seatId` (bắt buộc). Validate: số holder = quantity từng dòng, tổng ≤ 10, không trùng seatId. `HolderResponse` thêm `seatId`, `seatLabel`. `ErrorResponse` thêm `takenSeatIds`. |
| Entity | `OrderHolder` thêm `seatId`, `seatLabel`; UNIQUE (order_item_id, seat_id). Có sẵn trong Flyway V1 (§10.1). |
| `TicketClient.reserve` | Gửi `items[{ticketTypeId, seatIds}]`. Thêm `SEAT_TAKEN`, `SEAT_NOT_FOUND` vào danh sách mã giữ nguyên của Ticket và chuyển tiếp `takenSeatIds` về client; bỏ `INSUFFICIENT_TICKETS`. |
| Lưu đơn | Lấy `seatLabel` từ `reservation.items[].seats` Ticket trả về, gắn vào từng holder theo `seatId`. |
| Confirm (khi làm thanh toán) | `holders[]` gửi kèm `seatId` để Ticket phát đúng vé cho đúng ghế. |
| Frontend | Màn 3D chọn zone → sơ đồ ghế của zone → gửi ghế đã chọn. Nhận 409 SEAT_TAKEN thì tô xám các ghế trong `takenSeatIds` và cho chọn lại. |

Kiểm tra: ghế trùng trong request trả 400; ghế bị lấy trả 409 kèm đúng danh sách; lưu đơn lỗi sau khi giữ ghế thì release trả ghế; đơn hết hạn trả ghế qua release.

## 10. Chốt 2026-11-22: VNPay, hoàn tiền rút gọn, Idempotency-Key, Flyway (10.1, 10.3 đã làm 2026-11-23)

### 10.1 Flyway (C15)

DB dev chỉ có dữ liệu thử: xóa database `eventhub_order`, thêm `flyway-core` + `flyway-mysql` (Spring Boot 3.5), tạo `db/migration/V1__init.sql` từ [DDL tham chiếu](../database/order-service/init.sql), đặt `spring.flyway.enabled: true` (khác Notification/Analytics đang tắt khi chạy local) và `ddl-auto: validate`. Từ đây mọi thay đổi schema là file `V2__…`, `V3__…`, không sửa V1 sau khi đã chạy. Làm cùng lúc với Ticket ([plan Ticket §9](ticket-service.md#9-chốt-2026-11-22-flyway-khung-giờ-quét-hủy-vé-rút-gọn-flyway-đã-làm-2026-11-23)).

### 10.2 Idempotency-Key cho POST /orders (C14)

| Bước | Cách làm |
|---|---|
| Nhận request | Header `Idempotency-Key` (tùy chọn, ≤ 100 ký tự). Tính `request_hash` = SHA-256 của nội dung chuẩn hóa (eventId, các ghế đã sắp xếp, holder). |
| Đã có đơn cùng `(customerId, key)` | Hash trùng → trả 200 với đơn cũ, không gọi Ticket. Hash khác → 409 `IDEMPOTENCY_KEY_REUSED`. |
| Thứ tự kiểm | Kiểm key trước quy tắc một đơn chờ (C20), để gửi lại đúng request vẫn nhận đơn cũ thay vì 409 `PENDING_ORDER_EXISTS`. |
| Chưa có | Như luồng tạo đơn hiện tại; lưu key + hash trong cùng transaction lưu đơn. Hai request cùng key chạy song song: UNIQUE `(customer_id, idempotency_key)` chặn bản thứ hai; bản thua release ghế đã giữ (orderId của nó khác) rồi đọc lại và trả đơn thắng. |
| Frontend | Sinh UUID khi bấm "Giữ ghế", giữ nguyên key khi retry do lỗi mạng; đổi key khi khách chọn ghế khác. |

### 10.3 Thanh toán VNPay (C06)

| Thành phần | Trách nhiệm |
|---|---|
| `VnpayProperties` | `tmnCode`, `hashSecret`, `payUrl`, `apiUrl`, `returnUrl`, `version=2.1.0` từ biến môi trường `VNPAY_*`; không commit secret. |
| `VnpaySigner` | HMAC-SHA512: sắp xếp tham số `vnp_*` theo tên, URL-encode giá trị, nối `k=v&…`, ký bằng hash secret; so sánh chữ ký bằng so sánh thời gian hằng. |
| `PaymentService.initiate` | Đơn PENDING_PAYMENT, chưa hết hạn. Payment theo `Idempotency-Key` (provider VNPAY, `provider_txn_ref` = id bỏ gạch). Dựng URL với vnp_Amount = tiền × 100, vnp_ExpireDate = expiresAt (giờ Việt Nam, `yyyyMMddHHmmss`), vnp_IpAddr của khách. Payment PENDING, lưu `payment_url`. |
| `VnpayIpnService.handle(Map<String,String> params)` | Kiểm chữ ký (97) → tìm payment theo vnp_TxnRef (01) → so số tiền (04) → payment đã xử lý (02) → ghi `payment_webhooks` với `provider_event_id = txnRef:transactionNo` → áp kết quả trong một transaction: thành công (ResponseCode và TransactionStatus đều `00`) thì payment SUCCEEDED + `provider_transaction_id`, `provider_pay_date`, đơn PAID; thất bại thì chỉ payment FAILED, đơn vẫn PENDING_PAYMENT (C18). Trả `{RspCode, Message}` với HTTP 200. |
| Sau khi PAID | Ngoài transaction: `TicketConfirmationService` gọi `TicketClient.confirm` (holders kèm `seatId`, `customerEmail`). Lúc PAID lưu `orders.payment_id` (C21). Thành công → `orders.tickets_confirmed_at` + outbox `order.paid` trong một transaction. Lỗi tạm thời → `TicketConfirmationScheduler` (15 giây) thử lại các đơn PAID có `tickets_confirmed_at` NULL; Ticket confirm idempotent nên không phát vé hai lần. Ticket trả `RESERVATION_RELEASED_OR_EXPIRED`/`RESERVATION_NOT_FOUND` → đơn EXPIRED + `order.failed` EXPIRED, payment SUCCEEDED vào danh sách đối soát. |
| `PaymentService.verify` | Payment PENDING → gọi `querydr` (vnp_Command=querydr, vnp_RequestId mới, ký checksum theo tài liệu API) và áp kết quả bằng cùng hàm với IPN. |
| Tiền về sau EXPIRED/CANCELLED hoặc trả hai lần | Payment SUCCEEDED, đơn giữ nguyên, không confirm; payment khác `orders.payment_id` nên vào danh sách đối soát (§11). |

### 10.4 Hoàn tiền rút gọn (C05)

| Method | Logic |
|---|---|
| `RefundService.request` | Như §4: owner, PAID, ≥ 48 giờ, chưa có refund đang xử lý; amount = 70% tổng `line_total` (HALF_UP, scale 2). Kiểm vé chưa dùng ở bước duyệt (Ticket trả 409). |
| `approve` | ADMIN. Payment gốc lấy theo `orders.payment_id`. Kiểm lại 48 giờ; REQUESTED → PROCESSING (CAS). Gọi `TicketClient.cancelByOrder` → 409 thì FAILED (`TICKET_ALREADY_CHECKED_IN`). Gọi `VnpayRefundClient.refund` (vnp_TransactionType=03, vnp_Amount = 70% × 100, TxnRef/TransactionNo/PayDate của payment gốc, vnp_RequestId mới lưu `provider_request_id`). `00` → REFUNDED, `refunded_at`, `provider_refund_id`, đơn REFUNDED, outbox `order.refunded` (items[].refundedAmount phân bổ theo C04). Lỗi/timeout → FAILED + `failure_reason`. |
| `retry` | ADMIN, chỉ FAILED. Gọi lại `cancelByOrder` (idempotent) rồi VNPay với RequestId mới. |
| `reject` | Như §4. |

Hướng phát triển (ghi vào báo cáo, không code giai đoạn này): verify `querydr` cho kết quả hoàn chưa rõ, hold trước provider, `pending_operations` bền vững, hoàn bù 100% + `payment.compensated`.

### 10.5 Mã lỗi mới

`IDEMPOTENCY_KEY_REUSED` 409, `ORDER_NOT_PAYABLE` 409, `PENDING_ORDER_EXISTS` 409 (C20), `ORDER_NOT_CANCELLABLE` 409 (C19), `ORDER_EXPIRED` 409, `PAYMENT_PROVIDER_UNAVAILABLE` 503, `INVALID_REFUND_STATE` 409, `REFUND_WINDOW_EXPIRED` 422, `TICKET_ALREADY_CHECKED_IN` 422 (giữ nguyên từ Ticket), cùng `SEAT_TAKEN`/`SEAT_NOT_FOUND` của C12.

### 10.6 Kiểm tra thủ công

Cùng key gửi hai lần trả cùng đơn; cùng key khác ghế trả 409. Thanh toán sandbox bằng thẻ test NCB của VNPay: IPN thành công → PAID → vé phát, email QR có ghế; IPN sai chữ ký → 97; IPN lặp → 02 và không phát vé lần hai; khách hủy trên trang VNPay → payment FAILED, đơn vẫn chờ, "Thanh toán lại" thành công (C18). Không có IPN (local) → `verify` đưa đơn về đúng trạng thái. Hoàn tiền: duyệt → vé CANCELLED, ghế trống lại, refund REFUNDED, có `order.refunded`; vé đã quét → FAILED; VNPay lỗi → FAILED rồi retry.

## 11. Chốt 2026-11-23: giữ ghế 10 phút, thanh toán lại, hủy đơn, một đơn chờ, đối soát (đã làm 2026-11-23)

| Mục | Cách làm |
|---|---|
| C16 hạn 10 phút | `eventhub.order.payment-ttl: PT10M`; Ticket kẹp tối đa `PT10M` và trả hạn chính thức; `vnp_ExpireDate` = `orders.expires_at`. |
| C18 thanh toán lại | `PaymentResultService.fail` chỉ đặt payment FAILED. Đơn vẫn PENDING_PAYMENT; frontend sinh Idempotency-Key mới cho lần "Thanh toán lại" → payment mới, `vnp_TxnRef` mới. Payment PENDING cũ (khách đóng tab) không bị hủy; nếu cả hai cùng thành công thì payment đến sau vào đối soát. |
| C19 hủy đơn | `POST /orders/{id}/cancel` (CUSTOMER, chủ đơn). Khóa đơn; CANCELLED gọi lại trả đơn hiện tại; trạng thái khác PENDING_PAYMENT trả 409 `ORDER_NOT_CANCELLABLE`. Sau commit gọi `TicketClient.releaseQuietly`. Không phát Kafka. |
| C20 một đơn chờ | Trước khi gọi Ticket: tìm đơn PENDING_PAYMENT của (customer, event); còn hạn → 409 `PENDING_ORDER_EXISTS`; đã quá hạn mà job chưa quét → `OrderExpiryService.expire` ngay rồi đi tiếp. Chốt cuối là cột sinh `pending_slot` (= `customer_id:event_id` khi PENDING_PAYMENT, NULL khi khác) với UNIQUE `uk_order_pending_slot`: bản thua release ghế vừa giữ rồi trả 409. |
| C21 đối soát | `orders.payment_id` = payment làm đơn PAID. `GET /admin/payments/reconciliation` (ADMIN) trả tối đa 100 payment SUCCEEDED có id khác `orders.payment_id` (hoặc đơn chưa có payment_id), mới nhất trước, kèm `providerTransactionId`/`providerPayDate` để hoàn trên cổng VNPay. Chưa có thao tác "đã xử lý"; hoàn tiền thủ công xong thì ghi chú ngoài hệ thống. |
| Kiểm tra (2026-11-23) | Script IPN với secret thử: thất bại → đơn vẫn PENDING_PAYMENT, không có outbox; thanh toán lại thành công → PAID và `payment_id` đúng; payment thứ hai thành công → chỉ payment đó trong truy vấn đối soát; chèn hai đơn PENDING cùng khách/sự kiện → lỗi `uk_order_pending_slot`, sau khi hủy thì chèn được. `cancel` và `reconciliation` không token → 401. Chưa chạy với token Keycloak thật. |

## 12. Hoàn tiền, Idempotency-Key, đối soát (đã làm 2026-11-23)

| Mục | Cách làm |
|---|---|
| C14 Idempotency-Key | `POST /orders` nhận header tùy chọn (≤ 100 ký tự). `request_hash` = SHA-256 của sự kiện + hạng (theo ticketTypeId) + ghế (theo seatId) kèm holder, nên đổi thứ tự trong JSON không đổi hash. Kiểm key **trước** luật một đơn chờ: cùng key + cùng hash → 200 đơn cũ, không gọi Ticket; khác hash → 409 `IDEMPOTENCY_KEY_REUSED`. Hai request cùng key song song: bản thua UNIQUE `(customer_id, idempotency_key)` release ghế của nó rồi trả đơn thắng. |
| Mốc 48 giờ | Ticket trả `eventStartTime` trong phản hồi giữ ghế (lưu ở `reservations.event_start_time`); Order lưu `orders.event_start_time`. Event không cho đổi giờ sau publish nên không cần gọi Event hay API refund-eligibility. Khi còn ≥ 48 giờ thì cổng quét (mở 60 phút trước) chưa mở, nên "mọi vé chưa dùng" luôn đúng; Ticket vẫn chặn khi hủy. |
| `RefundService.request` | Khóa đơn. PAID + `tickets_confirmed_at` + `payment_id` (không thì 409 `ORDER_NOT_REFUNDABLE`); chưa có refund khác REJECTED (409 `REFUND_ALREADY_EXISTS`); `now ≤ startTime − 48h` (422 `REFUND_WINDOW_EXPIRED`). Số tiền `RefundAllocation.total`. |
| `approve` | Khóa đơn rồi refund; chỉ REQUESTED. Dưới 48 giờ → REJECTED, `review_reason = REFUND_WINDOW_EXPIRED…` (quyết định 1a). Còn hạn → PROCESSING, commit, rồi ngoài transaction: `TicketClient.cancelByOrder` → `VnpayClient.refund` (03, `vnp_RequestId` mới lưu `provider_request_id`, giao dịch gốc theo `orders.payment_id`, `vnp_TransactionDate` = `vnp_CreateDate` của URL thanh toán như querydr, `vnp_CreateBy` = id ADMIN). `00` → refund REFUNDED + đơn REFUNDED + outbox `order.refunded` trong một transaction. Lỗi → FAILED, `failure_reason` = mã lỗi Ticket, `VNPAY_<mã>`, `VNPAY_CHECKSUM_MISMATCH` hoặc `PAYMENT_PROVIDER_UNAVAILABLE`. |
| `reject` / `retry` | Reject: REQUESTED → REJECTED, lý do bắt buộc. Retry: chỉ FAILED → PROCESSING (khóa nên hai ADMIN bấm cùng lúc chỉ một bên gọi VNPay), chạy lại hủy vé (idempotent) rồi VNPay, không kiểm lại 48 giờ. Khách không có API rút yêu cầu (quyết định 2). |
| `RefundAllocation` | C04: tổng = round(Σ line_total × 0.70, 2, HALF_UP); mỗi hạng cắt xuống 0,01, phần lẻ chia theo phần dư lớn nhất rồi ticketTypeId; tính lại từ snapshot nên retry không đổi tiền. |
| C21 đã xử lý | `payments.reconciled_at/by/reconciliation_note`; `POST /admin/payments/{id}/resolve` (ADMIN, ghi chú bắt buộc) chỉ cho payment đang trong danh sách (409 `PAYMENT_NOT_RECONCILABLE`); danh sách bỏ payment đã xử lý. |
| Gọi API VNPay | querydr/refund gửi JSON đã serialize (byte[]) để có `Content-Length`, không dùng chunked transfer. |
| Kiểm tra (2026-11-23) | End-to-end trên máy local với Keycloak realm mới, Event/Ticket/Order thật, audience bật, VNPay refund giả lập ký bằng secret thử: 37/37 qua (Idempotency-Key, một đơn chờ, IPN → PAID → phát vé, khung giờ quét, hoàn tiền thành công và order.refunded, VNPay lỗi → FAILED → thử lại, từ chối, tự từ chối dưới 48 giờ, 422 khi yêu cầu dưới 48 giờ, đánh dấu đối soát, ngừng bán sau startTime). Outbox order.paid/order.refunded/ticket.issued/ticket.cancelled đều PUBLISHED lên Kafka. Chưa thử API refund với VNPay sandbox thật. |
