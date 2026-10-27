# Kế hoạch Notification và Analytics sau BC_32_V2

Bản tổng quan này được chi tiết hóa ngày 2026-10-28 tại [plan Notification](plans/notification-service.md), [plan Analytics](plans/analytics-service.md) và [phối hợp sáu service](plans/00-cross-service-contracts.md). Khi code dùng các plan đó cho danh sách hàm/repository/transaction; không coi phần tổng quan là đủ schema delivery/attendance.

Bản cập nhật thay thế đề xuất trước khi đọc báo cáo. Đã chốt UUID, ba hạng VIP/PREMIUM/STANDARD, messageId, UI/API tài khoản riêng qua Auth facade gọi Keycloak, Flyway/validate và path trong ErrorResponse. Lượt tài liệu sửa trực tiếp main theo yêu cầu; không tự tạo PR hoặc push. [Quyết định nguồn](architecture/source-decisions.md), [câu hỏi C01–C09](project-clarifications.md).

## 1. Tài liệu và hạ tầng

Hợp đồng [Kafka](kafka/events.md), [OpenAPI](api/README.md) và [database](database/README.md) là nguồn triển khai đồng bộ. Schema nền hai service ở migration V1; không đổi V1 đã áp dụng. Compose đã có Kafka KRaft/UI/Mailpit, chưa có Keycloak/Redis. Thêm hạ tầng identity sau khi biết realm/client/cấu hình và cơ chế S2S; không tự gắn khóa dùng chung EVENTHUB_JWT_KEY.

Root/module POM là hiện trạng, chưa có đủ dependency security/MapStruct/WebSocket/QR/Mustache/testcontainers. Khi code mới thêm dependency thực sự cần, mapstruct-processor/lombok-mapstruct-binding nếu dùng MapStruct. Không tuyên bố bộ khung đã có API.

## 2. Notification core

Package controller, service/impl, repository, entity/enums, dto/request|response, mapper, exception, config. Entity UUID CHAR mapping theo V1. notifications gồm channel, recipient_user_id/email, title/content snapshot, payload, trạng thái/attempts/error và dedup. Templates UUID PK, UNIQUE(code,channel), chỉ EMAIL/IN_APP giai đoạn này.

API mục tiêu: GET /notifications/me trả page chỉ IN_APP; GET /me/unread-count; PUT /{id}/read trả 204, lặp không đổi read_at, không phải owner trả 404. ADMIN GET template list/detail và PUT /{code} với channel trong body. Đây là chuẩn hóa kỹ thuật được ghi trong OpenAPI, chưa là API có sẵn.

TemplateRenderer dùng Mustache escape mặc định, không chạy expression từ dữ liệu khách; SmtpEmailSender dùng JavaMailSender/MimeMessageHelper, QR PNG ZXing inline cid. Render/lưu nội dung lúc tạo để sửa template không làm đổi thông báo cũ. Chỉ ADMIN sửa template; không trả payload/QR/email qua API in-app.

## 3. Notification Kafka/email

Consumer ticket.issued, order.failed, order.refunded; không ticket.cancelled. Chuẩn hóa target theo email/owner đã xác minh; C02 cần chốt nguồn email/fallback/owner, C03 địa điểm. Không dùng quyền Keycloak admin để lookup mỗi email nếu chưa có chính sách/cấp quyền.

Luồng: tạo/đọc notification dedup key → nếu SENT bỏ qua → gửi SMTP ngoài transaction → markSent transaction ngắn; lỗi ghi attempts/last_error đã lọc → retry → DLQ. Chỉ processed khi tất cả target hoàn thành. Cạnh tranh hai worker cần claim/lease target; exists+create và bắt duplicate một mình chưa ngăn cả hai worker cùng gửi. Crash sau send vẫn có thể gửi trùng; không ghi test cam kết exactly-once SMTP.

Kiểm tra có ý nghĩa: phân quyền owner/admin, read idempotent, template render, MIME/QR với GreenMail, Kafka duplicate không tạo thêm delivery đã SENT, partial target failure, retry/DLQ và DLQ publish thất bại. Context test không đủ.

## 4. Analytics query và projections

SalesMetric/Repository UPSERT nguyên tử theo event/type/date, BigDecimal và UUID. Chỉ order.paid cộng doanh số, order.refunded cộng số hoàn; cần phân bổ tiền từng hạng (C04/C09). Claim processed_events trong transaction cùng projection, không exists rồi cộng không khóa.

Snapshot V1 có total_revenue/total_tickets_sold/current_attendance/total_checked_in. Ý nghĩa revenue/sold thuần hay gộp còn C09, API có nhãn draft. Đề xuất sales report tách revenue/refund/net, ngày Asia/Ho_Chi_Minh, refund ghi ngày hoàn. Không sửa doanh số ngày cũ hoặc dùng giá hiện tại để tính hoàn.

Attendance theo một lần vào/ra của V2: lưu trạng thái/bằng chứng của từng vé, khóa và tính đóng góp trước/sau cho cả trường hợp checkout đến trước. Không chỉ GREATEST(current-1,0). Bổ sung migration tiếp theo cho dữ liệu trạng thái/unique business event cần thiết; V1 hiện chỉ là nền.

EventClient gọi internal response có id/organizerId/title/status/startTime/endTime, timeout rõ, dùng credential S2S khi có. ADMIN hoặc ORGANIZER owner được xem; xác minh sự kiện tồn tại trước trả 0, lỗi phụ thuộc trả 503. Cache ownership ngắn hạn/có invalidation; không cache quyền vô thời hạn, không để cache che việc đổi chủ/quyền.

## 5. WebSocket

Giữ /ws/dashboard theo Hình 2.81, Gateway route ws riêng. Topic /topic/event/{id}/dashboard; JWT trong STOMP CONNECT, kiểm tra role/owner khi SUBSCRIBE, từ chối destination khác/client SEND vào server topic, giới hạn session theo expiry. Handshake được public không có nghĩa SUBSCRIBE public. Origins cấu hình cụ thể.

GET /analytics/events/{id}/dashboard cung cấp snapshot ban đầu, cùng DTO với push. Push chỉ sau commit; tải snapshot đọc mới, có thể coalesce nhiều thay đổi. SimpleBroker chỉ phù hợp dev/một instance; đa instance cần broker/fanout riêng trước khi scale.

## 6. Trình tự triển khai và điều kiện hoàn tất

| Bước | Nội dung | Phụ thuộc |
|---|---|---|
| 0 | Đối chiếu tài liệu/hợp đồng | Đã cập nhật; các câu hỏi vẫn có nhãn pending |
| 1 | Local Kafka/UI/Mailpit và schema nền | Đã có cấu hình, chưa đồng nghĩa chạy tích hợp thành công |
| 2 | Notification API/template/query | Keycloak role/sub mapping và Auth facade |
| 3 | Email/Kafka/QR/retry | C02/C03 và hợp đồng producer nguồn |
| 4 | Analytics API/EventClient/projection sales | S2S, C04/C09, outbox từ Order |
| 5 | Attendance/WS | C07, projection chịu đảo thứ tự, JWT session/role |
| 6 | Test end-to-end | Provider C06, quy tắc bù trừ C05 và dữ liệu seed nhất quán |

Luồng tiền/vé dùng Testcontainers MySQL/Kafka, kiểm tra đồng thời, duplicate, đảo thứ tự, rollback/crash và job phục hồi. Không gom tính năng vào một thay đổi chỉ để có scaffold. [Workflow](../WORKFLOW.md) quy định chia sẻ thay đổi; xác nhận sửa main ở lượt tài liệu không tự là quyền push.
