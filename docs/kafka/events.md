# Hợp đồng Kafka EventHub

Cập nhật theo BC_32_V2 và xác nhận ngày 2026-10-27. Hợp đồng mục tiêu; repo chưa có producer/consumer nghiệp vụ. [Đối chiếu nguồn](../architecture/source-decisions.md), [điểm cần chốt C01–C09](../project-clarifications.md).

## 1. Envelope, ID và serialization

JSON UTF-8, envelope phẳng, không bọc payload. Tất cả message có:

| Field | Kiểu | Quy tắc |
|---|---|---|
| messageId | UUID string | Sinh một lần khi tạo outbox; retry/replay giữ nguyên. |
| eventType | string | Bằng tên topic, ví dụ order.paid. |
| occurredAt | UTC ISO-8601 instant | Thời điểm nghiệp vụ gốc, không là thời điểm retry. |
| eventId | UUID string | ID sự kiện mua vé/tham dự, không là message ID. |

orderId, refundId, customerId, ticketId, ticketTypeId, ownerUserId, gateId đều là UUID tham chiếu bảng. Không nhận số hoặc UUID sinh lại để né dedup. Mã provider, QR và templateCode là string nghiệp vụ, không ép UUID.

Java UUID/Instant/BigDecimal; tiền JSON number tối đa hai chữ số thập phân, quantity integer dương, tên vé VIP/PREMIUM/STANDARD snapshot. Trường bắt buộc nếu không ghi nullable/pending; mảng items/tickets không rỗng. Parse lỗi/field thiếu không được ACK như thành công.

Producer JsonSerializer đặt spring.json.add.type.headers=false. Consumer StringDeserializer + StringJsonMessageConverter lấy kiểu theo listener, không phụ thuộc tên package/class producer. Publisher DLQ raw JSON dùng StringSerializer để tránh encode JSON lần hai.

## 2. Topic và key

| Topic | Producer | Consumer giai đoạn hiện tại | Key |
|---|---|---|---|
| order.paid | Order | Analytics | orderId |
| order.failed | Order | Notification | orderId |
| order.refunded | Order | Notification, Analytics | orderId |
| ticket.issued | Ticket | Notification | orderId |
| ticket.checked-in | Ticket | Analytics | ticketId |
| ticket.checked-out | Ticket | Analytics | ticketId |
| ticket.cancelled | Ticket | Chưa đăng ký | orderId |
| notification.dlq | Notification | Vận hành/replay | Key gốc |
| analytics.dlq | Analytics | Vận hành/replay | Key gốc |

Key là UUID.toString(). Dev: ba partition/topic, replication factor một, group notification-service và analytics-service riêng. infrastructure/kafka/init-topics.sh tạo idempotent. Analytics chỉ tính bán từ order.paid, refund từ order.refunded; Notification không gửi thêm email từ ticket.cancelled để tránh thông báo refund trùng.

## 3. Payload nghiệp vụ

Các field dưới cộng với envelope ở §1.

### order.paid

orderId, customerId (UUID), currency (VND trong phạm vi V2), totalAmount (decimal), paidAt (instant), items[{ticketTypeId UUID, ticketTypeName, quantity, unitPrice, lineTotal}]. Tổng lineTotal = totalAmount khi không có phí/giảm giá; không tự bổ sung voucher/phí chưa chốt.

Phát sau Order PAID **và REST confirm đã phát hành vé thành công** (Hình 2.64). Không có consumer Ticket phát vé từ topic này. Confirm retry/outbox retry không tạo messageId mới cho cùng transition. paidAt giữ thời điểm thanh toán dù confirm hoàn thành muộn hơn.

~~~json
{
  "messageId": "10000000-0000-4000-8000-000000000001",
  "eventType": "order.paid",
  "occurredAt": "2026-10-27T03:00:00Z",
  "eventId": "30000000-0000-4000-8000-000000000001",
  "orderId": "20000000-0000-4000-8000-000000000001",
  "customerId": "40000000-0000-4000-8000-000000000001",
  "currency": "VND",
  "totalAmount": 1000000.00,
  "paidAt": "2026-10-27T03:00:00Z",
  "items": [{"ticketTypeId": "50000000-0000-4000-8000-000000000001", "ticketTypeName": "VIP", "quantity": 2, "unitPrice": 500000.00, "lineTotal": 1000000.00}]
}
~~~

### order.failed

orderId, customerId (UUID), customerEmail (email), reason (PAYMENT_FAILED|EXPIRED), totalAmount (decimal).

Chỉ producer thắng CAS FAILED/EXPIRED phát; webhook trùng/scheduler thua không phát lại. **C02 đang mở:** customerEmail chưa có nguồn xác minh/snapshot được duyệt. Chọn Keycloak chưa tự thêm email claim, email_verified hoặc cột customer_email. Không triển khai gửi thư bằng giá trị đoán hoặc email client chưa xác minh.

### order.refunded

orderId, refundId, customerId (UUID), customerEmail (C02), amount (decimal dương), refundedAt (instant), items[{ticketTypeId UUID, ticketTypeName, quantity}].

Phát sau provider xác nhận refund và Ticket cancel hoàn tất (Hình 2.67). Nếu provider thành công nhưng Ticket từ chối vĩnh viễn, chưa thể coi workflow hoàn thành: C05 cần quy tắc đối soát/bù trừ.

**Bổ sung kỹ thuật đề xuất:** currency và items[].refundedAmount, tổng phân bổ bằng amount. Payload I.D chỉ có quantity không đủ tính tiền refund từng hạng khi tỷ lệ/phí thay đổi. Đây là phụ thuộc C04/C09, chưa là field producer đã triển khai. Không tự chia đều, không dùng giá hiện tại và không tính refund như revenue âm của ngày mua.

### ticket.issued

orderId (UUID), eventTitle (snapshot string), eventStartTime (instant), venue (nullable, C03), ownerUserId (UUID), tickets[{ticketId UUID, ticketTypeId UUID, ticketTypeName, holderName, holderEmail nullable C02, qrCodeValue opaque}].

Sau transaction confirm tạo vé/QR commit (Hình 2.50–2.52). Event Client lấy title/startTime từ InternalEventResponse đã bổ sung title. venue chưa có nguồn trong từ điển V2, không tự phát dữ liệu giả.

owner_user_id của tickets là NOT NULL theo Bảng 2.9; ai là owner và có phải cùng người mua cho toàn message còn C02. Email holder được Order chuyển từ order_holders lúc confirm, cần giữ snapshot trong outbox để retry dù bảng tickets không có holder_email. Fallback holderEmail NULL chờ C02 trước khi hoàn tất consumer EMAIL. Đề xuất gom một email/địa chỉ với các QR và một IN_APP cho owner; không log QR/email trong lỗi.

### ticket.checked-in / ticket.checked-out

ticketId, ticketTypeId, gateId (UUID), scannedAt (instant).

Phát sau CAS trạng thái vé + ghi ticket_scans + outbox cùng transaction. gateId UUID đã chốt; nơi cấp và quyền thiết bị còn C07. Theo Hình 2.55–2.56 chỉ một lần vào/ra, không tái nhập. Cùng ticketId làm key **không** giữ thứ tự giữa hai topic.

### ticket.cancelled

orderId UUID, tickets[{ticketId UUID, ticketTypeId UUID}], cancelledAt instant. Phát sau transaction hủy vé commit; retry không hoàn quota hay phát sự kiện nghiệp vụ lần hai. Hai consumer mới chưa subscribe topic này.

## 4. Idempotency, transaction và ordering

- Ghi outbox trong cùng transaction với trạng thái nghiệp vụ. Outbox.id = payload.messageId. Publisher gửi có ACK, retry cùng message/key/payload. AFTER_COMMIT một mình vẫn mất event nếu process dừng sau commit.
- Analytics claim unique processed_events.id = messageId cùng transaction UPSERT/log/snapshot. Rollback cả claim khi xử lý lỗi; existsById trước khi cộng không chống hai worker chạy đồng thời.
- Notification dedup từng messageId:channel:recipient, ghi SENT trong transaction ngắn sau SMTP; processed chỉ hoàn tất sau mọi target. Có cửa sổ gửi trùng nếu crash sau SMTP trước markSent. Cần cơ chế provider idempotency nếu nghiệp vụ yêu cầu chặt hơn.
- Check-out có thể đến trước check-in. Với một lượt vào/ra: trạng thái đóng góp của một vé = 1 khi đã nhận CHECK_IN và chưa nhận CHECK_OUT; tổng checked-in tăng một lần mỗi vé. Cần khóa/serialize trạng thái vé và tính delta trước/sau, thay vì decrement + GREATEST(...,0). **V1 Analytics chưa đủ dữ liệu trạng thái này**; bổ sung migration/projection và test cả hai thứ tự trước triển khai. Không yêu cầu người dùng chọn thuật toán kỹ thuật.
- order.refunded có thể tới trước order.paid ở topic khác; cộng/trừ có tính giao hoán, không clamp tiền/số vé thuần về 0 vì sẽ mất điều chỉnh. C09 cần chốt ý nghĩa snapshot. Đề xuất báo cáo gộp + refund riêng, ngày Asia/Ho_Chi_Minh theo paidAt/refundedAt.
- Cùng một request confirm/refund/cancel lỗi tạm thời cần task retry bền vững. ACK webhook hoặc outbox không tự đảm bảo một lời gọi REST đã hoàn thành.

## 5. Retry và DLQ

Đề xuất DefaultErrorHandler retry lỗi DB/SMTP tạm thời ba lần với 1s/2s/4s. Parse/validation không thể tự hết lỗi đi DLQ. BusinessException không nên mặc định coi mọi loại là không retry: phân loại theo mã lỗi thực tế, lỗi phụ thuộc tạm thời vẫn retry.

DLQ giữ key và metadata topic/partition/offset/exception. Chỉ commit offset khi xử lý thành công hoặc publish DLQ có ACK; DLQ publish lỗi phải propagate để không mất message. Quá retry không đánh dấu processed như thành công.

Replay giữ nguyên messageId, sửa nguyên nhân trước, không đổi ID né dedup. Giới hạn quyền đọc topic/DLQ chứa PII/QR; không ghi payload nhạy cảm vào lastError.

## 6. Phần chưa thể triển khai trọn vẹn

C02 nguồn/fallback email + owner, C03 venue, C04 phân bổ tiền hoàn, C05/C06 lỗi điều phối/provider, C07 thiết bị cổng, C09 ý nghĩa chỉ số. Realm/JWK/issuer/audience/S2S chờ người phụ trách cấu hình như đã hẹn. Endpoint WS /ws/dashboard đã lấy từ báo cáo, không còn câu hỏi chọn lại path.

## 7. Local

~~~powershell
docker compose up -d kafka kafka-init kafka-ui mailpit
docker compose logs kafka-init
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe
~~~

Trong Docker kafka:9092; ngoài máy localhost:29092. Kafka UI http://localhost:8089, SMTP localhost:1025, Mailpit http://localhost:8025. [Docker.md](../../Docker.md).
