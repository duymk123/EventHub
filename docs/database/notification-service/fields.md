# Giải thích các trường database Notification Service

Database mặc định: `eventhub_notification`. Service này lưu mẫu nội dung, từng thông báo gửi cho người nhận và dấu vết message đã xử lý. Một dòng `notifications` là một thông báo cho **một người nhận qua một kênh**, không phải một sự kiện bán vé.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), [migration V1](../../../notification-service/src/main/resources/db/migration/V1__init.sql), [entity](../../../notification-service/src/main/java/com/eventhub/notification/entity), [plan nghiệp vụ](../../plans/notification-service.md), [hợp đồng Kafka](../../kafka/events.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong).

Các bảng dưới mô tả V1; kiểu, NULL và DEFAULT lấy từ SQL. Cấu hình [application.yml](../../../notification-service/src/main/resources/application.yml) hiện đặt `ddl-auto: update`, `flyway.enabled: false`, khác hướng dẫn Flyway/validate trong tài liệu cũ. Đây là đối chiếu file trong repo, chưa phải kiểm tra database đang chạy.

## 1. `notification_templates` — Mẫu nội dung thông báo

Đây là bản mẫu dùng nhiều lần. Ví dụ mẫu “vé đã phát hành” có chỗ để điền tên sự kiện và người nhận. Sửa mẫu ảnh hưởng các thông báo được tạo về sau; nội dung thông báo cũ đã lưu riêng trong `notifications`.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID riêng của bản mẫu; không phải mã nghiệp vụ của mẫu. | UUID của mẫu EMAIL |
| `code` | VARCHAR(50), bắt buộc | Tên nghiệp vụ để service chọn đúng mẫu khi có sự kiện phát sinh. Các mã ví dụ là đề xuất trong plan, không khẳng định đã được seed. | `TICKET_ISSUED` |
| `channel` | VARCHAR(10), bắt buộc | Kênh mà mẫu phục vụ. Code hiện hỗ trợ EMAIL và IN_APP; một mã có thể có một mẫu cho mỗi kênh. | `EMAIL` |
| `subject` | VARCHAR(255), bắt buộc | Mẫu tiêu đề. Sau khi điền dữ liệu sẽ thành tiêu đề email hoặc thông báo. | `Vé của bạn cho {{eventTitle}}` |
| `body_template` | TEXT, bắt buộc | Mẫu phần thân, còn chứa các chỗ cần điền dữ liệu; chưa phải nội dung cuối cùng cho một người cụ thể. | `Xin chào {{holderName}}, vé của bạn đã sẵn sàng.` |
| `updated_at` | TIMESTAMP, bắt buộc | Thời điểm mẫu được ghi/cập nhật. Entity dùng `@UpdateTimestamp`; SQL không khai báo DEFAULT tự lấy thời gian. | `2026-10-29T03:00:00Z` |

Cặp `(code, channel)` là duy nhất: có thể có `TICKET_ISSUED + EMAIL` và `TICKET_ISSUED + IN_APP`, nhưng không có hai mẫu cùng mã và cùng kênh. SQL không tạo FK từ `notifications.template_code` sang bảng này; code và channel được service sử dụng để tìm mẫu.

## 2. `notifications` — Thông báo cụ thể cho người nhận

Ví dụ cùng một lần phát hành vé, hệ thống có thể tạo một dòng EMAIL và một dòng IN_APP. Hai dòng có ID khác nhau, cùng `source_message_id`, khác `channel` và `dedup_key`. Cách chọn người nhận email còn phụ thuộc C02.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID của thông báo cụ thể. API đánh dấu đã đọc nhận ID này. | UUID của một thông báo IN_APP |
| `dedup_key` | VARCHAR(400), bắt buộc, UNIQUE | Khóa chống tạo hai thông báo cho cùng message, cùng kênh, cùng người nhận khi Kafka gửi lại. Không phải ID người dùng. | Dạng `messageId:IN_APP:userId` |
| `source_message_id` | CHAR(36), bắt buộc | `messageId` của Kafka đã kích hoạt thông báo. Dùng lần ngược về nguyên nhân tạo thông báo và nhóm các lần gửi cùng nguồn. | UUID của message `ticket.issued` |
| `recipient_user_id` | CHAR(36), được NULL | ID tài khoản nhận thông báo trong app. GET `/notifications/me` lọc theo ID này và kênh IN_APP; không lấy userId từ body client. | UUID tài khoản đang đăng nhập |
| `recipient_email` | VARCHAR(255), được NULL | Địa chỉ nhận email của dòng EMAIL; không phải email đăng nhập của service. | `lan@example.com` |
| `channel` | VARCHAR(10), bắt buộc | Cách chuyển thông báo: EMAIL gửi qua mail, IN_APP xuất hiện trong ứng dụng. | `IN_APP` |
| `template_code` | VARCHAR(50), bắt buộc | Mã mẫu đã chọn để tạo thông báo. Không lưu UUID của mẫu. | `TICKET_ISSUED` |
| `title` | VARCHAR(255), được NULL ở SQL | Tiêu đề đã điền dữ liệu cụ thể. Luồng IN_APP cần nội dung hiển thị dù SQL cho phép NULL. | `Vé của bạn cho Đêm nhạc tháng 11` |
| `content` | TEXT, được NULL ở SQL | Nội dung đã điền dữ liệu và lưu lại tại lúc tạo, để đọc về sau không bị thay đổi khi sửa mẫu. | `Bạn đã nhận được 2 vé VIP.` |
| `payload` | JSON, được NULL | Dữ liệu có cấu trúc phục vụ dựng/gửi thông báo, có thể gồm dữ liệu vé hoặc QR. Không phải response công khai; API in-app hiện không trả trường này. | `{"eventTitle":"Đêm nhạc tháng 11","quantity":2}` |
| `status` | VARCHAR(10), bắt buộc, SQL không có DEFAULT | Trạng thái chuyển thông báo: PENDING chờ gửi, SENT đã gửi/đưa vào app, FAILED gửi lỗi. Entity khởi tạo PENDING; câu SQL insert trực tiếp vẫn phải cung cấp giá trị. | `SENT` |
| `attempts` | INT, bắt buộc, DEFAULT 0 | Số lần đã thử gửi. Luồng delivery cần tăng khi bắt đầu một lần thử, để theo dõi retry. | `2`: đã thử hai lần |
| `last_error` | VARCHAR(500), được NULL | Lỗi gần nhất của việc gửi, dùng xử lý sự cố. Chỉ lưu thông tin lỗi đã lọc; không nhét QR/token hoặc toàn bộ payload. | `SMTP timeout` |
| `sent_at` | TIMESTAMP, được NULL | Thời điểm hệ thống ghi nhận gửi thành công; chưa gửi thành công có thể vẫn NULL. Không chứng minh người nhận đã mở email. | `2026-10-29T03:00:05Z` |
| `read_at` | TIMESTAMP, được NULL | Thời điểm người dùng đánh dấu thông báo IN_APP đã đọc lần đầu. NULL nghĩa chưa được đánh dấu đọc. | `2026-10-29T03:02:00Z` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo dòng thông báo, phục vụ sắp xếp danh sách. Entity gán bằng `@CreationTimestamp`. | `2026-10-29T03:00:01Z` |

SQL cho phép cả hai trường người nhận NULL để dùng chung một bảng cho nhiều kênh. Điều đó không có nghĩa thông báo hợp lệ được phép không có đích: service phải có `recipient_user_id` cho IN_APP và `recipient_email` phù hợp cho EMAIL. V1 chưa có CHECK đảm bảo điều này, cũng chưa ràng buộc enum channel/status bằng CHECK.

`idx_inapp(recipient_user_id, channel, read_at, created_at)` là chỉ mục giúp tìm thông báo của một người, lọc chưa đọc và theo thời gian; không phải các trường bổ sung.

## 3. `processed_events` — Message đã xử lý hoàn tất

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | Chính `messageId` của Kafka đã được xử lý hoàn tất. Consumer phải dùng lại ID nguồn, không sinh UUID mới cho dòng này. | Cùng UUID với `notifications.source_message_id` của nhóm thông báo |
| `processed_at` | TIMESTAMP, bắt buộc | Thời điểm ghi nhận hoàn tất xử lý message. Entity gán bằng `@CreationTimestamp`. | `2026-10-29T03:00:06Z` |

Một message có thể tạo nhiều dòng notifications nhưng chỉ có một dòng processed_events tại Notification DB. Theo thiết kế, chỉ đánh dấu processed sau khi mọi đích cần gửi đã hoàn thành. `dedup_key` bảo vệ từng thông báo; `processed_events.id` ghi nhận cả message. Hai trường giải quyết hai cấp độ khác nhau.

## 4. Đọc ví dụ từ đầu đến cuối

Giả sử message M báo “đã phát hành hai vé”, và người nhận U đã được xác định:

1. Chọn mẫu theo `code=TICKET_ISSUED` và từng `channel`.
2. Điền tên sự kiện, số vé… để có `title`/`content`, tạo thông báo với `source_message_id=M`. Dòng IN_APP có `recipient_user_id=U`.
3. Với EMAIL, sau một lần gửi thành công mới ghi `status=SENT`, `sent_at`; nếu lỗi ghi FAILED và thông tin lần thử. Mã xử lý gửi là phần thiết kế trong plan.
4. Khi tất cả đích hoàn tất, ghi `processed_events.id=M`.
5. U mở danh sách: API dùng `recipient_user_id=U`, `channel=IN_APP`; đánh dấu đọc sẽ đặt `read_at`. Trạng thái SENT của thông báo không chuyển thành một trạng thái tên READ.

## 5. Các điểm thường nhầm khi đọc code

| Dễ nhầm | Cách hiểu |
|---|---|
| `notification_templates.body_template` với `notifications.content` | Một bên là khuôn có biến, bên còn lại là nội dung đã điền cho lần gửi cụ thể. |
| `status=SENT` với `read_at` | SENT nói về chuyển thông báo; read_at nói về người dùng đánh dấu đã đọc trong app. |
| `source_message_id` với `event_id` | Trường đầu là ID message Kafka; bảng notifications V1 không có cột event_id. Event ID nghiệp vụ có thể xuất hiện trong dữ liệu nguồn/payload. |
| `id` với `dedup_key` | id định danh một dòng; dedup_key phát hiện yêu cầu tạo lại cùng thông báo dù có thể được sinh id khác. |
| `readAt`/`createdAt` trong Java | Ánh xạ lần lượt tới `read_at`/`created_at` trong database. |

`notification_batches`, lease token và lease time là đề xuất trong [plan](../../plans/notification-service.md), chưa có trong V1 nên không liệt kê như cột hiện hành. Nguồn email và fallback khi holder không có email còn [C02](../../project-clarifications.md). Xem tiếp [ERD](erd.md) hoặc [API Notification](../../api/notification-service-openapi.yaml).
