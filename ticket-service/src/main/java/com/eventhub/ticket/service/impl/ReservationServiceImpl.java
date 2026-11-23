package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.client.EventClient;
import com.eventhub.ticket.client.InternalEventResponse;
import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.common.ReservationStatus;
import com.eventhub.ticket.common.SeatLabels;
import com.eventhub.ticket.common.SeatStatus;
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
import com.eventhub.ticket.entity.ReservationSeat;
import com.eventhub.ticket.entity.Seat;
import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.ReservationRepository;
import com.eventhub.ticket.repository.ReservationSeatRepository;
import com.eventhub.ticket.repository.SeatRepository;
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
 * Giữ ghế / confirm / release / expire theo C12. Lời gọi Event Service luôn nằm ngoài transaction. Giữ ghế là một
 * câu UPDATE có điều kiện status = AVAILABLE trên đúng các ghế được chọn: đổi được đủ số dòng thì giữ, thiếu thì
 * rollback cả transaction và trả SEAT_TAKEN kèm danh sách ghế bị lấy. Không khóa ticket_types.
 */
@Service
public class ReservationServiceImpl implements ReservationService {
    private final ReservationRepository reservations;
    private final ReservationSeatRepository reservationSeats;
    private final SeatRepository seats;
    private final TicketTypeRepository types;
    private final TicketRepository tickets;
    private final EventClient eventClient;
    private final OutboxWriter outbox;
    private final QrTokenGenerator qrTokens;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration reservationTtl;

    public ReservationServiceImpl(ReservationRepository reservations, ReservationSeatRepository reservationSeats,
                                  SeatRepository seats, TicketTypeRepository types, TicketRepository tickets,
                                  EventClient eventClient, OutboxWriter outbox, QrTokenGenerator qrTokens,
                                  TransactionTemplate tx, Clock clock,
                                  @Value("${eventhub.ticket.reservation-ttl:PT10M}") Duration reservationTtl) {
        this.reservations = reservations;
        this.reservationSeats = reservationSeats;
        this.seats = seats;
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
        Map<UUID, UUID> typeOfSeat = requestedSeats(request);
        Optional<ReservationResponse> existing = findExisting(request.orderId(), request.eventId(), typeOfSeat.keySet());
        if (existing.isPresent()) {
            return existing.get();
        }
        // Ghế phải tồn tại, đúng hạng/sự kiện, hạng đang bán.
        List<Seat> chosen = seats.findByIdIn(typeOfSeat.keySet());
        if (chosen.size() != typeOfSeat.size()) {
            throw seatNotFound();
        }
        for (Seat seat : chosen) {
            TicketType type = seat.getTicketType();
            if (!type.getId().equals(typeOfSeat.get(seat.getId())) || !request.eventId().equals(type.getEventId())) {
                throw seatNotFound();
            }
            if (type.getStatus() != TicketTypeStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.TICKET_TYPE_NOT_ON_SALE, "Ticket type " + type.getName()
                        + " is not on sale");
            }
        }
        InternalEventResponse event = eventClient.getInternal(request.eventId());
        if (!event.isPublished()) {
            throw new BusinessException(ErrorCode.TICKET_TYPE_NOT_ON_SALE, "Event is not on sale");
        }
        Instant now = clock.instant();
        if (event.startTime() != null && !now.isBefore(event.startTime())) {
            // C17: ngừng bán khi sự kiện bắt đầu, kể cả khi organizer chưa chuyển CLOSED.
            throw new BusinessException(ErrorCode.TICKET_TYPE_NOT_ON_SALE, "Event has already started");
        }
        Instant latest = now.plus(reservationTtl);
        Instant expiresAt = request.expiresAt() == null ? latest : request.expiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "expiresAt must be in the future");
        }
        if (expiresAt.isAfter(latest)) {
            // Ticket cấp tối đa TTL (C16: 10 phút); giá trị trả về là hạn chính thức Order phải dùng.
            expiresAt = latest;
        }
        Instant deadline = expiresAt;
        Map<UUID, String> labels = chosen.stream().collect(Collectors.toMap(Seat::getId, Seat::getLabel));
        try {
            return tx.execute(status -> createReservation(request, typeOfSeat, labels, now, deadline,
                    event.startTime()));
        } catch (DataIntegrityViolationException error) {
            // Thua cuộc đua UNIQUE(order_id): transaction kia đã thắng, đọc lại ở transaction mới và so payload.
            return findExisting(request.orderId(), request.eventId(), typeOfSeat.keySet()).orElseThrow(() -> error);
        } catch (BusinessException error) {
            if (error.getErrorCode() != ErrorCode.SEAT_TAKEN) {
                throw error;
            }
            // Transaction đã rollback; đọc lại để báo đúng các ghế người khác đang giữ/đã mua.
            List<UUID> taken = seats.findByIdIn(typeOfSeat.keySet()).stream()
                    .filter(seat -> seat.getStatus() != SeatStatus.AVAILABLE)
                    .map(Seat::getId).sorted().toList();
            throw new BusinessException(ErrorCode.SEAT_TAKEN, "Some seats were just taken", taken);
        }
    }

    /** seatId → ticketTypeId; 1..10 ghế, không trùng. TreeMap giữ thứ tự id để thứ tự khóa ổn định. */
    private static Map<UUID, UUID> requestedSeats(ReserveRequest request) {
        Map<UUID, UUID> typeOfSeat = new TreeMap<>();
        int count = 0;
        for (ReserveItem item : request.items()) {
            for (UUID seatId : item.seatIds()) {
                count++;
                if (typeOfSeat.put(seatId, item.ticketTypeId()) != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A seat is selected twice");
                }
            }
        }
        if (count < 1 || count > SeatLabels.MAX_SEATS_PER_ORDER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "An order must hold 1.." + SeatLabels.MAX_SEATS_PER_ORDER + " seats");
        }
        return typeOfSeat;
    }

    /** Cùng orderId: cùng sự kiện và cùng bộ ghế thì trả reservation cũ (kể cả đã hết hạn), khác thì 409. */
    private Optional<ReservationResponse> findExisting(UUID orderId, UUID eventId, Set<UUID> seatIds) {
        return reservations.findByOrderId(orderId).map(reservation -> {
            List<ReservationSeat> held = reservationSeats.findByReservationIdOrderBySeatLabelAsc(reservation.getId());
            Set<UUID> heldIds = held.stream().map(rs -> rs.getSeat().getId()).collect(Collectors.toSet());
            boolean sameEvent = reservation.getItems().stream()
                    .allMatch(item -> eventId.equals(item.getTicketType().getEventId()));
            if (!sameEvent || !heldIds.equals(new HashSet<>(seatIds))) {
                throw new BusinessException(ErrorCode.RESERVATION_PAYLOAD_MISMATCH,
                        "A different reservation already exists for this order");
            }
            return ReservationResponse.from(reservation, held);
        });
    }

    private ReservationResponse createReservation(ReserveRequest request, Map<UUID, UUID> typeOfSeat,
                                                  Map<UUID, String> labels, Instant now, Instant expiresAt,
                                                  Instant eventStartTime) {
        Reservation reservation = new Reservation();
        reservation.setEventStartTime(eventStartTime);
        reservation.setOrderId(request.orderId());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setCreatedAt(now);
        reservation.setExpiresAt(expiresAt);
        reservations.saveAndFlush(reservation);

        int held = seats.holdSeats(typeOfSeat.keySet(), reservation.getId(), now);
        if (held != typeOfSeat.size()) {
            // Ném lỗi để rollback cả reservation lẫn các ghế vừa giữ; caller đọc lại ghế bị lấy.
            throw new BusinessException(ErrorCode.SEAT_TAKEN, "Some seats were just taken");
        }

        Map<UUID, Long> quantityByType = typeOfSeat.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        Map<UUID, TicketType> typeById = types.findAllById(quantityByType.keySet()).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
        for (Map.Entry<UUID, Long> entry : quantityByType.entrySet()) {
            TicketType type = typeById.get(entry.getKey());
            ReservationItem item = new ReservationItem();
            item.setReservation(reservation);
            item.setTicketType(type);
            item.setQuantity(entry.getValue().intValue());
            item.setUnitPrice(type.getPrice());
            reservation.getItems().add(item);
        }
        List<ReservationSeat> links = new ArrayList<>();
        for (UUID seatId : typeOfSeat.keySet()) {
            ReservationSeat link = new ReservationSeat();
            link.setReservation(reservation);
            link.setSeat(seats.getReferenceById(seatId));
            link.setSeatLabel(labels.get(seatId));
            links.add(link);
        }
        reservations.saveAndFlush(reservation);
        reservationSeats.saveAllAndFlush(links);
        return ReservationResponse.from(reservation, links);
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
        List<ReservationSeat> held = reservationSeats.findByReservationIdOrderBySeatLabelAsc(reservationId);
        Map<UUID, Holder> holderOfSeat = holdersBySeat(held, request.holders());
        UUID ownerUserId = request.holders().get(0).ownerUserId();
        UUID eventId = snapshot.getItems().get(0).getTicketType().getEventId();
        // Snapshot tên/giờ sự kiện cho ticket.issued, lấy trước khi khóa DB.
        InternalEventResponse event = eventClient.getInternal(eventId);

        ConfirmOutcome outcome = tx.execute(status ->
                doConfirm(reservationId, held, holderOfSeat, ownerUserId, request.customerEmail(), event));
        if (outcome.expired()) {
            // Transaction trên đã commit EXPIRED + trả ghế; giờ mới báo lỗi cho Order.
            throw releasedOrExpired();
        }
        return outcome.response();
    }

    /** Mỗi ghế đúng một holder, đúng hạng của ghế. C02: mọi vé thuộc buyer (cùng ownerUserId). */
    private static Map<UUID, Holder> holdersBySeat(List<ReservationSeat> held, List<Holder> holders) {
        Map<UUID, UUID> typeOfSeat = held.stream()
                .collect(Collectors.toMap(rs -> rs.getSeat().getId(), rs -> rs.getSeat().getTicketType().getId()));
        Map<UUID, Holder> bySeat = new LinkedHashMap<>();
        for (Holder holder : holders) {
            if (!holder.ticketTypeId().equals(typeOfSeat.get(holder.seatId()))
                    || bySeat.put(holder.seatId(), holder) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Each held seat needs exactly one holder of the same ticket type");
            }
        }
        if (!bySeat.keySet().equals(typeOfSeat.keySet())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Holders must cover every held seat");
        }
        Set<UUID> owners = new HashSet<>();
        holders.forEach(holder -> owners.add(holder.ownerUserId()));
        if (owners.size() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "All tickets must be owned by the buyer");
        }
        return bySeat;
    }

    private ConfirmOutcome doConfirm(UUID reservationId, List<ReservationSeat> held, Map<UUID, Holder> holderOfSeat,
                                     UUID ownerUserId, String customerEmail, InternalEventResponse event) {
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
            seats.releaseByReservation(reservationId, now);
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setReleasedAt(now);
            return new ConfirmOutcome(null, true);
        }
        int sold = seats.sellByReservation(reservationId, now);
        if (sold != held.size()) {
            throw new IllegalStateException("Reservation " + reservationId + " holds " + sold + " of "
                    + held.size() + " seats");
        }

        List<Ticket> issued = new ArrayList<>();
        List<Map<String, Object>> ticketPayload = new ArrayList<>();
        for (ReservationSeat rs : held) {
            Holder holder = holderOfSeat.get(rs.getSeat().getId());
            Ticket ticket = new Ticket();
            ticket.setReservation(reservation);
            ticket.setOrderId(reservation.getOrderId());
            ticket.setTicketType(types.getReferenceById(holder.ticketTypeId()));
            ticket.setSeat(seats.getReferenceById(rs.getSeat().getId()));
            ticket.setSeatLabel(rs.getSeatLabel());
            ticket.setOwnerUserId(ownerUserId);
            ticket.setHolderName(holder.holderName().trim());
            ticket.setQrCodeValue(qrTokens.generate());
            ticket.setStatus(TicketStatus.ISSUED);
            ticket.setIssuedAt(now);
            issued.add(ticket);
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmedAt(now);
        tickets.saveAllAndFlush(issued);

        for (int i = 0; i < issued.size(); i++) {
            Ticket ticket = issued.get(i);
            ReservationSeat rs = held.get(i);
            Holder holder = holderOfSeat.get(rs.getSeat().getId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ticketId", ticket.getId());
            entry.put("ticketTypeId", holder.ticketTypeId());
            entry.put("ticketTypeName", rs.getSeat().getTicketType().getName().name());
            entry.put("seatId", rs.getSeat().getId());
            entry.put("seatLabel", rs.getSeatLabel());
            entry.put("holderName", ticket.getHolderName());
            // holderEmail không lưu ở bảng tickets, chỉ trong outbox (C02).
            String holderEmail = holder.holderEmail();
            entry.put("holderEmail", holderEmail == null || holderEmail.isBlank() ? null : holderEmail.trim());
            entry.put("qrCodeValue", ticket.getQrCodeValue());
            ticketPayload.add(entry);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", reservation.getOrderId());
        fields.put("customerEmail", customerEmail);
        fields.put("eventTitle", event.title());
        fields.put("eventStartTime", event.startTime());
        // C03: Notification điền text địa điểm chung từ cấu hình.
        fields.put("venue", null);
        fields.put("ownerUserId", ownerUserId);
        fields.put("tickets", ticketPayload);
        outbox.append("ticket.issued", reservation.getOrderId(), event.id(), now, fields);

        return new ConfirmOutcome(new ConfirmResponse(reservationId, ReservationStatus.CONFIRMED,
                tickets.findByReservationIdOrderByIdAsc(reservationId).stream().map(TicketResponse::from).toList()),
                false);
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
                Instant now = clock.instant();
                seats.releaseByReservation(reservationId, now);
                reservation.setStatus(ReservationStatus.RELEASED);
                reservation.setReleasedAt(now);
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
                // Đã confirm/release/expire bởi request khác: bỏ qua, không trả ghế lần hai.
                return false;
            }
            seats.releaseByReservation(reservationId, now);
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setReleasedAt(now);
            return true;
        });
        return Boolean.TRUE.equals(expired);
    }

    // ---------------------------------------------------------------- helpers

    private static BusinessException reservationNotFound() {
        return new BusinessException(ErrorCode.RESERVATION_NOT_FOUND, "Reservation not found");
    }

    private static BusinessException releasedOrExpired() {
        return new BusinessException(ErrorCode.RESERVATION_RELEASED_OR_EXPIRED, "Reservation was released or expired");
    }

    private static BusinessException seatNotFound() {
        return new BusinessException(ErrorCode.SEAT_NOT_FOUND, "Seat not found for this ticket type and event");
    }
}
