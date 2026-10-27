# Database EventHub — cách đọc và schema hiện hành

Nguồn: BC_32_V2 §2.3 kết hợp xác nhận UUID/Keycloak của chủ dự án. [Đối chiếu nguồn](../architecture/source-decisions.md), [câu hỏi còn mở](../project-clarifications.md). Tất cả DDL trong docs là tài liệu tham chiếu; Notification/Analytics phải chạy migration Flyway trong module, không chạy thêm init.sql thủ công.

## ERD đồng bộ với DDL

| Service | Ảnh SVG và mô tả đầy đủ cột |
|---|---|
| Auth | [Sơ đồ dữ liệu/Keycloak](auth-service/erd.md), chưa tạo bảng local không có yêu cầu |
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
| [Auth/Keycloak](auth-service/README.md) | Keycloak quản lý identity; Auth facade giữ UI/API riêng; profile DB chưa cần tạo | userId/sub mapping khi cấu hình realm |
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

Notification/Analytics đã dùng Flyway + validate. Auth/Event/Ticket/Order/Gateway còn Hibernate update của scaffold; tài liệu mục tiêu không tự tạo bảng cho các module này. Không chạy init.sql vào DB có dữ liệu, không sửa V1 đã áp dụng. Đổi schema dùng migration mới, backup và kiểm tra dữ liệu trước khi áp dụng. [Seed](../../infrastructure/mysql/seed/README.md).

Ảnh [Order](order-service/1.JPEG) và [Ticket](ticket-service/1.jpeg) đã được tạo lại từ cùng model với SVG hiện hành: PK và ID tham chiếu đều là UUID `CHAR(36)`. Có thể tạo lại bằng [render-erd.ps1](render-erd.ps1) trên Windows; `init.sql` và `erd.md` mô tả đầy đủ constraint/quan hệ. SVG là bản đọc bằng mã để đối chiếu tên/kiểu cột, JPEG là bản tương thích để xem.

Model Order hiện có đã đổi `BaseEntity.id`, `Order.userId`, `OrderItem.ticketTypeId` sang Java `UUID`, sinh bằng `GenerationType.UUID` và ánh xạ `CHAR(36)`. Các FK `order_id` kế thừa kiểu cột khóa chính UUID. Mapping PK/FK đã được kiểm tra; không giữ file test theo yêu cầu chủ dự án. Các field/trạng thái khác của model Order chưa được coi là đã triển khai đầy đủ hợp đồng V2.

Thay đổi mapping/DDL không tự chuyển dữ liệu số của DB đã tạo trước đây thành UUID hợp lệ. Nếu có dữ liệu cũ, cần migration giữ mapping UUID giữa các bảng/service; chỉ ép số sang chuỗi không tạo UUID 36 ký tự. Không reset/xóa DB để đổi kiểu ID.
