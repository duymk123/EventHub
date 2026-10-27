# Plan code analytics-service

Ngày 2026-10-28. Đọc [quy ước chung](00-cross-service-contracts.md), [OpenAPI Analytics](../api/analytics-service-openapi.yaml), [DDL/V1](../database/analytics-service/init.sql), [ERD](../database/analytics-service/erd.md), [Kafka](../kafka/events.md). Analytics là read model từ Kafka, không JOIN Order/Ticket/Event DB.

## 1. Entity, model và migration

V1 có SalesMetric(id/eventId/ticketTypeId UUID, ticketTypeName, metricDate, revenue/ticketsSold/refundedAmount/ticketsRefunded/updatedAt), DashboardSnapshot(id UUID, eventId UNIQUE UUID, totalRevenue/totalTicketsSold/currentAttendance/totalCheckedIn/lastUpdated), AttendanceLog(id/ticketId/eventId/gateId UUID, action, occurredAt), ProcessedEvent(id=messageId UUID).

V2 đề xuất `attendance_state(id UUID, ticket_id UUID UNIQUE, event_id UUID, checked_in_at/checked_out_at TIMESTAMP NULL)` và index cần query; không sửa V1 đã áp dụng. AttendanceLog có thể dùng id=messageId để audit/replay ổn định, không dùng ticketId làm PK log vì mỗi vé có hai action.

Snapshot gross/net còn C09. API hiện có SalesReport tổng gộp/refund/net, byTicketType và daily; Dashboard tổng thuần là đề xuất chưa duyệt. Không đặt công thức thành mặc định business trong entity chỉ vì field tên total. Tiền refund theo hạng cần items[].refundedAmount và C04/C09, quantity đơn lẻ chưa đủ.

## 2. Query/access service

| Signature | Hành vi / transaction |
|---|---|
| `EventAccessService.assertCanView(Actor actor, UUID eventId): EventAccessContext` | ADMIN hoặc ORGANIZER owner; gọi EventClient để xác minh tồn tại/owner, mọi status được phép theo nghiệp vụ analytics. CUSTOMER bị 403, nonexistent 404, upstream lỗi 503, không giả snapshot 0 để che lỗi. |
| `AnalyticsQueryService.getSales(Actor actor, UUID eventId, LocalDate from, LocalDate to): SalesReportResponse` | Access trước → validate from≤to → read-only aggregate theo ngày/hạng. Thiếu đầu nào không giới hạn đầu đó. Mapping tiền gộp/refund/net theo C09, không load entity từng ticket. |
| `getAttendance(Actor actor, UUID eventId): AttendanceResponse` | Access rồi đọc snapshot; chưa có projection trả currentAttendance=0,totalCheckedIn=0,updatedAt=null. |
| `getDashboard(Actor actor, UUID eventId): DashboardSnapshotResponse` | Access rồi cùng DTO dùng WS; không có projection trả số 0/null time, không tạo một snapshot giả trong GET. |
| `DashboardReadService.getForPush(UUID eventId): DashboardSnapshotResponse` | Nội bộ server sau commit, transaction read-only mới để tránh persistence context stale; không là public bypass quyền. |

Access là lớp trước transaction DB query dài, không giữ DB connection lúc đợi Event HTTP. Cache chỉ metadata owner/tồn tại, không cache quyền theo mỗi eventId mà bỏ user/role; khóa đúng context và invalidation/TTL ngắn. Timeout 503 không được cache như event không tồn tại. Chi tiết TTL do triển khai chọn có test stale ownership.

## 3. Projection service

| Signature | Hành vi |
|---|---|
| `SalesProjectionService.handlePaid(OrderPaidEvent event): void` | Validate envelope/amount/items/currency → transaction claim messageId → UPSERT từng type/date từ paidAt → update snapshot đúng công thức đã chốt → publish DashboardChanged local event → commit. Không subscribe ticket.issued để cộng lần nữa. |
| `handleRefunded(OrderRefundedEvent event): void` | Tương tự claim+refund_amount/refundedQty theo refundedAt; phân bổ từng hạng theo payload đủ dữ liệu sau C04/C09. Không lấy giá Ticket hiện tại hoặc sửa lại ngày mua. |
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
| `applyRefund(UUID eventId, UUID initialSnapshotId, BigDecimal amount, int qty, Instant at): int` | Công thức snapshot chờ C09; không clamp mất adjustment khi refund tới trước sale. |
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
2. Sales/refund fixture theo hợp đồng đủ tiền từng hạng, dedup transaction+UPSERT; C04/C09 trước chốt expected business totals.
3. V2 attendance_state và projection, test IN→OUT/OUT→IN, duplicate, hai thread, rollback sau claim, khác eventId; snapshot sales/attendance update đồng thời không mất cột.
4. WS CONNECT/SUBSCRIBE/expiry/forbidden SEND, AFTER_COMMIT chỉ push khi commit, reconnect GET hội tụ; Kafka/DLQ integration.

ErrorCode: INVALID_DATE_RANGE/VALIDATION_ERROR 400, FORBIDDEN_NOT_OWNER/FORBIDDEN 403, EVENT_NOT_FOUND 404, EVENT_SERVICE_UNAVAILABLE 503. C09 là quyết định business về số gộp/thuần/ngày, không đẩy bài toán concurrency kỹ thuật lại cho chủ dự án lựa chọn.
