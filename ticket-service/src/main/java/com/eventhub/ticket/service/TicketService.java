package com.eventhub.ticket.service;

import com.eventhub.ticket.entity.Reservation;
import com.eventhub.ticket.entity.ReservationItem;
import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.TicketScan;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.entity.enums.ReservationStatus;
import com.eventhub.ticket.entity.enums.ScanType;
import com.eventhub.ticket.entity.enums.TicketStatus;
import com.eventhub.ticket.entity.enums.TicketTypeName;
import com.eventhub.ticket.entity.enums.TicketTypeStatus;
import com.eventhub.ticket.repository.ReservationRepository;
import com.eventhub.ticket.repository.TicketRepository;
import com.eventhub.ticket.repository.TicketScanRepository;
import com.eventhub.ticket.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketTypeRepository ticketTypeRepository;
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final TicketScanRepository ticketScanRepository;

    public TicketService(
            TicketTypeRepository ticketTypeRepository,
            ReservationRepository reservationRepository,
            TicketRepository ticketRepository,
            TicketScanRepository ticketScanRepository
    ) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.reservationRepository = reservationRepository;
        this.ticketRepository = ticketRepository;
        this.ticketScanRepository = ticketScanRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketType> listTicketTypes(UUID eventId) {
        return ticketTypeRepository.findByEventIdOrderByName(eventId);
    }

    @Transactional
    public List<TicketType> createTicketTypes(
            UUID eventId,
            List<TicketTypeDraft> drafts
    ) {
        if (eventId == null || drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("eventId and ticket types are required");
        }
        if (ticketTypeRepository.findByEventIdOrderByName(eventId).size() > 0) {
            throw new IllegalStateException("Ticket types already exist for event: " + eventId);
        }

        List<TicketType> ticketTypes = new ArrayList<>();
        for (TicketTypeDraft draft : drafts) {
            validateTicketTypeDraft(draft);
            TicketType ticketType = new TicketType();
            ticketType.setEventId(eventId);
            ticketType.setName(draft.name());
            ticketType.setPrice(draft.price());
            ticketType.setTotalQuota(draft.totalQuota());
            ticketType.setAvailableQuantity(draft.totalQuota());
            ticketType.setReservedQuantity(0);
            ticketType.setSoldQuantity(0);
            ticketType.setStatus(TicketTypeStatus.ACTIVE);
            ticketTypes.add(ticketType);
        }
        return ticketTypeRepository.saveAll(ticketTypes);
    }

    @Transactional
    public TicketType updateTicketType(
            UUID ticketTypeId,
            BigDecimal price,
            int totalQuota,
            TicketTypeStatus status
    ) {
        TicketType ticketType = getTicketTypeForUpdate(ticketTypeId);
        if (price == null || price.signum() < 0 || totalQuota < 0 || status == null) {
            throw new IllegalArgumentException("Invalid ticket type values");
        }
        int committedQuantity = ticketType.getReservedQuantity() + ticketType.getSoldQuantity();
        if (totalQuota < committedQuantity) {
            throw new IllegalArgumentException("totalQuota cannot be below reserved and sold quantity");
        }
        int quotaDelta = totalQuota - ticketType.getTotalQuota();
        ticketType.setPrice(price);
        ticketType.setTotalQuota(totalQuota);
        ticketType.setAvailableQuantity(ticketType.getAvailableQuantity() + quotaDelta);
        ticketType.setStatus(status);
        return ticketType;
    }

    @Transactional
    public Reservation reserve(
            UUID orderId,
            List<ReservationRequestItem> requestedItems,
            Duration holdDuration
    ) {
        if (orderId == null || requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("orderId and reservation items are required");
        }
        if (holdDuration == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("holdDuration must be positive");
        }
        if (reservationRepository.findByOrderId(orderId).isPresent()) {
            throw new IllegalStateException("A reservation already exists for order: " + orderId);
        }

        Reservation reservation = new Reservation();
        reservation.setOrderId(orderId);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setCreatedAt(Instant.now());
        reservation.setExpiresAt(Instant.now().plus(holdDuration));

        for (ReservationRequestItem requested : requestedItems) {
            if (requested.quantity() < 1) {
                throw new IllegalArgumentException("Reservation quantity must be positive");
            }
            TicketType ticketType = getTicketTypeForUpdate(requested.ticketTypeId());
            if (ticketType.getStatus() != TicketTypeStatus.ACTIVE
                    || ticketType.getAvailableQuantity() < requested.quantity()) {
                throw new IllegalStateException("Insufficient tickets for type: " + requested.ticketTypeId());
            }

            ticketType.setAvailableQuantity(ticketType.getAvailableQuantity() - requested.quantity());
            ticketType.setReservedQuantity(ticketType.getReservedQuantity() + requested.quantity());

            ReservationItem item = new ReservationItem();
            item.setReservation(reservation);
            item.setTicketType(ticketType);
            item.setQuantity(requested.quantity());
            item.setUnitPrice(ticketType.getPrice());
            reservation.getItems().add(item);
        }
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation releaseReservation(UUID reservationId) {
        Reservation reservation = getReservationForUpdate(reservationId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return reservation;
        }
        releaseReservedQuantities(reservation);
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        return reservation;
    }

    @Transactional
    public List<Ticket> confirmReservation(UUID reservationId, UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId is required");
        }
        Reservation reservation = getReservationForUpdate(reservationId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved reservations can be confirmed");
        }
        if (!reservation.getExpiresAt().isAfter(Instant.now())) {
            releaseReservedQuantities(reservation);
            reservation.setStatus(ReservationStatus.EXPIRED);
            throw new IllegalStateException("Reservation has expired");
        }

        List<Ticket> tickets = new ArrayList<>();
        for (ReservationItem item : reservation.getItems()) {
            TicketType ticketType = getTicketTypeForUpdate(item.getTicketType().getId());
            ticketType.setReservedQuantity(ticketType.getReservedQuantity() - item.getQuantity());
            ticketType.setSoldQuantity(ticketType.getSoldQuantity() + item.getQuantity());
            for (int i = 0; i < item.getQuantity(); i++) {
                Ticket ticket = new Ticket();
                ticket.setReservation(reservation);
                ticket.setOrderId(reservation.getOrderId());
                ticket.setTicketType(ticketType);
                ticket.setOwnerUserId(ownerUserId);
                ticket.setQrCodeValue(UUID.randomUUID().toString());
                ticket.setStatus(TicketStatus.ISSUED);
                ticket.setIssuedAt(Instant.now());
                tickets.add(ticket);
            }
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmedAt(Instant.now());
        return ticketRepository.saveAll(tickets);
    }

    @Transactional
    public int expireReservations(Instant now) {
        int expired = 0;
        for (Reservation reservation : reservationRepository
                .findByStatusAndExpiresAtLessThanEqual(ReservationStatus.RESERVED, now)) {
            releaseReservedQuantities(reservation);
            reservation.setStatus(ReservationStatus.EXPIRED);
            expired++;
        }
        return expired;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getTicketsForUser(UUID ownerUserId) {
        return ticketRepository.findByOwnerUserIdOrderByIssuedAtDesc(ownerUserId);
    }

    @Transactional
    public TicketScan scan(UUID ticketId, UUID gateId, ScanType scanType) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (gateId == null || scanType == null) {
            throw new IllegalArgumentException("gateId and scanType are required");
        }
        if (scanType == ScanType.CHECK_IN && ticket.getStatus() != TicketStatus.ISSUED) {
            throw new IllegalStateException("Ticket is not eligible for check-in");
        }
        if (scanType == ScanType.CHECK_OUT && ticket.getStatus() != TicketStatus.CHECKED_IN) {
            throw new IllegalStateException("Ticket is not eligible for check-out");
        }

        TicketScan scan = new TicketScan();
        scan.setTicket(ticket);
        scan.setGateId(gateId);
        scan.setScanType(scanType);
        scan.setScannedAt(Instant.now());
        ticket.setStatus(scanType == ScanType.CHECK_IN
                ? TicketStatus.CHECKED_IN
                : TicketStatus.CHECKED_OUT);
        ticketScanRepository.save(scan);
        return scan;
    }

    private TicketType getTicketTypeForUpdate(UUID ticketTypeId) {
        return ticketTypeRepository.findByIdForUpdate(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));
    }

    private Reservation getReservationForUpdate(UUID reservationId) {
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private void releaseReservedQuantities(Reservation reservation) {
        for (ReservationItem item : reservation.getItems()) {
            TicketType ticketType = getTicketTypeForUpdate(item.getTicketType().getId());
            ticketType.setReservedQuantity(ticketType.getReservedQuantity() - item.getQuantity());
            ticketType.setAvailableQuantity(ticketType.getAvailableQuantity() + item.getQuantity());
        }
    }

    private void validateTicketTypeDraft(TicketTypeDraft draft) {
        if (draft == null || draft.name() == null || draft.price() == null
                || draft.price().signum() < 0 || draft.totalQuota() < 1) {
            throw new IllegalArgumentException("Invalid ticket type draft");
        }
    }

    public record TicketTypeDraft(TicketTypeName name, BigDecimal price, int totalQuota) {
    }

    public record ReservationRequestItem(UUID ticketTypeId, int quantity) {
    }

    public static final class TicketTypeNotFoundException extends RuntimeException {
        public TicketTypeNotFoundException(UUID id) {
            super("Ticket type not found: " + id);
        }
    }

    public static final class ReservationNotFoundException extends RuntimeException {
        public ReservationNotFoundException(UUID id) {
            super("Reservation not found: " + id);
        }
    }

    public static final class TicketNotFoundException extends RuntimeException {
        public TicketNotFoundException(UUID id) {
            super("Ticket not found: " + id);
        }
    }
}
