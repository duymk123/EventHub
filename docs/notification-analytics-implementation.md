# Chạy Notification và Analytics

Muốn bắt đầu đọc code, mở [hướng dẫn đọc theo từng luồng](notification-analytics-code-reading.md). Các lớp/hàm/DTO đã có chú thích tiếng Việt ngay trong mã nguồn.

Cập nhật 2026-11-05 theo phạm vi triển khai bước 1–5. Hai service có REST security, Kafka consumer, retry/DLQ, xử lý thông báo và projection. Analytics cung cấp native WebSocket STOMP tại `/ws/dashboard`.

Sơ đồ publisher, chín topic, hai consumer và vị trí KafkaTemplate trong [luồng Kafka](kafka/flows.md).

## Quyết định áp dụng

- Code hiện gửi buyer, ticket.issued gom mọi QR vào một email, IN_APP thuộc buyer. C02 mới nhất yêu cầu từng vé ưu tiên holderEmail, thiếu fallback buyer snapshot, gom theo địa chỉ; C03 text địa điểm chung. Xử lý/batch cần sửa để hỗ trợ chính sách mới, chưa triển khai chỉ bằng sửa tài liệu. order.failed/refunded vẫn gửi buyer; producer snapshot buyer email xác minh lúc tạo đơn, ownerUserId=customerId.
- Dashboard giữ tổng doanh thu gộp và số vé đã bán của cả sự kiện. API sales trả riêng doanh thu gộp, tiền hoàn, thuần và số vé hoàn. Hoàn tiền ghi vào ngày hoàn, không sửa doanh thu ngày mua.
- Ngày báo cáo theo `Asia/Ho_Chi_Minh`, từ thời điểm `paidAt` hoặc `refundedAt`. Dữ liệu được lưu qua các ngày, không có job xóa/reset số liệu sau 24 giờ.
- Notification hiện có hai kênh EMAIL và IN_APP. Nội dung render được lưu khi tạo batch. Sửa template ảnh hưởng các batch mới.

## Cấu hình identity

Cấu hình các biến sau cho cả hai service theo realm/client thật của nhóm.

| Biến | Giá trị cần cung cấp |
|---|---|
| `KEYCLOAK_ISSUER` | Issuer đúng `iss` trong access token |
| `KEYCLOAK_JWK_SET_URI` | URL lấy JWK của realm |
| `EVENTHUB_JWT_AUDIENCE` | Audience được cấp cho API tương ứng |
| `EVENTHUB_ROLE_CLAIM` | Đường dẫn claim chứa role, ví dụ `realm_access.roles` nếu realm dùng mapping đó |
| `EVENTHUB_JWT_ALGORITHM` | Thuật toán cho phép, mặc định RS256 |

Resource Server xác minh JWT trước khi tạo `Actor`. Subject phải là UUID chuẩn, có đúng một business role trong CUSTOMER/ORGANIZER/ADMIN. Role kỹ thuật của Keycloak được bỏ qua. Khi cấu hình identity còn thiếu, API yêu cầu xác thực trả 401. Lỗi REST có `code`, `message`, `timestamp`, `path`.

Analytics gọi Event qua OAuth2 client credentials với cache access token đến gần hạn hết hạn.

| Biến | Mục đích |
|---|---|
| `EVENT_SERVICE_URL` | Base URL của Event, mặc định `http://localhost:8082` |
| `KEYCLOAK_SERVICE_TOKEN_URI` | Token endpoint cho service account |
| `ANALYTICS_SERVICE_CLIENT_ID` | Client ID được phép đọc internal Event |
| `ANALYTICS_SERVICE_CLIENT_SECRET` | Secret cấp riêng cho service, giữ ngoài Git |
| `ANALYTICS_SERVICE_SCOPE` | Scope tùy cấu hình Keycloak |
| `DASHBOARD_ALLOWED_ORIGINS` | Các origin frontend, phân cách bằng dấu phẩy |

Event Service cần chấp nhận credential này ở `GET /internal/events/{id}`. Timeout kết nối 2 giây và đọc 5 giây. Kiểm tra quyền hoàn tất trước transaction đọc Analytics. Lỗi phụ thuộc trả 503, sự kiện không tồn tại trả 404, ORGANIZER khác owner trả 403.

## Database và template

Local đang giữ cấu hình `ddl-auto: update` và Flyway tắt theo repo. Entity mới cho phép Hibernate tạo `notification_batches`, cột lease trong `notifications` và `attendance_state`.

Migration V2 tương ứng đã có cho môi trường dùng Flyway. Với schema đã được Flyway quản lý, bật `SPRING_FLYWAY_ENABLED=true` và `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. Với DB cũ tạo bởi Hibernate, đối chiếu schema và lịch sử migration trước khi chuyển cơ chế. Giữ V1 đã áp dụng.

Khi khởi động, Notification tạo sáu template mặc định cho ORDER_FAILED, ORDER_REFUNDED, TICKET_ISSUED và hai kênh. UUID mặc định ổn định theo code/channel. Các template đã tồn tại giữ nội dung admin đã chỉnh. Biến `MAIL_FROM` mặc định `noreply@eventhub.local`, SMTP mặc định localhost:1025 và có timeout 5 giây.

## Kafka và xử lý lại

| Service | Input |
|---|---|
| Notification | `order.failed`, `order.refunded`, `ticket.issued` |
| Analytics | `order.paid`, `order.refunded`, `ticket.checked-in`, `ticket.checked-out` |

Payload theo [events.md](kafka/events.md). Key là orderId hoặc ticketId đúng topic, `eventType` trùng topic. Các mẫu JSON tổng hợp ở [samples](kafka/samples). `messageId` giữ nguyên khi replay. Producer gửi JSON UTF-8 không phụ thuộc Java type headers.

Notification hiện tạo batch và hai target trong một transaction; C02 mới cần số EMAIL thay đổi theo nhóm địa chỉ và một IN_APP buyer. Fingerprint phát hiện messageId trùng nhưng khác payload. IN_APP sẵn sàng khi tạo, EMAIL có lease và SMTP ngoài transaction; chỉ mọi target SENT mới ghi processed/COMPLETE. SMTP vẫn có cửa sổ gửi trùng sau gửi trước ghi SENT. C03 cần snapshot text địa điểm thật vào model batch, config dự kiến EVENTHUB_VENUE_TEXT chưa có trong code hiện tại.

Analytics INSERT claim `processed_events` trong cùng transaction với metric, attendance log và snapshot. Duplicate claim rollback rồi consumer kết thúc record. Snapshot được khóa theo sự kiện và state theo từng vé. Checkout đến trước checkin vẫn hội tụ về currentAttendance=0, totalCheckedIn=1 khi đủ hai bằng chứng.

Lỗi tạm thời retry 1/2/4 giây rồi chuyển `notification.dlq` hoặc `analytics.dlq`. Payload sai contract đi DLQ. Chỉ tiếp tục offset sau khi DLQ publish thành công. Metadata lỗi chỉ ghi tên lớp lỗi, giữ payload gốc trong DLQ để đối soát. Quyền đọc DLQ cần được giới hạn vì có dữ liệu email/QR.

order.refunded gửi Analytics cần currency và items[].refundedAmount tổng bằng amount. C04 chốt 70% giá đã mua của cả đơn; quantity là toàn bộ vé, không phải 70% quantity. C05 hold trước provider và commit hủy xong mới phát event. Bù 100% khi chưa cung cấp vé dùng message riêng dự kiến, không trừ sale chưa có; xem [thiết kế phục hồi](architecture/payment-refund-recovery.md).

## WebSocket

1. Frontend GET `/api/v1/analytics/events/{id}/dashboard` bằng access token.
2. Mở native WebSocket `/ws/dashboard` từ origin được cấu hình.
3. STOMP CONNECT mang native header `Authorization` có giá trị `Bearer <access-token>`.
4. SUBSCRIBE exact destination `/topic/event/{id}/dashboard`. Analytics xác minh event owner hoặc ADMIN.
5. Sau khi SUBSCRIBE thành công, GET lại snapshot để hội tụ khoảng nối kết. Khi reconnect cũng lấy lại snapshot.

Kết nối chưa xác thực hết hạn sau 15 giây. Session đóng theo `exp` của token, client SEND bị từ chối, destination có wildcard bị từ chối. Push lấy snapshot mới sau commit, GET REST phục hồi khi push bị gián đoạn. SimpleBroker phục vụ một Analytics instance, triển khai nhiều instance cần fanout chung. Gateway cần route WS riêng theo [plan Gateway](plans/api-gateway.md).

## Build và khởi động

```powershell
.\mvnw.cmd -pl notification-service,analytics-service -am package
# MySQL local, Kafka và SMTP cần sẵn sàng, identity lấy từ cấu hình của nhóm.
.\mvnw.cmd -pl notification-service spring-boot:run
.\mvnw.cmd -pl analytics-service spring-boot:run
```

Cổng local là 8085 và 8086. Hạ tầng Kafka/Redis xem [local-development.md](local-development.md), SMTP thử có Mailpit trong Compose chính. Kiểm tra xuyên Order/Ticket/Event/Auth/Gateway thực hiện khi các thành viên hoàn tất phần tích hợp.

## Kiểm chứng ngày 2026-11-05

- Build hai module bằng Maven đã thành công.
- Chạy hai ứng dụng trên MySQL 8 với database riêng, kiểm tra cả Flyway V1/V2 + Hibernate validate và nâng schema V1 bằng Hibernate update theo cấu hình local. Template có sẵn được giữ khi khởi động.
- Gọi REST qua HTTP với JWT/JWK cục bộ, kiểm tra Principal, quyền đọc thông báo, đánh dấu đọc lặp, UUID sai định dạng, CUSTOMER bị chặn Analytics và khoảng ngày sai.
- Gọi consumer bằng các JSON mẫu, kiểm tra duplicate chạy đồng thời, tiền hoàn, ngày Việt Nam, checkout đến trước checkin và rollback claim khi SQL cập nhật số liệu thất bại.
- Gửi SMTP qua máy chủ cục bộ, kiểm tra QR PNG inline, gửi lặp, fingerprint conflict và gửi lại sau một lần SMTP trả lỗi.
- Nối STOMP qua WebSocket thật, nhận snapshot sau commit, từ chối client SEND và đóng session theo hạn token.

Các kiểm tra độc lập dùng fixture JWK/S2S/SMTP cục bộ. Kafka broker/DLQ, realm Keycloak thật và các service của nhóm cần được kiểm tra khi môi trường tích hợp sẵn sàng. Docker Desktop chưa chạy trong phiên kiểm chứng này.
