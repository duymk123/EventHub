# Các quyết định còn cần chốt sau BC_32_V2

Cập nhật 2026-10-27. Thay thế danh sách câu hỏi trước khi đọc BC_32_V2. Đã chốt trực tiếp: UUID cho mọi bảng/gateId, VIP/PREMIUM/STANDARD, messageId, Keycloak cấp token, sửa tài liệu trực tiếp trên main, Flyway/validate cho hai service mới, ErrorResponse có path. Đối chiếu nguồn chi tiết: [source-decisions](architecture/source-decisions.md).

Auth/UI đã chốt thêm: giữ giao diện/API tài khoản riêng, Auth gọi Keycloak. Đây là facade, không phải hệ thống password/token tự xây. C01 đã đóng về lựa chọn UI/API; grant flow, role/sub mapping, quyền gọi Admin API và DB profile là cấu hình/thiết kế kỹ thuật còn cần hoàn thiện, không hỏi lại lựa chọn đã xác nhận.

## Phần database đã rõ

- Event DB sở hữu events/categories, Ticket DB sở hữu ticket_types với ba bộ đếm available/reserved/sold; không có bản events authoritative trong Ticket DB, không FK xuyên DB.
- Order DB có orders, order_items, order_holders, payments, refund_requests, payment_webhooks. order_holders giải quyết trường hợp một dòng mua nhiều vé cho nhiều người.
- Reservation có UNIQUE(order_id), unit_price snapshot trong reservation_items. Order sinh UUID, reserve trước rồi lưu nên reservation_id NOT NULL phù hợp Hình 2.57.
- Vé gắn owner_user_id và holder_name; QR là token opaque riêng, không phải UUID vé.
- Schema Notification trong bảng báo cáo là bản tối thiểu, cần các trường channel/user/title/content để thực hiện luồng in-app đã vẽ. Analytics cần chiều hạng vé để đáp ứng báo cáo theo hạng.
- CHECKED_OUT không được check-in lại trong sơ đồ hiện tại; CLOSED ngừng bán. Không cần chốt lại những quy tắc này nếu giữ phạm vi V2.

## Câu hỏi ảnh hưởng việc bắt đầu code

| Mã | Cần chủ dự án trả lời | Phương án đề xuất để dễ chọn | Phần bị ảnh hưởng |
|---|---|---|---|
| C02 | Email người mua lấy từ claim đã xác minh của Keycloak hay adapter tra cứu? Holder không có email sẽ gửi vé về ai? Người mua hay người nhận có quyền xem /tickets/me? | Snapshot email đã xác minh lúc tạo đơn; owner là người mua, holder là người sử dụng; thiếu holder email thì gửi về người mua. | customer_email, holder_email, owner_user_id, ticket.issued/order.failed/refunded. Bảng 2.12 cho phép email NULL nên chưa đủ để bắt buộc gửi mail từng holder. |
| C03 | Sự kiện có địa điểm hiển thị không, và lưu địa điểm dạng text hay địa chỉ/venue riêng? | Thêm venue text vào Event và snapshot vào email; chưa tạo venue/zone/seat riêng. | Event API/DB, InternalEventResponse, email vé. Bảng 2.3 hiện chưa có địa điểm. |
| C04 | Chỉ hoàn cả đơn hay được chọn vé? Hoàn bao nhiêu %, có phí không? ORGANIZER được duyệt chỉ sự kiện của mình đúng không? | Giai đoạn đầu hoàn cả đơn, tiền hoàn tính theo giá đã mua; cần bạn quyết định tỷ lệ/phí và quyền duyệt. | refund_items nếu hoàn từng vé; amount/refundedAmount theo hạng; phân quyền admin/refunds. Không mặc định hoàn 100%. |
| C05 | Nếu đã thu tiền nhưng reservation hết hạn, hoặc đã hoàn tiền nhưng Ticket từ chối hủy vĩnh viễn thì xử lý thế nào? | Trạng thái điều phối + job đối soát; giữ vé/quyền hủy trước khi gọi provider; cần duyệt quy tắc bù trừ. | Hình 2.64/2.67 còn khoảng trống giữa hai service; retry chỉ giải quyết lỗi tạm thời. |
| C06 | Dùng payment provider sandbox nào? Thanh toán tới sau hạn tự hoàn ngay hay đưa vào hàng đợi duyệt đối soát? | Chọn một provider trước; không đoán payload/chữ ký hoặc coi redirect là kết quả thanh toán. | Webhook, idempotency, API verify, luồng late payment/refund. |
| C07 | gateId do hệ thống nào cấp/quản lý và thiết bị nào được quét sự kiện nào? | gateId chắc chắn UUID; cần chọn owner dữ liệu cổng và cách gắn thiết bị với sự kiện. | Chưa có bảng gates trong V2; không tạo FK sang bảng chưa tồn tại. |
| C08 | Mỗi sự kiện bắt buộc có đủ ba hạng với quota > 0, hay có thể hạng quota 0/ẩn? Có cần đủ vé trước submit/publish không? | Cho phép quota 0 và cấu hình Ticket sau tạo Event; điều kiện publish cần bạn chọn. | Ràng buộc ticket_types và validate submit/publish. V2 không mô tả đầy đủ điều kiện này. |
| C09 | Dashboard muốn hiển thị vé/doanh thu gộp hay sau hoàn tiền? | API trả riêng gộp, hoàn và thuần; ghi refund vào ngày hoàn theo Asia/Ho_Chi_Minh. | Ý nghĩa totalTicketsSold/totalRevenue trong snapshot, báo cáo ngày và tổng. Các quy tắc kế toán này là đề xuất, chưa coi đã duyệt. |

Cấu hình Keycloak/JWT (realm/client, grant flow cho UI/API riêng, issuer/audience, thuật toán/JWK, role claim/sub mapping, quyền Admin API/service account, TTL, service-to-service, thời gian hiệu lực khi khóa/đổi quyền) đã được chủ dự án nhận sẽ báo lại; không yêu cầu chốt lại ở lượt này. Việc chọn Keycloak chưa tự giải quyết nguồn email hay quyền thiết bị cổng.

## Thiết kế kỹ thuật cần hoàn thiện khi triển khai

- Cross-topic ordering: check-in và check-out ở hai topic có thể đảo thứ tự. Với luồng một lần vào/ra hiện tại, projection lưu log có messageId duy nhất, tính đóng góp theo trạng thái bằng chứng của từng vé; không chỉ decrement rồi clamp về 0. Chọn thiết kế cụ thể, bổ sung schema/migration và test checkout-đến-trước trước khi code attendance.
- Transaction DB và Kafka: AFTER_COMMIT không đủ chống mất event khi crash. Producer cần outbox/delivery log bền vững và retry. Không tự dùng lại order.paid để phát vé.
- Webhook được nhận thành công nhưng confirm còn retry cần job bền vững; không phụ thuộc provider gửi lại sau khi đã ACK.
- Claim processed_events và projection phải cùng transaction; email dedup theo recipient và chấp nhận giới hạn SMTP khi crash.
- Core notification là EMAIL + IN_APP. Reminder/PUSH trong phần tổng quan chưa có scheduler/hợp đồng đủ rõ; tách giai đoạn sau nếu cần.
- WebSocket giữ /ws/dashboard theo Hình 2.81, Gateway route riêng. JWT được kiểm tra lúc CONNECT và quyền sự kiện lúc SUBSCRIBE; chặn client SEND vào topic server.

## Phần có thể làm trước khi chốt các câu hỏi

Có thể làm bộ khung package, exception/DTO, truy vấn danh mục, máy trạng thái thuần, migration/reference schema phần đã rõ và cấu hình local Kafka/Mailpit. Chưa thể coi đã sẵn sàng triển khai end-to-end Auth, thanh toán/hoàn tiền, email vé và cổng quét khi các phụ thuộc C02–C08 còn mở. Từng PR cần ghi rõ phụ thuộc và không gắn mặc định kinh doanh vào code.
