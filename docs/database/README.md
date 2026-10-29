# Database EventHub — cách đọc và schema hiện hành

Nguồn: BC_32_V2 §2.3 kết hợp xác nhận UUID/Keycloak của chủ dự án. [Đối chiếu nguồn](../architecture/source-decisions.md), [câu hỏi còn mở](../project-clarifications.md). Tất cả DDL trong docs là tài liệu tham chiếu; Notification/Analytics phải chạy migration Flyway trong module, không chạy thêm init.sql thủ công.

## Bắt đầu từ tài liệu giải thích từng trường

Bộ tài liệu cập nhật 2026-10-29 dành cho việc đọc hiểu database. Mỗi file giải thích một dòng trong từng bảng đại diện cho điều gì, từng trường dùng để làm gì, kiểu/NULL/mặc định, ví dụ, trạng thái và cách các trường tham gia luồng nghiệp vụ. Đọc phần mô tả bảng trước rồi mới tra từng cột.

| Service | Tài liệu | Những điểm được giải thích |
|---|---|---|
| Auth | [Giải thích trường Auth](auth-service/fields.md) | User local trong code, audit và ranh giới Keycloak; chưa có DDL Auth được duyệt. |
| Event | [Giải thích trường Event](event-service/fields.md) | Danh mục, người tổ chức, duyệt/công bố, thời gian sự kiện và khác biệt audit của entity. |
| Ticket | [Giải thích trường Ticket](ticket-service/fields.md) | Giá/quota, available/reserved/sold, giữ chỗ, vé QR, quét vào/ra và outbox. |
| Order | [Giải thích trường Order](order-service/fields.md) | Đơn, dòng mua, từng người dùng vé, thanh toán, webhook và yêu cầu hoàn. |
| Notification | [Giải thích trường Notification](notification-service/fields.md) | Mẫu và nội dung đã điền, người nhận/kênh, gửi/đọc, khóa chống trùng. |
| Analytics | [Giải thích trường Analytics](analytics-service/fields.md) | Doanh số theo ngày/hạng, dashboard, lịch sử vào/ra và messageId. |

Ví dụ chỉ minh họa ý nghĩa, không phải dữ liệu seed hoặc xác nhận chính sách chưa chốt. UUID có thể được viết ngắn thành E (sự kiện), U (tài khoản), O (đơn), R (giữ chỗ), T (vé), M (message) trong lời giải thích; khi lưu thật phải là UUID hợp lệ, ví dụ `30000000-0000-4000-8000-000000000001`.

Nguồn được phân biệt rõ: init.sql là schema tham chiếu; V1 là migration nền Notification/Analytics; entity là mapping hiện có. Các file có thể khác nhau và không thay thế việc xem database đang chạy. API Gateway không có bảng nghiệp vụ riêng trong bộ DDL này; datasource scaffold không có nghĩa có thêm bảng cần tự suy ra.

<a id="cach-doc-tu-dien-truong"></a>

## Cách đọc từ điển trường

| Ký hiệu / kiểu | Giải thích dễ hiểu |
|---|---|
| Một dòng / bản ghi | Một đối tượng hoặc lần ghi nhận: một ticket là một vé; một order_item có thể đại diện hai vé cùng hạng. |
| PK / id | Khóa chính phân biệt từng dòng trong bảng. Hai bảng cùng có id không có nghĩa dùng chung ID. |
| FK vật lý | ID tham chiếu dòng cha trong cùng DB, được SQL kiểm tra. Ví dụ order_items.order_id → orders.id. |
| ID mềm | ID đối tượng do service khác quản lý; không có FK xuyên DB. Kiểm tra qua API/message theo thiết kế. |
| Bắt buộc / NOT NULL | Dòng lưu phải có giá trị. Không tự có nghĩa chuỗi không được rỗng hoặc đã qua kiểm tra nghiệp vụ. |
| Được NULL | Chưa có/không áp dụng thông tin đó. NULL khác 0 hay chuỗi rỗng; read_at=NULL nghĩa chưa đánh dấu đọc. |
| DEFAULT | Giá trị SQL dùng nếu insert bỏ cột. Khởi tạo Java/@Builder.Default không tự là SQL DEFAULT; truyền NULL rõ ràng khác bỏ cột. |
| UNIQUE một/nhiều cột | Không được lặp giá trị hoặc tổ hợp giá trị. Hai template cùng code vẫn được nếu channel khác. |
| CHECK | Điều kiện SQL kiểm tra, ví dụ quantity > 0. Quy tắc trong tài liệu chưa chắc đã có CHECK trong DDL. |
| INDEX / chỉ mục | Giúp tìm nhanh theo những cột đã có; idx_inapp không phải một cột mới và không tự tạo nghiệp vụ. |
| CHAR(36) | Chuỗi 36 ký tự dùng cho UUID ứng dụng; Java thường là UUID. QR/mã provider là chuỗi riêng, không ép UUID. |
| VARCHAR(n) / TEXT | Nội dung chữ: VARCHAR giới hạn độ dài khai báo, TEXT cho nội dung dài. Điện thoại lưu chuỗi để giữ số 0 đầu. |
| INT | Số nguyên để đếm lượng, lượt hoặc lần thử. |
| DECIMAL(14,2) | Số chính xác tối đa 14 chữ số tổng cộng, trong đó 2 số sau dấu thập phân; Java BigDecimal, ví dụ 500000.00. |
| DATE | Chỉ có ngày, không có giờ; metric_date=2026-10-29. Cần quy tắc nghiệp vụ để xếp giao dịch vào ngày nào. |
| TIMESTAMP | Mốc thời gian; quy ước UTC, ví dụ 03:00Z là 10:00 tại Việt Nam. Ghi/đọc cần cấu hình timezone phù hợp. |
| LocalDateTime trong entity | Ngày giờ không có offset/múi giờ; không tự đảm bảo UTC. Phân biệt với Instant và DDL mục tiêu. |
| JSON | Dữ liệu có cấu trúc trong một cột; key trong payload không tự là các cột riêng. |

| Thành phần tên / thuật ngữ | Cách đọc trong ngữ cảnh EventHub |
|---|---|
| `*_id`, `*_by` | ID đối tượng/người thao tác. requested_by là ai yêu cầu; approved_by là ai duyệt. Provider IDs là chuỗi bên ngoài. |
| `*_at`, `*_time` | Mốc việc gì xảy ra: paid_at lúc trả tiền, start_time lúc sự kiện bắt đầu. |
| quantity / quota / total | Lượng theo ngữ cảnh / hạn mức phân bổ / số tổng hợp. total không tự nói trước hay sau hoàn. |
| status | Trạng thái đối tượng trong chính bảng đó: SENT của notification khác SUCCEEDED của payment và PAID của order. |
| snapshot | Bản chụp dữ liệu: giá/tên lúc mua hoặc số tổng hợp dashboard tại lần cập nhật gần nhất. Đọc ngữ cảnh để biết có tiếp tục cập nhật không. |
| projection | Bản đọc/báo cáo dựng từ message, như sales_metrics; không phải nguồn giao dịch gốc. |
| outbox | Hộp thư đi trong DB, để worker gửi Kafka và thử lại khi cần. |
| dedup / idempotency | Nhận ra cùng yêu cầu/message gửi lại, tránh gửi thông báo/thu tiền/cộng số liệu thêm lần nữa. UNIQUE là một phần của luồng. |

Phân biệt ID: **eventId là sự kiện khách tham dự; messageId là một thông điệp về việc vừa xảy ra**. Một sự kiện có thể phát sinh hàng nghìn message. processed_events.id lưu messageId; outbox_events.event_type là loại message như order.paid, không phải tiêu đề sự kiện.

## ERD đồng bộ với DDL

| Service | Ảnh SVG và mô tả đầy đủ cột |
|---|---|
| Auth | [Sơ đồ ranh giới Keycloak](auth-service/erd.md); mapping User local hiện có được giải thích trong [từ điển trường](auth-service/fields.md), chưa có DDL local được duyệt |
| Event | [ERD](event-service/erd.md) |
| Ticket | [ERD](ticket-service/erd.md) |
| Order | [ERD](order-service/erd.md) |
| Notification | [ERD V1](notification-service/erd.md) |
| Analytics | [ERD V1](analytics-service/erd.md) |

Chạy `node docs/database/render-erd.mjs` từ root để sinh SVG/Mermaid từ DDL; `node docs/database/render-erd.mjs --check` phát hiện lệch nguồn và từ chối ID không dùng CHAR(36). Chạy `docs/database/render-erd.ps1` trên Windows để sinh lại cả SVG/Mermaid và JPEG Order/Ticket từ cùng model. SVG chứa tên cột/kiểu dưới dạng text, đọc được bằng mã, không chỉ là ảnh chụp. Đã kiểm tra parser đọc cả nhiều cột trên cùng một dòng của V1.

Phần bảng/cột bổ sung còn đề xuất trong [plan](../plans/README.md) chưa nằm trong ERD nền; chỉ thêm sau khi có DDL/migration tương ứng. Không dùng plan để suy rằng DB đã có sẵn pending_operations, notification_batches hoặc attendance_state.

## Quyền sở hữu và quan hệ

| Nguồn dữ liệu | Bảng chính | ID sang service khác |
|---|---|---|
| [Auth/Keycloak](auth-service/README.md) | Keycloak quản lý identity; local User đã có trong code Auth, chưa có DDL local được duyệt; xem [trường hiện có](auth-service/fields.md) | userId/sub mapping cần cấu hình/đồng bộ |
| [Event](event-service/init.sql) | categories, events | organizer_id |
| [Ticket](ticket-service/init.sql) | ticket_types, reservations/items, tickets, ticket_scans; outbox kỹ thuật | event_id, order_id, owner_user_id, gate_id |
| [Order](order-service/init.sql) | orders/items/holders, payments, refund_requests, payment_webhooks; outbox kỹ thuật | customer_id, event_id, reservation_id, ticket_type_id, requested_by, approved_by |
| [Notification](notification-service/init.sql) | notification_templates, notifications, processed_events | recipient_user_id; source_message_id là UUID message |
| [Analytics](analytics-service/init.sql) | sales_metrics, dashboard_snapshot, attendance_logs, processed_events | event_id, ticket_id, ticket_type_id, gate_id |

FK vật lý chỉ nối bảng cùng DB. Ví dụ order_items.order_id → orders.id là FK; orders.reservation_id → Ticket là UUID mềm, không FK. Ticket không có events riêng làm nguồn sự thật. Keycloak có DB do chính nó quản lý; không phải bảng người dùng trong eventhub_auth.

PK/tham chiếu bảng ứng dụng: UUID CHAR(36), không số tự tăng. Mọi thời điểm lưu UTC; tiền DECIMAL(14,2) và BigDecimal. provider_transaction_id/provider_event_id/idempotency_key/QR là mã opaque bên ngoài hoặc khóa nghiệp vụ, không ép UUID.

## Từ điển dữ liệu

DDL từng service liệt kê đầy đủ cột, kiểu, NULL, unique và index. Tên trường ứng dụng snake_case, DTO camelCase. Ba điểm dễ nhầm:

1. ticket_types đồng thời là loại vé và tồn kho; available + reserved + sold = total_quota. Không có ticket_inventory. Khóa hàng hoặc UPDATE điều kiện, không đọc số lượng rồi ghi lại không có khóa.
2. order_items là một dòng mỗi hạng vé; order_holders là từng người dùng vé. Kiểm tra số holder bằng quantity và giữ thứ tự/ID holder ổn định khi retry confirm. Đây là invariant service, SQL FK không kiểm tra tổng số dòng con.
3. processed_events.id là messageId Kafka, không phải eventId sự kiện. dashboard_snapshot.id là UUID PK, event_id UNIQUE; không sinh snapshot mới cho mỗi lần cập nhật.

## Ánh xạ tên/thiết kế từ báo cáo

| BC_32_V2 | Hợp đồng hiện hành | Lý do |
|---|---|---|
| users/password_hash, refresh_tokens | Không triển khai trong ứng dụng | Quyết định mới chọn Keycloak. Auth facade đã chốt; profile DB chỉ thêm khi cần dữ liệu ngoài Keycloak. |
| event_categories/categories | categories | Chuẩn hóa theo Bảng 2.4. |
| zones A–D trong hình cũ | Không có bảng zones/seat trong baseline | Chủ dự án chốt ba hạng, từ điển V2 chỉ ticket_types. Địa điểm còn C03. |
| order_holders, approved_by | Giữ đúng tên | Không nhét holder trực tiếp vào order_items; không đổi approved_by thành reviewed_by không có mapping. |
| orders DEFAULT PENDING | PENDING_PAYMENT | Sửa lỗi không khớp máy trạng thái. |
| provider_event_id UNIQUE | UNIQUE(provider, provider_event_id) | Bổ sung kỹ thuật để các provider không đụng ID; payload_hash không thay chữ ký. |
| processed_events.event_id | processed_events.id = messageId | Tách UUID message khỏi UUID sự kiện theo xác nhận. |
| notification_templates.code PK | id UUID PK + UNIQUE(code,channel) | UUID mọi bảng; cùng mã có EMAIL và IN_APP. |
| notifications chỉ email/status | Bổ sung recipient_user_id/channel/title/content/payload và dedup/retry | Thực hiện Hình 2.75–2.77 và tránh gửi trùng khi retry. |
| sales_metrics.record_date | metric_date | Giữ tên của migration nền; DTO dùng date. |
| sales_metrics thiếu hạng/refund | ticket_type_id/name, refunded_amount/tickets_refunded | Đáp ứng báo cáo theo hạng; cách tính hoàn còn C04/C09. |
| dashboard_snapshot.event_id PK | id UUID PK, event_id UNIQUE | Chuẩn PK thống nhất với schema nền; không thay đổi ý nghĩa một snapshot/sự kiện. |
| total_tickets | total_tickets_sold | Là vé bán, không là quota; API totalTicketsSold. Ý nghĩa gộp/thuần C09. |
| dashboard updated_at | last_updated → API updatedAt | Mapping tên rõ ràng, giữ migration V1. |
| attendance logged_at | occurred_at → API scannedAt/log time theo ngữ cảnh | Lưu thời điểm nghiệp vụ gốc; không lấy thời điểm consumer xử lý. |

## Bổ sung kỹ thuật và giới hạn

Event bổ sung review_reason để lưu lý do duyệt/từ chối; Order bổ sung refund_requests.review_reason để lưu lý do phản hồi, tách khỏi reason của người yêu cầu. Đây là bổ sung kỹ thuật phục vụ các request review/reject đã có, không phải cột gốc trong từ điển báo cáo.

Reference Ticket bổ sung gate_id theo Hình 2.55/2.79 và UUID đã xác nhận; cancelled_at theo Hình 2.54. Không có bảng gates vì owner còn C07. Outbox trong Ticket/Order là đề xuất kỹ thuật để không mất message khi crash; nó không có nghĩa đã triển khai publisher. Outbox.id phải bằng messageId trong payload.

DDL giữ holder_email NULL theo V2; luồng gửi mail còn thiếu fallback C02. Không thêm customer_email NOT NULL hoặc venue NOT NULL trước khi quyết định nguồn. Không tạo refund_items trước khi biết có hoàn từng vé; nếu có phải bổ sung migration và Kafka phân bổ tiền.

Migration Analytics V1 là nền, **chưa đủ chống đảo thứ tự check-in/out bằng một counter**: cần dữ liệu trạng thái/định danh log hoặc projection bổ sung ở migration tiếp theo. Cách tính snapshot/refund còn C09; không gắn nghĩa thuần/gộp mơ hồ vào code. Job retry confirm/refund/cancel cũng cần storage bền vững ở phần triển khai, chưa có trong DDL báo cáo.

## Triển khai và seed

Analytics/Notification đã có đủ bảy entity ánh xạ V1 và enums; metadata đã được đối chiếu với migration. File test đã được xóa theo yêu cầu chủ dự án. Chủ dự án chọn giữ schema hiện tại và bỏ qua BaseEntity: không thêm create_at/update_at/created_by/updated_by/deleted, không thêm migration audit. ProcessedEvent.id phải được gán bằng messageId; các entity nghiệp vụ còn lại dùng UUID generator. Timestamps nghiệp vụ của Analytics được service/projection gán, không thay bằng clock audit.

Đối chiếu file ngày 2026-10-29: Notification/Analytics có migration V1, nhưng application.yml hiện đặt Flyway disabled và Hibernate update, khác thiết kế Flyway + validate được ghi trong tài liệu trước đó. Các module khác cũng đang cấu hình Hibernate update. Môi trường chạy có thể override qua biến môi trường; chưa kiểm tra schema/setting DB trực tiếp trong bộ từ điển này. Không chạy init.sql vào DB có dữ liệu hoặc sửa V1 đã áp dụng để đồng bộ tài liệu. Đổi schema cần migration phù hợp. [Seed](../../infrastructure/mysql/seed/README.md).

Ảnh [Order](order-service/1.JPEG) và [Ticket](ticket-service/1.jpeg) đã được tạo lại từ cùng model với SVG hiện hành: PK và ID tham chiếu đều là UUID `CHAR(36)`. Có thể tạo lại bằng [render-erd.ps1](render-erd.ps1) trên Windows; `init.sql` và `erd.md` mô tả đầy đủ constraint/quan hệ. SVG là bản đọc bằng mã để đối chiếu tên/kiểu cột, JPEG là bản tương thích để xem.

Code Order hiện có bảy entity trong package entity, với customer_id/event_id/reservation_id, các bảng holders/webhooks/outbox và enum theo schema tham chiếu. BaseEntity hiện chỉ chứa created_at/updated_at, ID nằm trên từng entity; không còn dùng mô tả model cũ có user_id làm từ điển hiện hành. [Từ điển Order](order-service/fields.md) giải thích các trường này. Có mapping chưa chứng minh luồng thanh toán, retry hay database thực tế đã được đồng bộ đầy đủ.

Thay đổi mapping/DDL không tự chuyển dữ liệu số của DB đã tạo trước đây thành UUID hợp lệ. Nếu có dữ liệu cũ, cần migration giữ mapping UUID giữa các bảng/service; chỉ ép số sang chuỗi không tạo UUID 36 ký tự. Không reset/xóa DB để đổi kiểu ID.
