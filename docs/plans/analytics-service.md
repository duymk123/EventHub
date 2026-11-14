# Plan code analytics-service

Cập nhật 2026-11-13: thêm §8 **Thống kê toàn hệ thống cho ADMIN** (C10), gồm 2 API public, 2 API nội bộ ở Event/Ticket và migration V3 chỉ thêm index. Ngày 2026-11-14 đã triển khai phần analytics-service, xem §8.10; hai API nội bộ ở Event/Ticket chưa có. §1–§7 giữ nguyên.

Cập nhật triển khai 2026-11-05 có REST security/S2S, projection Kafka và STOMP. Dashboard là tổng doanh thu gộp/số vé bán của cả sự kiện. Báo cáo giữ riêng gộp/hoàn/thuần và chia ngày theo Asia/Ho_Chi_Minh. Schema attendance_state đã có migration V2/entity và DDL/ERD. Các mô tả đề xuất C09 cũ dưới đây được cập nhật theo xác nhận này. Xem [hướng dẫn chạy](../notification-analytics-implementation.md).

Ngày 2026-10-28. Đọc [quy ước chung](00-cross-service-contracts.md), [OpenAPI Analytics](../api/analytics-service-openapi.yaml), [DDL/V1](../database/analytics-service/init.sql), [ERD](../database/analytics-service/erd.md), [Kafka](../kafka/events.md). Analytics là read model từ Kafka, không JOIN Order/Ticket/Event DB.

## 1. Entity, model và migration

V1 có SalesMetric(id/eventId/ticketTypeId UUID, ticketTypeName, metricDate, revenue/ticketsSold/refundedAmount/ticketsRefunded/updatedAt), DashboardSnapshot(id UUID, eventId UNIQUE UUID, totalRevenue/totalTicketsSold/currentAttendance/totalCheckedIn/lastUpdated), AttendanceLog(id/ticketId/eventId/gateId UUID, action, occurredAt), ProcessedEvent(id=messageId UUID).

Đã code bốn entity tại [entity](../../analytics-service/src/main/java/com/eventhub/analytics/entity), cùng enum AttendanceAction đặt trực tiếp trong common của service. Theo xác nhận mới nhất, giữ V1 và bỏ qua BaseEntity/audit columns. UUID lưu CHAR(36), action VARCHAR(10), tiền DECIMAL(14,2). SalesMetric.updatedAt, DashboardSnapshot.lastUpdated và AttendanceLog.occurredAt được projection gán rõ ràng, không dùng audit callback để ghi đè thời điểm nghiệp vụ; ProcessedEvent.processedAt được Hibernate gán khi insert.

V2 đã bổ sung `attendance_state(id UUID, ticket_id UUID UNIQUE, event_id UUID, checked_in_at/checked_out_at TIMESTAMP NULL)` và index cần query; không sửa V1 đã áp dụng. AttendanceLog có thể dùng id=messageId để audit/replay ổn định, không dùng ticketId làm PK log vì mỗi vé có hai action.

Dashboard giữ tổng gộp của toàn sự kiện. API SalesReport tách gộp/refund/net, byTicketType và daily theo ngày Việt Nam. Không đặt công thức thành mặc định business trong entity chỉ vì field tên total. Tiền refund theo hạng cần items[].refundedAmount và C04, quantity đơn lẻ chưa đủ.

## 2. Query/access service

| Signature | Hành vi / transaction |
|---|---|
| `EventAccessService.assertCanView(Actor actor, UUID eventId): EventAccessContext` | ADMIN hoặc ORGANIZER owner; gọi EventClient để xác minh tồn tại/owner, mọi status được phép theo nghiệp vụ analytics. CUSTOMER bị 403, nonexistent 404, upstream lỗi 503, không giả snapshot 0 để che lỗi. |
| `AnalyticsQueryService.getSales(Actor actor, UUID eventId, LocalDate from, LocalDate to): SalesReportResponse` | Access trước → validate from≤to → read-only aggregate theo ngày/hạng. Thiếu đầu nào không giới hạn đầu đó. Mapping tiền gộp/refund/net theo xác nhận 2026-11-05, không load entity từng ticket. |
| `getAttendance(Actor actor, UUID eventId): AttendanceResponse` | Access rồi đọc snapshot; chưa có projection trả currentAttendance=0,totalCheckedIn=0,updatedAt=null. |
| `getDashboard(Actor actor, UUID eventId): DashboardSnapshotResponse` | Access rồi cùng DTO dùng WS; không có projection trả số 0/null time, không tạo một snapshot giả trong GET. |
| `DashboardReadService.getForPush(UUID eventId): DashboardSnapshotResponse` | Nội bộ server sau commit, transaction read-only mới để tránh persistence context stale; không là public bypass quyền. |

Access là lớp trước transaction DB query dài, không giữ DB connection lúc đợi Event HTTP. Cache chỉ metadata owner/tồn tại, không cache quyền theo mỗi eventId mà bỏ user/role; khóa đúng context và invalidation/TTL ngắn. Timeout 503 không được cache như event không tồn tại. Chi tiết TTL do triển khai chọn có test stale ownership.

## 3. Projection service

| Signature | Hành vi |
|---|---|
| `SalesProjectionService.handlePaid(OrderPaidEvent event): void` | Validate envelope/amount/items/currency → transaction claim messageId → UPSERT từng type/date từ paidAt → update snapshot đúng công thức đã chốt → publish DashboardChanged local event → commit. Không subscribe ticket.issued để cộng lần nữa. |
| `handleRefunded(OrderRefundedEvent event): void` | Claim và ghi thực hoàn theo refundedAt; C04 amount=70% giá mua, items gồm mọi hạng và toàn bộ quantity, tiền allocation tổng bằng amount. Không lấy giá hiện tại hoặc tính lại 70% ở consumer. Bù chưa có sale không gửi vào handler này. |
| `AttendanceProjectionService.handleCheckedIn(TicketCheckedInEvent event): void` | Claim message → create/lock state ticket → ghi bằng chứng in/scannedAt → tính delta trước/sau → append log → atomic snapshot → event local, cùng transaction. |
| `handleCheckedOut(TicketCheckedOutEvent event): void` | Giữ bằng chứng out kể cả chưa nhận in; không trừ âm rồi clamp, không bỏ out chỉ vì chưa có in. Cùng transaction và lock thứ tự thống nhất. |
| `MessageDeduplicationService.claim(UUID messageId, Instant at): void` | INSERT unique processed_events ở transaction hiện tại; nếu projection fail thì claim rollback. Duplicate đúng claim được outer consumer bỏ qua sau rollback, không nuốt mọi unique error khác. |
| `DashboardPushService.onChanged(DashboardChangedEvent event): void` | AFTER_COMMIT, đọc snapshot mới qua DashboardReadService, gửi /topic/event/{id}/dashboard. Có thể coalesce, push fail không rollback projection đã commit; client dùng GET để phục hồi. |

Không bọc một service projection trong transaction mới rồi lại tạo processed ở transaction khác. Snapshot update dùng SQL cộng/trừ/upsert nguyên tử để Sales và Attendance không ghi đè cột của nhau; entity read-modify-save không có lock/version là không đủ.

## 4. Attendance chịu đảo thứ tự

Trong V2 không tái nhập. Với mỗi vé: `inSeen` là đã nhận check-in hợp lệ; `outSeen` là đã nhận check-out. `currentContribution = inSeen && !outSeen ? 1 : 0`, `checkedInContribution = inSeen ? 1 : 0`.

| Dữ liệu đến | Delta current | Delta totalCheckedIn |
|---|---:|---:|
| IN đầu tiên, chưa OUT | +1 | +1 |
| OUT sau IN | -1 | 0 |
| OUT đến trước IN | 0 | 0 |
| IN đến sau OUT | 0 | +1 |
| Replay message/bằng chứng đã có | 0 | 0 |

Không lấy action đến sau cùng theo thời gian consumer làm trạng thái; cả hai bằng chứng phải được giữ. Check state.eventId khớp message, phát hiện một ticket thuộc hai event không được cộng vào cả hai. Unique ticket_id + INSERT/upsert để tạo state, sau đó SELECT FOR UPDATE; hai consumer cùng tạo state phải hội tụ về một row. Snapshot delta/log/claim commit cùng nhau.

Sau khi nhận đủ cặp IN/OUT, cả hai thứ tự đều cho current=0, totalCheckedIn=1. Unit test kiểm tra bảng chuyển, MySQL test kiểm tra cả hai handler chạy đồng thời. Không nhờ key ticketId bảo đảm thứ tự xuyên topic.

## 5. Repository methods

| Repository / signature | SQL/query và mục đích |
|---|---|
| `SalesMetricRepository.upsertSale(UUID eventId, UUID typeId, String typeName, LocalDate date, BigDecimal revenue, int qty, Instant at): int` | INSERT ON DUPLICATE KEY UPDATE revenue+=, tickets_sold+=; unique event/type/date. |
| `upsertRefund(UUID eventId, UUID typeId, String typeName, LocalDate date, BigDecimal amount, int qty, Instant at): int` | Chỉ refund_amount/tickets_refunded; không trừ revenue của ngày cũ. |
| `sumTotals(UUID eventId, LocalDate from, LocalDate to): SalesTotals` | Projection aggregate, missing bounds không giới hạn; COALESCE tổng trống về 0. |
| `aggregateDaily(UUID, LocalDate, LocalDate): List<DailySalesRow>` | GROUP BY metric_date, sort ngày tăng; không lặp thêm lượt bán do join type. |
| `aggregateByTicketType(UUID, LocalDate, LocalDate): List<TicketTypeSalesRow>` | GROUP BY type ID; tên snapshot chọn nhất quán, không GROUP riêng theo tên làm tách cùng ID sau đổi tên. |
| `DashboardSnapshotRepository.findByEventId(UUID): Optional<DashboardSnapshot>` | Một row/event, không dùng UUID id mới mỗi update. |
| `addSales(UUID eventId, UUID initialSnapshotId, BigDecimal amount, int qty, Instant at): int` | UPSERT phần sales, giữ nguyên attendance. UUID initial id chỉ dùng khi INSERT; duplicate không đổi PK. |
| `applyRefund(UUID eventId, UUID initialSnapshotId, BigDecimal amount, int qty, Instant at): int` | Dashboard gộp giữ nguyên phần sales khi nhận refund; không clamp mất adjustment khi refund tới trước sale. |
| `addAttendanceDelta(UUID eventId, UUID initialSnapshotId, int currentDelta, int checkedInDelta, Instant at): int` | Atomic update đúng hai cột attendance, giữ sales. Delta từ state locked, không trực tiếp lấy +1/-1 theo topic. |
| `AttendanceStateRepository.ensureExists(UUID stateId, UUID ticketId, UUID eventId): void` | Sau V2, unique ticketId, retry insert race đúng cách hoặc upsert giữ row cũ. |
| `findByTicketIdForUpdate(UUID): Optional<AttendanceState>` | Lock khi đọc đóng góp trước/sau và cập nhật evidence. |
| `AttendanceLogRepository.save(AttendanceLog)` | Append id=messageId hoặc UUID riêng với unique sourceMessageId sau migration; chọn một và giữ replay idempotent. |
| `ProcessedEventRepository.persistClaim(UUID messageId, Instant at): void` | Persist/flush unique trong transaction projection; không merge record đã xử lý để tiếp tục cộng. |

SQL UPSERT cần test với MySQL thực tế của repo. Không phụ thuộc số affected rows của ON DUPLICATE KEY UPDATE để suy ra dedup message: MySQL/JDBC có chế độ affected/found rows khác nhau, processed_events mới là claim.

updatedAt đề xuất không lùi khi Kafka đảo thứ tự: GREATEST thời gian hiện có với occurredAt nghiệp vụ mới; khi row mới chưa có data null handling rõ. Nếu nhóm chọn thời điểm projection xử lý, thay đổi ý nghĩa đồng bộ REST/WS/tài liệu, không trộn hai kiểu thời gian giữa endpoints.

## 6. Controller, WebSocket và listener

| Operation | Method |
|---|---|
| GET `/api/v1/analytics/events/{id}/sales?from=&to=` | getSales |
| GET `/api/v1/analytics/events/{id}/attendance` | getAttendance |
| GET `/api/v1/analytics/events/{id}/dashboard` | getDashboard |

`EventClient.getInternal(UUID): InternalEventResponse` dùng S2S credential khi chốt. Controller chỉ nhận Principal/date/page nếu có và gọi service. DTO SalesReportResponse/DailySales/TicketTypeSales/AttendanceResponse/DashboardSnapshotResponse map đúng schema API; mapper không query Event/Ticket DB.

`WebSocketConfig` endpoint `/ws/dashboard`, topic `/topic/event/{id}/dashboard`, Gateway route WS riêng. `StompAuthorizationInterceptor.authenticateConnect(headers)` validate Keycloak JWT và expiry; `authorizeSubscribe(Actor,destination)` chỉ chấp nhận exact destination hợp lệ, parse event UUID và assertCanView. Chặn wildcard destination, client SEND đến topic và mọi subscribe chưa CONNECT xác thực. Session phải hết hạn/đóng theo token, không chỉ xác thực một lần rồi đọc vô hạn.

AFTER_COMMIT push không chứng minh realtime nhiều instance: SimpleBroker chỉ dev/một instance; scale phải có broker/fanout chung. Origins là config cụ thể. Client GET snapshot rồi subscribe có cửa sổ cập nhật; nên GET lại sau SUBSCRIBE thành công để hội tụ, phiên bản snapshot có thể bổ sung qua migration/contract khi cần ordering trên UI.

OrderEventConsumer nhận order.paid/refunded; TicketEventConsumer nhận checked-in/out. Group analytics-service, String JSON converter, lỗi transient retry/backoff, malformed fixed lỗi vào analytics.dlq; publish DLQ fail không ACK. Rebuild/replay phải có quy trình reset projection/checkpoint phù hợp; không xóa processed riêng rồi cộng lại vào snapshot cũ.

## 7. Kiểm tra và triển khai

1. V1 entity mapping/query repo/API/EventClient/access; test 0 khi event tồn tại, 404/403/503 khác nhau, range/null bounds và UUID.
2. Sales/refund fixture C04 cả đơn 1.000.000 hoàn 700.000, quantity giữ toàn bộ; dedup/UPSERT và allocation từng hạng. C05 compensation khi chưa có sale không làm âm báo cáo.
3. V2 attendance_state và projection, test IN→OUT/OUT→IN, duplicate, hai thread, rollback sau claim, khác eventId; snapshot sales/attendance update đồng thời không mất cột.
4. WS CONNECT/SUBSCRIBE/expiry/forbidden SEND, AFTER_COMMIT chỉ push khi commit, reconnect GET hội tụ; Kafka/DLQ integration.

ErrorCode: INVALID_DATE_RANGE/VALIDATION_ERROR 400, FORBIDDEN_NOT_OWNER/FORBIDDEN 403, EVENT_NOT_FOUND 404, EVENT_SERVICE_UNAVAILABLE 503. C09 là quyết định business về số gộp/thuần/ngày, không đẩy bài toán concurrency kỹ thuật lại cho chủ dự án lựa chọn.

## 8. Thống kê toàn hệ thống cho ADMIN (C10)

### 8.1 Bối cảnh và phạm vi

Frontend admin có trang **Phân tích** (`/admin/analytics`): doanh thu, vé bán ra và khán giả tham gia **theo từng sự kiện, từng tháng, từng ngày**. Ba API ở §6 chỉ đọc **một** sự kiện, nên trang này cần thống kê toàn hệ thống. Quyết định nghiệp vụ ghi tại [C10](../project-clarifications.md#c10--thống-kê-toàn-hệ-thống-cho-admin); hợp đồng tại [OpenAPI Analytics](../api/analytics-service-openapi.yaml) tag `AdminAnalytics`.

Phía frontend đã có giao diện chạy bằng dữ liệu mô phỏng, đúng shape response dưới đây:

| File frontend | Vai trò |
|---|---|
| `front-end/src/types/analytics.ts` | Type khớp schema `AnalyticsOverview`, `EventAnalyticsRow` |
| `front-end/src/hooks/use-platform-analytics.ts` | Điểm duy nhất cần đổi sang gọi 2 API thật (TanStack Query) |
| `front-end/src/lib/mock/platform-analytics.ts` | Mô phỏng phép tổng hợp của server; xóa khi có API |
| `front-end/src/components/features/dashboard/analytics.tsx` | UI: bộ lọc, KPI, biểu đồ, bảng theo sự kiện, bảng theo kỳ |

Phạm vi xem (cập nhật 2026-11-14): ADMIN xem mọi sự kiện; ORGANIZER xem tổng và bảng xếp hạng chỉ gồm sự kiện mình sở hữu; CUSTOMER 403. Ngoài phạm vi giai đoạn này: số đơn hàng, số người mua khác nhau, lọc/tìm theo tên hoặc trạng thái sự kiện.

### 8.2 Định nghĩa số liệu

Mọi kỳ tính theo `eventhub.analytics.reporting-zone` (Asia/Ho_Chi_Minh). Giữ nguyên C09: không trừ hoàn tiền vào ngày bán.

| Số liệu | Nguồn | Ghi vào kỳ theo |
|---|---|---|
| `grossRevenue`, `ticketsSold` | `sales_metrics.revenue`, `tickets_sold` | `metric_date` (ngày VN của paidAt) |
| `refundedAmount`, `ticketsRefunded` | `sales_metrics.refunded_amount`, `tickets_refunded` | `metric_date` (ngày VN của refundedAt) |
| `netRevenue` | gross − refunded, tính khi đọc | Có thể âm trong kỳ chỉ có hoàn |
| `attendees` (khán giả tham gia) | `COUNT(*)` trên `attendance_state` có `checked_in_at` trong kỳ | Ngày VN của `checked_in_at` (= scannedAt lần vào đầu tiên). Mỗi vé tính một lần, không trừ khi check-out. |
| `validTicketsLifetime` | `dashboard_snapshot.total_tickets_sold − SUM(sales_metrics.tickets_refunded)` toàn thời gian | Không theo kỳ |
| `attendeesLifetime` | `dashboard_snapshot.total_checked_in` | Không theo kỳ |
| Tỉ lệ check-in (frontend tính) | `attendeesLifetime / validTicketsLifetime`, chỉ hiện khi sự kiện đã diễn ra | Không theo kỳ; tránh >100% khi vé bán trước khoảng lọc |
| `capacity` | Ticket: `SUM(total_quota)` mọi hạng | Không theo kỳ |
| `previousTotals` | Cùng công thức cho khoảng liền trước có **cùng số ngày**: `previousTo = from − 1`, `previousFrom = from − (to − from + 1)` | — |

"Khán giả" là số **vé** đã check-in, không phải số người khác nhau (một người mua 3 vé cho 3 người là 3 khán giả). Đây là số đo đúng cho sức chứa và vận hành cổng.

### 8.3 API public mới

| Operation | Quyền | Mô tả |
|---|---|---|
| GET `/api/v1/analytics/overview?from&to&granularity=DAY\|MONTH[&eventId]` | Không có `eventId`: ADMIN = toàn hệ thống, ORGANIZER = các sự kiện của mình (INT-03). Có `eventId`: ADMIN hoặc ORGANIZER owner (`EventAccessService.assertCanView`) | Tổng, kỳ trước, chuỗi theo kỳ đã zero-fill, phân bổ theo hạng vé |
| GET `/api/v1/analytics/events?from&to&sort&page&size` | ADMIN = mọi sự kiện; ORGANIZER = sự kiện của mình (INT-03) | Bảng xếp hạng sự kiện, sắp xếp + phân trang ở server, rồi bổ sung metadata cho trang hiện tại |

Validate: `from`, `to` bắt buộc; `from > to` → 400 `INVALID_DATE_RANGE`; DAY tối đa 366 ngày, MONTH tối đa 36 tháng → 400 **`DATE_RANGE_TOO_LARGE`** (ErrorCode mới); `sort` ngoài whitelist → 400 `VALIDATION_ERROR`. Không gọi Event Service ở `/overview` khi không có `eventId`. Role sai → 403 `FORBIDDEN` trước khi query DB.

Kỳ MONTH: `period = yyyy-MM`; tháng đầu/cuối chỉ cộng phần ngày nằm trong from..to (ví dụ preset "12 tháng gần nhất" gửi `from=2025-12-01&to=2026-11-13`). Server zero-fill để frontend không phải đoán kỳ thiếu.

### 8.4 API nội bộ cần thêm ở service khác

| Hợp đồng | Caller → owner | Ghi chú |
|---|---|---|
| GET `/internal/events/ids?organizerId=...` (INT-03, bổ sung 2026-11-14) | Analytics → Event | Trả mảng id mọi sự kiện của organizer (mọi status), rỗng nếu chưa có. organizerId lấy từ JWT đã xác minh. Chỉ gọi khi người xem là ORGANIZER và không có `eventId`. Lỗi → 503, không đoán phạm vi rỗng. Đã thêm vào [Event OpenAPI](../api/event-service-openapi.yaml). |
| GET `/internal/events?ids=...` (INT-02, ≤100 id) | Analytics → Event | Trả `InternalEventResponse[]` mọi status, bỏ qua id không tồn tại. Đã thêm vào [Event OpenAPI](../api/event-service-openapi.yaml). Dùng thay vì gọi INT-01 N lần. |
| GET `/internal/ticket-types/capacity?eventIds=...` (≤100 id) | Analytics → Ticket | Trả `[{eventId, capacity}]`, capacity = SUM(total_quota). Đã thêm vào [Ticket OpenAPI](../api/ticket-service-openapi.yaml). |

Analytics chỉ gọi hai API này cho **các eventId của trang đang trả** (mặc định 20), không cho toàn bộ danh sách. Event lỗi/timeout → 503 `EVENT_SERVICE_UNAVAILABLE` (không trả tên giả). Ticket lỗi/timeout → vẫn 200, `capacity = null`, ghi log cảnh báo. Event trả thiếu một id (dữ liệu lệch) → bỏ dòng đó khỏi `content` và log lỗi; không trả 500.

Analytics vẫn không JOIN DB service khác và không cần topic Kafka mới ở giai đoạn này.

**Phạm vi sự kiện (2026-11-14).** Service tính một phạm vi trước khi đọc DB: ADMIN = không giới hạn; ORGANIZER không có `eventId` = mảng id từ INT-03; có `eventId` = đúng sự kiện đó sau `assertCanView`. Phạm vi rỗng (organizer chưa có sự kiện) trả kết quả 0 mà không chạy SQL, vì `IN ()` là lỗi cú pháp.

### 8.5 Migration V3 (chỉ thêm index)

Không đổi bảng/cột, không sửa V1/V2. File đề xuất `V3__platform_analytics_indexes.sql`:

~~~sql
-- Range theo ngày trên toàn hệ thống (/overview, /events không lọc event).
CREATE INDEX idx_sales_date_event ON sales_metrics (metric_date, event_id);
-- Range theo ngày của một sự kiện (/overview?eventId=). uk_metric có event_id đứng đầu nhưng ticket_type_id chen giữa.
CREATE INDEX idx_sales_event_date ON sales_metrics (event_id, metric_date);
-- Đếm khán giả theo khoảng check-in.
CREATE INDEX idx_state_checkin_event ON attendance_state (checked_in_at, event_id);
CREATE INDEX idx_state_event_checkin ON attendance_state (event_id, checked_in_at);
~~~

Hiện `application.yml` để `flyway.enabled: false`, `ddl-auto: update`: Hibernate **không** chạy file SQL này. Khi code phải chọn một trong hai: bật Flyway (khuyến nghị, đúng hướng dẫn chung) hoặc khai báo cùng index trong `@Table(indexes = ...)` của `SalesMetric`/`AttendanceState`. Sau khi có migration mới cập nhật [init.sql](../database/analytics-service/init.sql) và chạy `node docs/database/render-erd.mjs --check` (ERD sinh tự động không đổi vì không thêm cột). Sơ đồ quan hệ Analytics ↔ Event/Ticket và vị trí index V3 cho báo cáo: [ERD C10](../database/analytics-service/erd-c10.md).

### 8.6 Query (MySQL 8.4)

Mọi query dưới đây thêm cùng một điều kiện phạm vi `AND (:allEvents = 1 OR event_id IN (:eventIds))` (trong CTE thì thêm vào cả `s` và `a`); để gọn, SQL bên dưới chỉ ghi phần lọc theo một `eventId`.

Session JDBC phải ở UTC (`connectionTimeZone=UTC`, `hibernate.jdbc.time_zone=UTC`) để `CONVERT_TZ(..., '+00:00', :offset)` đúng. Việt Nam không có DST nên offset `+07:00` cố định là an toàn; lấy offset từ `reporting-zone` lúc khởi động. Mọi khoảng thời gian chuyển sang biên UTC trong Java: `fromUtc = from 00:00 VN`, `toUtcExclusive = (to + 1) 00:00 VN`.

**Sales theo ngày** (một query cho cả DAY và MONTH; Java gộp tháng và zero-fill, tối đa ~1.100 dòng):

~~~sql
SELECT metric_date, SUM(revenue), SUM(refunded_amount), SUM(tickets_sold), SUM(tickets_refunded)
FROM sales_metrics
WHERE metric_date BETWEEN :from AND :to
  AND (:eventId IS NULL OR event_id = :eventId)
GROUP BY metric_date
~~~

**Khán giả theo ngày:**

~~~sql
SELECT DATE(CONVERT_TZ(checked_in_at, '+00:00', :offset)) AS d, COUNT(*)
FROM attendance_state
WHERE checked_in_at >= :fromUtc AND checked_in_at < :toUtcExclusive
  AND (:eventId IS NULL OR event_id = :eventId)
GROUP BY d
~~~

**Theo hạng vé** (gom theo tên snapshot vì mỗi sự kiện có ticket_type_id riêng):

~~~sql
SELECT ticket_type_name, SUM(tickets_sold - tickets_refunded), SUM(revenue - refunded_amount)
FROM sales_metrics
WHERE metric_date BETWEEN :from AND :to AND (:eventId IS NULL OR event_id = :eventId)
GROUP BY ticket_type_name
~~~

`totals` = tổng của series (tính trong Java). `previousTotals` = hai query sales/attendance ở trên bỏ GROUP BY, chạy với khoảng kỳ trước.

**Bảng theo sự kiện** (sắp xếp + phân trang tại DB; `ORDER BY` lấy từ whitelist enum, không ghép chuỗi từ request):

~~~sql
WITH s AS (
  SELECT event_id, SUM(revenue) gross, SUM(refunded_amount) refund,
         SUM(tickets_sold) sold, SUM(tickets_refunded) sold_refunded
  FROM sales_metrics WHERE metric_date BETWEEN :from AND :to GROUP BY event_id),
a AS (
  SELECT event_id, COUNT(*) att FROM attendance_state
  WHERE checked_in_at >= :fromUtc AND checked_in_at < :toUtcExclusive GROUP BY event_id),
ids AS (SELECT event_id FROM s UNION SELECT event_id FROM a)
SELECT ids.event_id, COALESCE(s.gross,0) gross, COALESCE(s.refund,0) refund,
       COALESCE(s.gross,0) - COALESCE(s.refund,0) net,
       COALESCE(s.sold,0) sold, COALESCE(s.sold_refunded,0) sold_refunded, COALESCE(a.att,0) att
FROM ids LEFT JOIN s ON s.event_id = ids.event_id LEFT JOIN a ON a.event_id = ids.event_id
ORDER BY net DESC, ids.event_id ASC   -- cột sort: net | gross | sold | att
LIMIT :size OFFSET :offset;
-- totalElements: cùng CTE, SELECT COUNT(*) FROM ids.
~~~

**Lifetime cho các id của trang:**

~~~sql
SELECT d.event_id, d.total_tickets_sold, d.total_checked_in, COALESCE(r.refunded, 0)
FROM dashboard_snapshot d
LEFT JOIN (SELECT event_id, SUM(tickets_refunded) refunded FROM sales_metrics
           WHERE event_id IN (:ids) GROUP BY event_id) r ON r.event_id = d.event_id
WHERE d.event_id IN (:ids)
~~~

Sự kiện chưa có snapshot (không nên xảy ra khi đã có metric, nhưng vẫn phòng) → lifetime = 0.

### 8.7 Lớp code đề xuất

| Thành phần | Signature / trách nhiệm |
|---|---|
| `PlatformAnalyticsController` (`/api/v1/analytics`) | `GET /overview` → `getOverview(Actor, LocalDate from, LocalDate to, Granularity g, UUID eventId)`; `GET /events` → `listEvents(Actor, LocalDate from, LocalDate to, EventAnalyticsSort sort, Pageable)`. Tách khỏi `AnalyticsController` (đang map `/api/v1/analytics/events` cho các path có `{id}`); kiểm tra không trùng mapping. |
| `common.Granularity`, `common.EventAnalyticsSort` | Enum DAY/MONTH; netRevenue/grossRevenue/ticketsSold/attendees kèm tên cột SQL whitelist. |
| `PlatformAnalyticsService` / `Impl` | `@Transactional(readOnly = true)` ngắn cho phần DB. Thứ tự `/events`: kiểm role → validate → query DB (đóng transaction) → gọi Event/Ticket batch **ngoài transaction** → ghép DTO. Không giữ connection khi chờ HTTP (đúng §2). |
| `PlatformAnalyticsQueries` (JDBC, giống `SalesMetricQueriesImpl`) | `dailySales`, `dailyAttendance`, `byTicketTypeName`, `sumRange`, `pageEvents`, `countEvents`, `lifetime(List<UUID>)`. |
| `EventClient.getBatch(Collection<UUID>): Map<UUID, InternalEventResponse>` | Thêm vào client hiện có, cùng S2S token, timeout ngắn; lỗi → `EVENT_SERVICE_UNAVAILABLE`. |
| `EventClient.getIdsByOrganizer(UUID): List<UUID>` | INT-03, phạm vi của ORGANIZER; lỗi → `EVENT_SERVICE_UNAVAILABLE`. |
| `TicketCapacityClient.getCapacity(Collection<UUID>): Map<UUID, Integer>` | Client mới; lỗi trả map rỗng + log WARN (capacity=null). Cấu hình `eventhub.ticket-service.url` / biến `TICKET_SERVICE_URL` (cùng kiểu `EVENT_SERVICE_URL`). |
| DTO record | `AnalyticsMetrics`, `AnalyticsSeriesPoint`, `TicketTypeTotals`, `AnalyticsOverviewResponse`, `EventAnalyticsRow` đúng OpenAPI. Trả `Page<EventAnalyticsRow>` với serialization via-dto theo [quy ước chung](00-cross-service-contracts.md). |
| `ErrorCode.DATE_RANGE_TOO_LARGE(400)` | Mã lỗi mới; frontend thêm vào `lib/api/errors.ts`. |
| `SecurityConfig` | Hai path mới cho ADMIN và ORGANIZER (matcher `/api/v1/analytics/**` sẵn có); service giới hạn phạm vi theo owner. |

Cache (tùy chọn): kết quả `/overview` không lọc event có thể cache 30–60 giây theo khóa (from, to, granularity); không cache phần có kiểm tra owner theo user.

### 8.8 Kiểm thử

1. Định nghĩa: refund ghi đúng ngày refundedAt, netRevenue âm được; khán giả đếm theo ngày VN của checked_in_at (vé quét 23:30 UTC ngày 1 thuộc ngày 2 giờ VN); check-out không làm giảm attendees.
2. Kỳ: zero-fill đủ ngày/tháng; MONTH cắt đúng tháng đầu/cuối; previous period cùng độ dài; giới hạn 366 ngày/36 tháng; from > to.
3. Quyền và phạm vi: CUSTOMER 403; ORGANIZER `/overview` không eventId chỉ cộng sự kiện của mình (kể cả kỳ trước), `/events` chỉ liệt kê sự kiện của mình; organizer chưa có sự kiện nhận số 0/trang rỗng; INT-03 lỗi → 503 với ORGANIZER nhưng ADMIN không bị ảnh hưởng; eventId của người khác 403.
4. `/events`: sắp xếp từng trường + tie-break ổn định qua các trang; sự kiện chỉ có check-in (không bán trong khoảng) vẫn xuất hiện; Event 503 → 503; Ticket lỗi → capacity null; chỉ gọi batch với id của trang.
5. Tỉ lệ check-in không vượt 100% khi vé bán trước khoảng lọc (lỗi đã gặp ở bản mô phỏng frontend).
6. EXPLAIN các query trên MySQL 8.4 dùng index V3.

### 8.9 Mở rộng sau (không làm ngay)

- **Lọc/tìm theo tên hoặc trạng thái sự kiện, bỏ phụ thuộc INT-02/INT-03 khi đọc**: ORGANIZER đã xem được tổng sự kiện của mình qua INT-03 (2026-11-14). Nếu sau này cần tìm theo tên, lọc theo trạng thái hoặc không muốn thống kê phụ thuộc Event Service lúc đọc, hướng đề xuất là Event phát topic `event.updated` (id, organizerId, title, status, startTime) qua outbox và Analytics lưu bảng `event_dimension`. Theo §3 của [quy ước chung](00-cross-service-contracts.md), chỉ tạo outbox Event khi use case này được chốt.
- **Số đơn hàng / số người mua khác nhau**: `sales_metrics` đã gộp nên không suy ra được; cần bảng mới theo đơn (order_id, customer_id, event_id, paid_date) từ `order.paid`.
- **Realtime cho trang Phân tích**: hiện chỉ GET; nếu cần push dùng topic STOMP riêng cho ADMIN, không tái dùng topic theo event.

### 8.10 Trạng thái triển khai (2026-11-14)

Đã code trong `analytics-service` (biên dịch qua `mvnw compile`):

| Thành phần | File |
|---|---|
| Controller | `controller/PlatformAnalyticsController` (`/api/v1/analytics/overview`, `/api/v1/analytics/events`) |
| Service | `service/PlatformAnalyticsService`, `service/impl/PlatformAnalyticsServiceImpl` (quyền, validate, gọi HTTP ngoài transaction), `service/impl/PlatformAnalyticsReadService` (transaction read-only, gộp kỳ, zero-fill, kỳ trước) |
| Query | `repository/PlatformAnalyticsQueries` (SQL đúng §8.6) |
| Client | `EventClient.getBatch` (INT-02), `EventClient.getIdsByOrganizer` (INT-03), `TicketCapacityClient`/`Impl`, `EventCapacityResponse` |
| Enum, lỗi, DTO | `common/Granularity`, `common/EventAnalyticsSort`, `ErrorCode.DATE_RANGE_TOO_LARGE`; `dto/response/AnalyticsMetrics`, `AnalyticsSeriesPoint`, `TicketTypeTotals`, `AnalyticsOverviewResponse`, `EventAnalyticsRow` |
| Schema | `db/migration/V3__platform_analytics_indexes.sql` + `@Index` cùng tên trên `SalesMetric`, `AttendanceState` |
| Cấu hình | `SecurityConfig`: hai path mới cho ADMIN/ORGANIZER qua matcher sẵn có (CUSTOMER bị chặn ở filter). `application.yml`: `eventhub.ticket-service.url`, `spring.data.web.pageable.serialization-mode: via-dto`, `max-page-size: 100`, JDBC `forceConnectionTimeZoneToSession=true`. `docker-compose.yml`: `TICKET_SERVICE_URL` cho analytics-service. |

Quyết định khi code:

- `page`, `size`, `sort` nhận tường minh bằng `@RequestParam` rồi tạo `PageRequest`, không dùng `Pageable`: `sort=netRevenue` là tên số liệu luôn giảm dần, nếu để `Pageable` tự đọc sẽ hiểu thành sắp xếp tăng dần theo thuộc tính entity. Response vẫn là `Page` dạng via-dto.
- `forceConnectionTimeZoneToSession=true`: `serverTimezone=UTC` của Connector/J không tự đặt session MySQL về UTC. Trên máy có MySQL ở UTC+7 (đã gặp khi kiểm chứng), `CONVERT_TZ(checked_in_at, +00:00, ...)` lệch 7 giờ nếu dữ liệu được ghi bằng session UTC. Ép session UTC làm TIMESTAMP lưu đúng UTC bất kể cấu hình server. Dữ liệu cũ đã ghi trên server không phải UTC (nếu có) sẽ lệch 7 giờ sau thay đổi này; database local lúc đổi đang rỗng.
- Thiếu `from`/`to`/`granularity` hoặc sai kiểu trả 400 `VALIDATION_ERROR` qua `GlobalExceptionHandler` sẵn có. `/events` dùng giới hạn 36 tháng.

Đã kiểm chứng thủ công (theo quy ước repo không thêm file test) trên MySQL 8.0 cục bộ với database tạm tạo từ V1+V2+V3, chạy trực tiếp các class đã build, giả lập Event/Ticket client. Lần 1 (2026-11-14, chỉ ADMIN): 38/38 đạt. Lần 2 sau khi thêm phạm vi ORGANIZER: 45/46 đạt; phép còn lại là kỳ vọng sai định dạng trong bài test (`0` so với `0.00` do `COALESCE(SUM)`), giá trị đúng bằng 0. Các ca đã kiểm gồm gộp tháng, zero-fill 45 ngày, vé check-in 23:30 UTC rơi vào ngày hôm sau giờ VN, check-out không trừ khán giả, doanh thu thuần âm, kỳ trước, giới hạn 366 ngày/36 tháng, quyền ADMIN/ORGANIZER, sắp xếp + phân trang ổn định, sự kiện chỉ có check-in vẫn xuất hiện, batch chỉ gọi id của trang, Ticket lỗi → `capacity=null`, shape JSON. EXPLAIN xác nhận dùng index V3.

Còn thiếu để chạy đầu-cuối:

1. **Event Service** chưa có `GET /internal/events?ids=` (INT-02): `/api/v1/analytics/events` sẽ trả 503 `EVENT_SERVICE_UNAVAILABLE` khi có dữ liệu.
2. **Event Service** chưa có `GET /internal/events/ids?organizerId=` (INT-03): ORGANIZER gọi `/overview` không có `eventId` hoặc `/events` sẽ nhận 503. ADMIN và ORGANIZER xem từng sự kiện (`?eventId=`) không phụ thuộc.
3. **Ticket Service** chưa có `GET /internal/ticket-types/capacity`: `capacity` luôn `null` (không lỗi).
4. **API Gateway**: thêm route `analytics-admin` theo [plan Gateway](api-gateway.md).
5. **Frontend**: đổi `front-end/src/hooks/use-platform-analytics.ts` sang gọi 2 API, thêm `DATE_RANGE_TOO_LARGE` vào `lib/api/errors.ts`; trang "Báo cáo doanh thu" của Ban tổ chức (lựa chọn "Tất cả sự kiện") dùng `/overview` không có `eventId`.
