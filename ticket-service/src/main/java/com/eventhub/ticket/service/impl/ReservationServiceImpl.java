package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.client.EventClient;
import com.eventhub.ticket.client.InternalEventResponse;
import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.common.ReservationStatus;
import com.eventhub.ticket.common.TicketStatus;
import com.eventhub.ticket.common.TicketTypeStatus;
import com.eventhub.ticket.dto.request.ConfirmRequest;
import com.eventhub.ticket.dto.request.Holder;
import com.eventhub.ticket.dto.request.ReserveItem;
import com.eventhub.ticket.dto.request.ReserveRequest;
import com.eventhub.ticket.dto.response.ConfirmResponse;
import com.eventhub.ticket.dto.response.ReservationResponse;
import com.eventhub.ticket.dto.response.ReservationStatusResponse;
import com.eventhub.ticket.dto.response.TicketResponse;
import com.eventhub.ticket.entity.Reservation;
import com.eventhub.ticket.entity.ReservationItem;
import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.ReservationRepository;
import com.eventhub.ticket.repository.TicketRepository;
import com.eventhub.ticket.repository.TicketTypeRepository;
import com.eventhub.ticket.service.OutboxWriter;
import com.eventhub.ticket.service.QrTokenGenerator;
import com.eventhub.ticket.service.ReservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reserve/confirm/release/expire. Lời gọi Event Service luôn nằm ngoài transaction; mọi thay đổi tồn kho
 * chạy dưới khóa hàng ticket_types theo thứ tự id. Tồn kho luôn giữ available + reserved + sold = totalQuota.
 */
@Service
public class ReservationServiceImpl implements ReservationService {
    private final ReservationRepository reservations;
    private final TicketTypeRepository types;
    private final TicketRepository tickets;
    private final EventClient eventClient;
    private final OutboxWriter outbox;
    private final QrTokenGenerator qrTokens;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration reservationTtl;

    public ReservationServiceImpl(ReservationRepository reservations, TicketTypeRepository types,
                                  TicketRepository tickets, EventClient eventClient, OutboxWriter outbox,
                                  QrTokenGenerator qrTokens, TransactionTemplate tx, Clock clock,
                                  @Value("${eventhub.ticket.reservation-ttl:PT3M}") Duration reservationTtl) {
        this.reservations = reservations;
        this.types = types;
        this.tickets = tickets;
        this.eventClient = eventClient;
        this.outbox = outbox;
        this.qrTokens = qrTokens;
        this.tx = tx;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    // ---------------------------------------------------------------- reserve

    @Override
    public ReservationResponse reserve(ReserveRequest request) {
        Map<UUID, Integer> wanted = normalize(request.items());
        Optional<ReservationResponse> existing = findExisting(request.orderId(), request.eventId(), wanted);
        if (existing.isPresent()) {
            return existing.get();
        }
        InternalEventResponse event = eventClient.getInternal(request.eventId());
        if (!event.isPublished()) {
            throw new BusinessException(ErrorCode.TICKET_TYPE_NOT_ON_SALE, "Event is not on sale");
        }
        Instant now = clock.instant();
        Instant latest = now.plus(reservationTtl);
        Instant expiresAt = request.expiresAt() == null ? latest : request.expiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "expiresAt must be in the future");
        }
        if (expiresAt.isAfter(latest)) {
            // Ticket cấp tối đa TTL 3 phút; giá trị trả về là hạn chính thức Order phải dùng.
            expiresAt = latest;
        }
        Instant deadline = expiresAt;
        try {
            return tx.execute(status -> createReservation(request, wanted, now, deadline));
        } catch (DataIntegrityViolationException error) {
            // Thua cuộc đua UNIQUE(order_id): transaction kia đã thắng, đọc lại ở transaction mới và so payload.
            return findExisting(request.orderId(), request.eventId(), wanted).orElseThrow(() -> error);
        }
    }

    /** Gộp dòng trùng ticketTypeId; TreeMap cho thứ tự ổn định. */
    private static Map<UUID, Integer> normalize(List<ReserveItem> items) {
        Map<UUID, Integer> wanted = new TreeMap<>();
        for (ReserveItem item : items) {
            wanted.merge(item.ticketTypeId(), item.quantity(), Integer::sum);
        }
        return wanted;
    }

    private Optional<ReservationResponse> findExisting(UUID orderId, UUID eventId, Map<UUID, Integer> wanted) {
        return reservations.findByOrderId(orderId).map(reservation -> {
            Map<UUID, Integer> reserved = new TreeMap<>();
            boolean sameEvent = true;
            for (ReservationItem item : reservation.getItems()) {
                reserved.put(item.getTicketType().getId(), item.getQuantity());
                sameEvent &= eventId.equals(item.getTicketType().getEventId());
            }
            if (!sameEvent || !reserved.equals(wanted)) {
                throw new BusinessException(ErrorCode.RESERVATION_PAYLOAD_MISMATCH,
                        "A different reservation already exists for this order");
            }
            // Trả nguyên trạng (kể cả EXPIRED), không gia hạn khi retry.
            return ReservationResponse.from(reservation);
        });
    }

    private ReservationResponse createReservation(ReserveRequest request, Map<UUID, Integer> wanted, Instant now,
                                                  Instant expiresAt) {
        List<TicketType> locked = types.findAllByIdsForUpdate(wanted.keySet());
        if (locked.size() != wanted.size()) {
            throw new BusinessException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Ticket type not found");
        }
        for (TicketType type : locked) {
            int quantity = wanted.get(type.getId());
            if (!request.eventId().equals(type.getEventId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ticket type does not belong to the event");
            }
            if (type.getStatus() != TicketTypeStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.TICKET_TYPE_NOT_ON_SALE, "Ticket type " + type.getName()
                        + " is not on sale");
            }
            if (type.getAvailableQuantity() < quantity) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_TICKETS, "Not enough " + type.getName()
                        + " tickets left");
            }
        }

        Reservation reservation = new Reservation();
        reservation.setOrderId(request.orderId());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setCreatedAt(now);
        reservation.setExpiresAt(expiresAt);
        for (TicketType type : locked) {
            int quantity = wanted.get(type.getId());
            type.setAvailableQuantity(type.getAvailableQuantity() - quantity);
            type.setReservedQuantity(type.getReservedQuantity() + quantity);

            ReservationItem item = new ReservationItem();
            item.setReservation(reservation);
            item.setTicketType(type);
            item.setQuantity(quantity);
            item.setUnitPrice(type.getPrice());
            reservation.getItems().add(item);
        }
        reservations.saveAndFlush(reservation);
        return ReservationResponse.from(reservation);
    }

    // ---------------------------------------------------------------- confirm

    private record ConfirmOutcome(ConfirmResponse response, boolean expired) {
    }

    @Override
    public ConfirmResponse confirm(UUID reservationId, ConfirmRequest request) {
        Reservation snapshot = reservations.findWithItemsById(reservationId)
                .orElseThrow(ReservationServiceImpl::reservationNotFound);
        if (snapshot.getStatus() == ReservationStatus.CONFIRMED) {
            return confirmedResponse(reservationId);
        }
        if (snapshot.getStatus() != ReservationStatus.RESERVED) {
            throw releasedOrExpired();
        }
        Map<UUID, List<Holder>> holders = holdersByType(snapshot, request.holders());
        UUID ownerUserId = request.holders().get(0).ownerUserId();
        UUID eventId = snapshot.getItems().get(0).getTicketType().getEventId();
        // Snapshot tên/giờ sự kiện cho ticket.issued, lấy trước khi khóa DB.
        InternalEventResponse event = eventClient.getInternal(eventId);

        ConfirmOutcome outcome = tx.execute(status ->
                doConfirm(reservationId, holders, ownerUserId, request.customerEmail(), event));
        if (outcome.expired()) {
            // Transaction trên đã commit EXPIRED + hoàn quota; giờ mới báo lỗi cho Order.
            throw releasedOrExpired();
        }
        return outcome.response();
    }

    /**
     * Mỗi vé một holder; số holder từng hạng phải đúng số đã giữ. C02: mọi vé thuộc buyer (cùng ownerUserId).
     */
    private static Map<UUID, List<Holder>> holdersByType(Reservation reservation, List<Holder> holders) {
        Map<UUID, Integer> reserved = reservation.getItems().stream()
                .collect(Collectors.toMap(item -> item.getTicketType().getId(), ReservationItem::getQuantity));
        Map<UUID, List<Holder>> byType = new LinkedHashMap<>();
        for (Holder holder : holders) {
            byType.computeIfAbsent(holder.ticketTypeId(), key -> new ArrayList<>()).add(holder);
        }
        boolean matches = byType.keySet().equals(reserved.keySet())
                && byType.entrySet().stream().allMatch(entry -> entry.getValue().size() == reserved.get(entry.getKey()));
        if (!matches) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Holders must match the reserved quantity of each ticket type");
        }
        Set<UUID> owners = new HashSet<>();
        holders.forEach(holder -> owners.add(holder.ownerUserId()));
        if (owners.size() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "All tickets must be owned by the buyer");
        }
        return byType;
    }

    private ConfirmOutcome doConfirm(UUID reservationId, Map<UUID, List<Holder>> holders, UUID ownerUserId,
                                     String customerEmail, InternalEventResponse event) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(ReservationServiceImpl::reservationNotFound);
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            // Request song song đã confirm trước: trả bộ vé cũ, không tạo thêm QR.
            return new ConfirmOutcome(confirmedResponse(reservationId), false);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw releasedOrExpired();
        }
        Instant now = clock.instant();
        if (!reservation.getExpiresAt().isAfter(now)) {
            returnReservedQuantities(reservation);
            reservation.setStatus(ReservationStatus.EXPIRED);
            return new ConfirmOutcome(null, true);
        }

        Map<UUID, TicketType> locked = lockTypes(reservation);
        List<Ticket> issued = new ArrayList<>();
        Map<UUID, Holder> holderOfTicket = new LinkedHashMap<>();
        List<ReservationItem> items = new ArrayList<>(reservation.getItems());
        items.sort(Comparator.comparing(item -> item.getTicketType().getId()));
        for (ReservationItem item : items) {
            TicketType type = locked.get(item.getTicketType().getId());
            if (type.getReservedQuantity() < item.getQuantity()) {
                throw new IllegalStateException("Reserved counter below reservation quantity for type " + type.getId());
            }
            type.setReservedQuantity(type.getReservedQuantity() - item.getQuantity());
            type.setSoldQuantity(type.getSoldQuantity() + item.getQuantity());
            for (Holder holder : holders.get(type.getId())) {
                Ticket ticket = new Ticket();
                ticket.setReservation(reservation);
                ticket.setOrderId(reservation.getOrderId());
                ticket.setTicketType(type);
                ticket.setOwnerUserId(ownerUserId);
                ticket.setHolderName(holder.holderName().trim());
                ticket.setQrCodeValue(qrTokens.generate());
                ticket.setStatus(TicketStatus.ISSUED);
                ticket.setIssuedAt(now);
                issued.add(ticket);
            }
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmedAt(now);
        tickets.saveAllAndFlush(issued);

        // Ghép holderEmail theo thứ tự đã tạo vé (holder không lưu email trong bảng tickets, chỉ trong outbox).
        int index = 0;
        for (ReservationItem item : items) {
            for (Holder holder : holders.get(item.getTicketType().getId())) {
                holderOfTicket.put(issued.get(index++).getId(), holder);
            }
        }
        List<Map<String, Object>> ticketPayload = new ArrayList<>();
        for (Ticket ticket : issued) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ticketId", ticket.getId());
            entry.put("ticketTypeId", ticket.getTicketType().getId());
            entry.put("ticketTypeName", ticket.getTicketType().getName().name());
            entry.put("holderName", ticket.getHolderName());
            String holderEmail = holderOfTicket.get(ticket.getId()).holderEmail();
            entry.put("holderEmail", holderEmail == null || holderEmail.isBlank() ? null : holderEmail.trim());
            entry.put("qrCodeValue", ticket.getQrCodeValue());
            ticketPayload.add(entry);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", reservation.getOrderId());
        fields.put("customerEmail", customerEmail);
        fields.put("eventTitle", event.title());
        fields.put("eventStartTime", event.startTime());
        fields.put("venue", null);
        fields.put("ownerUserId", ownerUserId);
        fields.put("tickets", ticketPayload);
        outbox.append("ticket.issued", reservation.getOrderId(), event.id(), now, fields);

        return new ConfirmOutcome(new ConfirmResponse(reservationId, ReservationStatus.CONFIRMED,
                issued.stream().map(TicketResponse::from).toList()), false);
    }

    private ConfirmResponse confirmedResponse(UUID reservationId) {
        return new ConfirmResponse(reservationId, ReservationStatus.CONFIRMED,
                tickets.findByReservationIdOrderByIdAsc(reservationId).stream().map(TicketResponse::from).toList());
    }

    // ---------------------------------------------------------------- release / expire

    @Override
    public ReservationStatusResponse release(UUID reservationId) {
        return tx.execute(status -> {
            Reservation reservation = reservations.findByIdForUpdate(reservationId)
                    .orElseThrow(ReservationServiceImpl::reservationNotFound);
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CONFIRMED,
                        "Reservation already confirmed; cancel through refund instead");
            }
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                returnReservedQuantities(reservation);
                reservation.setStatus(ReservationStatus.RELEASED);
                reservation.setReleasedAt(clock.instant());
            }
            return new ReservationStatusResponse(reservation.getId(), reservation.getStatus());
        });
    }

    @Override
    public boolean expire(UUID reservationId, Instant now) {
        Boolean expired = tx.execute(status -> {
            Reservation reservation = reservations.findByIdForUpdate(reservationId).orElse(null);
            if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED
                    || reservation.getExpiresAt().isAfter(now)) {
                // Đã confirm/release/expire bởi request khác: bỏ qua, không hoàn quota lần hai.
                return false;
            }
            returnReservedQuantities(reservation);
            reservation.setStatus(ReservationStatus.EXPIRED);
            return true;
        });
        return Boolean.TRUE.equals(expired);
    }

    // ---------------------------------------------------------------- helpers

    /** Gọi khi đã thắng chuyển trạng thái RESERVED → RELEASED/EXPIRED: reserved → available. */
    private void returnReservedQuantities(Reservation reservation) {
        Map<UUID, TicketType> locked = lockTypes(reservation);
        for (ReservationItem item : reservation.getItems()) {
            TicketType type = locked.get(item.getTicketType().getId());
            if (type.getReservedQuantity() < item.getQuantity()) {
                throw new IllegalStateException("Reserved counter below reservation quantity for type " + type.getId());
            }
            type.setReservedQuantity(type.getReservedQuantity() - item.getQuantity());
            type.setAvailableQuantity(type.getAvailableQuantity() + item.getQuantity());
        }
    }

    private Map<UUID, TicketType> lockTypes(Reservation reservation) {
        List<UUID> ids = reservation.getItems().stream().map(item -> item.getTicketType().getId()).toList();
        return types.findAllByIdsForUpdate(ids).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
    }

    private static BusinessException reservationNotFound() {
        return new BusinessException(ErrorCode.RESERVATION_NOT_FOUND, "Reservation not found");
    }

    private static BusinessException releasedOrExpired() {
        return new BusinessException(ErrorCode.RESERVATION_RELEASED_OR_EXPIRED, "Reservation was released or expired");
    }
}
