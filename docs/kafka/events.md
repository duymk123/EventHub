# Hợp đồng Kafka EventHub

Đọc sơ đồ publisher → topic → consumer và vị trí KafkaTemplate trong [luồng Kafka](flows.md).

Cập nhật theo BC_32_V2 và xác nhận ngày 2026-10-27. Consumer Notification/Analytics đã triển khai ngày 2026-11-05. Producer outbox của Ticket (`ticket.issued`, `ticket.checked-in/out`, `ticket.cancelled`) và Order (`order.failed` lý do EXPIRED) đã có code ngày 2026-11-19; `order.paid` có code ngày 2026-11-23 cùng luồng VNPay C06; `order.refunded` có code cùng ngày với hoàn tiền C05 rút gọn. Từ C18 (2026-11-23) Order không phát `order.failed` lý do PAYMENT_FAILED nữa (thanh toán lỗi không làm hỏng đơn); khách tự hủy đơn (C19) cũng không phát message.

Bổ sung 2026-11-22 theo [C12](../project-clarifications.md#c12--đặt-vé-theo-ghế-cụ-thể): bán vé theo ghế cụ thể, giữ ghế bằng REST đồng bộ. Không có topic mới; `ticket.issued` thêm `seatId`/`seatLabel`. Thiết kế giữ chỗ qua Kafka (C11, 2026-11-21) đã bị thay và không còn trong hợp đồng này. [Đối chiếu nguồn](../architecture/source-decisions.md), [quyết định C02–C12](../project-clarifications.md).

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

Producer gửi JSON UTF-8. Khi dùng JsonSerializer, đặt spring.json.add.type.headers=false. Consumer hiện dùng StringDeserializer và EventDecoder để parse JSON/validate theo topic, độc lập với tên package/class producer. Publisher DLQ raw JSON dùng StringSerializer để giữ payload JSON gốc.

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

Key là UUID.toString(). Dev: ba partition/topic, replication factor một, group notification-service và analytics-service riêng. infrastructure/kafka/init-topics.sh tạo idempotent; Analytics chỉ tính bán từ order.paid, refund từ order.refunded; Notification không gửi thêm email từ ticket.cancelled để tránh thông báo refund trùng.

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

orderId, customerId (UUID), customerEmail (email), reason (PAYMENT_FAILED|EXPIRED), totalAmount (decimal). Hiện chỉ phát EXPIRED (job hết hạn, tiền về sau hạn, hoặc Ticket báo reservation đã hết khi confirm); PAYMENT_FAILED giữ trong hợp đồng để consumer không phải đổi.

Chỉ producer thắng CAS FAILED/EXPIRED phát; webhook trùng/scheduler thua không phát lại. C02 chốt snapshot email buyer đã xác minh lúc tạo đơn; producer dùng snapshot cho customerEmail. Adapter/claim mapper và cột customer_email còn cần triển khai; không lấy email buyer tùy ý từ client hay lookup hồ sơ mới khi retry.

### order.refunded

orderId, refundId, customerId (UUID), customerEmail (snapshot C02), currency (VND), amount (decimal dương), refundedAt (instant), items[{ticketTypeId UUID, ticketTypeName, quantity, refundedAmount}].

Phát sau khi Ticket đã hủy vé, trả ghế (`ticket.cancelled`) **và** VNPay xác nhận hoàn tiền thành công (C05 rút gọn, 2026-11-22). C04 hoàn khách 70% giá mua nhưng quantity là tất cả vé của đơn. VNPay lỗi hoặc timeout thì refund FAILED, không phát event; ADMIN thử lại, chỉ lần thành công mới phát và giữ một messageId cho refund đó.

Consumer Analytics yêu cầu currency và items[].refundedAmount, tổng bằng amount. Order tính 70% line_total từng hạng theo snapshot, xử lý phần lẻ theo [C04](../project-clarifications.md), không chia đều hạng khác giá. samples/order.refunded.json minh họa đơn 1.000.000 hoàn 700.000 với quantity=2. Không dùng giá mới hoặc ghi refund như revenue âm của ngày mua.

### ticket.issued

orderId (UUID), customerEmail (snapshot email buyer đã xác minh), eventTitle (snapshot string), eventStartTime (instant), venue (text cấu hình C03 `141 Đường Chiến Thắng, Hà Nội`; nullable để tương thích message cũ), ownerUserId (UUID buyer), tickets[{ticketId UUID, ticketTypeId UUID, ticketTypeName, seatId UUID, seatLabel (ví dụ "B-07", C12), holderName, holderEmail nullable C02, qrCodeValue opaque}].

Sau transaction confirm tạo vé/QR commit. Event Client lấy title/startTime từ InternalEventResponse. C03 chốt một địa điểm: Notification dùng text cấu hình chung `eventhub.notification.venue-text` (biến EVENTHUB_VENUE_TEXT) khi venue trống, snapshot lúc tạo batch; chưa cần venue trong Event DB. venue nullable giữ tương thích message cũ; thiếu cấu hình thật không dùng địa chỉ giả.

C02 mới nhất chốt owner_user_id=customerId buyer. Mỗi vé ưu tiên holderEmail, thiếu fallback buyer customerEmail; gom cùng địa chỉ, mỗi email chỉ nhận QR nhóm vé gửi tới mình. Một IN_APP thuộc buyer, không suy tài khoản holder từ email. Order chuyển email vào confirm/outbox để retry không tra dữ liệu mới. Đã có code ngày 2026-11-23: batch gồm một IN_APP và một EMAIL cho mỗi địa chỉ (`expected_count` = 1 + số địa chỉ), mỗi email chỉ nhúng QR của vé gửi tới địa chỉ đó; email in giờ Việt Nam, địa điểm (C03) và "Khu … · Ghế …" (C12). Không log QR/email trong lỗi.

### ticket.checked-in / ticket.checked-out

ticketId, ticketTypeId, gateId (UUID), scannedAt (instant).

Phát sau CAS trạng thái vé + ghi ticket_scans + outbox cùng transaction. gateId UUID đã chốt; C07 quét bằng JWT ORGANIZER/ADMIN trong khung giờ sự kiện, không có bảng thiết bị. Theo Hình 2.55–2.56 chỉ một lần vào/ra, không tái nhập. Cùng ticketId làm key **không** giữ thứ tự giữa hai topic.

### ticket.cancelled

orderId UUID, tickets[{ticketId UUID, ticketTypeId UUID}], cancelledAt instant. Phát sau transaction hủy vé commit; retry không hoàn quota hay phát sự kiện nghiệp vụ lần hai. Hai consumer mới chưa subscribe topic này.

## 4. Idempotency, transaction và ordering

- Ghi outbox trong cùng transaction với trạng thái nghiệp vụ. Outbox.id = payload.messageId. Publisher gửi có ACK, retry cùng message/key/payload. AFTER_COMMIT một mình vẫn mất event nếu process dừng sau commit.
- Analytics claim unique processed_events.id = messageId cùng transaction UPSERT/log/snapshot. Rollback cả claim khi xử lý lỗi; existsById trước khi cộng không chống hai worker chạy đồng thời.
- Notification dedup từng messageId:channel:recipient, ghi SENT trong transaction ngắn sau SMTP; processed chỉ hoàn tất sau mọi target. Có cửa sổ gửi trùng nếu crash sau SMTP trước markSent. Cần cơ chế provider idempotency nếu nghiệp vụ yêu cầu chặt hơn.
- Check-out có thể đến trước check-in. Với một lượt vào/ra: trạng thái đóng góp của một vé = 1 khi đã nhận CHECK_IN và chưa nhận CHECK_OUT; tổng checked-in tăng một lần mỗi vé. Cần khóa/serialize trạng thái vé và tính delta trước/sau, thay vì decrement + GREATEST(...,0). Analytics V2 đã có attendance_state và projection lưu cả hai bằng chứng, đã kiểm tra hai thứ tự. Không yêu cầu người dùng chọn thuật toán kỹ thuật.
- order.refunded có thể tới trước order.paid ở topic khác; cộng/trừ có tính giao hoán, không clamp tiền/số vé thuần về 0 vì sẽ mất điều chỉnh. Xác nhận 2026-11-05 dashboard gộp toàn sự kiện, báo cáo gộp + refund riêng theo ngày Asia/Ho_Chi_Minh từ paidAt/refundedAt.
- Cùng một request confirm/refund/cancel lỗi tạm thời cần task retry bền vững. ACK webhook hoặc outbox không tự đảm bảo một lời gọi REST đã hoàn thành.

## 5. Retry và DLQ

Đề xuất DefaultErrorHandler retry lỗi DB/SMTP tạm thời ba lần với 1s/2s/4s. Parse/validation không thể tự hết lỗi đi DLQ. BusinessException không nên mặc định coi mọi loại là không retry: phân loại theo mã lỗi thực tế, lỗi phụ thuộc tạm thời vẫn retry.

DLQ giữ key và metadata topic/partition/offset/exception. Chỉ commit offset khi xử lý thành công hoặc publish DLQ có ACK; DLQ publish lỗi phải propagate để không mất message. Quá retry không đánh dấu processed như thành công.

Replay giữ nguyên messageId, sửa nguyên nhân trước, không đổi ID né dedup. Giới hạn quyền đọc topic/DLQ chứa PII/QR; không ghi payload nhạy cảm vào lastError.

## 6. Quyết định và phần tích hợp còn thiếu

C02–C05/C08/C12 đã có quyết định trong [project-clarifications](../project-clarifications.md), C09 giữ xác nhận trước đó. C06 chọn VNPay sandbox, C07 quét bằng JWT ORGANIZER/ADMIN (2026-11-22). Cần triển khai snapshot email, target holder, text địa điểm, quote 70%; realm/JWK/issuer/audience/S2S vẫn cần cấu hình.

C12 cần triển khai: Ticket thêm `seatId`/`seatLabel` vào payload `ticket.issued`; Notification in số ghế trong email QR (field mới là tùy chọn với consumer hiện tại, message cũ không có ghế vẫn đọc được). Chi tiết ở [plan Ticket](../plans/ticket-service.md).

Message `payment.compensated` (hoàn bù 100% khi thu tiền nhưng không phát được vé) là **hướng phát triển** theo thiết kế C05 đầy đủ, không nằm trong phạm vi giai đoạn này; topic/producer/consumer chưa có.

## 7. Local

~~~powershell
docker compose up -d kafka kafka-init kafka-ui mailpit
docker compose logs kafka-init
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe
~~~

Trong Docker kafka:9092; ngoài máy localhost:29092. Kafka UI http://localhost:8089, SMTP localhost:1025, Mailpit http://localhost:8025. [Docker.md](../../Docker.md).
