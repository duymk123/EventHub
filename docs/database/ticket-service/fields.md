# Giải thích các trường database Ticket Service

Database mặc định: `eventhub_ticket`. Ticket quản lý hạng vé và số lượng còn bán, giữ chỗ trong lúc thanh toán, phát vé QR và ghi nhận vào/ra. “Hạng vé”, “giữ chỗ” và “vé đã phát hành” là ba đối tượng khác nhau.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), [entity hiện có](../../../ticket-service/src/main/java/com/eventhub/ticket/entity), [enum](../../../ticket-service/src/main/java/com/eventhub/ticket/common), [plan Ticket](../../plans/ticket-service.md), [Kafka](../../kafka/events.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong). Kiểu/NULL/DEFAULT trong bảng dưới lấy từ DDL tham chiếu; entity có mặt không đồng nghĩa toàn bộ luồng đã chạy. Cấu hình hiện dùng Hibernate update; tài liệu này không kiểm tra database đang chạy.

## 1. `ticket_types` — Hạng vé (zone) và cấu hình ghế của một sự kiện

Một dòng là một hạng vé như VIP của một sự kiện, không phải một tấm vé. VIP của hai sự kiện khác nhau là hai dòng khác nhau. Theo C12 (2026-11-22), mỗi hạng cũng là một zone ngồi: VIP sát sân khấu, PREMIUM ở giữa, STANDARD phía sau.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID của hạng vé (zone) cụ thể; frontend dùng để lấy sơ đồ ghế của zone. | UUID hạng VIP sự kiện E |
| `event_id` | CHAR(36), bắt buộc | Sự kiện mà hạng vé thuộc về. ID mềm sang Event, không phải FK vật lý. | UUID sự kiện E |
| `name` | VARCHAR(100), bắt buộc | Tên hạng; chỉ VIP, PREMIUM, STANDARD theo CHECK của DDL và enum. | `VIP` |
| `price` | DECIMAL(14,2), bắt buộc | Giá bán hiện tại của một vé thuộc hạng này, không âm. Giá đã mua phải được chụp lại ở reservation/order để sau này đổi giá không sửa lịch sử. | `500000.00` |
| `row_count` | INT, bắt buộc, DEFAULT 0, 0–30 | C12: số hàng ghế organizer nhập cho zone này của sự kiện. Hàng đặt nhãn A, B, C… 0 nghĩa là zone chưa mở bán (C08). | `5` |
| `seats_per_row` | INT, bắt buộc, DEFAULT 0, 0–60 | C12: số ghế mỗi hàng, các hàng bằng nhau. | `20` |
| `total_quota` | INT, bắt buộc, DEFAULT 0 | Tổng số ghế của zone = row_count × seats_per_row (DDL CHECK). Không phải tổng vé đã bán. | `100` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT ACTIVE | Cho phép nhận bán/giữ chỗ mới hay đang tắt bán; enum code có ACTIVE và INACTIVE. DDL chưa có CHECK danh sách status này. | `ACTIVE` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo hạng vé; entity dùng @CreationTimestamp. | `2026-10-29T02:00:00Z` |

UNIQUE `(event_id, name)` ngăn tạo hai hạng VIP trong cùng sự kiện. C12 bỏ các bộ đếm `available_quantity`, `reserved_quantity`, `sold_quantity`: tồn kho là số dòng `seats` theo trạng thái (AVAILABLE/HELD/SOLD). Giữ ghế chỉ chạm vào đúng các dòng ghế được chọn, không có một dòng tồn kho chung bị mọi đơn tranh nhau.

Ví dụ zone VIP 5 hàng × 20 ghế = 100 ghế, đang trống 80, giữ 5, bán 15. Khách giữ hai ghế B-07, B-08: hai dòng seats đổi AVAILABLE → HELD, trống còn 78. Confirm: hai ghế HELD → SOLD. Hết hạn trước confirm: hai ghế về AVAILABLE. Organizer được thêm hàng; chỉ bớt hàng cuối khi mọi ghế hàng đó còn AVAILABLE; đổi `seats_per_row` chỉ khi chưa có ghế HELD/SOLD.

## 2. `reservations` — Một lần giữ chỗ cho đơn hàng

Giữ chỗ là lời hứa giữ các ghế đã chọn trong mười phút (C16) cho Order thanh toán, chưa phải vé để quét QR.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID giữ chỗ; Order lưu lại trong orders.reservation_id để confirm/release. | UUID giữ chỗ R |
| `order_id` | CHAR(36), bắt buộc, UNIQUE | Đơn hàng yêu cầu giữ chỗ. ID mềm sang Order; UNIQUE đảm bảo một đơn không tạo nhiều reservation khi gọi lại. | UUID đơn O |
| `status` | VARCHAR(20), bắt buộc, DEFAULT RESERVED | Tình trạng giữ chỗ: RESERVED đang giữ; CONFIRMED đã xác nhận/phát vé; RELEASED đã trả giữ chỗ; EXPIRED đã hết hạn. | `RESERVED` |
| `expires_at` | TIMESTAMP, bắt buộc | Hạn cuối còn được giữ vé. Luồng reserve đặt hạn mười phút (C16); worker tìm reservation quá hạn theo trường này. | Tạo 03:00 UTC, hết hạn `2026-10-29T03:10:00Z` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo lần giữ chỗ. Không phải thời điểm khách trả tiền. | `2026-10-29T03:00:00Z` |
| `confirmed_at` | TIMESTAMP, được NULL | Thời điểm chuyển sang CONFIRMED. Còn đang chờ thì NULL. | `2026-10-29T03:01:00Z` |
| `released_at` | TIMESTAMP, được NULL | Mốc ghi nhận giải phóng giữ chỗ khi luồng trả giữ chỗ thực hiện. Xem status để biết RELEASED hay EXPIRED; không dùng timestamp này thay status. | `2026-10-29T03:02:00Z`, hoặc NULL |
| `event_start_time` | TIMESTAMP, được NULL | Giờ bắt đầu sự kiện đọc từ Event lúc giữ ghế, trả cho Order để kiểm mốc 48 giờ hoàn tiền (C04). Event không cho đổi giờ sau publish nên giá trị này không lệch. | `2026-11-28T12:00:00Z` |

RESERVED chỉ kết thúc bằng CONFIRMED, RELEASED hoặc EXPIRED. Không release một reservation đã CONFIRMED. Release/expire đưa các ghế của reservation từ HELD về AVAILABLE đúng một lần; gọi lại không đổi gì thêm. Chỉ mục `(status, expires_at)` giúp tìm các giữ chỗ cần xử lý hết hạn.

## 3. `seats` — Từng ghế của một zone (C12)

Ghế được sinh khi organizer lưu `row_count` × `seats_per_row` cho hạng vé. Mỗi sự kiện có bộ ghế riêng; một địa điểm chung (C03) không có nghĩa sơ đồ ghế cố định.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID ghế; frontend gửi danh sách ID này khi tạo đơn. | UUID ghế S-B07 |
| `ticket_type_id` | CHAR(36), bắt buộc, FK | Zone (hạng vé) chứa ghế. Sự kiện suy ra qua ticket_types.event_id. | UUID hạng VIP |
| `row_index` | INT, bắt buộc, >= 1 | Thứ tự hàng 1, 2, 3… để sắp xếp đúng cả khi nhãn vượt quá Z. | `2` |
| `row_label` | VARCHAR(3), bắt buộc | Nhãn hàng hiển thị: A…Z, rồi AA, AB… | `B` |
| `seat_number` | INT, bắt buộc, >= 1 | Số ghế trong hàng. Nhãn ghế hiển thị = row_label + "-" + số 2 chữ số. | `7` (nhãn `B-07`) |
| `status` | VARCHAR(10), bắt buộc, DEFAULT AVAILABLE | AVAILABLE còn trống; HELD đang được một reservation giữ; SOLD đã phát vé. | `HELD` |
| `reservation_id` | CHAR(36), được NULL, FK | Reservation đang giữ hoặc đã mua ghế. NULL khi AVAILABLE (DDL CHECK), bắt buộc khi HELD/SOLD. | UUID R |
| `updated_at` | TIMESTAMP, bắt buộc | Lần đổi trạng thái gần nhất; frontend dùng để biết sơ đồ đã thay đổi. | `2026-10-29T03:00:00Z` |

UNIQUE `(ticket_type_id, row_index, seat_number)` không cho trùng vị trí. Giữ ghế bằng một câu `UPDATE seats SET status='HELD', reservation_id=? WHERE id IN (…) AND status='AVAILABLE'` rồi so số dòng đổi được với số ghế yêu cầu; thiếu thì rollback và trả 409 SEAT_TAKEN. Hai đơn chỉ tranh nhau khi chọn trùng ghế. Chỉ mục `(ticket_type_id, status)` phục vụ đếm ghế trống và vẽ sơ đồ.

## 4. `reservation_items` — Số lượng giữ theo từng hạng

Một reservation có thể giữ VIP hai vé và STANDARD một vé, tương ứng hai dòng ở bảng này.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID dòng chi tiết giữ chỗ. | UUID dòng VIP trong R |
| `reservation_id` | CHAR(36), bắt buộc, FK | Giữ chỗ cha, trỏ reservations.id trong cùng DB. | UUID R |
| `ticket_type_id` | CHAR(36), bắt buộc, FK | Hạng vé đang được giữ, trỏ ticket_types.id. | UUID hạng VIP |
| `quantity` | INT, bắt buộc, > 0 | Số vé giữ của hạng này; chưa phải số vé của tất cả các hạng. | `2` |
| `unit_price` | DECIMAL(14,2), bắt buộc, >= 0 | Giá một vé được chốt tại lúc giữ chỗ, giúp Order không dùng giá client hoặc giá đổi về sau. | `500000.00` |

UNIQUE `(reservation_id, ticket_type_id)` đảm bảo một hạng chỉ có một dòng trong một lần giữ chỗ. Tổng tiền minh họa của dòng = quantity × unit_price, nhưng bảng này không có cột line_total.

## 5. `reservation_seats` — Ghế thuộc từng lần giữ chỗ (C12)

`seats.reservation_id` bị xóa khi ghế được trả lại, nên bảng này giữ lịch sử ghế nào thuộc reservation nào. Confirm đọc bảng này để phát đúng một vé cho mỗi ghế; gọi lại confirm trả cùng bộ vé.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID dòng liên kết. | UUID |
| `reservation_id` | CHAR(36), bắt buộc, FK | Lần giữ chỗ. | UUID R |
| `seat_id` | CHAR(36), bắt buộc, FK | Ghế được giữ. | UUID ghế S-B07 |
| `seat_label` | VARCHAR(8), bắt buộc | Nhãn ghế chụp lại lúc giữ, không đổi khi organizer sửa sơ đồ sau này. | `B-07` |

UNIQUE `(reservation_id, seat_id)`. Một ghế có thể xuất hiện ở nhiều reservation theo thời gian (giữ, hết hạn, người khác giữ lại).

## 6. `tickets` — Từng vé đã phát hành

Mua hai vé VIP tạo hai dòng tickets, mỗi dòng có ID và QR riêng. Quantity không xuất hiện ở đây vì một dòng đã là một vé.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID tấm vé, dùng API tra cứu và các message liên quan vé. Không phải chính QR. | UUID vé T1 |
| `reservation_id` | CHAR(36), bắt buộc, FK | Lần giữ chỗ được xác nhận để sinh ra vé, trỏ reservations.id. | UUID R |
| `order_id` | CHAR(36), bắt buộc | Đơn đã mua vé; ID mềm sang Order. Hỗ trợ tra vé theo đơn hoặc hủy theo đơn. | UUID O |
| `ticket_type_id` | CHAR(36), bắt buộc, FK | Hạng của vé, trỏ ticket_types.id. | UUID hạng VIP |
| `seat_id` | CHAR(36), bắt buộc, FK | C12: ghế của vé, trỏ seats.id. Vé hủy (hoàn tiền) thì ghế về AVAILABLE và có thể bán cho vé khác, nên không UNIQUE. | UUID ghế S-B07 |
| `seat_label` | VARCHAR(8), bắt buộc | Nhãn ghế in trên vé và email QR. | `B-07` |
| `owner_user_id` | CHAR(36), bắt buộc | C02 chốt tài khoản mua đơn; holder là người sử dụng. Có holderEmail không tự cấp quyền xem/lấy QR bằng tài khoản khác. | UUID buyer U |
| `holder_name` | VARCHAR(100), được NULL | Tên người sử dụng vé tại sự kiện, lấy từ thông tin holder theo luồng confirm. Không tự là tài khoản đăng nhập. | `Nguyễn Lan` |
| `qr_code_value` | VARCHAR(255), bắt buộc, UNIQUE | Chuỗi bí mật/opaque dùng làm nội dung QR và kiểm tra khi quét. Không lấy ticketId làm QR thay thế. | `<chuỗi-QR-minh-họa>` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT ISSUED | Trạng thái vé: ISSUED đã phát; CHECKED_IN đã vào; CHECKED_OUT đã ra; CANCELLED đã hủy. | `CHECKED_IN` |
| `issued_at` | TIMESTAMP, bắt buộc | Thời điểm phát hành vé khi confirm thành công, không phải lúc tạo reservation. | `2026-10-29T03:01:00Z` |
| `cancelled_at` | TIMESTAMP, được NULL | Thời điểm hủy vé nếu đã chuyển CANCELLED; bình thường NULL. | NULL khi vé đang ISSUED |

Luồng vào/ra V2: ISSUED → CHECKED_IN → CHECKED_OUT, không tái nhập. Nhánh hủy: ISSUED → CANCELLED theo điều kiện nghiệp vụ. Chỉ mục owner/issued_at phục vụ danh sách vé; chỉ mục order_id phục vụ tìm vé theo đơn. Bảng tickets hiện không lưu holder_email; không suy ra rằng có thể lấy email từ vé mà không giữ snapshot/nguồn phù hợp.

## 7. `ticket_scans` — Ghi nhận từng lần vào/ra hợp lệ

Một dòng cho một lần chuyển trạng thái vào/ra hợp lệ. DDL không có trường lưu lỗi của mọi lần quét QR sai.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID bản ghi quét, khác ID vé được quét. | UUID lượt quét S |
| `ticket_id` | CHAR(36), bắt buộc, FK | Vé được quét, trỏ tickets.id. | UUID T1 |
| `gate_id` | CHAR(36), bắt buộc | Mã cổng/thiết bị thực hiện quét. Không có bảng gates/FK: C07 chốt quét bằng tài khoản ORGANIZER/ADMIN, gateId là UUID chọn trên trang quét. | UUID cổng A |
| `scan_type` | VARCHAR(10), bắt buộc | Loại hành động: CHECK_IN vào, CHECK_OUT ra; được CHECK trong DDL. | `CHECK_OUT` |
| `scanned_at` | TIMESTAMP, bắt buộc | Thời điểm quét được ghi nhận trong nghiệp vụ. Kafka và Analytics dùng thời điểm này để phản ánh hành động gốc. | `2026-11-15T14:00:00Z` |

Vé có status là trạng thái hiện tại; ticket_scans là lịch sử đã xảy ra. Cập nhật trạng thái và thêm log phải cùng transaction. Không có cột checked_in_at trực tiếp trên tickets trong DDL này.

## 8. `outbox_events` — Message chờ phát lên Kafka

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

## 9. Ví dụ nối các bảng

Đơn O mua hai VIP và một STANDARD cho sự kiện E:

1. ticket_types có hai dòng được sử dụng: VIP và STANDARD của E, mỗi dòng có bộ ghế riêng trong seats.
2. Khách chọn VIP B-07, B-08 và STANDARD D-12. reservations có một dòng R với order_id=O; ba ghế đổi sang HELD với reservation_id=R; reservation_items có hai dòng, quantity lần lượt 2 và 1; reservation_seats có ba dòng.
3. Confirm R thành công: ba ghế HELD → SOLD và sinh ba tickets, mỗi vé một seat_id/seat_label, đều có reservation_id=R và order_id=O nhưng id/QR khác nhau.
4. Người giữ vé T1 vào rồi ra tạo hai ticket_scans cho T1, còn tickets.status cuối cùng là CHECKED_OUT.
5. Các message phát vé/vào/ra đi qua outbox_events; Analytics và Notification nhận dữ liệu theo topic tương ứng.

C08 đã chốt quota 0 và cấu hình sau tạo Event; submit không đòi đủ ba hạng, publish cần một ACTIVE có total_quota > 0 (theo C12: row_count × seats_per_row > 0). Hold/fence theo C05 đầy đủ là hướng phát triển; C05 rút gọn chỉ dùng cancel-by-order (hủy vé, trả ghế). C07: quét bằng JWT ORGANIZER/ADMIN trong khung giờ sự kiện. Xem [quyết định](../../project-clarifications.md), [thiết kế phục hồi](../../architecture/payment-refund-recovery.md), [ERD](erd.md) và [API Ticket](../../api/ticket-service-openapi.yaml).
