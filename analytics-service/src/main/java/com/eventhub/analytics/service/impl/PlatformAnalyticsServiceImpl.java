package com.eventhub.analytics.service.impl;

import com.eventhub.analytics.client.EventClient;
import com.eventhub.analytics.client.InternalEventResponse;
import com.eventhub.analytics.client.TicketCapacityClient;
import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.common.BusinessRole;
import com.eventhub.analytics.common.ErrorCode;
import com.eventhub.analytics.common.EventAnalyticsSort;
import com.eventhub.analytics.common.Granularity;
import com.eventhub.analytics.dto.response.AnalyticsOverviewResponse;
import com.eventhub.analytics.dto.response.EventAnalyticsRow;
import com.eventhub.analytics.exception.BusinessException;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries.EventMetricsRow;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries.LifetimeRow;
import com.eventhub.analytics.service.EventAccessService;
import com.eventhub.analytics.service.PlatformAnalyticsService;
import com.eventhub.analytics.service.impl.PlatformAnalyticsReadService.EventPageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Điều phối thống kê gộp (C10). Thứ tự cố định: kiểm role (không cần mạng) → validate khoảng ngày → xác định
 * phạm vi sự kiện qua Event (owner của eventId, hoặc danh sách sự kiện của ORGANIZER) → đọc DB trong transaction
 * ngắn → gọi Event/Ticket batch sau khi transaction đóng. Không giữ connection DB trong lúc chờ HTTP.
 *
 * <p>Phạm vi: ADMIN = mọi sự kiện; ORGANIZER = chỉ sự kiện mình sở hữu (INT-03); CUSTOMER = 403.
 */
@Service
public class PlatformAnalyticsServiceImpl implements PlatformAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAnalyticsServiceImpl.class);

    static final int MAX_DAYS = 366;
    static final int MAX_MONTHS = 36;
    static final int MAX_PAGE_SIZE = 100;

    private final EventAccessService access;
    private final PlatformAnalyticsReadService reader;
    private final EventClient eventClient;
    private final TicketCapacityClient capacityClient;

    public PlatformAnalyticsServiceImpl(EventAccessService access, PlatformAnalyticsReadService reader,
                                        EventClient eventClient, TicketCapacityClient capacityClient) {
        this.access = access;
        this.reader = reader;
        this.eventClient = eventClient;
        this.capacityClient = capacityClient;
    }

    /**
     * Có eventId: cùng kiểm tra quyền với các API theo sự kiện (ADMIN hoặc ORGANIZER owner, 404/503 theo Event
     * Service). Không có eventId: ADMIN nhận tổng toàn hệ thống, ORGANIZER nhận tổng các sự kiện của mình.
     */
    @Override
    public AnalyticsOverviewResponse getOverview(Actor actor, LocalDate from, LocalDate to, Granularity granularity,
                                                 UUID eventId) {
        requireAnalyticsRole(actor);
        validateRange(from, to, granularity);
        Collection<UUID> scope;
        if (eventId != null) {
            access.assertCanView(actor, eventId);
            scope = List.of(eventId);
        } else {
            scope = scopeOf(actor);
        }
        return reader.overview(from, to, granularity, eventId, scope);
    }

    /**
     * Trang sự kiện có phát sinh trong khoảng: ADMIN thấy mọi sự kiện, ORGANIZER chỉ sự kiện của mình.
     * Event Service lỗi → 503; Ticket Service lỗi → capacity=null. Id Event không trả về (dữ liệu lệch) bị bỏ
     * khỏi content và ghi log, totalElements giữ theo DB Analytics.
     */
    @Override
    public Page<EventAnalyticsRow> listEvents(Actor actor, LocalDate from, LocalDate to, EventAnalyticsSort sort,
                                              int page, int size) {
        requireAnalyticsRole(actor);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page must be >= 0 and size within 1..100");
        }
        // Bảng sự kiện không có granularity, dùng giới hạn rộng nhất là 36 tháng.
        validateRange(from, to, Granularity.MONTH);

        EventPageData data = reader.eventPage(from, to, scopeOf(actor), sort, page, size);
        PageRequest pageable = PageRequest.of(page, size);
        if (data.rows().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, data.total());
        }

        List<UUID> ids = data.rows().stream().map(EventMetricsRow::eventId).toList();
        Map<UUID, InternalEventResponse> events = eventClient.getBatch(ids);
        Map<UUID, Integer> capacity = capacityClient.getCapacity(ids);

        List<EventAnalyticsRow> content = new ArrayList<>(data.rows().size());
        for (EventMetricsRow row : data.rows()) {
            InternalEventResponse event = events.get(row.eventId());
            if (event == null) {
                log.error("Analytics has metrics for event {} that Event Service did not return", row.eventId());
                continue;
            }
            LifetimeRow lifetime = data.lifetime().get(row.eventId());
            long validLifetime = lifetime == null ? 0 : lifetime.ticketsSold() - lifetime.ticketsRefunded();
            long attendeesLifetime = lifetime == null ? 0 : lifetime.checkedIn();
            content.add(new EventAnalyticsRow(row.eventId(), event.title(), event.status(), event.startTime(),
                    capacity.get(row.eventId()), row.gross(), row.refund(), row.net(), row.sold(),
                    row.refundedTickets(), row.attendees(), validLifetime, attendeesLifetime));
        }
        return new PageImpl<>(content, pageable, data.total());
    }

    private static void requireAnalyticsRole(Actor actor) {
        if (actor == null || (actor.role() != BusinessRole.ADMIN && actor.role() != BusinessRole.ORGANIZER)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Analytics requires ADMIN or ORGANIZER");
        }
    }

    /**
     * null = không giới hạn (ADMIN). ORGANIZER: id mọi sự kiện mình sở hữu, lấy qua Event INT-03 với userId từ
     * JWT đã xác minh (không nhận organizerId từ request). Danh sách rỗng hợp lệ: organizer chưa có sự kiện.
     */
    private Collection<UUID> scopeOf(Actor actor) {
        return actor.role() == BusinessRole.ADMIN ? null : eventClient.getIdsByOrganizer(actor.userId());
    }

    /**
     * from/to bắt buộc, from ≤ to; DAY tối đa 366 ngày, MONTH tối đa 36 tháng dương lịch (tính cả tháng đầu/cuối).
     */
    static void validateRange(LocalDate from, LocalDate to, Granularity granularity) {
        if (from == null || to == null || granularity == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from, to and granularity are required");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "from date must not be after to date");
        }
        boolean tooLarge = granularity == Granularity.DAY
                ? ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS
                : ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1 > MAX_MONTHS;
        if (tooLarge) {
            throw new BusinessException(ErrorCode.DATE_RANGE_TOO_LARGE, granularity == Granularity.DAY
                    ? "DAY range must not exceed " + MAX_DAYS + " days"
                    : "MONTH range must not exceed " + MAX_MONTHS + " months");
        }
    }
}
