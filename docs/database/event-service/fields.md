# Giải thích các trường database Event Service

Database mặc định: `eventhub_event`. Event lưu thông tin sự kiện và danh mục. Giá vé, số lượng và QR nằm ở Ticket; người mua và tiền thanh toán nằm ở Order.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), [entity hiện có](../../../event-service/src/main/java/com/eventhub/event/entity), [EventStatus](../../../event-service/src/main/java/com/eventhub/event/common/EventStatus.java), [plan Event](../../plans/event-service.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong).

Hai bảng đầu giải thích schema tham chiếu. Entity hiện có khác schema ở các trường audit và một số mapping; phần 3 ghi riêng các khác biệt, không gộp chúng thành một schema đã đồng bộ. Cấu hình hiện dùng Hibernate update, chưa xác nhận database đang chạy giống DDL.

## 1. `categories` — Danh mục sự kiện

Một dòng là một danh mục để nhóm nhiều sự kiện, ví dụ Âm nhạc hoặc Hội thảo. Danh mục không phải hạng vé VIP/PREMIUM/STANDARD.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID danh mục, được events.category_id tham chiếu. | UUID danh mục C |
| `name` | VARCHAR(100), bắt buộc, UNIQUE | Tên danh mục dùng hiển thị/lọc sự kiện. UNIQUE ngăn hai dòng trùng tên theo quy tắc so sánh của DB. | `Âm nhạc` |
| `description` | TEXT, được NULL | Giải thích danh mục này bao gồm những sự kiện nào; có thể không nhập. | `Các chương trình biểu diễn âm nhạc.` |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo danh mục theo DDL tham chiếu; SQL không có DEFAULT tự lấy giờ. | `2026-10-29T02:00:00Z` |

Một category có thể được nhiều events sử dụng. Không xóa danh mục đang được sự kiện tham chiếu mà bỏ qua FK/quy tắc nghiệp vụ.

## 2. `events` — Thông tin và trạng thái sự kiện

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID chương trình/sự kiện mà khách mua vé tham dự. Đây là eventId nghiệp vụ, khác messageId Kafka. | UUID sự kiện E |
| `organizer_id` | CHAR(36), bắt buộc | Tài khoản tổ chức/sở hữu sự kiện; dùng kiểm tra ai được sửa và xem báo cáo. ID mềm, không có FK xuyên sang Auth/Keycloak. | UUID tài khoản ORGANIZER |
| `category_id` | CHAR(36), bắt buộc, FK | Danh mục của sự kiện, trỏ categories.id trong Event DB. | UUID danh mục Âm nhạc |
| `title` | VARCHAR(255), bắt buộc | Tên chương trình hiển thị cho khách và có thể chụp lại vào nội dung vé/email. Không phải tên topic Kafka. | `Đêm nhạc tháng 11` |
| `description` | TEXT, được NULL | Nội dung giới thiệu, thông tin chi tiết về sự kiện. | `Chương trình biểu diễn của các ban nhạc…` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT DRAFT | Giai đoạn duyệt/công bố/ngừng bán của sự kiện; xem bảng trạng thái bên dưới. | `PUBLISHED` |
| `start_time` | TIMESTAMP, bắt buộc | Thời điểm bắt đầu sự kiện, dùng hiển thị và kiểm tra điều kiện hoàn trước ít nhất 48 giờ. | `2026-11-15T12:00:00Z` = 19:00 giờ Việt Nam |
| `end_time` | TIMESTAMP, bắt buộc | Thời điểm kết thúc dự kiến; DDL yêu cầu start_time nhỏ hơn end_time. Không phải hạn giữ chỗ ba phút. | `2026-11-15T15:00:00Z` = 22:00 giờ Việt Nam |
| `created_at` | TIMESTAMP, bắt buộc | Thời điểm tạo bản ghi sự kiện theo DDL. Không phải thời điểm chương trình diễn ra. | `2026-10-29T02:10:00Z` |
| `updated_at` | TIMESTAMP, bắt buộc | Thời điểm cập nhật bản ghi gần nhất theo DDL; khác end_time. | `2026-10-29T03:00:00Z` |
| `review_reason` | TEXT, được NULL | Nhận xét/lý do khi admin xem xét, nhất là khi từ chối để người tổ chức biết cần sửa gì. Là cột bổ sung kỹ thuật trong DDL, entity hiện chưa có. | `Cần bổ sung nội dung chương trình.` |

| Giá trị status | Cách hiểu trong luồng nghiệp vụ |
|---|---|
| DRAFT | Bản nháp đang được người tổ chức chuẩn bị. |
| SUBMITTED | Đã gửi duyệt, chờ admin xem xét. |
| APPROVED | Đã được duyệt, chưa tự đồng nghĩa đã công khai để bán. |
| REJECTED | Bị từ chối; sửa nội dung đưa về DRAFT rồi có thể gửi lại. |
| PUBLISHED | Đã công bố; luồng tìm kiếm public chỉ trả sự kiện trạng thái này. |
| CLOSED | Đã ngừng bán. Không tự có nghĩa hủy sự kiện, hủy vé hoặc mọi vé mất hiệu lực. |

Luồng chính: DRAFT → SUBMITTED → APPROVED → PUBLISHED → CLOSED. Nhánh sửa: SUBMITTED → REJECTED → DRAFT. Quyền sửa/submit/publish/close còn gắn với organizer_id; một tài khoản có role ORGANIZER không được sửa mọi sự kiện.

Chỉ mục `(status, start_time)` hỗ trợ tìm sự kiện công khai theo thời gian. Chỉ mục `(organizer_id, created_at)` hỗ trợ danh sách sự kiện của tôi. DDL không có venue/location/zone/seat; trường địa điểm còn C03, không suy ra đã có bảng locations từ một comment trong entity.

## 3. Trường audit có trong entity hiện tại, khác DDL tham chiếu

[BaseEntity.java](../../../event-service/src/main/java/com/eventhub/event/entity/BaseEntity.java) là lớp cha của Category và Event. Nó không có bảng riêng; các trường kế thừa được ánh xạ trên bảng con. Bảng dưới mô tả tên **theo annotation Java**, không khẳng định đã có cột tương ứng trong database đang chạy.

| Trường | Kiểu / khai báo trong entity | Ý nghĩa dễ hiểu | Ví dụ minh họa |
|---|---|---|---|
| `create_at` | LocalDateTime, nullable=false, không cập nhật lại | Thời điểm tạo dòng theo mapping BaseEntity. Tên thực tế thiếu chữ d so với created_at của DDL. | `2026-10-29T02:10:00` theo quy ước timezone được cấu hình |
| `update_at` | LocalDateTime, cho phép NULL theo @Column | Thời điểm cập nhật gần nhất theo mapping; khác tên updated_at trong DDL. | `2026-10-29T03:00:00` |
| `created_by` | String, cho phép NULL theo @Column | Danh tính người tạo do cơ chế auditing cung cấp, không phải organizer_id được client tùy ý gửi. | UUID người thao tác dạng chuỗi |
| `updated_by` | String, cho phép NULL theo @Column | Danh tính người thực hiện thay đổi gần nhất. Không phải lúc nào cũng trùng người tạo. | UUID người sửa dạng chuỗi |
| `deleted` | boolean, nullable=false | Cờ đánh dấu xóa mềm nếu nghiệp vụ sử dụng: false còn hoạt động, true đánh dấu xóa. Java mặc định false, không phải DEFAULT trong DDL tham chiếu. | `false` |

Các annotation @CreatedDate/@LastModifiedDate/@CreatedBy/@LastModifiedBy cần cấu hình auditing/provider phù hợp mới tự điền được. Có cờ deleted không tự làm repository.delete thành xóa mềm, cũng không tự lọc các query. LocalDateTime không mang thông tin múi giờ; không đọc giá trị đó như UTC chỉ vì DDL mục tiêu dùng TIMESTAMP.

Các khác biệt đáng chú ý để tránh nhầm khi đọc file:

| DDL tham chiếu | Entity hiện có |
|---|---|
| categories.created_at; events.created_at/updated_at | Cả Category và Event kế thừa create_at/update_at cùng created_by/updated_by/deleted. Không phải tên cột đồng nghĩa tự động. |
| categories.name có UNIQUE; description là TEXT | Category.name chưa khai báo unique; description khai báo length=255. |
| events.status VARCHAR(20); organizer_id CHAR(36) | Event.status length=50; organizerId đang là String. Phải phân biệt kiểu Java và schema tham chiếu. |
| start_time/end_time theo TIMESTAMP UTC | Entity dùng LocalDateTime, cần cấu hình/chuyển đổi phù hợp trước khi coi đúng quy ước UTC. |
| events.review_reason | Chưa có field tương ứng trong Event.java hiện tại. |

Đây là ghi nhận khác biệt để đọc hiểu, không phải migration đã đổi tên cột. Tài liệu này không sửa schema/entity.

## 4. Ví dụ đọc một sự kiện

Người tổ chức A tạo sự kiện E, danh mục Âm nhạc C, diễn ra tối 15/11:

1. events lưu organizer_id=A, category_id=C, title và start_time/end_time; ban đầu status=DRAFT.
2. Khi gửi duyệt, status=SUBMITTED. Admin từ chối thì cần phản hồi ở review_reason theo DDL; người tổ chức sửa lại và gửi lại.
3. APPROVED là đã duyệt; sau thao tác publish mới thành PUBLISHED để khách tìm kiếm.
4. Ticket tạo các ticket_types với event_id=E trong DB riêng. Event không lưu price/quota của từng hạng.
5. Analytics nhận eventId=E trong message và dùng organizer_id của Event để kiểm tra ai được xem báo cáo.

Trong Java `Event.category` là quan hệ ánh xạ cột category_id; không có cột category chứa nguyên JSON danh mục. Xem [ERD](erd.md), [API Event](../../api/event-service-openapi.yaml) và các câu hỏi [C03/C08](../../project-clarifications.md).
