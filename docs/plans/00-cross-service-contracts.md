# Quy ước triển khai và hợp đồng phối hợp

Nguồn chung cho [sáu plan](README.md), cập nhật 2026-10-28. Các phương thức trong plan là hợp đồng Java đề xuất, không phải API đã triển khai. Phần business C02–C09 giữ trạng thái chờ chốt theo [danh sách](../project-clarifications.md).

## 1. Kiểu dữ liệu và phân lớp

- ID bảng ứng dụng: `UUID`; JPA `GenerationType.UUID` với `@JdbcTypeCode(SqlTypes.CHAR)`/`length=36`, cấu hình `preferred_uuid_jdbc_type=CHAR`; repository `JpaRepository<Entity, UUID>`. FK mềm cũng phải có CHAR mapping, không chỉ PK. Không `Long`, `IDENTITY`, số tự tăng hoặc UUID nhị phân cho ID.
- Tiền `BigDecimal`/`DECIMAL(14,2)`, thời điểm `Instant` UTC, ngày báo cáo `LocalDate`. Quy tắc gom ngày/refund theo C09; không tự chọn timezone hệ điều hành.
- `Actor(UUID userId, BusinessRole role)` là Principal đã xác minh; một role nghiệp vụ CUSTOMER/ORGANIZER/ADMIN. Tên role claim/mapping Keycloak còn cấu hình. `ServiceActor`/`GateActor` là identity riêng đã xác minh cho lời gọi nội bộ/thiết bị, không nhận từ body.
- Controller → service interface → ServiceImpl → repository. Service public trả DTO/record, không trả Entity; repository được trả Entity. Projection nhận Kafka record đúng hợp đồng. Client REST/Keycloak/Payment có interface để test và thay provider.
- `PageResponse<T>` thống nhất `items,page,size,totalElements,totalPages`, page từ 0, size 1–100 và sort ổn định thêm id. `findById`/`save` là hàm kế thừa repository; không khai báo lại nếu không thay hành vi.
- Quy ước page chỉ áp dụng endpoint đã có phân trang. `/tickets/me`, `/admin/refunds`, danh mục, type list và template list hiện trả array; không tự đổi sang page mà bỏ qua OpenAPI/client.
- Package `com.eventhub.<service>` theo SKILL. Test/Clock injectable để kiểm tra ranh giới TTL/48 giờ; không gọi now rải rác làm lệch deadline giữa hai service.
- `entity` chỉ chứa entity và mapped superclass; enum nằm trực tiếp ở `com.eventhub.<service>.common`. Không tạo entity/enums hoặc import enum từ service khác.

## 2. Quyền, transaction và cạnh tranh

Role được kiểm tra ở HTTP/STOMP; service vẫn kiểm tra owner. `Actor`/`ServiceActor` chỉ có sau xác thực, không có header userId tự khai. ADMIN được bỏ kiểm tra owner ở endpoint cho phép, nhưng vẫn xác minh event/order có tồn tại. Không gọi public Event detail để đọc sự kiện chưa PUBLISHED.

Method đọc DB dùng transaction read-only ngắn. Method ghi đơn service dùng một transaction; HTTP/SMTP/Keycloak/provider không giữ lock DB suốt thời gian chờ. Các bước có gọi mạng tách thành orchestrator không transaction lớn và helper transaction ở bean khác hoặc TransactionTemplate; tránh self-invocation khiến @Transactional không chạy.

Khóa nhiều ticket type/ticket theo thứ tự UUID ổn định. CAS phải check số dòng; bulk update có flush/clear để không đọc entity stale. Hai trạng thái lỗi owner/not-found/state phải được phân biệt theo hợp đồng API; không trả 500 cho race hợp lệ.

## 3. Bổ sung schema kỹ thuật cần thiết khi code

Các mục dưới **chưa có đủ trong DDL/migration hiện tại**. Lập migration tương ứng, cập nhật reference DDL rồi ERD trong cùng thay đổi; không sửa V1 đã áp dụng và không coi sơ đồ hiện tại đã có các cột này.

| Service | Bổ sung đề xuất | Mục đích |
|---|---|---|
| Ticket | `reservations.request_fingerprint`; khóa outbox/lease giao nhận nếu nhiều worker | Cùng orderId khác giỏ vé không bị coi là retry hợp lệ; publish có thể phục hồi |
| Order | `pending_operations`: id/aggregate_id/lease_token UUID, kind/status, payload JSON, attempts, next_attempt_at, lease_until, last_error, UNIQUE(aggregate_id,kind) | Lưu confirm/release/cancel/refund retry sau webhook đã ACK; task DONE không bị reset bởi webhook lặp |
| Order | Idempotency request/fingerprint cho tạo order và payment; snapshot email sau C02 | Retry không tạo đơn/giao dịch mới, không làm mất recipient |
| Notification | `notification_batches` id=messageId UUID, fingerprint/expected_count/status; notifications thêm lease_token UUID, lease_until | Tạo toàn bộ target nguyên tử, một worker gửi mỗi target tại một thời điểm |
| Analytics | `attendance_state`: id UUID, ticket_id UUID UNIQUE, event_id UUID, checked_in_at/checked_out_at nullable | Chịu đảo thứ tự giữa hai topic; không decrement rồi clamp |

Outbox Ticket/Order đã có reference DDL tối thiểu. Khi code nhiều publisher, bổ sung attempts/next_attempt_at/lease_token/lease_until hoặc cơ chế claim DB tương đương. `outbox.id = payload.messageId`; không sinh lại messageId khi retry. Không tạo outbox cho Event nếu chưa có topic/use case cần phát.

Nếu tiếp tục có DB model cũ ID số: lập bảng mapping UUID giữa các bảng/soft reference và migration có kiểm tra trước khi đổi mapping. Ép số thành chuỗi 36 ký tự không tạo UUID. Không reset dữ liệu như một bước mặc định của plan.

## 4. Outbox và task worker

`OutboxService.append(OutboxMessage)` chạy cùng transaction nghiệp vụ. `OutboxPublisher.publishDue(int limit)` lấy batch claim ngắn, gửi Kafka ngoài lock dài, `markPublished(UUID messageId, UUID leaseToken, Instant ackAt)` chỉ khi ACK. Publish xong rồi crash có thể publish lại, vì vậy consumer vẫn dedup. Lỗi gửi giữ PENDING/FAILED và lịch retry, không đánh dấu đã publish.

`OperationWorker.runDue(int limit)` claim task bằng lease có token UUID, gọi client ngoài DB transaction rồi hoàn tất bằng update có token. Các kind dự kiến `CONFIRM_RESERVATION`, `RELEASE_RESERVATION`, `CANCEL_TICKETS`, `REFUND_PAYMENT`; chi tiết refund/late-payment còn C04–C06. Mọi lời gọi dùng cùng aggregate/provider idempotency key của lần đầu. Timeout là kết quả chưa biết, không lập tức kết luận provider thất bại.

Scheduler lấy batch theo index status/deadline, không load tất cả bảng hoặc giữ transaction khi duyệt toàn bộ batch. Nhiều instance có thể chạy; `lease_until`, CAS và số dòng cập nhật là cơ chế bảo vệ, không dựa vào chỉ có một cron.

## 5. Những API nội bộ còn thiếu trước khi nối service

Đường dẫn dưới là **đề xuất bổ sung**, chưa có trong OpenAPI hiện tại. Chốt request/response/status/auth và thêm OpenAPI trước khi implement client/server. Không tự route các API này qua Gateway.

| Hợp đồng đề xuất | Caller → owner | Dữ liệu cần |
|---|---|---|
| `GET /internal/tickets/by-order/{orderId}/refund-eligibility` | Order → Ticket | tickets/order, status từng vé, eventId/typeId và thời điểm event hoặc dùng EventClient; phục vụ kiểm tra refund, không trả QR |
| `GET /internal/events/{eventId}/ticket-types/configuration` | Event → Ticket, chỉ nếu C08 bắt buộc trước submit/publish | Trạng thái cấu hình hạng/quota, không JOIN Ticket DB |
| Cơ chế giữ quyền hủy vé trước provider refund | Order ↔ Ticket, C05 | Nếu chọn hold/lock: token UUID, TTL/release/confirm và chặn scan; chưa có state/endpoint trong baseline |

Nếu giữ endpoint đã có để lấy cùng thông tin, phải chứng minh không dùng API của CUSTOMER hoặc giả user để đọc nội bộ. API nội bộ trả DTO tối thiểu, không lộ email/QR không cần thiết.

## 6. Kafka, dedup và ordering

[Payload nguồn](../kafka/events.md): producer Order/Ticket; Notification chỉ `ticket.issued/order.failed/order.refunded`, Analytics chỉ `order.paid/order.refunded/ticket.checked-in/ticket.checked-out`. Không phát vé bằng cả REST confirm lẫn consumer order.paid.

Projection: claim `processed_events.id=messageId` cùng transaction với SQL cộng/trừ/log. Unique conflict đúng claim message được bỏ qua sau rollback; không nuốt mọi DataIntegrityViolationException. Chỉ check exists trước xử lý không chống hai worker.

Notification tạo batch/targets trong một transaction, rồi lease từng delivery trước SMTP; processed chỉ khi toàn bộ target SENT. SMTP không có transaction chung với DB, crash sau gửi có thể gửi trùng. Không cam kết exactly-once bằng dedup DB đơn lẻ.

Cùng key chỉ giữ thứ tự trong một topic/partition. Attendance lưu bằng chứng mỗi vé: đóng góp hiện diện = có check-in và chưa có check-out; checkout đến trước không làm âm hoặc mất lần checkout. Sales/refund phải chịu đảo topic, không clamp điều chỉnh tiền về 0; ý nghĩa snapshot chờ C09.

## 7. Chuẩn lỗi và dependency

REST EventHub dùng `ErrorResponse(code,message,timestamp,path)`, kể cả entry point/access denied; không lộ exception/provider credential/payload QR. Provider webhook ACK và Keycloak OIDC là wire protocol riêng, adapter map đúng hợp đồng, không áp response ứng dụng vào provider tùy ý.

Khi triển khai mới thêm validation/security/resource-server, Flyway/MySQL và MapStruct thực sự cần; compiler processor Lombok/MapStruct/binding đi cùng. Ticket/Order thêm Kafka; Notification thêm mail/Mustache/ZXing; Analytics thêm WebSocket/cache/Caffeine. Test dùng JUnit/Mockito, Testcontainers MySQL/Kafka, GreenMail khi có luồng tương ứng. Không đổi phiên bản stack chỉ để viết plan.

## 8. Hoàn tất xuyên sáu service

- Đủ 52 operations hiện có được map trong sáu plan; API đề xuất thêm có nhãn riêng.
- Create event → cấu hình vé → reserve 3 phút → payment thành công → confirm/issue → email/in-app/sales; cùng UUID xuyên dịch vụ và messageId replay giữ nguyên.
- Retry/duplicate request, webhook và Kafka không phát vé, trả quota, hoàn tiền hoặc cộng projection hai lần. Test DB thật cho CAS/unique/UPSERT và worker chạy đồng thời.
- Kiểm tra timeout sau provider đã xử lý, restart sau ACK webhook, restart sau publish Kafka, lỗi DLQ publish; không dùng context test để kết luận các trường hợp này đã đúng.
- Test cổng với checkout Kafka đến trước checkin; refund chỉ publish khi các bước bắt buộc hoàn tất theo C05; các chỉ số/policy chưa chốt không được gán mặc định âm thầm.
- DDL, migration, entity, OpenAPI, Kafka, SVG/Mermaid/JPEG cập nhật cùng thay đổi. Các ảnh sinh từ DDL phải vượt kiểm tra renderer.
