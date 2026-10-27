# Mẫu code EventHub sau BC_32_V2

Ví dụ minh họa Java 21/Spring Boot 3; import và boilerplate được lược bớt, chưa phải code đang có trong module. Đối chiếu [SKILL](SKILL.md), [DDL](docs/database/README.md), [OpenAPI](docs/api/README.md) và [câu hỏi còn mở](docs/project-clarifications.md) trước khi chép mẫu. Keycloak cấp token, không dùng mẫu Auth tự ký JWT/refresh table phiên bản cũ.

## 1. Entity, UUID và BaseEntity

### 1.1. Entity theo schema hiện tại

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

### 1.2. BaseEntity có audit và cờ deleted

Dùng mẫu dưới khi bảng có đủ cột audit tương ứng. Đây là mẫu theo yêu cầu chủ dự án; Analytics/Notification hiện vẫn giữ schema V1 và không kế thừa BaseEntity theo lựa chọn trước đó. Muốn áp dụng cho các entity này cần migration trước, không chỉ thêm extends rồi để Hibernate validate thất bại.

`entity/BaseEntity.java` trong chính service đang triển khai:

~~~java
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {

    @CreatedDate
    @Column(name = "create_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 36)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
~~~

BaseEntity là mapped superclass, không tạo bảng riêng và không tự khai báo khóa chính. Mỗi entity con khai báo UUID theo quy tắc của bảng; ProcessedEvent vẫn phải nhận messageId thay vì sinh ID mới. Tạo BaseEntity cục bộ trong service, không import model của Order hoặc service khác để kế thừa.

### 1.3. Entity kế thừa BaseEntity

Mẫu minh họa cho một bảng mới đã có các cột audit; `audited_records` không phải bảng đang tồn tại trong dự án:

~~~java
@Entity
@Table(name = "audited_records")
@Getter
@Setter
@NoArgsConstructor
public class AuditedRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @Column(name = "label", nullable = false, length = 100)
    private String label;
}
~~~

Không khai báo lại createdAt/updatedAt trong lớp con vì các field này đã được kế thừa. Nếu cần Lombok builder bao gồm field của lớp cha, dùng `@SuperBuilder` nhất quán ở cả cha và con; `@Builder` ở riêng lớp con không bao gồm audit fields.

### 1.4. Bật JPA Auditing

Chỉ thêm cấu hình này vào service thực sự sử dụng BaseEntity. Mẫu lấy actor từ Spring Security nên cần dependency/config security tương ứng; không tự thêm JWT issuer hoặc grant flow mới để dùng auditing.

`config/JpaAuditingConfig.java`:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(
    auditorAwareRef = "auditorAware",
    dateTimeProviderRef = "utcAuditingDateTimeProvider"
)
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            return Optional.ofNullable(authentication.getName());
        };
    }

    @Bean
    public DateTimeProvider utcAuditingDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
    }
}
~~~

Trong mẫu này, security adapter phải map authentication.name về userId UUID đã xác minh nếu created_by/updated_by lưu UUID người thao tác. Không lấy actor từ body/header do client tự khai. Worker Kafka/scheduler không có SecurityContext người dùng sẽ không tự có createdBy/updatedBy; nếu cần audit actor hệ thống, bổ sung provider theo danh tính service đã được cấu hình.

LocalDateTime của audit được quy ước UTC bằng DateTimeProvider trên. Timestamps nghiệp vụ như paidAt, scannedAt, occurredAt, processedAt hoặc lastUpdated vẫn giữ field/mapping riêng theo hợp đồng, không thay bằng thời điểm audit của entity.

### 1.5. Schema và cách sử dụng

DDL minh họa tương ứng cho bảng mới; không chạy tự động vào database hiện có:

~~~sql
CREATE TABLE audited_records (
    id CHAR(36) PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    create_at DATETIME(6) NULL,
    update_at DATETIME(6) NULL,
    created_by VARCHAR(36) NULL,
    updated_by VARCHAR(36) NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
~~~

- Giữ đúng tên `create_at`/`update_at` của mẫu bạn cung cấp; đây là tên khác với `created_at`/`updated_at` trong một số bảng hiện có. Nếu tái dùng cột cũ, thiết kế mapping bằng `@AttributeOverride` và kiểu dữ liệu phù hợp; không tạo hai property Java trùng tên.
- Bảng đang có dữ liệu phải thêm migration version mới và xử lý giá trị audit cũ; không sửa migration V1 đã áp dụng. Nếu schema chưa có các cột này thì dùng mẫu không kế thừa ở mục 1.1.
- `deleted` là cờ dữ liệu, không tự biến `repository.delete()` thành soft delete. Service cần cập nhật cờ và query cần lọc deleted=false theo nghiệp vụ; không xóa cứng rồi coi dữ liệu vẫn còn.
- AuditingEntityListener chạy theo lifecycle JPA. Native SQL, bulk JPQL update và UPSERT không tự đi qua listener; các câu lệnh đó phải cập nhật timestamp/actor phù hợp nếu cần audit.
- Không tạo file test hoặc dependency test cho mẫu này theo yêu cầu hiện tại của chủ dự án.

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
