# Quyết định nghiệp vụ và các phần còn cần hoàn thiện

Cập nhật 2026-11-05 theo xác nhận mới nhất của chủ dự án. Các câu hỏi C02–C05/C08 trước đây được thay bằng quyết định bên dưới; C06 và C07 chủ động để sau. [Nguồn và thứ tự ưu tiên](architecture/source-decisions.md).

Đã chốt từ trước: UUID cho mọi PK/tham chiếu ứng dụng và gateId; VIP/PREMIUM/STANDARD; messageId khác eventId; Keycloak cấp token, Auth giữ UI/API riêng; lỗi REST có code/message/timestamp/path. C01 đã đóng về lựa chọn Auth facade. Realm/client, sub/role mapper, issuer/audience/JWK và S2S là cấu hình tích hợp còn cần hoàn thiện.

## Bảng quyết định hiện hành

| Mã | Trạng thái và nguồn | Quyết định áp dụng |
|---|---|---|
| C02 | Chủ dự án chốt | Snapshot email người mua đã xác minh lúc tạo đơn. Owner vé là người mua; holder là người sử dụng. Email vé gửi tới holderEmail nếu có; thiếu thì dùng customerEmail đã snapshot. Gom vé cùng địa chỉ thành một email; IN_APP cho người mua. |
| C03 | Chủ dự án chốt một địa điểm; cần text thật | Dùng text cấu hình chung trong email kèm QR. Chưa cần bảng địa điểm, zone/seat hoặc venue bắt buộc trên Event. Tên/địa chỉ thật chưa được cung cấp, không bịa giá trị cho môi trường chạy. |
| C04 | Chủ dự án chọn cả đơn và 70%; quyền/phí là phương án Codex chọn | Khách hủy cả đơn: hoàn 70% giá đã mua, giữ lại 30%, không cộng thêm phí. Chỉ ADMIN xem/duyệt/từ chối refund; CUSTOMER yêu cầu cho đơn mình, ORGANIZER chưa có quyền duyệt. Giữ điều kiện PAID, mọi vé chưa dùng, còn ít nhất 48 giờ lúc yêu cầu và duyệt. |
| C05 | Chủ dự án giao Codex thiết kế; đã chọn phương án | Order lưu việc điều phối bền vững; Ticket giữ quyền hủy trước khi gọi provider. Timeout phải xác minh. Đã thu tiền nhưng không phát được vé thì hoàn bù 100% khoản thực thu chưa hoàn. Chi tiết [luồng phục hồi](architecture/payment-refund-recovery.md). |
| C06 | Để sau theo chủ dự án | Chưa chọn provider sandbox, wire format, chữ ký và khả năng truy vấn/idempotency. Chính sách tiền muộn nằm ở C05; chưa bật luồng tiền thật khi adapter chưa có. |
| C07 | Để sau theo chủ dự án | gateId vẫn UUID; chưa chọn nơi quản lý cổng, thiết bị và quyền quét. Không tự tạo bảng gates hoặc mở quyền thiết bị. |
| C08 | Chủ dự án chốt quota 0/cấu hình sau tạo Event; giao Codex chọn publish | Tạo Event trước rồi cấu hình Ticket, được để hạng quota 0. Submit không đòi đủ ba hạng. Publish cần owner, APPROVED, thời gian hợp lệ và ít nhất một hạng ACTIVE có total_quota > 0; không buộc cả ba hạng đều dương. |
| C09 | Giữ xác nhận trước đó ngày 2026-11-05 | Dashboard tổng gộp doanh thu/vé bán cả sự kiện; sales tách gộp, hoàn, thuần. Ngày Asia/Ho_Chi_Minh theo paidAt/refundedAt, giữ lịch sử qua các ngày. Không mở lại quyết định này. |

Các mục “Codex chọn” ghi rõ nguồn thiết kế, không phải lời xác nhận nguyên văn của chủ dự án. Đây là quyết định tài liệu; migration, adapter và API mới vẫn cần triển khai.

## C02 — Danh tính, email và người nhận vé

- customer_id lấy từ Principal đã xác minh. Dự kiến orders.customer_email lưu email đã xác minh lúc tạo đơn; cột này chưa có trong DDL Order, cần migration khi triển khai. Không lấy email người mua tùy ý từ body.
- Email xác minh lấy qua claim tin cậy hoặc adapter identity theo cấu hình Keycloak. Chốt yêu cầu xác minh không có nghĩa mọi claim/adapter đã có.
- tickets.owner_user_id = orders.customer_id. Holder có email không tự có quyền GET /tickets/me hoặc lấy QR bằng tài khoản khác.
- Order chuyển snapshot email người mua và holder qua confirm; Ticket giữ trong outbox ticket.issued để retry không tra email hiện tại.
- Mỗi vé gửi holderEmail nếu có và hợp lệ; thiếu thì dùng buyer snapshot. Gom cùng địa chỉ; mỗi địa chỉ chỉ nhận QR các vé gửi cho họ. IN_APP thuộc người mua, không tạo tài khoản holder suy từ email.
- Cập nhật này thay chính sách gom mọi QR về buyer trong code Notification hiện có. Xử lý/batch cần đổi theo chính sách mới; sửa tài liệu chưa làm code hỗ trợ nhiều người nhận.

## C03 — Một địa điểm dùng chung

Phương án cấu hình dự kiến `EVENTHUB_VENUE_TEXT` tại Notification. Khi tạo batch, snapshot text vào model nội dung cùng QR; retry dùng nội dung đã lưu dù config đổi. `venue` nullable trong message cũ giữ để tương thích; nguồn hiển thị chuẩn giai đoạn một là text chung đã cấu hình. Thiếu text thật thì báo lỗi cấu hình, không gửi địa chỉ trống hoặc giả.

Chưa cần đổi EventRequest/Entity để mỗi sự kiện chọn địa điểm. Có nhiều địa điểm sau này sẽ mở yêu cầu schema/API riêng.

## C04 — Cách tính hoàn 70%

Giá gốc lấy từ tổng order_items.line_total = quantity × unit_price đã snapshot, không lấy giá Ticket hiện tại. Trong phạm vi không phí/voucher, tổng dòng phải khớp khoản thanh toán của đơn.

`refundAmount = round(sum(line_total) * 0.70, 2, HALF_UP)` bằng BigDecimal. Đơn 1.000.000 đồng hoàn 700.000 đồng, giữ lại 300.000 đồng. Hoàn cả đơn nghĩa hủy tất cả vé, không phải chỉ 70% số vé.

Phân bổ tiền theo 70% line_total từng hạng; phần lẻ đơn vị 0,01 xử lý theo phần dư lớn nhất, hòa thì ticketTypeId tăng dần. Tổng items[].refundedAmount phải bằng amount. Không chia đều giữa các hạng khác giá; lưu quote/phân bổ cố định để retry không đổi tiền.

Đơn miễn phí/quote bằng 0 không tạo lệnh chuyển tiền hoàn dương; hủy đơn miễn phí cần quy tắc riêng. Provider C06 phải kiểm tra đơn vị tiền hỗ trợ, không âm thầm đổi số tiền đã chốt.

## C05 — Hướng xử lý lỗi đã chọn

- Khách hủy sau phát vé: 70%, Ticket chuẩn bị giữ quyền hủy toàn bộ vé trước khi Order gọi provider.
- Provider xác nhận hoàn: Ticket commit hủy theo quyền đã giữ; đi qua mốc 48 giờ trong lúc chờ không làm mất quyền đó. Chỉ phát order.refunded khi tiền và hủy đều hoàn tất.
- Provider xác nhận chắc chắn không hoàn: release hold, vé vẫn hiệu lực, đơn vẫn PAID. Timeout/chưa rõ: giữ hold và đối soát, không mở vé hoặc gọi lần hai bằng key mới.
- Đã thu tiền nhưng reservation hết hạn/phát vé thất bại: xác minh và fence luồng phát vé trước, hoàn bù 100% khoản thực thu chưa hoàn. Không mở lại giữ chỗ hoặc bỏ kiểm tra quota.
- Lỗi không tự sửa được vào danh sách ADMIN đối soát, giữ trạng thái/bằng chứng; không tự thu tiền lại hoặc ghi thành công để bỏ lỗi.

[Thiết kế chi tiết, trạng thái, hợp đồng nội bộ và storage cần bổ sung](architecture/payment-refund-recovery.md).

## C08 — Điều kiện publish đã chọn

Tạo Event và submit không phụ thuộc đủ ba hạng. Trước publish, Event gọi API nội bộ Ticket đọc cấu hình; cần ít nhất một hạng ACTIVE có total_quota > 0, sau đó recheck owner/trạng thái và CAS APPROVED → PUBLISHED. Không giữ khóa DB Event lúc chờ HTTP. Ticket không phản hồi: 503 và chưa publish; chưa đủ cấu hình: 409 TICKET_CONFIGURATION_NOT_READY.

Đây là kiểm tra tại lúc chuẩn bị publish, không phải transaction hai DB hoặc cam kết luôn còn hàng. Sau publish có thể hết vé/tắt bán hạng; reserve vẫn kiểm tra Event PUBLISHED, hạng ACTIVE và available_quantity đủ tại lúc đặt, cập nhật số lượng nguyên tử. Thay đổi đồng thời không cho phép oversell.

## Những việc còn cần trước khi tích hợp

Cung cấp text địa điểm thật; hoàn thiện identity/S2S và nguồn email xác minh. C06/C07 để sau. Triển khai snapshot email, quote/phân bổ 70%, durable worker, hold/fence và thông báo hoàn bù trước khi bật refund. Không sửa migration đã áp dụng hoặc coi bảng/endpoint đề xuất là đã có.

Notification/Analytics đã có consumer/projection/batch/lease và attendance_state trong code hiện tại. Checklist kiểm tra dùng chạy thử thủ công theo quy ước repo; không tạo lại file/profile/dependency test tự động.
