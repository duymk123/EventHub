package com.eventhub.ticket.service;

import com.eventhub.ticket.common.ReservationStatus;
import com.eventhub.ticket.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Trả quota cho reservation quá hạn: lấy một lô ID rồi expire từng ID trong transaction riêng (plan Ticket §5),
 * một reservation lỗi không làm rollback cả lô.
 */
@Component
public class ReservationExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final Clock clock;

    public ReservationExpiryScheduler(ReservationRepository reservations, ReservationService reservationService,
                                      Clock clock) {
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eventhub.ticket.expiry-delay-ms:10000}")
    public void expireDue() {
        Instant now = clock.instant();
        List<UUID> due = reservations.findDueIds(ReservationStatus.RESERVED, now, PageRequest.of(0, BATCH_SIZE));
        for (UUID id : due) {
            try {
                reservationService.expire(id, now);
            } catch (RuntimeException error) {
                log.warn("Cannot expire reservation {}: {}", id, error.getClass().getSimpleName());
            }
        }
    }
}
