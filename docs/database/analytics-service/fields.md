# Giải thích các trường database Analytics Service

Database mặc định: `eventhub_analytics`. Analytics giữ dữ liệu tổng hợp để báo cáo nhanh và số liệu điểm danh. Order/Ticket là nơi phát sinh giao dịch gốc; Analytics nhận Kafka rồi cập nhật bản tổng hợp của riêng mình, không truy vấn JOIN sang database của hai service đó.

Nguồn đối chiếu: [DDL tham chiếu](init.sql), [migration V1](../../../analytics-service/src/main/resources/db/migration/V1__init.sql), [entity](../../../analytics-service/src/main/java/com/eventhub/analytics/entity), [plan Analytics](../../plans/analytics-service.md), [Kafka](../../kafka/events.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong).

Kiểu, NULL và DEFAULT dưới đây lấy từ V1. [application.yml](../../../analytics-service/src/main/resources/application.yml) hiện đặt `ddl-auto: update`, `flyway.enabled: false`, khác hướng dẫn Flyway/validate trong tài liệu cũ. Tài liệu này giải thích schema/file trong repo, không xác nhận database đang chạy đã giống V1.

## 1. `sales_metrics` — Doanh số theo sự kiện, hạng vé và ngày

Một dòng là số liệu của **một sự kiện + một hạng vé + một ngày**, không phải một đơn hàng hay một vé. Nhiều đơn mua vé VIP trong cùng ngày sẽ cộng vào cùng dòng. Tên hạng vé và số liệu tiền ở đây phục vụ báo cáo lịch sử, không thay thế bảng ticket_types của Ticket; bảng này không có cột giá bán một vé.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID của dòng thống kê. Không phải ID sự kiện hoặc ID đơn. | UUID của dòng VIP ngày 29/10 |
| `event_id` | CHAR(36), bắt buộc | Sự kiện đang được thống kê; ID mềm sang Event. API `/analytics/events/{id}/sales` lọc trường này. | UUID sự kiện “Đêm nhạc tháng 11” |
| `ticket_type_id` | CHAR(36), bắt buộc | Hạng vé cụ thể thuộc sự kiện đó; ID mềm sang Ticket. Hai sự kiện cùng có tên VIP vẫn có ID hạng vé khác nhau. | UUID hạng VIP của sự kiện trên |
| `ticket_type_name` | VARCHAR(100), bắt buộc | Tên hạng vé được lưu cùng số liệu để hiển thị báo cáo mà không cần gọi Ticket mỗi lần đọc. | `VIP` |
| `metric_date` | DATE, bắt buộc | Ngày mà số liệu được xếp vào, dùng lọc from/to và nhóm báo cáo theo ngày. Ngày Việt Nam từ paidAt/refundedAt theo xác nhận 2026-11-05. | `2026-10-29` |
| `revenue` | DECIMAL(14,2), bắt buộc, DEFAULT 0 | Tiền bán vé đã cộng vào dòng. Repository hiện cộng từ doanh số, không tự trừ tiền hoàn vào cột này. Không phải giá một vé. | `1000000.00` cho hai vé giá 500000 |
| `tickets_sold` | INT, bắt buộc, DEFAULT 0 | Tổng số vé đã bán được cộng vào dòng, không phải số đơn và không phải số vé còn bán. | `2` |
| `refunded_amount` | DECIMAL(14,2), bắt buộc, DEFAULT 0 | Tiền thực hoàn từ Order, tách khỏi revenue. C04 hoàn khách 70% giá đã mua, quantity là cả đơn; Analytics dùng allocation producer, không tính từ giá mới. | Đơn VIP `1000000.00` hoàn `700000.00` |
| `tickets_refunded` | INT, bắt buộc, DEFAULT 0 | Số vé hoàn được ghi vào dòng, tách khỏi tickets_sold. | `1` |
| `updated_at` | TIMESTAMP, bắt buộc | Thời điểm cập nhật số liệu do mã projection cung cấp. SQL repository hiện dùng GREATEST để timestamp không lùi khi message đến muộn. | `2026-10-29T03:05:00Z` |

UNIQUE `(event_id, ticket_type_id, metric_date)` giữ một dòng cho mỗi bộ ba. Các lần cập nhật dùng cách thêm mới nếu chưa có, cộng vào dòng cũ nếu đã có (UPSERT). V1 không có CHECK đảm bảo mọi bộ đếm không âm; kiểm tra dữ liệu vẫn cần ở luồng xử lý.

Ví dụ C04: bán hai VIP tổng 1000000, khách hoàn cả đơn nhận 700000, tickets_refunded=2; API sales tính netRevenue=300000. netRevenue là số tính, không phải cột V1. Phân bổ do Order thực hiện, không nhân số vé với 70%. Bù 100% khi chưa cung cấp vé dùng operation/message riêng dự kiến, không ghi refund vào doanh thu chưa có. [C04/C05](../../project-clarifications.md).

## 2. `dashboard_snapshot` — Số liệu tổng quan mới nhất của một sự kiện

“Snapshot” ở đây là bản tổng hợp hiện tại để tải dashboard nhanh. Mỗi sự kiện chỉ có một dòng, được cập nhật nhiều lần; không tạo dòng mới cho mỗi người check-in.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID riêng của dòng tổng quan; giữ nguyên khi cập nhật số liệu. | UUID của snapshot |
| `event_id` | CHAR(36), bắt buộc, UNIQUE | Sự kiện sở hữu snapshot. UNIQUE đảm bảo không có hai snapshot cho cùng sự kiện. | UUID sự kiện |
| `total_revenue` | DECIMAL(14,2), bắt buộc, DEFAULT 0 | Tổng doanh thu gộp cả sự kiện theo xác nhận 2026-11-05. Tiền hoàn được giữ riêng trong sales_metrics. | `1000000.00`, chỉ là giá trị minh họa |
| `total_tickets_sold` | INT, bắt buộc, DEFAULT 0 | Tổng số vé đã bán của cả sự kiện, giữ nguyên khi hoàn vé. Số vé hoàn được giữ riêng trong sales_metrics. | `2` |
| `current_attendance` | INT, bắt buộc, DEFAULT 0 | Số người hiện còn bên trong sự kiện: đã check-in và chưa check-out theo dữ liệu nhận được. | `1` |
| `total_checked_in` | INT, bắt buộc, DEFAULT 0 | Tổng vé đã từng check-in hợp lệ. Không giảm chỉ vì người đó check-out. Trong V2 mỗi vé chỉ vào một lần. | `2` |
| `last_updated` | TIMESTAMP, bắt buộc | Mốc cập nhật snapshot do projection cung cấp. API đổi tên thành updatedAt. | `2026-10-29T03:10:00Z` |

Ví dụ hai người đã vào, một người đã ra: `current_attendance=1`, `total_checked_in=2`. Các số này không phải số lượt gọi API hoặc số lần thiết bị quét lỗi.

Database yêu cầu last_updated không NULL **khi dòng tồn tại**. API có thể trả updatedAt=null và các số 0 khi chưa có dòng snapshot, sau khi đã kiểm tra sự kiện và quyền truy cập; đó là response dự phòng, không phải một dòng DB có NULL.

## 3. `attendance_logs` — Lịch sử hành động vào/ra

Một dòng ghi một hành động vào hoặc ra mà Analytics nhận từ Ticket. Một vé có thể có một dòng CHECK_IN và một dòng CHECK_OUT. Bảng này phục vụ tra cứu lịch sử, khác snapshot là các con số tổng cộng.

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | ID của dòng log. Entity hiện sinh UUID riêng; V1 không có cột source_message_id trên bảng log. | UUID của log check-in |
| `ticket_id` | CHAR(36), bắt buộc | Vé đã được quét; ID mềm sang Ticket. | UUID vé của Lan |
| `event_id` | CHAR(36), bắt buộc | Sự kiện nơi hành động vào/ra xảy ra; giúp lọc log theo sự kiện. | UUID sự kiện |
| `gate_id` | CHAR(36), được NULL | Mã cổng/thiết bị quét từ dữ liệu nghiệp vụ. V1 Analytics cho NULL dù Ticket ticket_scans yêu cầu gate_id; không có bảng gates trong schema này. | UUID cổng A, hoặc NULL ở V1 |
| `action` | VARCHAR(10), bắt buộc | Hành động đã xảy ra: CHECK_IN là vào, CHECK_OUT là ra. Enum code có hai giá trị này; SQL V1 không có CHECK cho action. | `CHECK_IN` |
| `occurred_at` | TIMESTAMP, bắt buộc | Thời điểm hành động gốc xảy ra, lấy từ thời gian quét trong message; không phải lúc Kafka giao message cho Analytics. | `2026-10-29T03:09:00Z` |

Chỉ mục `(event_id, occurred_at)` hỗ trợ xem lịch sử theo thời gian. V1 chưa có dữ liệu trạng thái từng vé đủ để xử lý chắc chắn CHECK_OUT đến trước CHECK_IN. V2 đã bổ sung attendance_state và log lấy id=messageId để phục vụ projection chịu đảo thứ tự.

## 4. `processed_events` — Chống cộng một message nhiều lần

| Trường | Kiểu / ràng buộc theo SQL | Ý nghĩa và lúc sử dụng | Ví dụ minh họa |
|---|---|---|---|
| `id` | CHAR(36), PK, bắt buộc | Chính messageId của Kafka, do consumer truyền vào. Không phải eventId và không được sinh UUID mới mỗi lần retry. | UUID message `order.paid` |
| `processed_at` | TIMESTAMP, bắt buộc | Thời điểm ghi nhận xử lý message; entity gán khi insert. | `2026-10-29T03:00:03Z` |

Theo thiết kế, ghi dấu message và cộng số liệu cùng một transaction: cả hai cùng thành công hoặc cùng rollback. Nếu cộng lỗi thì không được để lại dấu “đã xử lý” khiến lần nhận lại bị bỏ qua. Analytics và Notification có bảng processed_events riêng, vì hai service đều cần xử lý cùng một số message.

## 5. Từ database tới các API bạn đang đọc

| API | Nguồn dữ liệu / trường cần chú ý |
|---|---|
| GET `/api/v1/analytics/events/{id}/sales?from=&to=` | Đọc sales_metrics theo event_id và metric_date; cộng các cột tiền/vé, nhóm ngày hoặc hạng vé. |
| GET `/api/v1/analytics/events/{id}/attendance` | Đọc dashboard_snapshot.current_attendance, total_checked_in, last_updated. |
| GET `/api/v1/analytics/events/{id}/dashboard` | Đọc snapshot của event_id, trả số liệu tổng quan trước cập nhật WebSocket theo thiết kế. |

`metric_date → date`, `last_updated → updatedAt`, `ticket_type_name → ticketTypeName` là các ví dụ đổi tên snake_case của DB sang camelCase của DTO. `from`/`to`, `daily`, `byTicketType`, `netRevenue` là bộ lọc hoặc cấu trúc response, không phải cột mới.

Luồng thiết kế: order.paid cộng sales; order.refunded ghi phần hoàn; ticket.checked-in/out cập nhật điểm danh. Không cộng thêm sales từ ticket.issued. Dashboard giữ tổng gộp toàn sự kiện, sales report tách gộp/hoàn/thuần theo ngày Việt Nam. Xem [ERD](erd.md), [API Analytics](../../api/analytics-service-openapi.yaml) và [các quyết định còn mở](../../project-clarifications.md).

## 6. attendance_state trong V2

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| id | CHAR(36), PK | UUID bản ghi trạng thái |
| ticket_id | CHAR(36), UNIQUE | UUID vé, một dòng cho mỗi vé |
| event_id | CHAR(36) | UUID sự kiện của vé |
| checked_in_at | TIMESTAMP NULL | Bằng chứng check-in, giữ khi checkout đã đến trước |
| checked_out_at | TIMESTAMP NULL | Bằng chứng checkout, giữ khi chưa nhận check-in |

Đóng góp hiện diện bằng 1 khi có check-in và chưa có checkout. Projection khóa dòng theo vé, cập nhật claim/log/snapshot cùng transaction. Migration ở [V2](../../../analytics-service/src/main/resources/db/migration/V2__attendance_state.sql).
