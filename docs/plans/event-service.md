# Plan code event-service

Ngày 2026-10-28. Nguồn: [quy ước chung](00-cross-service-contracts.md), [OpenAPI Event](../api/event-service-openapi.yaml), [DDL](../database/event-service/init.sql), [ERD UUID](../database/event-service/erd.md). Event sở hữu `categories/events`, không sở hữu ticket_types, tồn kho, zone A–D hoặc bảng người dùng.

## 1. Entity, DTO và package

Tạo `entity/Category, Event`, enum `EventStatus`; `EventRepository, CategoryRepository`; `EventService, CategoryService` cùng `impl`; controller Event/OrganizerEvent/AdminEvent/Category/InternalEvent; mapper, exception, security và config.

ID/organizerId/categoryId đều UUID, local Category association ánh xạ FK `events.category_id`; organizerId là ID mềm Keycloak/Auth. Event có title/description/status/startTime/endTime/createdAt/updatedAt/reviewReason theo DDL; không tự thêm venue trước C03. reviewReason là bổ sung kỹ thuật đã có reference DDL, map API rejectReason cho kết quả từ chối phù hợp.

Request: EventRequest, ReviewRequest, CategoryRequest. Response: EventResponse (category object CategoryRef), EventSummary, EventStatusResponse, CategoryResponse, InternalEventResponse, PageResponse. Internal response gồm id/organizerId/title/status/startTime/endTime đúng API. Các command `EventSearch`/`OrganizerEventFilter` chỉ là DTO query, không Entity.

## 2. EventService

| Signature đề xuất | Logic / transaction |
|---|---|
| `create(Actor organizer, EventRequest request): EventResponse` | ORGANIZER, category tồn tại, startTime < endTime → DRAFT; organizerId từ Principal. Ghi một transaction, không tạo loại vé cùng DB. |
| `update(Actor organizer, UUID eventId, EventRequest request): EventResponse` | Lock event, owner, chỉ DRAFT/REJECTED; sửa REJECTED → DRAFT, xóa lý do review cũ khi resubmit vòng mới; cập nhật field cho phép. |
| `submit(Actor organizer, UUID eventId): EventStatusResponse` | Lock/CAS DRAFT → SUBMITTED, owner. Điều kiện đủ hạng/quota trước submit chờ C08; không tự thêm lỗi bắt buộc chưa duyệt. |
| `review(Actor admin, UUID eventId, ReviewRequest request): EventStatusResponse` | ADMIN, lock SUBMITTED → APPROVED/REJECTED; reason bắt buộc khi REJECT; lưu review_reason/update time cùng transaction. |
| `publish(Actor organizer, UUID eventId): EventStatusResponse` | Owner, APPROVED → PUBLISHED. Nếu C08 yêu cầu cấu hình Ticket, gọi client trước transaction và recheck/CAS khi ghi; cần quy tắc chịu thay đổi cấu hình đồng thời. |
| `close(Actor organizer, UUID eventId): EventStatusResponse` | Owner, PUBLISHED → CLOSED; nghĩa ngừng bán, không cancel vé hay cấm check-in. |
| `searchPublic(EventSearch filter, Pageable pageable): PageResponse<EventSummary>` | Chỉ PUBLISHED, keyword/category/startDate/endDate, sort ổn định; read-only. Validate date range; normalize keyword, không ghép SQL từ input. |
| `getPublic(UUID eventId): EventResponse` | Query id+PUBLISHED, không có trả 404; không tự gom Ticket DB. |
| `getMine(Actor organizer, OrganizerEventFilter filter, Pageable pageable): PageResponse<EventResponse>` | Query organizerId=Principal, filter status nếu có theo API, read-only. |
| `getInternal(ServiceActor caller, UUID eventId): InternalEventResponse` | Service được cấp quyền, trả mọi status cho kiểm tra owner/giờ/sales; không public, 404 nếu không tồn tại. |

Chọn một cách nhất quán cho máy trạng thái: `findByIdForUpdate` kiểm tra rồi mutate/save cùng transaction, hoặc CAS có điều kiện trạng thái/owner. Không đọc entity ngoài transaction rồi save toàn bộ vì có thể ghi đè review/close của worker khác. Với network ở publish, không giữ khóa Event trong lúc chờ Ticket.

Không tự tạo topic event.created/event.published hoặc outbox Event: chưa có consumer/hợp đồng yêu cầu. Nếu cần audit review nhiều vòng, đề xuất schema audit riêng, không giả bảng đó đã có trong baseline.

## 3. CategoryService

| Signature | Logic |
|---|---|
| `create(Actor admin, CategoryRequest request): CategoryResponse` | ADMIN, normalize name, unique constraint cuối cùng chống hai request tạo trùng; transaction ghi. |
| `list(): List<CategoryResponse>` | Public read-only, sort name/id, không trả entity. |
| `update(Actor admin, UUID categoryId, CategoryRequest request): CategoryResponse` | ADMIN, tồn tại, không đụng tên category khác; transaction ghi. |
| `deleteUnused(Actor admin, UUID categoryId): void` | DELETE của API chỉ cho category chưa có event tham chiếu; exists check + FK RESTRICT là chốt cuối. Có event thì 409 CATEGORY_IN_USE. Không cascade xóa events để xóa category. |

Nếu nhóm muốn soft-delete category, thêm field/migration/API filter trước, không gọi delete rồi giả là đã soft-delete vì DDL chưa có cột active/deleted_at.

## 4. Repository methods

Tất cả extends JpaRepository với UUID. Method ghi/lock chỉ chạy trong transaction service.

| Repository / method | Kiểu query / index và mục đích |
|---|---|
| `EventRepository.findByIdAndStatus(UUID id, EventStatus status): Optional<Event>` | Derived query cho public detail, không lộ event chưa publish. |
| `findByIdForUpdate(UUID id): Optional<Event>` | JPQL + PESSIMISTIC_WRITE cho edit/state; UUID PK. |
| `findByOrganizerId(UUID organizerId, Pageable page): Page<Event>` | Dựa idx_events_owner; có thể dùng Specification cho status filter. |
| `findAll(Specification<Event> filters, Pageable page): Page<Event>` | JpaSpecificationExecutor; public filter luôn kèm status PUBLISHED ở service/spec builder tin cậy. |
| `existsByCategoryId(UUID categoryId): boolean` | Chặn xóa category đang dùng; FK DB vẫn bắt race. |
| `compareAndSetStatus(UUID id, UUID ownerId, EventStatus from, EventStatus to, Instant updatedAt): int` | Phương án thay pessimistic lock cho owner transitions; admin review dùng điều kiện phù hợp và review_reason cùng update. Không dùng owner giả cho ADMIN. |
| `CategoryRepository.existsByName(String normalizedName): boolean` | UX kiểm tra trước; UNIQUE là chốt chống race. |
| `existsByNameAndIdNot(String name, UUID id): boolean` | Update không tự đụng tên chính nó. |
| `findAllByOrderByNameAscIdAsc(): List<Category>` | Danh mục public ổn định. |
| `save/findById/delete` | Kế thừa; delete chỉ qua service đã kiểm tra. Không cần CRUD repository thứ ba cho bảng không có. |

Chốt cách chuẩn hóa/collation tên với UNIQUE của MySQL: không dựa riêng check case-sensitive trong Java rồi kỳ vọng DB cho phép tên trùng khác hoa/thường. Không dùng enum cho tên category.

## 5. API → service

| Operation | Service method |
|---|---|
| POST `/api/v1/events` | create |
| PUT `/api/v1/events/{id}` | update |
| POST `/api/v1/events/{id}/submit` | submit |
| PATCH `/api/v1/admin/events/{id}/review` | review |
| POST `/api/v1/events/{id}/publish` | publish |
| POST `/api/v1/events/{id}/close` | close |
| GET `/api/v1/events` | searchPublic |
| GET `/api/v1/events/{id}` | getPublic |
| GET `/api/v1/organizer/events` | getMine |
| POST/GET `/api/v1/categories` | CategoryService.create/list |
| PUT/DELETE `/api/v1/categories/{id}` | CategoryService.update/deleteUnused |
| GET `/internal/events/{id}` | getInternal |

Nếu C08 yêu cầu Event kiểm tra Ticket trước submit/publish, thêm `TicketConfigurationClient.getConfiguration(UUID): TicketConfigurationResponse` và OpenAPI nội bộ theo [plan chung](00-cross-service-contracts.md). Không giả endpoint này đã có.

## 6. Migration, lỗi và kiểm tra

Event chưa có Flyway/entity: tạo migration đầu tiên dựa DDL tham chiếu, đổi ddl-auto validate cùng thay đổi triển khai, dùng UUID CHAR(36) và các index hiện có. Với DB đã có bảng dùng mapping cũ phải kiểm tra/baseline có kiểm soát; không bật tự động để che khác schema.

ErrorCode: EVENT_NOT_FOUND/CATEGORY_NOT_FOUND 404, FORBIDDEN_NOT_OWNER/FORBIDDEN 403, INVALID_EVENT_STATE/CATEGORY_ALREADY_EXISTS/CATEGORY_IN_USE 409, INVALID_DATE_RANGE/VALIDATION_ERROR 400, TICKET_SERVICE_UNAVAILABLE 503 nếu C08 cần client.

Test cần có: role+owner; DRAFT→SUBMITTED→APPROVED→PUBLISHED→CLOSED; REJECTED edit→DRAFT; hai review/edit đồng thời chỉ một trạng thái hợp lệ; public không thấy draft/closed; internal vẫn đọc được; filter ngày/page ổn định; xóa category đua create Event bị FK chặn; schema UUID cột organizer/category. Unit service + WebMvcTest, MySQL Testcontainers cho lock/FK/unique. C03/C08 chỉ chặn nhánh phụ thuộc, không chặn toàn bộ Category/Event core.
