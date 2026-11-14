package com.eventhub.analytics.service.impl;

import com.eventhub.analytics.messaging.InvalidEventException;
import com.eventhub.analytics.messaging.event.*;
import com.eventhub.analytics.repository.ProjectionRepository;
import com.eventhub.analytics.service.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.UUID;

/**
 * Dựng read model bằng ba handler paid, refunded và scan. Mỗi handler ghi claim và thay đổi DB trong một
 * transaction rồi phát DashboardChangedEvent nội bộ.
 */
@Service
public class AnalyticsProjectionServiceImpl implements AnalyticsProjectionService {
    private final ProjectionRepository repository;
    private final com.eventhub.analytics.repository.SalesMetricRepository metrics;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId reportingZone;

    public AnalyticsProjectionServiceImpl(ProjectionRepository repository,
            com.eventhub.analytics.repository.SalesMetricRepository metrics, ApplicationEventPublisher events,
            Clock clock, Environment environment) {
        this.repository = repository;
        this.metrics = metrics;
        this.events = events;
        this.clock = clock;
        this.reportingZone = ZoneId.of(environment.getRequiredProperty("eventhub.analytics.reporting-zone"));
    }

    /**
     * Kiểm tra các dòng vé, phép nhân giá/số lượng và tổng tiền. Trong một transaction, claim messageId, khóa
     * snapshot, UPSERT doanh số theo ngày Việt Nam rồi cập nhật tổng gộp và phát tín hiệu dashboard.
     */
    @Override
    @Transactional
    public void handlePaid(OrderPaidEvent event) {
        var types = new HashSet<UUID>();
        BigDecimal total = BigDecimal.ZERO;
        int quantity = 0;
        for (var item : event.items()) {
            if (!types.add(item.ticketTypeId())
                    || item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())).compareTo(item.lineTotal()) != 0) {
                throw new InvalidEventException("Invalid order sales lines");
            }
            total = total.add(item.lineTotal());
            quantity = Math.addExact(quantity, item.quantity());
        }
        if (total.compareTo(event.totalAmount()) != 0) {
            throw new InvalidEventException("Sales total does not match lines");
        }
        // Claim và tất cả phép cộng bên dưới cùng transaction của handlePaid.
        // Message trùng làm transaction này rollback trước khi consumer kết thúc record.
        repository.claim(event.messageId(), clock.instant());
        repository.lockSnapshot(event.eventId(), event.paidAt());
        for (var item : event.items()) {
            // UUID mới phục vụ INSERT, unique event/type/date chọn dòng cần cộng khi đã tồn tại.
            metrics.upsertSale(UUID.randomUUID(), event.eventId(), item.ticketTypeId(), item.ticketTypeName(),
                    event.paidAt().atZone(reportingZone).toLocalDate(), item.lineTotal(), item.quantity(), event.paidAt());
        }
        repository.addSalesSnapshot(event.eventId(), total, quantity, event.paidAt());
        // Tín hiệu trong application context, DashboardPushService xử lý khi transaction đã commit.
        events.publishEvent(new DashboardChangedEvent(event.eventId()));
    }

    /**
     * Kiểm tra mỗi hạng vé xuất hiện một lần và tổng refundedAmount bằng amount. Claim messageId rồi ghi phần
     * hoàn theo ngày refundedAt, giữ tổng gộp dashboard và cập nhật mốc thời gian.
     */
    @Override
    @Transactional
    public void handleRefunded(OrderRefundedEvent event) {
        var types = new HashSet<UUID>();
        BigDecimal total = BigDecimal.ZERO;
        for (var item : event.items()) {
            if (!types.add(item.ticketTypeId())) {
                throw new InvalidEventException("Duplicate refund ticket type");
            }
            total = total.add(item.refundedAmount());
        }
        if (total.compareTo(event.amount()) != 0) {
            throw new InvalidEventException("Refund allocation does not match total");
        }
        repository.claim(event.messageId(), clock.instant());
        repository.lockSnapshot(event.eventId(), event.refundedAt());
        for (var item : event.items()) {
            metrics.upsertRefund(UUID.randomUUID(), event.eventId(), item.ticketTypeId(), item.ticketTypeName(),
                    event.refundedAt().atZone(reportingZone).toLocalDate(),
                    item.refundedAmount(), item.quantity(), event.refundedAt());
        }
        // Dashboard giữ tổng gộp toàn sự kiện, số hoàn đã được lưu riêng trong sales_metrics.
        repository.addSalesSnapshot(event.eventId(), BigDecimal.ZERO, 0, event.refundedAt());
        events.publishEvent(new DashboardChangedEvent(event.eventId()));
    }

    /**
     * Claim messageId, khóa snapshot và state của vé, tính đóng góp trước/sau từ hai bằng chứng vào/ra. Ghi
     * log, state và chênh lệch attendance cùng transaction để chịu đảo thứ tự và xử lý đồng thời.
     */
    @Override
    @Transactional
    public void handleScan(TicketScanEvent event) {
        boolean checkIn = "ticket.checked-in".equals(event.eventType());
        repository.claim(event.messageId(), clock.instant());
        repository.lockSnapshot(event.eventId(), event.scannedAt());
        var before = repository.lockTicket(event.ticketId(), event.eventId());
        // Giữ cả hai bằng chứng. Checkout đến trước vẫn được nhớ khi checkin đến sau.
        boolean inSeen = before.checkedIn() || checkIn;
        boolean outSeen = before.checkedOut() || !checkIn;
        int contribution = inSeen && !outSeen ? 1 : 0;
        // Chỉ cộng chênh lệch trước/sau. Một vé đã ra cổng có đóng góp hiện diện bằng 0.
        repository.recordAttendance(event.messageId(), event.eventId(), event.ticketId(), event.gateId(), checkIn,
                event.scannedAt(), contribution - before.contribution(), (inSeen ? 1 : 0) - (before.checkedIn() ? 1 : 0));
        events.publishEvent(new DashboardChangedEvent(event.eventId()));
    }
}
