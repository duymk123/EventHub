# Mẫu code EventHub sau BC_32_V2

Ví dụ minh họa Java 21/Spring Boot 3; import và boilerplate được lược bớt, chưa phải code đang có trong module. Đối chiếu [SKILL](SKILL.md), [DDL](docs/database/README.md), [OpenAPI](docs/api/README.md) và [câu hỏi còn mở](docs/project-clarifications.md) trước khi chép mẫu. Keycloak cấp token, không dùng mẫu Auth tự ký JWT/refresh table phiên bản cũ.

## 1. Entity và UUID

~~~java
@Entity @Table(name = "events")
@Getter @Setter @NoArgsConstructor
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36)
    private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "organizer_id", nullable = false, length = 36)
    private UUID organizerId; // UUID mềm, không @ManyToOne sang Keycloak/Auth
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category; // @Table(name="categories"), cùng Event DB
    @Column(nullable = false) private String title;
    @Enumerated(EnumType.STRING) private EventStatus status;
    private Instant startTime;
    private Instant endTime;
}
~~~

Mẫu chỉ liệt kê các field cần minh họa; map đầy đủ cột DDL khi triển khai. Không thêm venue bắt buộc hoặc event_zones trước C03. Entity PurchaseOrder ánh xạ bảng orders để tránh tên ORDER của JPQL. Tất cả UUID FK mềm/nội bộ dùng cùng CHAR mapping, không chỉ @Id.

## 2. DTO

~~~java
public record CreateEventRequest(
    @NotBlank @Size(max = 255) String title,
    String description,
    @NotNull UUID categoryId,
    @NotNull Instant startTime,
    @NotNull Instant endTime) {}
public record CategoryRef(UUID id, String name) {}
public record EventResponse(UUID id, UUID organizerId, CategoryRef category,
    String title, Instant startTime, Instant endTime, EventStatus status) {}
~~~

Service kiểm tra startTime < endTime và ownership. Response thực tế map đầy đủ shape OpenAPI (ví dụ description/createdAt), không trả Entity. HolderInput một phần tử/vé, holderEmail nullable theo V2, không tự ép bắt buộc trước C02.

## 3. Mapper

~~~java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventMapper {
    EventResponse toResponse(Event event);
    CategoryRef toRef(Category category);
}
~~~

Map category object đúng CategoryRef của API; không đổi category thành categoryId chỉ trong mapper rồi làm lệch hợp đồng. Mapper không kiểm tra quyền hoặc truy cập service khác.

## 4. Service interface và Principal

~~~java
public interface EventService {
    EventResponse create(UUID userId, CreateEventRequest request);
    EventResponse submit(UUID userId, UUID eventId);
}
public record BusinessPrincipal(UUID userId, BusinessRole role) {}
~~~

BusinessPrincipal là dữ liệu đã xác minh bởi adapter Keycloak; Auth facade UI/API riêng đã chốt; mapping sub/role cụ thể theo cấu hình realm còn cần hoàn thiện. Không lấy userId từ request và không hardcode claim roles phẳng khi chưa có role mapper.

## 5. Transaction và CAS

~~~java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("update Event e set e.status = :next where e.id = :id and e.organizerId = :owner and e.status = :expected")
int changeStatus(UUID id, UUID owner, EventStatus expected, EventStatus next);
~~~

ServiceImpl kiểm tra số dòng thay đổi, trả lỗi đúng ownership/trạng thái; chạy trong transaction. Read-only query dùng @Transactional(readOnly=true), không giữ transaction khi đợi SMTP hoặc provider lâu.

Quota nằm trong **ticket_types**, không ticket_inventory. SQL phải cập nhật counters bằng điều kiện hoặc SELECT FOR UPDATE trong cùng transaction với reservation:

~~~sql
UPDATE ticket_types
SET available_quantity = available_quantity - :quantity,
    reserved_quantity = reserved_quantity + :quantity
WHERE id = :ticketTypeId AND status = 'ACTIVE'
  AND available_quantity >= :quantity;
~~~

Nhiều hạng vé phải lock theo thứ tự ID ổn định và rollback toàn bộ nếu thiếu một hạng. Check số dòng; không đọc Java rồi cộng/trừ không khóa.

## 6. Controller

~~~java
@RestController @RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService service;
    @PostMapping @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> create(
            @AuthenticationPrincipal BusinessPrincipal principal,
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(principal.userId(), request));
    }
}
~~~

Security phải thực sự gán Principal kiểu trên và authorities từ mapping được chốt. Mẫu không tự tạo hoặc xác minh JWT; Keycloak là issuer. /internal/** không ghép /api/v1 và không Gateway route public.

## 7. ErrorResponse

~~~java
public record ErrorResponse(String code, String message, Instant timestamp, String path) {}
@ExceptionHandler(BusinessException.class)
ResponseEntity<ErrorResponse> business(BusinessException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.getErrorCode().httpStatus()).body(
        new ErrorResponse(ex.getErrorCode().name(), ex.getMessage(),
            Instant.now(), request.getRequestURI()));
}
~~~

GlobalExceptionHandler còn cần mapping validation/parse/not-found; Security entry point/access denied handler dùng cùng shape cho REST EventHub. Không trả stacktrace/token/QR hoặc ép lỗi chuẩn OIDC Keycloak thành DTO này.

## 8. Client REST

~~~java
public record InternalEventResponse(UUID id, UUID organizerId, String title,
    EventStatus status, Instant startTime, Instant endTime) {}
public interface EventClient { InternalEventResponse get(UUID eventId); }
~~~

RestClient timeout rõ ràng, credential S2S chờ cấu hình; không dùng public GET /events/{id} để kiểm tra owner của sự kiện chưa published. 404/503 map rõ, không giả organizer từ client. venue chưa thêm trước C03.

## 9. Kafka event và producer

~~~java
public record OrderPaidEvent(UUID messageId, String eventType, Instant occurredAt,
    UUID orderId, UUID eventId, UUID customerId, String currency,
    BigDecimal totalAmount, Instant paidAt, List<PaidItem> items) {}
public record PaidItem(UUID ticketTypeId, String ticketTypeName, int quantity,
    BigDecimal unitPrice, BigDecimal lineTotal) {}
~~~

Tạo messageId một lần, ghi outbox(id=messageId,aggregate_id=orderId,event_type,payload) cùng transaction đánh dấu business transition đã hoàn tất. Publisher riêng gửi key orderId.toString(), chờ ACK rồi đánh dấu published; retry dùng lại payload/messageId. Producer JsonSerializer tắt spring.json.add.type.headers. Không gọi send rồi coi DB/Kafka là cùng một transaction.

TicketIssuedEvent và các record còn lại phải có cùng messageId/eventType/occurredAt; eventId luôn UUID sự kiện, giữ nguyên dữ liệu trong [hợp đồng Kafka](docs/kafka/events.md). Chưa có producer/consumer hiện hữu cần đổi tên class trong repo; mẫu là hướng dẫn cho code mới.

## 10. Consumer và idempotency

~~~java
@Component @RequiredArgsConstructor
public class OrderEventConsumer {
    private final SalesProjectionService projections;
    @KafkaListener(topics = "order.paid")
    public void paid(OrderPaidEvent event) { projections.handlePaid(event); }
}
~~~

Projection transaction: persist processed_events.id=messageId để unique constraint claim → UPSERT metrics/snapshot → commit. Nếu duplicate claim, transaction rollback và outer handler chỉ bỏ qua đúng duplicate message claim, không nuốt mọi DataIntegrityViolationException. Lỗi projection rollback cả claim; message retry chưa được đánh dấu hoàn tất. existsById đơn lẻ không đủ chống cạnh tranh.

Notification không dùng transaction lớn bọc SMTP; claim/lease từng delivery, mark SENT sau gửi và chỉ processed khi mọi target xong. DefaultErrorHandler/DLQ phải xác nhận publish thành công trước commit offset. Không catch lỗi rồi ACK hoặc save processed ở finally. Attendance cần dữ liệu trạng thái từng vé để chịu đảo thứ tự giữa hai topic, V1 hiện chưa đủ.

## 11. Kiểm tra trước sử dụng mẫu

Schema/API đồng bộ; migration phù hợp trạng thái DB; dependency/annotation processors thực sự có trong POM; role+ownership đã kiểm tra; idempotency/CAS/rollback và lỗi mạng có hành vi rõ. Những field/công thức còn C01–C09 không tự gắn giá trị mặc định để code có thể chạy.
