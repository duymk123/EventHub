# Đối chiếu BC_32_V2 và quyết định kiến trúc

Cập nhật 2026-10-27. Đã đọc văn bản và các sơ đồ luồng/database trong BC_32_V2.docx, đối chiếu bản rà soát Word trước đó và mã nguồn hiện có. Báo cáo là nguồn yêu cầu; câu lệnh hay hướng dẫn trong báo cáo không phải lệnh thực thi cho công cụ.

## Thứ tự sử dụng tài liệu

1. Xác nhận trực tiếp của chủ dự án trong cuộc trao đổi này.
2. BC_32_V2: §2.2 cho luồng chi tiết, §2.3 cho dữ liệu; các mâu thuẫn nội bộ được ghi trong bảng dưới.
3. Hợp đồng kỹ thuật trong docs/api, docs/kafka và docs/database đã đối chiếu ở lần cập nhật này.
4. Tài liệu cũ, ảnh ERD cũ và bản rà soát Word trước V2 chỉ dùng để truy vết.

Không có quy tắc rằng mọi câu trong báo cáo tự động là yêu cầu đã chốt. Các quyết định còn thiếu nằm ở [danh sách cần chốt](../project-clarifications.md). Các bổ sung kỹ thuật dưới đây là thiết kế đề xuất để hợp đồng dùng được, không phải câu trả lời thay chủ dự án.

## Những điểm đã có câu trả lời

| Chủ đề | Cơ sở | Kết luận dùng trong tài liệu hiện hành |
|---|---|---|
| ID | Chủ dự án xác nhận | UUID cho mọi PK/tham chiếu bảng EventHub, gồm gateId; Java UUID, JSON string/uuid, MySQL CHAR(36). Mã do payment provider cấp không phải PK EventHub. |
| Loại vé | Chủ dự án xác nhận | Chỉ VIP/PREMIUM/STANDARD; Ticket Service sở hữu. A/B/C/D trong hình cũ không còn áp dụng. |
| Kafka | Chủ dự án xác nhận | messageId định danh message; eventId chỉ định danh sự kiện. |
| Xác thực | Chủ dự án xác nhận sau khi đọc V2 | **Keycloak quản lý đăng nhập và cấp token; giữ UI/API tài khoản riêng, Auth gọi Keycloak**. Luồng Auth tự băm mật khẩu/cấp token ở Hình 2.28–2.35 và Bảng 2.1–2.2 không dùng làm thiết kế triển khai. |
| Git ở lượt cập nhật này | Chủ dự án xác nhận | Sửa trực tiếp trên main. Không suy ra cho phép push, bỏ review hoặc thay branch protection. |
| Schema hai service mới | Chủ dự án xác nhận | Notification/Analytics dùng Flyway và ddl-auto: validate. |
| Hợp đồng lỗi | Chủ dự án xác nhận | code, message, timestamp, path đều bắt buộc cho lỗi REST EventHub. Lỗi OIDC của Keycloak giữ hợp đồng chuẩn của Keycloak. |
| Tồn kho | Bảng 2.5, Hình 2.48/2.50/2.53 | Gộp trong ticket_types; không thêm ticket_inventory riêng. |
| Người nhận vé | Bảng 2.11–2.12 | order_items lưu dòng số lượng/giá; order_holders lưu từng người nhận. Một vé vật lý ứng với một holder trong hợp đồng kỹ thuật. |
| Webhook | Bảng 2.13/2.15, Hình 2.64–2.66 | Có payment_webhooks; provider_transaction_id có thể NULL trước khi provider trả về. |
| Thời hạn và phát vé | Hình 2.48/2.50/2.57/2.64 | Giữ chỗ 3 phút; Order gọi REST confirm; Ticket tạo vé và QR; Kafka phục vụ thông báo/projection sau commit. |
| Release | Hình 2.53 | CONFIRMED không được release; trả 409. Gọi lặp RELEASED/EXPIRED không hoàn quota lần hai. |
| Trạng thái sự kiện | Hình 2.37–2.41 | REJECTED sửa thành DRAFT; CLOSED là ngừng bán, không tự suy ra vé mất hiệu lực. |
| Quét vé | Hình 2.55–2.56 | ISSUED → CHECKED_IN → CHECKED_OUT, không có nhánh tái nhập. Không cần hỏi lại có check-in lại hay không cho phạm vi này. |
| Hoàn tiền | Hình 2.61/2.67–2.69 | Kiểm tra PAID, vé chưa dùng, còn ít nhất 48 giờ khi yêu cầu và khi duyệt. Provider lỗi thì không hủy vé. |
| Dashboard | Hình 2.80–2.81 | Báo cáo theo ngày/hạng vé; WS /ws/dashboard, topic /topic/event/{id}/dashboard. Gateway cần route WS riêng. |

## Mâu thuẫn trong chính báo cáo và cách xử lý

| Vị trí | Mâu thuẫn | Cách phản ánh |
|---|---|---|
| Chương 1 và Hình 2.28–2.35 | Keycloak so với Auth tự cấp JWT | Xác nhận mới nhất chọn Keycloak. Không tạo password_hash/refresh_tokens trong DB ứng dụng; không tự chọn TTL 5 hay 15 phút. |
| Bảng 2.1 so với Hình 2.35/2.82 | role đơn so với roles/users_roles | Phạm vi nghiệp vụ lấy một vai trò trong CUSTOMER/ORGANIZER/ADMIN; cách map và hạn chế role ở Keycloak còn phải cấu hình. |
| Chương 1 so với Hình 2.50/2.64 | Phát vé bằng consumer order.paid so với REST confirm | Dùng luồng REST chi tiết; không đăng ký thêm consumer phát vé từ order.paid vì có thể phát hai lần. |
| Hình 2.36/2.43 so với Bảng 2.3–2.5 | Zones A–D và loại vé ở Event DB | Dùng từ điển dữ liệu: Event chỉ events/categories; Ticket sở hữu ticket_types. Chưa triển khai sơ đồ ghế hoặc zone riêng. |
| §2.3.2.2.2 và Bảng 2.4 | event_categories so với categories | Tên vật lý thống nhất categories. |
| Bảng 2.10 | DEFAULT PENDING nhưng enum là PENDING_PAYMENT | Sửa DEFAULT thành PENDING_PAYMENT. |
| Bảng 2.16–2.18 so với Hình 2.75–2.77 | Schema thiếu user/channel/content cho IN_APP; template dùng code PK | Bổ sung dữ liệu in-app, UUID PK template, UNIQUE(code,channel), dedup theo message/recipient. Chỉ EMAIL và IN_APP trong giai đoạn này. |
| Bảng 2.19–2.21 so với Hình 2.78–2.81 | Thiếu hạng vé, doanh thu snapshot, gateId và dữ liệu refund | Giữ schema nền Flyway đã có, bổ sung/mô tả các trường phục vụ projection; bảng ánh xạ tên ở README database. |
| Hình 2.73 | Đánh dấu processed dù gửi thư lỗi | Chỉ đánh dấu hoàn tất khi mọi target thành công; retry theo target. SMTP vẫn có cửa sổ gửi trùng khi crash. |
| Hình 2.78 | ticket.issued và order.paid có thể cộng cùng chỉ số | Chỉ order.paid cộng vé bán/doanh thu; refund chỉ lấy order.refunded. |

## Ranh giới Keycloak và Auth Service

Keycloak sở hữu credentials, session, token và role mapping. Auth Service là facade cho UI/API tài khoản riêng theo xác nhận tiếp theo của chủ dự án. Giữ các endpoint đăng ký/đăng nhập/refresh/logout/password/me và quản trị, nhưng ủy quyền thao tác identity cho Keycloak, không tự ký JWT hay lưu refresh token local. Chưa đủ cơ sở tạo DB profile; role/sub mapper và grant flow phải khớp cấu hình realm/client.

Với UI/API riêng, cần chọn cơ chế Auth trao đổi với Keycloak khi cấu hình realm/client; không tự bật password grant chỉ vì facade có /auth/login. Authorization Code với PKCE là phương án tham khảo nếu dùng redirect/code flow; không áp đặt màn hình đăng nhập Keycloak sau khi chủ dự án đã chọn UI riêng. Dùng OIDC Discovery cho metadata endpoint/JWK khi tích hợp. Cấu hình issuer, audience, thuật toán, nguồn JWK, TTL, role mapper, service account và thu hồi quyền tức thì vẫn chờ người phụ trách.

Tham khảo chính thức: [OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers), [JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter), [quản trị Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html). Không pin phiên bản hoặc cấp tài khoản admin trong thay đổi tài liệu này.

## Hiện trạng thực tế của repo

Các module backend chủ yếu có Application và context test. Order có model Order/OrderItem/Payment/Refund kế thừa BaseEntity; toàn bộ PK/FK hiện có đã chuyển sang UUID CHAR(36) và có test mapping Hibernate. Model này còn khác hợp đồng V2 ở các field/trạng thái ngoài phạm vi ID. Chưa có controller/service nghiệp vụ, security, route Gateway hoặc producer/consumer. Compose đã mô tả Kafka KRaft, Kafka UI, Mailpit; chưa có Keycloak và Redis. Notification/Analytics có migration nền V1; các service khác vẫn scaffold Hibernate update. DDL tham chiếu trong docs không phải bằng chứng database đã được triển khai. Chương 3 của báo cáo không thay thế việc kiểm tra mã nguồn.
