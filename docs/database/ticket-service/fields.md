# Giải thích các trường database Ticket Service

Database mặc định: `eventhub_ticket`. Ticket quản lý hạng vé và số lượng còn bán, giữ chỗ trong lúc thanh toán, phát vé QR và ghi nhận vào/ra. “Hạng vé”, “giữ chỗ” và “vé đã phát hành” là ba đối tượng khác nhau.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), [entity hiện có](../../../ticket-service/src/main/java/com/eventhub/ticket/entity), [enum](../../../ticket-service/src/main/java/com/eventhub/ticket/common), [plan Ticket](../../plans/ticket-service.md), [Kafka](../../kafka/events.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong). Kiểu/NULL/DEFAULT trong bảng dưới lấy từ DDL tham chiếu; entity có mặt không đồng nghĩa toàn bộ luồng đã chạy. Cấu hình hiện dùng Hibernate update; tài liệu này không kiểm tra database đang chạy.

## 1. `ticket_types` — Hạng vé và tồn kho của một sự kiện

Một dòng là một hạng vé như VIP của một sự kiện, không phải một tấm vé. VIP của hai sự kiện khác nhau là hai dòng khác nhau.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID của hạng vé cụ thể; Order dùng ID này để yêu cầu giữ số lượng. | UUID hạng VIP sự kiện E |
| `event_id` | CHAR(36), bắt buộc | Sự kiện mà hạng vé thuộc về. ID mềm sang Event, không phải FK vật lý. | UUID sự kiện E |
| `name` | VARCHAR(100), bắt buộc | Tên hạng; chỉ VIP, PREMIUM, STANDARD theo CHECK của DDL và enum. | `VIP` |
| `price` | DECIMAL(14,2), bắt buộc | Giá bán hiện tại của một vé thuộc hạng này, không âm. Giá đã mua phải được chụp lại ở reservation/order để sau này đổi giá không sửa lịch sử. | `500000.00` |
| `total_quota` | INT, bắt buộc | Tổng số vé được phân bổ cho hạng này. Không phải tổng vé đã bán. | `100` |
| `available_quantity` | INT, bắt buộc | Số vé còn có thể nhận giữ chỗ mới. Khi giữ chỗ thành công, số này giảm. | `80` |
| `reserved_quantity` | INT, bắt buộc, DEFAULT 0 | Số vé đang được giữ tạm cho các đơn chưa hoàn tất; khách khác chưa được mua số lượng này. | `5` |
| `sold_quantity` | INT, bắt buộc, DEFAULT 0 | Số lượng đã chuyển sang bán thành công khi xác nhận reservation. Không phải lượt check-in. | `15` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT ACTIVE | Cho phép nhận bán/giữ chỗ mới hay đang tắt bán; enum code có ACTIVE và INACTIVE. DDL chưa có CHECK danh sách status này. | `ACTIVE` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo hạng vé; entity dùng @CreationTimestamp. | `2026-10-29T02:00:00Z` |

UNIQUE `(event_id, name)` ngăn tạo hai hạng VIP trong cùng sự kiện. DDL yêu cầu `total_quota = available_quantity + reserved_quantity + sold_quantity` và ba lượng không âm. Không có bảng ticket_inventory riêng.

Ví dụ tổng quota 100, đang còn 80, giữ 5, bán 15. Khách đặt thêm hai vé: `available=78`, `reserved=7`, `sold=15`. Thanh toán và confirm hai vé đó: `available=78`, `reserved=5`, `sold=17`. Nếu hết hạn trước confirm: thay vì bán, trả hai vé từ reserved về available. Các thay đổi cần được thực hiện nguyên tử trong transaction, không lấy số từ client.

## 2. `reservations` — Một lần giữ chỗ cho đơn hàng

Giữ chỗ là lời hứa giữ số lượng trong ba phút cho Order thanh toán, chưa phải vé để quét QR.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID giữ chỗ; Order lưu lại trong orders.reservation_id để confirm/release. | UUID giữ chỗ R |
| `order_id` | CHAR(36), bắt buộc, UNIQUE | Đơn hàng yêu cầu giữ chỗ. ID mềm sang Order; UNIQUE đảm bảo một đơn không tạo nhiều reservation khi gọi lại. | UUID đơn O |
| `status` | VARCHAR(20), bắt buộc, DEFAULT RESERVED | Tình trạng giữ chỗ: RESERVED đang giữ; CONFIRMED đã xác nhận/phát vé; RELEASED đã trả giữ chỗ; EXPIRED đã hết hạn. | `RESERVED` |
| `expires_at` | TIMESTAMP, bắt buộc | Hạn cuối còn được giữ vé. Luồng reserve đặt hạn ba phút; worker tìm reservation quá hạn theo trường này. | Tạo 03:00 UTC, hết hạn `2026-10-29T03:03:00Z` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo lần giữ chỗ. Không phải thời điểm khách trả tiền. | `2026-10-29T03:00:00Z` |
| `confirmed_at` | TIMESTAMP, được NULL | Thời điểm chuyển sang CONFIRMED. Còn đang chờ thì NULL. | `2026-10-29T03:01:00Z` |
| `released_at` | TIMESTAMP, được NULL | Mốc ghi nhận giải phóng giữ chỗ khi luồng trả giữ chỗ thực hiện. Xem status để biết RELEASED hay EXPIRED; không dùng timestamp này thay status. | `2026-10-29T03:02:00Z`, hoặc NULL |

RESERVED chỉ kết thúc bằng CONFIRMED, RELEASED hoặc EXPIRED. Không release một reservation đã CONFIRMED. Gọi release/expire lần nữa không được cộng lại vé vào available lần hai. Chỉ mục `(status, expires_at)` giúp tìm các giữ chỗ cần xử lý hết hạn.

## 3. `reservation_items` — Số lượng giữ theo từng hạng

Một reservation có thể giữ VIP hai vé và STANDARD một vé, tương ứng hai dòng ở bảng này.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID dòng chi tiết giữ chỗ. | UUID dòng VIP trong R |
| `reservation_id` | CHAR(36), bắt buộc, FK | Giữ chỗ cha, trỏ reservations.id trong cùng DB. | UUID R |
| `ticket_type_id` | CHAR(36), bắt buộc, FK | Hạng vé đang được giữ, trỏ ticket_types.id. | UUID hạng VIP |
| `quantity` | INT, bắt buộc, > 0 | Số vé giữ của hạng này; chưa phải số vé của tất cả các hạng. | `2` |
| `unit_price` | DECIMAL(14,2), bắt buộc, >= 0 | Giá một vé được chốt tại lúc giữ chỗ, giúp Order không dùng giá client hoặc giá đổi về sau. | `500000.00` |

UNIQUE `(reservation_id, ticket_type_id)` đảm bảo một hạng chỉ có một dòng trong một lần giữ chỗ. Tổng tiền minh họa của dòng = quantity × unit_price, nhưng bảng này không có cột line_total.

## 4. `tickets` — Từng vé đã phát hành

Mua hai vé VIP tạo hai dòng tickets, mỗi dòng có ID và QR riêng. Quantity không xuất hiện ở đây vì một dòng đã là một vé.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID tấm vé, dùng API tra cứu và các message liên quan vé. Không phải chính QR. | UUID vé T1 |
| `reservation_id` | CHAR(36), bắt buộc, FK | Lần giữ chỗ được xác nhận để sinh ra vé, trỏ reservations.id. | UUID R |
| `order_id` | CHAR(36), bắt buộc | Đơn đã mua vé; ID mềm sang Order. Hỗ trợ tra vé theo đơn hoặc hủy theo đơn. | UUID O |
| `ticket_type_id` | CHAR(36), bắt buộc, FK | Hạng của vé, trỏ ticket_types.id. | UUID hạng VIP |
| `owner_user_id` | CHAR(36), bắt buộc | Tài khoản có quyền sở hữu/xem vé trong hệ thống; khác tên người đi sự kiện. Việc owner là người mua hay người nhận còn C02. | UUID tài khoản U |
| `holder_name` | VARCHAR(100), được NULL | Tên người sử dụng vé tại sự kiện, lấy từ thông tin holder theo luồng confirm. Không tự là tài khoản đăng nhập. | `Nguyễn Lan` |
| `qr_code_value` | VARCHAR(255), bắt buộc, UNIQUE | Chuỗi bí mật/opaque dùng làm nội dung QR và kiểm tra khi quét. Không lấy ticketId làm QR thay thế. | `<chuỗi-QR-minh-họa>` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT ISSUED | Trạng thái vé: ISSUED đã phát; CHECKED_IN đã vào; CHECKED_OUT đã ra; CANCELLED đã hủy. | `CHECKED_IN` |
| `issued_at` | TIMESTAMP, bắt buộc | Thời điểm phát hành vé khi confirm thành công, không phải lúc tạo reservation. | `2026-10-29T03:01:00Z` |
| `cancelled_at` | TIMESTAMP, được NULL | Thời điểm hủy vé nếu đã chuyển CANCELLED; bình thường NULL. | NULL khi vé đang ISSUED |

Luồng vào/ra V2: ISSUED → CHECKED_IN → CHECKED_OUT, không tái nhập. Nhánh hủy: ISSUED → CANCELLED theo điều kiện nghiệp vụ. Chỉ mục owner/issued_at phục vụ danh sách vé; chỉ mục order_id phục vụ tìm vé theo đơn. Bảng tickets hiện không lưu holder_email; không suy ra rằng có thể lấy email từ vé mà không giữ snapshot/nguồn phù hợp.

## 5. `ticket_scans` — Ghi nhận từng lần vào/ra hợp lệ

Một dòng cho một lần chuyển trạng thái vào/ra hợp lệ. DDL không có trường lưu lỗi của mọi lần quét QR sai.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID bản ghi quét, khác ID vé được quét. | UUID lượt quét S |
| `ticket_id` | CHAR(36), bắt buộc, FK | Vé được quét, trỏ tickets.id. | UUID T1 |
| `gate_id` | CHAR(36), bắt buộc | Mã cổng/thiết bị thực hiện quét. Không có bảng gates/FK tương ứng trong DDL; chủ sở hữu và quyền thiết bị còn C07. | UUID cổng A |
| `scan_type` | VARCHAR(10), bắt buộc | Loại hành động: CHECK_IN vào, CHECK_OUT ra; được CHECK trong DDL. | `CHECK_OUT` |
| `scanned_at` | TIMESTAMP, bắt buộc | Thời điểm quét được ghi nhận trong nghiệp vụ. Kafka và Analytics dùng thời điểm này để phản ánh hành động gốc. | `2026-11-15T14:00:00Z` |

Vé có status là trạng thái hiện tại; ticket_scans là lịch sử đã xảy ra. Cập nhật trạng thái và thêm log phải cùng transaction. Không có cột checked_in_at trực tiếp trên tickets trong DDL này.

## 6. `outbox_events` — Message chờ phát lên Kafka

“Outbox” giống hộp thư đi: cùng lúc lưu vé/đổi trạng thái, service lưu một message cần gửi. Worker đọc hộp thư và gửi Kafka sau đó; Kafka tạm lỗi thì message vẫn còn trong DB để thử lại.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | Chính messageId đã chọn cho message JSON; retry giữ nguyên, không phải eventId sự kiện. Entity Ticket yêu cầu gán ID này. | UUID message M |
| `aggregate_id` | CHAR(36), bắt buộc | ID đối tượng nghiệp vụ dùng để gom message. Theo hợp đồng key, phát vé theo orderId, quét vào/ra theo ticketId; chọn theo loại message, không coi luôn là eventId. | UUID O cho ticket.issued |
| `event_type` | VARCHAR(100), bắt buộc | Tên sự kiện kỹ thuật/topic mà message mô tả. “event” ở tên cột này không có nghĩa một chương trình bán vé. | `ticket.issued` |
| `payload` | JSON, bắt buộc | Toàn bộ dữ liệu message cần phát: messageId, eventType, occurredAt, eventId và nội dung theo hợp đồng Kafka. | Envelope ticket.issued kèm danh sách vé |
| `status` | VARCHAR(16), bắt buộc, DEFAULT PENDING | Trạng thái gửi Kafka của dòng outbox; PENDING là chờ gửi. Tên trạng thái sau ACK thuộc triển khai publisher, DDL không có CHECK danh sách trạng thái. | `PENDING` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm ghi message vào outbox cùng giao dịch nghiệp vụ. | `2026-10-29T03:01:00Z` |
| `published_at` | TIMESTAMP, được NULL | Thời điểm được ghi nhận đã gửi Kafka thành công sau ACK; chưa gửi thành công thì NULL. Không phải thời điểm người nhận đọc thông báo. | `2026-10-29T03:01:01Z` |

`aggregate_id` không có FK vì có thể tham chiếu các đối tượng khác nhau tùy message. Có entity/bảng outbox chưa chứng minh publisher đã được triển khai. Không phát vé lần hai bằng consumer order.paid: Order gọi REST confirm để Ticket phát vé, sau đó Kafka phục vụ Notification/Analytics.

## 7. Ví dụ nối các bảng

Đơn O mua hai VIP và một STANDARD cho sự kiện E:

1. ticket_types có hai dòng được sử dụng: VIP và STANDARD của E.
2. reservations có một dòng R với order_id=O; reservation_items có hai dòng, quantity lần lượt 2 và 1.
3. Confirm R thành công sinh ba tickets, đều có reservation_id=R và order_id=O, nhưng id/QR khác nhau.
4. Người giữ vé T1 vào rồi ra tạo hai ticket_scans cho T1, còn tickets.status cuối cùng là CHECKED_OUT.
5. Các message phát vé/vào/ra đi qua outbox_events; Analytics và Notification nhận dữ liệu theo topic tương ứng.

Xem [ERD](erd.md), [API Ticket](../../api/ticket-service-openapi.yaml) và các điểm chưa chốt [C02, C07, C08](../../project-clarifications.md). C08 còn quyết định có bắt buộc đủ ba hạng/quota dương trước publish không; có CHECK tổng lượng không đồng nghĩa quy tắc publish này đã được chốt.
