# Mẫu code chuẩn EventHub

Các mẫu dưới đây minh họa luồng `Controller → Service → ServiceImpl → Repository → Entity` + DTO request/response. Thay `Event` bằng thực thể của service đang code, giữ nguyên cấu trúc. Java 17+, Spring Boot 3 (`jakarta.*`), Lombok, MapStruct.

## 1. Entity

`entity/EventStatus.java`
```java
public enum EventStatus { DRAFT, SUBMITTED, APPROVED, REJECTED, PUBLISHED, CLOSED }
```

`entity/Event.java`
```java
@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Soft FK -> auth_service_db.users.id (KHÔNG dùng @ManyToOne sang service khác) */
    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private EventCategory category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

## 2. Repository

`repository/EventRepository.java`
```java
public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByOrganizerId(Long organizerId, Pageable pageable);

    @Query("""
        select e from Event e
        where e.status = com.eventhub.event.entity.EventStatus.PUBLISHED
          and (:keyword is null or lower(e.title) like lower(concat('%', :keyword, '%')))
          and (:categoryId is null or e.category.id = :categoryId)
        """)
    Page<Event> searchPublished(@Param("keyword") String keyword,
                                @Param("categoryId") Long categoryId,
                                Pageable pageable);

    /** Compare-and-set trạng thái: trả về số dòng cập nhật (0 = có thao tác khác đã đổi trạng thái). */
    @Modifying
    @Query("update Event e set e.status = :to where e.id = :id and e.status = :from")
    int transitionStatus(@Param("id") Long id,
                         @Param("from") EventStatus from,
                         @Param("to") EventStatus to);
}
```

## 3. DTO

`dto/request/CreateEventRequest.java`
```java
public record CreateEventRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotBlank @Size(max = 255) String venue,
        @NotNull Long categoryId,
        @NotNull @Future Instant startTime,
        @NotNull Instant endTime
) {}
```

`dto/response/EventResponse.java`
```java
public record EventResponse(
        Long id,
        Long organizerId,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        String venue,
        EventStatus status,
        Instant startTime,
        Instant endTime,
        Instant createdAt
) {}
```

`dto/response/PageResponse.java`
```java
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
```

## 4. Mapper (MapStruct)

`mapper/EventMapper.java`
```java
@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    EventResponse toResponse(Event event);
}
```

## 5. Service (interface) và ServiceImpl

`service/EventService.java`
```java
public interface EventService {
    EventResponse create(Long organizerId, CreateEventRequest request);
    EventResponse submit(Long organizerId, Long eventId);
    EventResponse getPublished(Long eventId);
    PageResponse<EventResponse> searchPublished(String keyword, Long categoryId, Pageable pageable);
}
```

`service/impl/EventServiceImpl.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional
    public EventResponse create(Long organizerId, CreateEventRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_TIME);
        }
        EventCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Event event = Event.builder()
                .organizerId(organizerId)
                .category(category)
                .title(request.title())
                .description(request.description())
                .venue(request.venue())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(EventStatus.DRAFT)
                .build();
        return eventMapper.toResponse(eventRepository.save(event));
    }

    @Override
    @Transactional
    public EventResponse submit(Long organizerId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }
        // máy trạng thái: DRAFT -> SUBMITTED, atomic
        int updated = eventRepository.transitionStatus(eventId, EventStatus.DRAFT, EventStatus.SUBMITTED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_STATUS);
        }
        return eventMapper.toResponse(eventRepository.findById(eventId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getPublished(Long eventId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .map(eventMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchPublished(String keyword, Long categoryId, Pageable pageable) {
        return PageResponse.from(eventRepository.searchPublished(keyword, categoryId, pageable)
                .map(eventMapper::toResponse));
    }
}
```

## 6. Controller

`controller/EventController.java`
```java
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;   // luôn inject INTERFACE

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateEventRequest request) {
        Long organizerId = Long.valueOf(jwt.getSubject());      // userId lấy từ JWT, không từ body
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(organizerId, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return eventService.submit(Long.valueOf(jwt.getSubject()), id);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return eventService.getPublished(id);
    }

    @GetMapping
    public PageResponse<EventResponse> search(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long category,
                                              Pageable pageable) {
        return eventService.searchPublished(keyword, category, pageable);
    }
}
```

## 7. Xử lý lỗi thống nhất

`exception/ErrorCode.java`
```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"),
    INVALID_EVENT_TIME(HttpStatus.BAD_REQUEST, "Thời gian sự kiện không hợp lệ"),
    INVALID_EVENT_STATUS(HttpStatus.CONFLICT, "Trạng thái sự kiện không cho phép thao tác này"),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "Bạn không phải chủ sở hữu"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String message;
}
```

`exception/BusinessException.java`
```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

`exception/GlobalExceptionHandler.java`
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public record ErrorResponse(String code, String message, Instant timestamp, String path) {}

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(new ErrorResponse(ec.name(), ec.getMessage(), Instant.now(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), msg, Instant.now(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error at {}", req.getRequestURI(), ex);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(new ErrorResponse(ec.name(), ec.getMessage(), Instant.now(), req.getRequestURI()));
    }
}
```

## 8. Gọi REST service khác (`client/`)

`client/TicketClient.java` – interface (Order Service dùng, mock được khi test)
```java
public interface TicketClient {
    ReservationResult reserve(Long orderId, List<ReserveItem> items, Instant expiresAt);
    void confirm(Long reservationId);
    void release(Long reservationId);
}
```

`client/impl/TicketClientImpl.java`
```java
@Component
public class TicketClientImpl implements TicketClient {

    private final RestClient restClient;

    public TicketClientImpl(RestClient.Builder builder, @Value("${eventhub.ticket-service.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();   // cấu hình connect/read timeout ở RestClient factory
    }

    @Override
    public ReservationResult reserve(Long orderId, List<ReserveItem> items, Instant expiresAt) {
        return restClient.post().uri("/internal/reservations")
                .body(new ReserveRequest(orderId, items, expiresAt))
                .retrieve()
                .body(ReservationResult.class);
    }

    @Override
    public void confirm(Long reservationId) {
        restClient.post().uri("/internal/reservations/{id}/confirm", reservationId).retrieve().toBodilessEntity();
    }

    @Override
    public void release(Long reservationId) {
        restClient.post().uri("/internal/reservations/{id}/release", reservationId).retrieve().toBodilessEntity();
    }
}
```
Quy tắc: luôn đặt timeout; Reserve/Confirm/Release/Cancel đều **idempotent** nên được phép retry; lỗi tạm thời thì retry có giới hạn, sau đó chuyển xử lý thủ công/đối soát (không rollback xuyên service).

## 9. Phát Kafka event sau khi commit

`messaging/event/TicketIssuedEvent.java`
```java
public record TicketIssuedEvent(
        String eventId,           // UUID, dùng cho idempotency phía consumer
        String eventType,         // "ticket.issued"
        Instant occurredAt,
        Long orderId,
        List<IssuedTicket> tickets
) {
    public record IssuedTicket(Long ticketId, String qrCodeValue, String holderName, String holderEmail, Long ownerUserId) {}
}
```

`messaging/producer/TicketEventPublisher.java`
```java
@Component
@RequiredArgsConstructor
public class TicketEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Chỉ chạy SAU KHI transaction ghi vé đã commit -> không phát event cho dữ liệu bị rollback. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketIssuedEvent event) {
        kafkaTemplate.send("ticket.issued", String.valueOf(event.orderId()), event);
    }
}
```
Trong `ServiceImpl`: inject `ApplicationEventPublisher` và gọi `publisher.publishEvent(new TicketIssuedEvent(...))` bên trong method `@Transactional`. Với Event Service dùng bảng `outbox_events` thay thế (ghi outbox cùng transaction, job riêng đẩy lên Kafka).

## 10. Consume Kafka idempotent

`messaging/consumer/OrderEventConsumer.java`
```java
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationProcessingService processingService;   // interface, nghiệp vụ nằm ở ServiceImpl

    @KafkaListener(topics = "order.failed", groupId = "notification-service")
    public void onOrderFailed(OrderFailedEvent event) {
        processingService.handleOrderFailed(event);
    }
}
```

Trong `NotificationProcessingServiceImpl`:
```java
@Override
@Transactional
public void handleOrderFailed(OrderFailedEvent event) {
    if (processedEventRepository.existsById(event.eventId())) {
        return;                                   // đã xử lý -> bỏ qua (idempotent)
    }
    // ... render template ORDER_FAILED, gửi email, lưu notifications
    processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
}
```
Lỗi tạm thời: ném exception để Spring Kafka retry có backoff; hết số lần retry thì `DeadLetterPublishingRecoverer` đẩy sang `notification.dlq`.

## 11. Scheduler dùng compare-and-set

`scheduler/OrderExpirationScheduler.java`
```java
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderExpirationService orderExpirationService;   // interface

    @Scheduled(fixedDelayString = "${eventhub.order.expire-scan-ms:15000}")
    public void expireOrders() {
        orderExpirationService.expireOverdueOrders();
    }
}
```
Trong `ServiceImpl`: lấy danh sách `PENDING_PAYMENT` có `expires_at < now`, với mỗi đơn gọi `repository.transitionStatus(id, PENDING_PAYMENT, EXPIRED)`; chỉ khi trả về `1` mới gọi `ticketClient.release(...)` và publish `order.failed`; trả về `0` (đã `PAID`) thì bỏ qua. Nếu chạy nhiều instance, thêm khóa job (ví dụ ShedLock).

## 12. Cấu hình cần có trong mỗi service

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate          # schema do Flyway quản lý
    open-in-view: false
  flyway:
    enabled: true
  security:
    oauth2:
      resourceserver:
        jwt:
          secret-or-public-key: ${EVENTHUB_JWT_KEY}   # khóa dùng chung xác thực JWT do Auth Service cấp
```
Bật `@EnableMethodSecurity` trong `SecurityConfig`; map claim `roles` thành `ROLE_*`; mở `permitAll` đúng các endpoint public; `/internal/**` và `/gate/**` có cơ chế xác thực riêng.
