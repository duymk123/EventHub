# Plan code order-service

Ngày 2026-10-28. Đọc [plan chung](00-cross-service-contracts.md), [OpenAPI Order](../api/order-service-openapi.yaml), [DDL](../database/order-service/init.sql), [ERD UUID](../database/order-service/erd.md), [Kafka](../kafka/events.md). Order điều phối tiền/đơn, không sở hữu quota/QR và không đọc Ticket DB.

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
| `OrderService.create(Actor customer, CreateOrderRequest request, IdempotencyContext context): OrderResponse` | Validate type/quantity/holders; lấy customer từ Principal, sinh orderId UUID; Ticket.reserve trước → dùng giá/hạn server → lưu orders/items/holders transaction ngắn. Nếu lưu lỗi thì release bù trừ; fail-safe Ticket expiry khi không lưu được task do DB lỗi. |
| `OrderQueryService.getMine(Actor customer, Pageable page): PageResponse<OrderResponse>` | CustomerId owner, sort createdAt/id; map items/holders tránh N+1, read-only. |
| `getDetail(Actor customer, UUID orderId): OrderResponse` | Order thuộc customer, trả snapshot names/prices/deadline đúng API; không tính lại giá hiện tại từ Ticket. |
| `OrderExpiryService.expire(UUID orderId, Instant now): boolean` | Lock/CAS PENDING_PAYMENT có expiresAt≤now → EXPIRED; ghi task RELEASE + outbox order.failed reason=EXPIRED cùng transaction. Không chạm PAID/FAILED/REFUNDED. |

IdempotencyContext cho POST /orders là **bổ sung kỹ thuật đề xuất**, hiện OpenAPI chưa có header tạo order; bổ sung hợp đồng/fingerprint/storage trước khi triển khai. Một key retry phải giữ orderId cũ, payload khác trả 409; không gửi lại reserve với orderId mới chỉ vì HTTP timeout. Holders count phải bằng quantity theo từng item; email/owner mapping còn C02.

## 3. PaymentService và webhook

| Signature | Logic / ranh giới |
|---|---|
| `PaymentService.initiate(Actor customer, UUID orderId, String idempotencyKey, InitPaymentRequest request): PaymentResponse` | Owner, PENDING_PAYMENT chưa hết hạn; lưu/đọc Payment CREATED theo key, amount từ Order; gọi provider ngoài transaction → update PENDING/url/transactionId bằng điều kiện. Timeout truy vấn lại/retry cùng key, không tạo payment thứ hai. |
| `PaymentWebhookService.handle(String provider, RawWebhook request): ProviderWebhookAck` | Verify chữ ký trên raw bytes/query/form đúng provider trước → adapter normalize → đối chiếu transaction/order/amount/currency → helper transaction dedup/CAS/task/outbox → ACK đúng protocol. Wire contract chờ C06. |
| `WebhookTransactionService.applySuccess(VerifiedPaymentEvent event): WebhookOutcome` | Claim UNIQUE(provider,eventId), lock Order/Payment; PENDING_PAYMENT→PAID, Payment SUCCEEDED/paidAt, ghi CONFIRM task **cùng transaction trước ACK**. PAID/REFUNDED lặp không confirm/phát Kafka lần hai. EXPIRED/FAILED nhận tiền muộn giữ bằng chứng, chính sách hoàn/đối soát C05/C06. |
| `applyFailure(VerifiedPaymentEvent event): WebhookOutcome` | Chỉ PENDING_PAYMENT→FAILED, Payment FAILED, ghi RELEASE task và order.failed PAYMENT_FAILED. Failure đến sau PAID/EXPIRED không đổi ngược trạng thái. |
| `FulfillmentService.confirmOrder(UUID orderId): FulfillmentOutcome` | Worker đọc snapshot holders và reservationId, gọi Ticket.confirm; thành công ghi task DONE + outbox order.paid cùng transaction. Lỗi mạng retry; Reservation EXPIRED/RELEASED là lỗi vĩnh viễn chuyển đối soát theo C05, không retry mù. |
| `releaseOrder(UUID orderId): void` | Worker gọi Ticket.release sau Order FAILED/EXPIRED, retry cùng reservationId; CONFIRMED 409 cần đối soát, không gọi cancel nếu chưa có refund hợp lệ. |

`RawWebhook` giữ raw body/headers/query để xác minh; không parse thành WebhookPayload JSON ứng dụng rồi mất dữ liệu chữ ký. `payload_hash` chỉ phát hiện cùng eventId khác payload, không xác thực provider. Claim webhook cùng transition/job trong một transaction; chỉ đánh PROCESSED khi đã ghi bền vững quyết định local, không đòi REST confirm đã xong để ACK.

`order.paid` chỉ phát sau Ticket đã phát vé. Tiền đã thu nhưng confirm pending vẫn giữ PAID theo báo cáo; trạng thái fulfillment kỹ thuật/task phải cho phép truy vết riêng, không phát order.paid sớm để làm analytics chạy. `paidAt` là thời điểm thu tiền, không thời điểm worker retry thành công.

## 4. RefundService và retry

| Signature | Logic / ranh giới |
|---|---|
| `RefundService.request(Actor customer, UUID orderId, RefundRequest request): RefundResponse` | Owner/PAID, không có refund còn hiệu lực, Ticket eligibility chưa dùng và còn >=48h; amount do policy server tính từ snapshot sau C04. Lock Order khi tạo để chặn hai request cùng lúc; dữ liệu eligibility có race được C05 xử lý trước bật hoàn tiền. |
| `listForReview(Actor reviewer, RefundFilter filter): List<RefundResponse>` | Array theo API hiện tại. ADMIN/ORGANIZER, phạm vi ORGANIZER chờ C04, mặc định không cho xem mọi event. Query scope phải thể hiện quyền ngay trong query. |
| `approve(Actor reviewer, UUID refundId): RefundResponse` | Kiểm tra reviewer/sự kiện, REQUESTED và recheck >=48h/vé; chuyển APPROVED/PROCESSING + ghi REFUND task. Không giữ DB lock lúc gọi provider. |
| `reject(Actor reviewer, UUID refundId, RejectRefundRequest request): RefundResponse` | Chỉ REQUESTED→REJECTED, review_reason/reviewedAt cùng transaction; không sửa amount hoặc Order PAID. approvedBy chỉ ghi khi duyệt. |
| `RefundExecutionService.execute(UUID refundId): RefundExecutionOutcome` | Worker gọi provider refund bằng key ổn định; timeout VERIFY/PENDING, không kết luận thất bại; provider confirmed success ghi tiền đã hoàn và CANCEL task, provider permanent failure ghi FAILED nhưng không hủy vé. |
| `completeCancellation(UUID refundId): void` | Gọi Ticket.cancel cùng scope được C04 cho phép; sau cancel thành công ghi task DONE + order.refunded đúng một lần. Provider hoàn xong nhưng cancel bị từ chối xử lý theo C05, không báo workflow hoàn tất giả. |
| `RefundPolicy.evaluate(RefundContext context): RefundQuote` | Pure rule: điều kiện, phạm vi và amount/từng hạng từ snapshot; **chờ C04**, không mặc định 100%/không phí hoặc tự chia đều. |

API hiện chỉ có reason để xin refund, chưa chọn ticketIds. Nếu C04 chọn từng vé: bổ sung refund_items, request/eligibility/cancel scope, phân bổ tiền và Kafka trước khi code. `order.refunded.items[].refundedAmount` cần hợp đồng đủ để Analytics tổng hợp theo hạng; quantity đơn lẻ chưa đủ khi có phí/tỷ lệ.

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
| `PendingOperationRepository.enqueueIfAbsent(OperationCommand): UUID` | Unique aggregate/kind; trùng DONE không revive. UUID record và payload/fingerprint ổn định. |
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
| POST `/payments/webhook/{provider}` | handle |
| POST `/orders/{id}/refunds` | request |
| GET `/admin/refunds` | listForReview |
| POST `/admin/refunds/{id}/approve` | approve |
| POST `/admin/refunds/{id}/reject` | reject |

`TicketClient.reserve/confirm/release/cancelByOrder` dùng DTO theo Ticket OpenAPI; `getRefundEligibility` là contract cần thêm theo plan chung. `EventClient.getInternal` kiểm tra owner/startTime khi cần. `PaymentGatewayClient.createPayment/verifyPayment/refund/verifyRefund` và `WebhookVerifier.verify` tách adapter provider C06, mock không được làm implementation live mặc định.

`OrderExpiryScheduler.expireDue`, `OperationWorker.runDue`, `OutboxPublisher.publishDue` xử lý batch ngoài một transaction lớn. Response DTO: OrderResponse/OrderItemResponse/HolderResponse/PaymentResponse/RefundResponse; Order history trả page, list refunds trả array theo OpenAPI hiện hành. Nếu bổ sung phân trang refunds thì cập nhật contract trước code.

## 7. Kiểm tra và thứ tự code

1. Refactor entity + migration, đối chiếu DDL/metadata cho class/table mới để tránh quay lại Long/IDENTITY. Không tạo lại file test đã xóa theo yêu cầu chủ dự án.
2. Query/create/reserve + holders/fingerprint; fake Ticket client và test bù trừ; sau đó MySQL lock/unique.
3. Payment adapter/verifier khi C06 có câu trả lời; webhook local transaction/task/outbox; worker integration với Ticket.
4. RefundPolicy/review/worker sau C04/C05; không bật thu tiền/hoàn tiền thật để thử thiếu policy.

Test bắt buộc: price tamper; quantity/holders mismatch; reserve thành công rồi DB save lỗi; payment request lặp và timeout sau provider đã nhận; webhook duplicate/cùng key khác hash/sai chữ ký/sai amount; webhook đua expiry chỉ một nhánh thắng; crash sau PAID/task commit trước confirm; PAID nhưng reservation expired đi reconcile; confirm thành công rồi worker crash không thêm vé/order.paid; refund/reject/approve races; provider refund thành công nhưng cancel 409; hoàn lặp không gọi provider/cộng analytics hai lần. Chỉ business error đúng code mới là non-retryable; timeout/503 còn retry/verify.
