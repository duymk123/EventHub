# Hợp đồng API sau BC_32_V2

Sáu file OpenAPI 3.0.3 là **hợp đồng mục tiêu 2.0.0-draft**, không phải tài liệu API đã triển khai. Xem [quyết định nguồn](../architecture/source-decisions.md) và [câu hỏi C01–C09](../project-clarifications.md). x-contract-status/x-open-decisions ghi các phần còn mở hoặc bổ sung kỹ thuật.

| File | Nội dung |
|---|---|
| [Auth](auth-service-openapi.yaml) | Giữ UI/API riêng: register/login/refresh/logout/password/me/admin là facade Auth → Keycloak, không tự cấp JWT. |
| [Event](event-service-openapi.yaml) | events/categories, vòng đời, tìm theo startDate/endDate, internal response có title/organizerId. |
| [Ticket](ticket-service-openapi.yaml) | Hạng vé và quota gộp, reserve/confirm/release/cancel, vé cá nhân, quét cổng. |
| [Order](order-service-openapi.yaml) | Đơn/items/holders, payment, webhook draft phụ thuộc provider, refund còn chính sách C04–C06. |
| [Notification](notification-service-openapi.yaml) | In-app có nội dung/phân trang, read idempotent, unread-count, template theo code+channel. |
| [Analytics](analytics-service-openapi.yaml) | Sales theo ngày/hạng, attendance, dashboard ban đầu và hợp đồng STOMP bổ sung. |

Public server URL là http://localhost:8080/api/v1 nên path /orders tương ứng /api/v1/orders. /internal/** có server riêng của service, không ghép thêm /api/v1 và không expose Gateway. WS giữ /ws/dashboard, dùng route ws riêng của Gateway theo Hình 2.81.

UUID string/uuid cho PK/tham chiếu; code của provider là string bên ngoài. ErrorResponse EventHub bắt buộc code/message/timestamp/path. Lỗi chuẩn OIDC của Keycloak và phản hồi ACK theo provider là hợp đồng bên ngoài riêng, không tự ép về DTO ứng dụng.

Chủ dự án chốt giữ giao diện/API tài khoản riêng, Auth gọi Keycloak. API Auth vẫn có tên register/login/refresh/logout/password/me/admin nhưng xử lý qua Keycloak, không triển khai password hash/refresh-token DB hoặc issuer ứng dụng thứ hai. Contract login draft có email/password, grant/code flow cụ thể còn cấu hình; không tự bật Direct Access Grants. Xem [source-decisions](../architecture/source-decisions.md); realm/client/issuer/audience/roles chưa chốt.

Pagination notification, PUT template, unread-count, dashboard GET, review_reason và tiền hoàn theo hạng là bổ sung kỹ thuật có ghi rõ nguồn/giới hạn. Không giả định frontend cũ tương thích với thay đổi shape array → page hay POST → PUT. Repo chưa có client nghiệp vụ để migrate.
