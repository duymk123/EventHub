package com.eventhub.event.service.impl;

import com.eventhub.event.client.TicketConfigurationClient;
import com.eventhub.event.common.Actor;
import com.eventhub.event.common.ErrorCode;
import com.eventhub.event.common.EventStatus;
import com.eventhub.event.common.ReviewDecision;
import com.eventhub.event.dto.request.EventRequest;
import com.eventhub.event.dto.request.ReviewRequest;
import com.eventhub.event.dto.response.EventResponse;
import com.eventhub.event.dto.response.EventStatusResponse;
import com.eventhub.event.dto.response.EventSummary;
import com.eventhub.event.dto.response.InternalEventResponse;
import com.eventhub.event.entity.Category;
import com.eventhub.event.entity.Event;
import com.eventhub.event.exception.BusinessException;
import com.eventhub.event.repository.CategoryRepository;
import com.eventhub.event.repository.EventRepository;
import com.eventhub.event.service.EventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Mọi chuyển trạng thái khóa hàng event (PESSIMISTIC_WRITE) rồi kiểm tra owner/trạng thái trong cùng transaction,
 * nên hai request song song không ghi đè nhau. Publish gọi Ticket ngoài transaction để không giữ khóa khi chờ
 * mạng, rồi kiểm tra lại khi ghi.
 */
@Service
public class EventServiceImpl implements EventService {
    private static final Set<String> SORTABLE = Set.of("startTime", "endTime", "title", "createdAt", "updatedAt",
            "status");
    private static final int MAX_BATCH = 100;

    private final EventRepository events;
    private final CategoryRepository categories;
    private final TicketConfigurationClient ticketConfiguration;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId searchZone;

    public EventServiceImpl(EventRepository events, CategoryRepository categories,
                            TicketConfigurationClient ticketConfiguration, PlatformTransactionManager transactions,
                            Clock clock, @Value("${eventhub.event.search-zone:Asia/Ho_Chi_Minh}") String searchZone) {
        this.events = events;
        this.categories = categories;
        this.ticketConfiguration = ticketConfiguration;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.searchZone = ZoneId.of(searchZone);
    }

    @Override
    @Transactional
    public EventResponse create(Actor organizer, EventRequest request) {
        validateTimes(request);
        Event event = Event.builder()
                .organizerId(organizer.userId())
                .category(category(request.categoryId()))
                .title(request.title().trim())
                .description(blankToNull(request.description()))
                .status(EventStatus.DRAFT)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();
        // category đã có trong bộ nhớ nên không cần đi thêm DB khi map response.
        return EventResponse.from(events.saveAndFlush(event));
    }

    @Override
    @Transactional
    public EventResponse update(Actor organizer, UUID eventId, EventRequest request) {
        Event event = lockOwned(organizer, eventId);
        requireStatus(event, EnumSet.of(EventStatus.DRAFT, EventStatus.REJECTED));
        validateTimes(request);
        event.setCategory(category(request.categoryId()));
        event.setTitle(request.title().trim());
        event.setDescription(blankToNull(request.description()));
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        if (event.getStatus() == EventStatus.REJECTED) {
            // Sửa sau khi bị từ chối = bắt đầu vòng duyệt mới.
            event.setStatus(EventStatus.DRAFT);
            event.setReviewReason(null);
        }
        return EventResponse.from(events.saveAndFlush(event));
    }

    @Override
    @Transactional
    public EventStatusResponse submit(Actor organizer, UUID eventId) {
        Event event = lockOwned(organizer, eventId);
        requireStatus(event, EnumSet.of(EventStatus.DRAFT));
        requireNotStarted(event);
        event.setStatus(EventStatus.SUBMITTED);
        return EventStatusResponse.from(events.saveAndFlush(event));
    }

    @Override
    @Transactional
    public EventStatusResponse review(Actor admin, UUID eventId, ReviewRequest request) {
        Event event = events.findByIdForUpdate(eventId).orElseThrow(EventServiceImpl::eventNotFound);
        requireStatus(event, EnumSet.of(EventStatus.SUBMITTED));
        if (request.decision() == ReviewDecision.REJECT) {
            String reason = blankToNull(request.reason());
            if (reason == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason: is required when decision is REJECT");
            }
            event.setStatus(EventStatus.REJECTED);
            event.setReviewReason(reason);
        } else {
            event.setStatus(EventStatus.APPROVED);
            event.setReviewReason(null);
        }
        return EventStatusResponse.from(events.saveAndFlush(event));
    }

    @Override
    public EventStatusResponse publish(Actor organizer, UUID eventId) {
        // 1) Kiểm tra nhanh không khóa để không gọi Ticket cho request chắc chắn bị từ chối.
        //    Dùng findWithCategoryById (không phải findById): lúc commit, entity đang attached được Hibernate
        //    kiểm tra và flush; category lazy chưa nạp sẽ ném LazyInitializationException (open-in-view: false).
        tx.executeWithoutResult(status -> {
            Event event = events.findWithCategoryById(eventId).orElseThrow(EventServiceImpl::eventNotFound);
            requireOwner(organizer, event);
            requireStatus(event, EnumSet.of(EventStatus.APPROVED));
            requireNotStarted(event);
        });
        // 2) Preflight C08 ngoài transaction: ít nhất một hạng ACTIVE có total_quota > 0.
        if (!ticketConfiguration.isReadyToPublish(eventId)) {
            throw new BusinessException(ErrorCode.TICKET_CONFIGURATION_NOT_READY,
                    "At least one ACTIVE ticket type with quota > 0 is required");
        }
        // 3) Khóa và kiểm tra lại: trạng thái có thể đã đổi trong lúc chờ Ticket.
        return tx.execute(status -> {
            Event event = lockOwned(organizer, eventId);
            requireStatus(event, EnumSet.of(EventStatus.APPROVED));
            requireNotStarted(event);
            event.setStatus(EventStatus.PUBLISHED);
            return EventStatusResponse.from(events.saveAndFlush(event));
        });
    }

    @Override
    @Transactional
    public EventStatusResponse close(Actor organizer, UUID eventId) {
        Event event = lockOwned(organizer, eventId);
        requireStatus(event, EnumSet.of(EventStatus.PUBLISHED));
        event.setStatus(EventStatus.CLOSED);
        return EventStatusResponse.from(events.saveAndFlush(event));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventSummary> searchPublic(String keyword, UUID categoryId, LocalDate startDate, LocalDate endDate,
                                           Pageable pageable) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "startDate must not be after endDate");
        }
        List<Specification<Event>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> cb.equal(root.get("status"), EventStatus.PUBLISHED));
        String normalized = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !normalized.isEmpty()) {
            String pattern = "%" + escapeLike(normalized) + "%";
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (categoryId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (startDate != null) {
            Instant from = startDate.atStartOfDay(searchZone).toInstant();
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from));
        }
        if (endDate != null) {
            // Bao gồm cả ngày endDate: so sánh < 00:00 của ngày hôm sau.
            Instant until = endDate.plusDays(1).atStartOfDay(searchZone).toInstant();
            filters.add((root, query, cb) -> cb.lessThan(root.get("startTime"), until));
        }
        return events.findAll(Specification.allOf(filters), stable(pageable, Sort.by("startTime").ascending()))
                .map(EventSummary::from);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getPublic(UUID eventId) {
        return events.findByIdAndStatus(eventId, EventStatus.PUBLISHED)
                .map(EventResponse::from)
                .orElseThrow(EventServiceImpl::eventNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getMine(Actor organizer, EventStatus status, Pageable pageable) {
        Pageable page = stable(pageable, Sort.by("createdAt").descending());
        Page<Event> result = status == null
                ? events.findByOrganizerId(organizer.userId(), page)
                : events.findByOrganizerIdAndStatus(organizer.userId(), status, page);
        return result.map(EventResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getAll(EventStatus status, Pageable pageable) {
        Pageable page = stable(pageable, Sort.by("updatedAt").descending());
        Page<Event> result = status == null ? events.findAll(page) : events.findByStatus(status, page);
        return result.map(EventResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalEventResponse getInternal(UUID eventId) {
        return events.findById(eventId).map(InternalEventResponse::from).orElseThrow(EventServiceImpl::eventNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getInternalIdsByOrganizer(UUID organizerId) {
        return events.findIdsByOrganizerId(organizerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalEventResponse> getInternalBatch(Collection<UUID> eventIds) {
        Set<UUID> ids = new LinkedHashSet<>(eventIds);
        if (ids.isEmpty() || ids.size() > MAX_BATCH || ids.contains(null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ids must contain 1.." + MAX_BATCH + " UUIDs");
        }
        return events.findAllById(ids).stream().map(InternalEventResponse::from).toList();
    }

    private Event lockOwned(Actor organizer, UUID eventId) {
        Event event = events.findByIdForUpdate(eventId).orElseThrow(EventServiceImpl::eventNotFound);
        requireOwner(organizer, event);
        return event;
    }

    private static void requireOwner(Actor organizer, Event event) {
        if (!event.getOrganizerId().equals(organizer.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER, "Event belongs to another organizer");
        }
    }

    private static void requireStatus(Event event, Set<EventStatus> allowed) {
        if (!allowed.contains(event.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_STATE,
                    "Event is " + event.getStatus() + ", expected one of " + allowed);
        }
    }

    private void requireNotStarted(Event event) {
        if (!event.getStartTime().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_STATE, "Event has already started");
        }
    }

    private void validateTimes(EventRequest request) {
        if (!request.startTime().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startTime: must be in the future");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "endTime: must be after startTime");
        }
    }

    private Category category(UUID categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found"));
    }

    /**
     * Chỉ cho sắp xếp theo các trường công khai (không để client sort theo cột nội bộ), thêm id làm tie-breaker
     * để phân trang ổn định.
     */
    private static Pageable stable(Pageable pageable, Sort defaultSort) {
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "sort: unsupported property " + order.getProperty());
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException eventNotFound() {
        return new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found");
    }
}
