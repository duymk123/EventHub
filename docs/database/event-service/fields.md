# Giải thích các trường database Event Service

Database mặc định: `eventhub_event`. Event lưu thông tin sự kiện và danh mục. Giá vé, số lượng và QR nằm ở Ticket; người mua và tiền thanh toán nằm ở Order.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), migration [`V1__init.sql`](../../../event-service/src/main/resources/db/migration/V1__init.sql), [entity](../../../event-service/src/main/java/com/eventhub/event/entity), [EventStatus](../../../event-service/src/main/java/com/eventhub/event/common/EventStatus.java), [plan Event](../../plans/event-service.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong).

**Nguồn sự thật của schema là migration Flyway V1.** [application.yml](../../../event-service/src/main/resources/application.yml) đặt `spring.flyway.enabled: true` và `ddl-auto: validate`: Hibernate chỉ kiểm tra mapping khớp cột thật, không tự tạo/sửa bảng. [init.sql](init.sql) là bản tham chiếu cùng nội dung V1 (chạy tay khi cần dựng DB mẫu, không chạy trên DB đã có dữ liệu). Entity được dựng khớp cột/cột thời gian nên không còn mục "khác DDL" như bản tài liệu 2026-10-29.

Mọi cột ID dùng `CHAR(36)` lưu UUID (Hibernate `@JdbcTypeCode(SqlTypes.CHAR)`), không dùng `BINARY(16)`. Mọi cột thời gian dùng `DATETIME(6)` lưu **giờ UTC**: service ghi/đọc bằng `java.time.Instant` với `hibernate.jdbc.time_zone: UTC` và `forceConnectionTimeZoneToSession=true`, nên giá trị đọc trực tiếp trong MySQL là UTC, không phải giờ Việt Nam.

## 1. `categories` — Danh mục sự kiện

Một dòng là một danh mục để nhóm nhiều sự kiện, ví dụ Âm nhạc hoặc Hội thảo. Danh mục không phải hạng vé VIP/PREMIUM/STANDARD.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID danh mục, được `events.category_id` tham chiếu. | UUID danh mục C |
| `name` | VARCHAR(100), bắt buộc, `UNIQUE KEY uk_categories_name` | Tên danh mục dùng hiển thị/lọc sự kiện. UNIQUE ngăn hai dòng trùng tên theo collation MySQL (không phân biệt hoa/thường) — đây là chốt cuối khi hai ADMIN tạo cùng lúc; service trả 409 `CATEGORY_NAME_EXISTS`. | `Âm nhạc` |
| `description` | TEXT, được NULL | Giải thích danh mục bao gồm những sự kiện nào; có thể không nhập. | `Các chương trình biểu diễn âm nhạc.` |
| `created_at` | DATETIME(6), bắt buộc | Thời điểm tạo, UTC. Không có DEFAULT trong SQL; Hibernate `@CreationTimestamp` điền khi insert. | `2026-10-29 02:00:00` (UTC) |
| `updated_at` | DATETIME(6), bắt buộc | Thời điểm cập nhật gần nhất, UTC; Hibernate `@UpdateTimestamp` điền khi update. | `2026-10-29 03:00:00` (UTC) |

Một danh mục dùng được cho nhiều sự kiện. Xoá danh mục đang có sự kiện bị chặn: service kiểm `existsByCategoryId` trả 409 `CATEGORY_IN_USE`, và FK `fk_events_category` là chốt cuối ở tầng DB.

## 2. `events` — Thông tin và trạng thái sự kiện

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID chương trình/sự kiện mà khách mua vé tham dự. Đây là `eventId` nghiệp vụ dùng ở Ticket/Analytics, khác `messageId` Kafka. | UUID sự kiện E |
| `organizer_id` | CHAR(36), bắt buộc | Tài khoản tổ chức sở hữu sự kiện; dùng kiểm tra ai được sửa và xem báo cáo. **Khóa mềm**: là Keycloak `sub`, không có FK sang Auth/Keycloak. | UUID tài khoản ORGANIZER |
| `category_id` | CHAR(36), bắt buộc, FK `fk_events_category` → `categories.id` | Danh mục của sự kiện, trong cùng DB Event. Index cùng tên `fk_events_category` giúp kiểm tra FK khi xoá danh mục. | UUID danh mục Âm nhạc |
| `title` | VARCHAR(255), bắt buộc | Tên chương trình hiển thị cho khách và có thể chụp lại vào nội dung vé/email. Không phải tên topic Kafka. | `Đêm nhạc tháng 11` |
| `description` | TEXT, được NULL | Nội dung giới thiệu, thông tin chi tiết về sự kiện. | `Chương trình biểu diễn của các ban nhạc…` |
| `status` | VARCHAR(20), bắt buộc, DEFAULT `'DRAFT'`, CHECK `chk_events_status` | Giai đoạn duyệt/công bố/ngừng bán; xem bảng trạng thái bên dưới. | `PUBLISHED` |
| `start_time` | DATETIME(6), bắt buộc, UTC | Thời điểm bắt đầu sự kiện; dùng hiển thị, kiểm tra chưa tới giờ mới cho submit/publish, và điều kiện hoàn vé trước 48 giờ (Order). | `2026-11-15 12:00:00` (UTC) = 19:00 giờ Việt Nam |
| `end_time` | DATETIME(6), bắt buộc, UTC, CHECK `chk_events_time` (`start_time < end_time`) | Thời điểm kết thúc dự kiến. Không phải hạn giữ chỗ ba phút của Ticket. | `2026-11-15 15:00:00` (UTC) = 22:00 giờ Việt Nam |
| `created_at` | DATETIME(6), bắt buộc, UTC | Thời điểm tạo bản ghi. Không phải thời điểm chương trình diễn ra. | `2026-10-29 02:10:00` (UTC) |
| `updated_at` | DATETIME(6), bắt buộc, UTC | Thời điểm cập nhật gần nhất; cũng là khóa sắp xếp mặc định của `GET /api/v1/admin/events`. Khác `end_time`. | `2026-10-29 03:00:00` (UTC) |
| `review_reason` | TEXT, được NULL | Lý do ADMIN từ chối, để organizer biết cần sửa gì. API trả tên trường `rejectReason`. Xoá khi APPROVE và khi organizer sửa lại event REJECTED (bắt đầu vòng duyệt mới). | `Cần bổ sung nội dung chương trình.` |

| Giá trị status | Cách hiểu trong luồng nghiệp vụ |
|---|---|
| DRAFT | Bản nháp đang được người tổ chức chuẩn bị; sửa được. |
| SUBMITTED | Đã gửi duyệt, chờ admin xem xét. |
| APPROVED | Đã được duyệt, chưa tự đồng nghĩa đã công khai để bán. |
| REJECTED | Bị từ chối, có `review_reason`; sửa nội dung đưa về DRAFT rồi gửi lại. |
| PUBLISHED | Đã công bố và mở bán; luồng tìm kiếm public chỉ trả sự kiện trạng thái này. |
| CLOSED | Đã ngừng bán. Không tự có nghĩa hủy sự kiện, hủy vé hay mọi vé mất hiệu lực. |

Luồng chính: DRAFT → SUBMITTED → APPROVED → PUBLISHED → CLOSED. Nhánh từ chối: SUBMITTED → REJECTED → (sửa) DRAFT. Quyền sửa/submit/publish/close còn gắn với `organizer_id`; tài khoản có role ORGANIZER không sửa được sự kiện của người khác (403 `FORBIDDEN_NOT_OWNER`).

Chỉ mục: `idx_events_public (status, start_time)` cho tìm public theo trạng thái/khoảng thời gian, `idx_events_owner (organizer_id, created_at)` cho "sự kiện của tôi". C03 chốt một địa điểm với text cấu hình chung trong email QR; schema chưa có venue/location/zone/seat. C08: quota 0 không đủ để publish, cần ít nhất một hạng ACTIVE có `total_quota > 0` ở Ticket — Event chỉ gọi API Ticket, không JOIN DB Ticket.

## 3. Ánh xạ entity ↔ cột

[BaseEntity.java](../../../event-service/src/main/java/com/eventhub/event/entity/BaseEntity.java) là lớp cha `@MappedSuperclass` của `Category` và `Event`; nó không có bảng riêng, hai cột dưới được ánh xạ lên cả hai bảng.

| Cột | Entity | Ghi chú |
|---|---|---|
| `created_at` | `BaseEntity.createdAt` — `Instant`, `nullable = false`, `updatable = false`, `@CreationTimestamp` | Hibernate điền khi insert, không cần bật JPA Auditing. |
| `updated_at` | `BaseEntity.updatedAt` — `Instant`, `nullable = false`, `@UpdateTimestamp` | Hibernate điền mỗi lần update. |

| Cột | Entity | Ghi chú |
|---|---|---|
| `categories.id`, `events.id` | `@Id @UuidGenerator @JdbcTypeCode(SqlTypes.CHAR)`, `length = 36` | UUID do Hibernate sinh, không dùng AUTO_INCREMENT. |
| `categories.name` | `@Column(length = 100, unique = true)` | Trùng `uk_categories_name` của V1. |
| `categories.description`, `events.description`, `events.review_reason` | `@Column(columnDefinition = "TEXT")` | Khai báo tường minh để validate khớp TEXT. |
| `events.organizer_id` | `UUID organizerId`, `@JdbcTypeCode(SqlTypes.CHAR)`, `updatable = false` | So sánh với `sub` của token; đổi chủ sở hữu không được phép. |
| `events.category_id` | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "category_id")` | Trong Java là đối tượng `Event.category`; không có cột chứa JSON danh mục. Truy vấn đọc DTO đều fetch-join để tránh `LazyInitializationException` (`open-in-view: false`). |
| `events.status` | `EventStatus status` với `@Enumerated(EnumType.STRING)`, `length = 20` | Enum Java nằm ở `common`, không ở `entity`. DB là VARCHAR chứ không phải kiểu ENUM của MySQL. |
| `events.start_time`, `end_time` | `Instant`, `nullable = false` | Kiểm `start < end` ở cả service (400) và CHECK ở DB. |

## 4. Ví dụ đọc một sự kiện

Người tổ chức A tạo sự kiện E, danh mục Âm nhạc C, diễn ra tối 15/11:

1. `events` lưu `organizer_id = A`, `category_id = C`, `title`, `start_time/end_time` (UTC); ban đầu `status = DRAFT`.
2. Gửi duyệt → `status = SUBMITTED`. Admin từ chối thì `review_reason` ghi lý do; người tổ chức sửa lại (về DRAFT, xoá lý do) rồi gửi lại.
3. APPROVED là đã duyệt; sau thao tác publish (qua preflight Ticket) mới thành PUBLISHED và khách tìm kiếm được.
4. Ticket tạo các `ticket_types` với `event_id = E` trong DB riêng. Event không lưu price/quota của từng hạng.
5. Analytics nhận `eventId = E` trong message và dùng `organizer_id` để kiểm tra ai được xem báo cáo.

Xem [ERD](erd.md), [API Event](../../api/event-service-openapi.yaml) và các câu hỏi [C03/C08](../../project-clarifications.md).
