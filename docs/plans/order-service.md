# Plan code order-service

Ngày 2026-10-28. Đọc [plan chung](00-cross-service-contracts.md), [OpenAPI Order](../api/order-service-openapi.yaml), [DDL](../database/order-service/init.sql), [ERD UUID](../database/order-service/erd.md), [Kafka](../kafka/events.md). Order điều phối tiền/đơn, không sở hữu quota/QR và không đọc Ticket DB.

Bổ sung quyết định 2026-11-05: C02 snapshot buyer email đã xác minh, owner=buyer; C04 hoàn cả đơn 70% giá mua, giữ 30%, ADMIN duyệt theo phương án giai đoạn đầu; C05 hold trước provider, verify UNKNOWN và bù lỗi phát vé 100%. [Thiết kế phục hồi](../architecture/payment-refund-recovery.md) thay mọi mô tả chờ C04/C05 trước đó. Provider C06 để sau. Cần migration snapshot email, quote/allocation, operation ledger, hold/fence trước bật luồng này.

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
| `OrderQueryService.getMine(Actor customer, Pageable pageable): Page<OrderResponse>` | CustomerId owner, sort mặc định createdAt/id qua @PageableDefault; map items/holders tránh N+1, read-only. Trả Page DTO, JSON content/page theo cấu hình via-dto chung. |
| `getDetail(Actor customer, UUID orderId): OrderResponse` | Order thuộc customer, trả snapshot names/prices/deadline đúng API; không tính lại giá hiện tại từ Ticket. |
| `OrderExpiryService.expire(UUID orderId, Instant now): boolean` | Lock/CAS PENDING_PAYMENT có expiresAt≤now → EXPIRED; ghi task RELEASE + outbox order.failed reason=EXPIRED cùng transaction. Không chạm PAID/FAILED/REFUNDED. |

IdempotencyContext cho POST /orders là bổ sung đề xuất, cần hợp đồng/fingerprint/storage trước triển khai. Retry giữ orderId, khác payload trả 409; không reserve bằng ID mới chỉ vì timeout. Holders count bằng quantity từng item. C02 owner=customerId, snapshot buyer email đã xác minh khi tạo đơn; holderEmail thiếu fallback buyer.

## 3. PaymentService và webhook

| Signature | Logic / ranh giới |
|---|---|
| `PaymentService.initiate(Actor customer, UUID orderId, String idempotencyKey, InitPaymentRequest request): PaymentResponse` | Owner, PENDING_PAYMENT chưa hết hạn; lưu/đọc Payment CREATED theo key, amount từ Order; gọi provider ngoài transaction → update PENDING/url/transactionId bằng điều kiện. Timeout truy vấn lại/retry cùng key, không tạo payment thứ hai. |
| `PaymentWebhookService.handle(String provider, RawWebhook request): ProviderWebhookAck` | Verify chữ ký trên raw bytes/query/form đúng provider trước → adapter normalize → đối chiếu transaction/order/amount/currency → helper transaction dedup/CAS/task/outbox → ACK đúng protocol. Wire contract chờ C06. |
| `WebhookTransactionService.applySuccess(VerifiedPaymentEvent event): WebhookOutcome` | Claim provider/eventId, lock Order/Payment; PENDING_PAYMENT→PAID, SUCCEEDED/paidAt và CONFIRM task cùng transaction trước ACK. Lặp không tạo hiệu ứng mới; EXPIRED/FAILED nhận tiền muộn giữ bằng chứng, query/fence Ticket rồi bù 100% thực thu chưa hoàn theo C05. |
| `applyFailure(VerifiedPaymentEvent event): WebhookOutcome` | Chỉ PENDING_PAYMENT→FAILED, Payment FAILED, ghi RELEASE task và order.failed PAYMENT_FAILED. Failure đến sau PAID/EXPIRED không đổi ngược trạng thái. |
| `FulfillmentService.confirmOrder(UUID orderId): FulfillmentOutcome` | Confirm cùng reservation/payload, success ghi DONE + outbox order.paid. Timeout query/retry; EXPIRED/RELEASED hoặc lỗi không thể phát vé phải fence Ticket trước operation bù 100%, không vừa phát vé vừa bù. |
| `releaseOrder(UUID orderId): void` | Worker gọi Ticket.release sau Order FAILED/EXPIRED, retry cùng reservationId; CONFIRMED 409 cần đối soát, không gọi cancel nếu chưa có refund hợp lệ. |

`RawWebhook` giữ raw body/headers/query để xác minh; không parse thành WebhookPayload JSON ứng dụng rồi mất dữ liệu chữ ký. `payload_hash` chỉ phát hiện cùng eventId khác payload, không xác thực provider. Claim webhook cùng transition/job trong một transaction; chỉ đánh PROCESSED khi đã ghi bền vững quyết định local, không đòi REST confirm đã xong để ACK.

`order.paid` chỉ phát sau Ticket đã phát vé. Tiền đã thu nhưng confirm pending vẫn giữ PAID theo báo cáo; trạng thái fulfillment kỹ thuật/task phải cho phép truy vết riêng, không phát order.paid sớm để làm analytics chạy. `paidAt` là thời điểm thu tiền, không thời điểm worker retry thành công.

## 4. RefundService và retry

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
4. RefundPolicy/review/hold/fence/worker theo C04/C05 đã chốt; chưa bật luồng tiền thật khi provider C06 và khả năng query/idempotency chưa có.

Test bắt buộc: price tamper; quantity/holders mismatch; reserve thành công rồi DB save lỗi; payment request lặp và timeout sau provider đã nhận; webhook duplicate/cùng key khác hash/sai chữ ký/sai amount; webhook đua expiry chỉ một nhánh thắng; crash sau PAID/task commit trước confirm; PAID nhưng reservation expired đi reconcile; confirm thành công rồi worker crash không thêm vé/order.paid; refund/reject/approve races; provider refund thành công nhưng cancel 409; hoàn lặp không gọi provider/cộng analytics hai lần. Chỉ business error đúng code mới là non-retryable; timeout/503 còn retry/verify.
