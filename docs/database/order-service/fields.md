# Giải thích các trường database Order Service

Database mặc định: `eventhub_order`. Order lưu ai mua gì, giá đã chốt, trạng thái thanh toán và yêu cầu hoàn tiền. Ticket mới là nơi giữ số lượng và phát vé; có đơn hàng chưa đồng nghĩa đã có vé hoặc đã thu tiền.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), [entity hiện có](../../../order-service/src/main/java/com/eventhub/order/entity), [enum](../../../order-service/src/main/java/com/eventhub/order/common), [plan Order](../../plans/order-service.md), [Kafka](../../kafka/events.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong). Bảng dưới lấy kiểu, NULL, DEFAULT và constraint từ DDL. Code đã có entity cho bảy bảng; một số đoạn “model cũ” trong plan chưa phản ánh các entity mới. Cấu hình hiện dùng Hibernate update; không suy từ file rằng DB đang chạy đã có đủ constraint.

## 1. `orders` — Đơn mua vé

Một dòng là một đơn của một khách cho một sự kiện. Một đơn có thể có nhiều hạng vé và nhiều người sử dụng vé.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID đơn do Order tạo; gửi sang Ticket khi reserve và dùng suốt luồng thanh toán. | UUID đơn O |
| `customer_id` | CHAR(36), bắt buộc | Tài khoản mua đơn, lấy từ danh tính đã xác minh. ID mềm sang hệ thống identity, khác tên người dùng vé. | UUID khách U |
| `event_id` | CHAR(36), bắt buộc | Sự kiện khách đang mua vé; ID mềm sang Event. | UUID sự kiện E |
| `reservation_id` | CHAR(36), bắt buộc | ID lần giữ chỗ do Ticket trả về, dùng confirm/release. Không có FK xuyên sang Ticket DB. | UUID R |
| `status` | VARCHAR(20), bắt buộc, DEFAULT PENDING_PAYMENT | Trạng thái tổng thể đơn: chờ trả tiền, đã trả, thất bại, hết hạn hoặc hoàn tiền; xem bảng trạng thái bên dưới. | `PENDING_PAYMENT` |
| `subtotal` | DECIMAL(14,2), bắt buộc, >= 0 | Tổng các dòng vé trước điều chỉnh: cộng line_total của order_items. Tính từ giá server đã chốt. | `1300000.00` |
| `total_amount` | DECIMAL(14,2), bắt buộc, >= 0 | Tổng số tiền đơn yêu cầu thanh toán. Khi không có phí/giảm giá thì bằng subtotal; schema có hai cột không có nghĩa đã có nghiệp vụ voucher/phí. | `1300000.00` |
| `currency` | VARCHAR(3), bắt buộc, DEFAULT VND | Đơn vị tiền của số tiền trên đơn; không phải tỉ giá. Phạm vi hiện mô tả VND. | `VND` |
| `expires_at` | TIMESTAMP, bắt buộc | Hạn thanh toán của đơn, cần đồng bộ với hạn giữ chỗ Ticket trả về. Worker tìm đơn chờ thanh toán đã quá hạn theo trường này. | `2026-10-29T03:03:00Z` |
| `paid_at` | TIMESTAMP, được NULL | Thời điểm thanh toán thành công được xác nhận; không lấy từ lúc trình duyệt quay lại trang thành công. | `2026-10-29T03:01:00Z` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo dòng đơn; entity nhận từ BaseEntity của Order. | `2026-10-29T03:00:00Z` |
| `updated_at` | TIMESTAMP, bắt buộc | Thời điểm ghi cập nhật dòng đơn gần nhất; khác paid_at là một mốc nghiệp vụ cụ thể. | `2026-10-29T03:01:02Z` |

| Giá trị status | Cách hiểu |
|---|---|
| PENDING_PAYMENT | Đơn đang chờ thanh toán trong hạn giữ chỗ. |
| PAID | Đã xác nhận thu tiền. Cần theo dõi confirm Ticket đến khi phát vé thành công; không coi cột này một mình là bằng chứng đã phát vé. |
| FAILED | Đơn thất bại theo luồng xử lý thanh toán. |
| EXPIRED | Đơn quá hạn trước khi hoàn tất theo luồng hiện hành. Thanh toán tới muộn còn C05/C06. |
| REFUNDED | Đơn đi vào kết quả hoàn tiền theo luồng đã hoàn tất; chính sách hoàn cả đơn/từng vé còn C04. |

Chỉ mục customer_id/created_at phục vụ lịch sử đơn của tôi; status/expires_at phục vụ tìm đơn quá hạn. DDL kiểm tra số tiền không âm nhưng không tự kiểm tra subtotal bằng tổng dòng con; service phải đảm bảo các số khớp nhau.

## 2. `order_items` — Dòng mua theo từng hạng vé

Mua hai VIP và một STANDARD tạo hai dòng items, không phải ba. Mỗi dòng chứa số lượng và giá lịch sử tại lúc mua.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID dòng mua hạng vé, được holder tham chiếu. | UUID dòng I-VIP |
| `order_id` | CHAR(36), bắt buộc, FK | Đơn cha, trỏ orders.id trong cùng DB. | UUID O |
| `ticket_type_id` | CHAR(36), bắt buộc | Hạng vé được mua; ID mềm sang Ticket. | UUID hạng VIP của E |
| `ticket_type_name` | VARCHAR(100), bắt buộc | Tên hạng tại thời điểm mua, giữ để xem lịch sử mà không phụ thuộc tên hiện tại phía Ticket. | `VIP` |
| `quantity` | INT, bắt buộc, > 0 | Số vé mua của hạng này. Service phải có đủ từng ấy người nhận trong order_holders. | `2` |
| `unit_price` | DECIMAL(14,2), bắt buộc, >= 0 | Giá một vé đã được chốt từ Ticket, không phải giá tùy ý client gửi. | `500000.00` |
| `line_total` | DECIMAL(14,2), bắt buộc | Tổng tiền riêng dòng, DDL yêu cầu bằng quantity × unit_price. | `1000000.00` |

UNIQUE `(order_id, ticket_type_id)` ngăn lặp cùng hạng trong một đơn. Nếu giá hiện tại của VIP tăng lên 600000 thì unit_price của đơn cũ vẫn 500000; đó là ý nghĩa “snapshot giá”.

## 3. `order_holders` — Từng người sử dụng vé

Một dòng là thông tin một người nhận/sử dụng một vé. Với item VIP quantity=2, phải có hai holder cho item đó, dù người mua chỉ là một tài khoản.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID thông tin một người sử dụng vé; không phải userId hoặc ticketId. | UUID holder H1 |
| `order_item_id` | CHAR(36), bắt buộc, FK | Dòng hạng vé người này được phân vào, trỏ order_items.id. | UUID I-VIP |
| `holder_name` | VARCHAR(100), bắt buộc | Tên người sẽ dùng vé, có thể khác tên tài khoản đã mua. | `Nguyễn Lan` |
| `holder_email` | VARCHAR(255), được NULL | Email người dùng vé nếu được cung cấp. Không có email thì gửi về ai còn C02; không tự giả định luôn có. | `lan@example.com` hoặc NULL |
| `holder_phone` | VARCHAR(20), được NULL | Số điện thoại liên hệ của người dùng vé, nếu có. Lưu chuỗi để giữ dấu + và số 0 đầu. | `0900000000` |

SQL có FK nhưng không tự kiểm tra số holder bằng quantity; đó là kiểm tra nghiệp vụ. Không có order_holders.ticket_id trong DDL này; cần quy tắc ánh xạ ổn định khi confirm/retry, không suy rằng đã có FK từ holder sang vé.

## 4. `payments` — Lần thanh toán qua nhà cung cấp

Đơn là “cần mua gì”; payment là “lần trao đổi tiền với cổng thanh toán”. Quan hệ SQL cho phép một đơn có nhiều dòng payments, không tự áp đặt mỗi đơn chỉ có một lần thử.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID bản ghi thanh toán bên EventHub; khác mã giao dịch provider. | UUID payment P |
| `order_id` | CHAR(36), bắt buộc, FK | Đơn được thanh toán, trỏ orders.id. | UUID O |
| `provider` | VARCHAR(50), bắt buộc | Tên cổng xử lý thanh toán; nhà cung cấp cụ thể còn C06. | `SANDBOX_DEMO`, chỉ là tên giả minh họa |
| `provider_transaction_id` | VARCHAR(100), được NULL | Mã giao dịch cổng cấp sau khi tạo/nhận kết quả. NULL trước phản hồi là hợp lệ; đây là chuỗi bên ngoài, không ép UUID. | `txn-demo-001` |
| `amount` | DECIMAL(14,2), bắt buộc | Số tiền của lần thanh toán, cần đối chiếu với đơn và webhook. DDL bảng này không có CHECK số tiền dương. | `1300000.00` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT CREATED | CREATED mới tạo nội bộ; PENDING chờ kết quả; SUCCEEDED đã xác minh thành công; FAILED thất bại. | `PENDING` |
| `payment_url` | TEXT, được NULL | Đường dẫn provider trả để khách tiếp tục thanh toán. NULL khi chưa nhận URL hoặc luồng không dùng URL. | `https://payments.example/checkout/demo` |
| `idempotency_key` | VARCHAR(100), bắt buộc, UNIQUE | Khóa nhận biết cùng yêu cầu tạo thanh toán khi client retry, để trả kết quả cũ thay vì tạo thêm lần thu tiền. Không phải messageId Kafka. | `checkout-request-demo-001` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo bản ghi payment; Hibernate gán qua BaseEntity. | `2026-10-29T03:00:10Z` |
| `updated_at` | TIMESTAMP, bắt buộc | Thời điểm cập nhật payment gần nhất, ví dụ nhận kết quả thành công. | `2026-10-29T03:01:00Z` |

Cặp `(provider, provider_transaction_id)` là duy nhất khi có mã giao dịch, tránh ghi hai payment cho cùng giao dịch provider. Trạng thái payments và orders khác nhau vì mô tả hai đối tượng khác nhau; phải được điều phối nhất quán, không suy rằng chuyển payment là tự động đổi order bằng SQL.

## 5. `refund_requests` — Yêu cầu hoàn tiền và quá trình xử lý

Tạo yêu cầu hoàn chưa có nghĩa tiền đã được trả lại. Các mốc yêu cầu, xem xét và hoàn thành được lưu riêng.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID yêu cầu hoàn tiền; khác orderId và paymentId. | UUID yêu cầu F |
| `order_id` | CHAR(36), bắt buộc, FK | Đơn được yêu cầu hoàn, trỏ orders.id. | UUID O |
| `requested_by` | CHAR(36), bắt buộc | Tài khoản gửi yêu cầu, lấy từ danh tính đã xác minh; ID mềm. | UUID khách U |
| `amount` | DECIMAL(14,2), bắt buộc, > 0 | Số tiền đề nghị/được xử lý theo chính sách hoàn. Tỉ lệ, phí và hoàn từng vé còn C04; không mặc định bằng toàn bộ total_amount. | `200000.00`, chỉ minh họa |
| `reason` | TEXT, bắt buộc | Lý do do người yêu cầu cung cấp. Khác phản hồi của người duyệt. | `Tôi không thể tham dự.` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT REQUESTED | Giai đoạn yêu cầu: REQUESTED, APPROVED, REJECTED, PROCESSING, REFUNDED, FAILED. | `REQUESTED` |
| `approved_by` | CHAR(36), được NULL | ID tài khoản phê duyệt nếu yêu cầu được duyệt. Không dùng tên cột này để tự suy ra ADMIN hay ORGANIZER được duyệt; quyền cụ thể còn C04. | UUID người duyệt, hoặc NULL khi chưa duyệt |
| `review_reason` | TEXT, được NULL | Nhận xét/lý do phản hồi khi xem xét, ví dụ lý do từ chối. Là bổ sung kỹ thuật trong DDL tham chiếu. | `Vé đã được sử dụng.` |
| `requested_at` | TIMESTAMP, bắt buộc | Thời điểm khách tạo yêu cầu hoàn. Dùng kiểm tra điều kiện tại lúc yêu cầu. | `2026-11-10T03:00:00Z` |
| `reviewed_at` | TIMESTAMP, được NULL | Thời điểm người có quyền xem xét/duyệt/từ chối yêu cầu. Điều kiện hoàn cần được kiểm tra lại khi duyệt. | `2026-11-10T04:00:00Z` |
| `refunded_at` | TIMESTAMP, được NULL | Thời điểm ghi nhận tiền đã hoàn theo kết quả provider/luồng hoàn; chưa hoàn xong thì NULL. Không phải requested_at. | `2026-11-10T04:02:00Z` |

| Giá trị status | Cách hiểu |
|---|---|
| REQUESTED | Đã gửi yêu cầu, chưa có quyết định. |
| APPROVED | Đã được duyệt, chưa khẳng định provider đã trả tiền. |
| REJECTED | Bị từ chối sau khi xem xét. |
| PROCESSING | Đang thực hiện thao tác hoàn với provider/luồng điều phối. |
| REFUNDED | Kết quả hoàn tiền thành công theo luồng. |
| FAILED | Thao tác hoàn thất bại; cần xử lý lỗi/đối soát theo thiết kế. |

Nghiệp vụ hiện yêu cầu đơn PAID, vé chưa dùng và còn ít nhất 48 giờ đến sự kiện, kiểm tra lúc yêu cầu và lúc duyệt. DDL không chứa đủ dữ liệu để tự kiểm tra các điều kiện này. C04/C05 còn xử lý phạm vi tiền hoàn và trường hợp đã hoàn tiền nhưng hủy vé không thành công; chưa có bảng refund_items trong DDL.

## 6. `payment_webhooks` — Thông báo kết quả từ cổng thanh toán

Webhook là request cổng thanh toán gửi vào server. Provider có thể gửi lại cùng kết quả nhiều lần; bảng này giúp nhận ra bản trùng và theo dõi xử lý.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID bản ghi webhook bên EventHub, không phải mã provider gửi. | UUID webhook W |
| `provider` | VARCHAR(50), bắt buộc | Cổng gửi request, dùng cùng mã sự kiện provider để phân biệt bản trùng. | `SANDBOX_DEMO` |
| `provider_event_id` | VARCHAR(100), bắt buộc | ID sự kiện do provider cấp; không phải events.id của EventHub và không bắt buộc là UUID. | `evt-demo-001` |
| `order_id` | CHAR(36), bắt buộc, FK | Đơn đã được xác định từ webhook, trỏ orders.id. | UUID O |
| `payload_hash` | VARCHAR(255), bắt buộc | Dấu băm nội dung để phát hiện cùng provider_event_id nhưng nội dung khác. Không thay thế kiểm tra chữ ký/xác thực request của provider. | `<hash-của-payload>` |
| `received_at` | TIMESTAMP, bắt buộc | Thời điểm server nhận và ghi webhook. Không nhất thiết bằng thời điểm khách trả tiền. | `2026-10-29T03:01:01Z` |
| `processed_at` | TIMESTAMP, được NULL | Thời điểm ghi nhận xử lý webhook hoàn tất; nhận rồi nhưng chưa xử lý xong thì NULL. | `2026-10-29T03:01:02Z` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT RECEIVED | RECEIVED đã nhận; PROCESSED đã xử lý; FAILED xử lý lỗi. Đây là trạng thái xử lý webhook, khác payment.status. | `PROCESSED` |

UNIQUE `(provider, provider_event_id)` chống xử lý lại cùng thông báo provider. Một giao dịch có thể có các thông báo provider khác nhau; provider_event_id không được coi luôn bằng provider_transaction_id. Cần so khớp chữ ký, tiền, đơn và giao dịch theo provider được chọn.

## 7. `outbox_events` — Message nghiệp vụ cần gửi Kafka

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | messageId của message cần phát; phải trùng messageId trong payload và giữ nguyên khi gửi lại. | UUID message M |
| `aggregate_id` | CHAR(36), bắt buộc | ID đơn làm đối tượng gom các message Order; dùng orderId làm key theo hợp đồng Kafka. Không phải ID sự kiện bán vé. | UUID O |
| `event_type` | VARCHAR(100), bắt buộc | Loại sự kiện nghiệp vụ/topic sẽ phát, không phải events.title. | `order.paid` |
| `payload` | JSON, bắt buộc | Nội dung đã chốt của message: envelope và dữ liệu đơn/hạng vé/tiền cần cho consumer. | Message order.paid có eventId=E, orderId=O |
| `status` | VARCHAR(16), bắt buộc, DEFAULT PENDING | Tình trạng gửi Kafka: PENDING đang chờ. Tên trạng thái sau gửi thành công do publisher triển khai; DDL chưa có CHECK giá trị. | `PENDING` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm ghi message vào outbox trong transaction nghiệp vụ. Entity dùng @CreationTimestamp. | `2026-10-29T03:01:02Z` |
| `published_at` | TIMESTAMP, được NULL | Mốc worker ghi nhận Kafka đã ACK. Chưa gửi thành công thì NULL; không chứng minh các consumer đã xử lý xong. | `2026-10-29T03:01:03Z` |

Entity Order OutboxEvent hiện có UUID generator; khi xây publisher/producer phải đảm bảo ID được tạo đúng một lần và dùng chính giá trị đó cho messageId payload. Constraint SQL không tự kiểm tra sự bằng nhau của hai giá trị.

Theo thiết kế, order.paid chỉ được phát sau xác nhận thu tiền **và** Ticket confirm phát vé thành công. Đã PAID mà confirm tạm lỗi cần retry bền vững; bản thân outbox_events không lưu đủ mọi việc điều phối còn chờ. pending_operations là đề xuất trong plan, chưa có trong DDL này.

## 8. Ví dụ nối các trường để đọc một đơn

Khách U mua hai VIP giá 500000 và một STANDARD giá 300000 cho sự kiện E:

1. Ticket trả reservation R; orders lưu customer_id=U, event_id=E, reservation_id=R, subtotal=total_amount=1300000 nếu không có điều chỉnh.
2. order_items có hai dòng: VIP quantity=2, line_total=1000000; STANDARD quantity=1, line_total=300000.
3. order_holders có ba dòng: hai người trỏ item VIP, một người trỏ item STANDARD.
4. payments lưu lần trả tiền; payment_webhooks lưu kết quả provider gửi về. Một bên mô tả tiền, bên kia mô tả xử lý request kết quả.
5. Thu tiền và confirm Ticket xong, outbox_events chứa order.paid; Analytics nhận message để cộng báo cáo. Notification gửi vé dựa trên ticket.issued của Ticket.
6. Nếu khách yêu cầu hoàn, refund_requests lưu quá trình từ yêu cầu tới kết quả; không sửa giá lịch sử order_items thành giá hiện tại.

Trong Java, `Order.items`, `payments`, `refundRequests`, `paymentWebhooks`, `OrderItem.holders` là các quan hệ tới bảng con, không phải cột JSON/tên cột mới trong orders. Xem [ERD](erd.md), [API Order](../../api/order-service-openapi.yaml) và [C02/C04–C06](../../project-clarifications.md).
