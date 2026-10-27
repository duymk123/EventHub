# Plan code notification-service

Ngày 2026-10-28. Đọc [plan chung](00-cross-service-contracts.md), [OpenAPI](../api/notification-service-openapi.yaml), [DDL/V1](../database/notification-service/init.sql), [ERD](../database/notification-service/erd.md), [payload Kafka](../kafka/events.md). Hiện có Application, Kafka/mail config và migration nền; chưa có delivery/API nghiệp vụ.

## 1. Entity và migration

V1: NotificationTemplate(id UUID, code, channel, subject, bodyTemplate, updatedAt), Notification(id UUID, dedupKey UNIQUE, sourceMessageId UUID, recipientUserId UUID nullable, recipientEmail nullable, channel/templateCode/title/content/payload/status/attempts/error/time), ProcessedEvent(id=messageId UUID, processedAt).

Đã code ba entity tại [entity](../../notification-service/src/main/java/com/eventhub/notification/entity), cùng NotificationChannel/NotificationStatus. Theo xác nhận mới nhất, giữ schema V1 và bỏ qua BaseEntity/audit columns. UUID lưu CHAR(36), enum ép VARCHAR để khớp V1, payload Map dùng JSON. Hibernate tự gán Notification.createdAt/ProcessedEvent.processedAt khi insert và Template.updatedAt khi insert/update; native SQL không đi qua lifecycle nên phải tự truyền timestamp.

Chỉ EMAIL/IN_APP giai đoạn này. Template UNIQUE(code,channel), không dùng code làm PK. IN_APP phải có recipientUserId và title/content đã render; EMAIL phải có recipientEmail được quyết định ở C02. Payload có thể chứa QR/email nên không trả vào REST in-app và không log.

**V2 đề xuất khi triển khai delivery:** notification_batches(id=messageId UUID, expectedCount, targetFingerprint, status, createdAt) và các cột leaseToken UUID/leaseUntil của notifications. Không sửa V1 đã áp dụng. Batch tạo toàn bộ targets một transaction để worker không đánh processed trước khi target cuối được tạo. Template seed TICKET_ISSUED/ORDER_FAILED/ORDER_REFUNDED cho EMAIL/IN_APP đi migration riêng với UUID cố định, không dữ liệu người thật.

## 2. Service API/query

| Interface / signature | Hành vi |
|---|---|
| `NotificationService.getMine(Actor actor, boolean unreadOnly, Pageable page): PageResponse<NotificationResponse>` | Chỉ IN_APP của Principal; read-only, sort createdAt/id giảm dần. Không trả delivery EMAIL hoặc payload/QR. |
| `markRead(Actor actor, UUID notificationId): void` | UPDATE id+recipientUserId+IN_APP WHERE read_at IS NULL. Đã đọc vẫn 204, không đổi mốc đầu. Không tồn tại/thuộc người khác/EMAIL đều 404. |
| `countUnread(Actor actor): UnreadCountResponse` | COUNT theo user+IN_APP+read_at NULL, cùng semantics GET page. |
| `TemplateService.upsert(Actor admin, String code, TemplateRequest request): TemplateResponse` | ADMIN, code pattern/max length, channel EMAIL/IN_APP; upsert (code,channel), giữ id khi cập nhật, render kiểm tra cú pháp. Transaction ghi ngắn. |
| `get(Actor admin, String code, NotificationChannel channel): TemplateResponse` | Query unique code/channel, 404 nếu thiếu, read-only. |
| `list(Actor admin): List<TemplateResponse>` | Sort code/channel, chỉ ADMIN, read-only. |

markRead update trả 0 chưa phân biệt đã đọc và không thuộc user: cần query tồn tại id+recipient+channel sau update để trả đúng 204/404, hoặc lock/read rồi update trong transaction. Không trả 404 cho mọi update=0 vì lần đọc thứ hai là hợp lệ.

Upsert khi hai request cùng code/channel: UNIQUE chặn insert trùng; đọc lại sau transaction thua hoặc dùng native atomic upsert. Không generate UUID mới rồi ghi đè id của bản đã có. Chỉnh template không render lại thông báo cũ.

## 3. Service Kafka và delivery

| Interface / signature | Logic / transaction |
|---|---|
| `NotificationProcessingService.handleTicketIssued(TicketIssuedEvent event): void` | Validate envelope/tickets → RecipientResolver → model QR/event snapshot → prepareBatch → gửi targets → hoàn batch. Source email/owner và venue phụ thuộc C02/C03. |
| `handleOrderFailed(OrderFailedEvent event): void` | Validate reason PAYMENT_FAILED/EXPIRED, customer identity/email snapshot; chọn ORDER_FAILED, một EMAIL/IN_APP theo policy recipient. Không tự lookup bằng quyền admin chưa cấp. |
| `handleOrderRefunded(OrderRefundedEvent event): void` | Tương tự ORDER_REFUNDED, amount/currency snapshot; không consume ticket.cancelled để gửi thêm thư cùng refund. |
| `NotificationBatchService.prepareBatch(NotificationCommand command): BatchWork` | Một transaction: claim/đọc batch, so fingerprint, tạo toàn bộ Notification snapshots/dedup keys. Cùng messageId khác targets/payload phải báo conflict, không im lặng thay recipient. |
| `NotificationDeliveryService.deliver(UUID notificationId): DeliveryOutcome` | Orchestrator: claim lease transaction ngắn → SMTP ngoài transaction → markSent/markFailed transaction ngắn, sử dụng lease token. IN_APP không cần SMTP, có thể ghi SENT khi chuẩn bị batch. |
| `DeliveryLeaseService.claim(UUID id, UUID leaseToken, Instant now, Instant leaseUntil): Optional<DeliveryWork>` | Chỉ PENDING/FAILED, chưa có lease còn hạn; attempts tăng khi bắt đầu attempt. SENT không được claim lại. |
| `markSent(UUID id, UUID leaseToken, Instant at): boolean` | UPDATE đúng id+leaseToken, lưu sentAt và bỏ lease. Worker mất lease không ghi đè kết quả worker mới. |
| `markFailed(UUID id, UUID leaseToken, String safeError): boolean` | Ghi FAILED/error đã lọc và bỏ lease; không ghi token/QR/email nguyên văn vào lỗi. |
| `NotificationBatchService.completeIfAllSent(UUID messageId): boolean` | Một transaction: batch đã materialize, count target khớp expectedCount và tất cả SENT → ghi processed_events + COMPLETE. Chưa đủ thì false, không processed trong finally. |
| `RecipientResolver.resolve(NotificationEvent event): List<NotificationTarget>` | Pure mapping theo C02; gom theo địa chỉ người nhận và owner, normalization nhất quán cho dedup. Không âm thầm bỏ target email NULL trước khi có fallback được chốt. |
| `TemplateRenderer.render(TemplateSnapshot template, Map<String,Object> model): RenderedMessage` | Mustache escape mặc định; không eval expression, không chèn raw HTML từ dữ liệu người dùng. Subject/body render một lần khi tạo notification. |
| `QrCodeGenerator.generatePng(String qrCodeValue, int size): byte[]` | ZXing QRCodeWriter; không sinh token QR mới, PNG lấy từ opaque token Ticket đã cấp. |
| `EmailSender.send(EmailMessage message): SendReceipt` | JavaMailSender/MimeMessageHelper, HTML + InlineImage cid; có SMTP timeout, receipt không chứng minh người dùng đã đọc. |

DTO nội bộ: NotificationCommand/NotificationTarget/BatchWork/DeliveryWork/TemplateSnapshot/RenderedMessage/EmailMessage/InlineImage. Tất cả batch/notification/sourceMessage/user/lease ID là UUID; template code/email là string nghiệp vụ. Service interfaces không trả Entity.

## 4. Repository methods

| Repository / signature | Mục đích |
|---|---|
| `NotificationRepository.findByRecipientUserIdAndChannel(UUID, NotificationChannel, Pageable): Page<Notification>` | GET me, index user/channel/read/created. |
| `findByRecipientUserIdAndChannelAndReadAtIsNull(UUID, NotificationChannel, Pageable): Page<Notification>` | unreadOnly đúng semantics. |
| `findByIdAndRecipientUserIdAndChannel(UUID, UUID, NotificationChannel): Optional<Notification>` | Ownership và phân biệt read lặp với 404. |
| `markReadIfUnread(UUID id, UUID userId, Instant at): int` | SQL user+IN_APP+read_at NULL; không đổi readAt cũ. |
| `countByRecipientUserIdAndChannelAndReadAtIsNull(UUID, NotificationChannel): long` | Đếm, long là số lượng chứ không là kiểu ID. |
| `findByDedupKey(String): Optional<Notification>` | Dedup messageId:channel:recipient; unique DB là chốt cuối. |
| `findBySourceMessageIdOrderByIdAsc(UUID): List<Notification>` | Delivery work của một batch đã chuẩn bị. |
| `claimDelivery(UUID id, UUID leaseToken, Instant now, Instant until): int` | Conditional update status/lease/attempt; không giữ row lock khi gửi SMTP. |
| `markSent/markFailed(UUID id, UUID leaseToken, ...): int` | Update có fencing token; không ghi từ worker đã hết lease. |
| `countBySourceMessageIdAndStatusNot(UUID, NotificationStatus): long` | Kiểm tra tất cả target thành công; thêm count tổng so expectedCount. |
| `NotificationTemplateRepository.findByCodeAndChannel(String, NotificationChannel): Optional<NotificationTemplate>` | UNIQUE code/channel. |
| `findAllByOrderByCodeAscChannelAsc(): List<NotificationTemplate>` | Admin list. |
| `NotificationBatchRepository.findByIdForUpdate(UUID messageId): Optional<NotificationBatch>` | Sau V2, hoàn batch/processed không đua. |
| `createIfAbsent(BatchRecord command): void` | Unique messageId, cả targets tạo cùng transaction; compare target fingerprint. |
| `ProcessedEventRepository.findById/existsById(UUID)` | Chỉ fast path khi batch đã hoàn; không là lock chống cạnh tranh. |
| `persistCompletion(UUID messageId, Instant at): void` | Unique completion trong transaction completeIfAllSent, không dùng exists rồi send như guarantee. |

`save/saveAll` kế thừa cho entities. Nếu transaction insert dedup/batch thua unique, rollback rồi đọc lại bằng transaction mới; không query tiếp trong transaction rollback-only. Các custom methods claim/complete viết rõ JPQL/native và số dòng cập nhật.

## 5. API → service và Kafka config

| Operation | Method |
|---|---|
| GET `/api/v1/notifications/me` | getMine |
| GET `/api/v1/notifications/me/unread-count` | countUnread |
| PUT `/api/v1/notifications/{id}/read` | markRead |
| GET `/api/v1/admin/notification-templates` | TemplateService.list |
| GET `/api/v1/admin/notification-templates/{code}?channel=` | get |
| PUT `/api/v1/admin/notification-templates/{code}` | upsert |

TicketEventConsumer chỉ gọi handleTicketIssued; OrderEventConsumer gọi hai handler order. StringDeserializer + StringJsonMessageConverter, group notification-service, ack record. DefaultErrorHandler phân loại parse/validation permanent với SMTP/DB transient; backoff đề xuất 1/2/4 giây rồi notification.dlq. Recoverer chỉ ACK khi DLQ publish thành công.

Nếu target đang được worker khác lease, không coi message đã xong; retry/wait theo cơ chế Kafka phù hợp, tránh tight loop hoặc đánh processed. Lease dài hơn timeout SMTP có biên an toàn hoặc có heartbeat; vẫn có cửa sổ duplicate khi crash sau SMTP trước markSent. V2 lease giảm concurrent send, không bảo đảm exactly-once.

## 6. Kiểm tra và các bước code

1. Map đầy đủ V1 với UUID/JSON field, repository/query/API/security và mapper; test owner/admin/read idempotent/page/unread count.
2. Template/renderer/QR/mail interface, seed template version tiếp theo; test escape/Mustache syntax và MIME cid với GreenMail.
3. V2 batch/lease và dịch vụ transaction ngắn; test hai worker cùng message/target, lặp SENT skip, target thất bại không gửi lại target đã SENT, lease stale không overwrite.
4. Kafka/DLQ + Testcontainers MySQL/Kafka, malformed payload, SMTP timeout, DLQ publish fail, replay giữ messageId. Không viết test khẳng định một crash luôn không gửi trùng email.

ErrorCode API: NOTIFICATION_NOT_FOUND/TEMPLATE_NOT_FOUND 404, FORBIDDEN 403, VALIDATION_ERROR 400; lỗi delivery phân loại retry riêng, không expose toàn bộ SMTP exception. C02/C03 chặn phần dựng target/email đầy đủ, không chặn in-app CRUD/template và bộ khung delivery bằng fixture được ghi rõ.
