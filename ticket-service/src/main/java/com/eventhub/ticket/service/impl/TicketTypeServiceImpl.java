package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.client.EventClient;
import com.eventhub.ticket.client.InternalEventResponse;
import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.common.SeatStatus;
import com.eventhub.ticket.common.TicketTypeName;
import com.eventhub.ticket.common.TicketTypeStatus;
import com.eventhub.ticket.dto.request.CreateTicketTypesRequest;
import com.eventhub.ticket.dto.request.TicketTypeInput;
import com.eventhub.ticket.dto.request.UpdateTicketTypeRequest;
import com.eventhub.ticket.dto.response.EventCapacityResponse;
import com.eventhub.ticket.dto.response.SeatMapResponse;
import com.eventhub.ticket.dto.response.TicketConfigurationResponse;
import com.eventhub.ticket.dto.response.TicketTypeResponse;
import com.eventhub.ticket.entity.Seat;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.ReservationSeatRepository;
import com.eventhub.ticket.repository.SeatRepository;
import com.eventhub.ticket.repository.TicketTypeRepository;
import com.eventhub.ticket.service.TicketTypeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hạng vé = zone ngồi (C12). Gọi Event Service (HTTP) ngoài transaction, chỉ mở transaction ngắn khi ghi để không giữ
 * lock DB trong lúc chờ mạng. Ghế được sinh/xóa cùng transaction với cấu hình hàng × ghế.
 */
@Service
public class TicketTypeServiceImpl implements TicketTypeService {
    private final TicketTypeRepository types;
    private final SeatRepository seats;
    private final ReservationSeatRepository reservationSeats;
    private final EventClient eventClient;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TicketTypeServiceImpl(TicketTypeRepository types, SeatRepository seats,
                                 ReservationSeatRepository reservationSeats, EventClient eventClient,
                                 TransactionTemplate tx, Clock clock) {
        this.types = types;
        this.seats = seats;
        this.reservationSeats = reservationSeats;
        this.eventClient = eventClient;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public List<TicketTypeResponse> createTypes(Actor organizer, CreateTicketTypesRequest request) {
        Set<TicketTypeName> names = EnumSet.noneOf(TicketTypeName.class);
        for (TicketTypeInput input : request.ticketTypes()) {
            if (!names.add(input.name())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Duplicate ticket type name " + input.name());
            }
        }
        requireOwner(organizer, eventClient.getInternal(request.eventId()));
        try {
            return tx.execute(status -> {
                boolean exists = types.findByEventId(request.eventId()).stream()
                        .anyMatch(type -> names.contains(type.getName()));
                if (exists) {
                    throw alreadyExist();
                }
                List<TicketType> created = request.ticketTypes().stream().map(input -> {
                    TicketType type = new TicketType();
                    type.setEventId(request.eventId());
                    type.setName(input.name());
                    type.setPrice(input.price());
                    type.setRowCount(input.rowCount());
                    type.setSeatsPerRow(input.seatsPerRow());
                    type.setTotalQuota(input.rowCount() * input.seatsPerRow());
                    type.setStatus(TicketTypeStatus.ACTIVE);
                    return type;
                }).toList();
                List<TicketType> saved = types.saveAllAndFlush(created);
                Instant now = clock.instant();
                for (TicketType type : saved) {
                    addRows(type, 1, type.getRowCount(), now);
                }
                return responses(saved);
            });
        } catch (DataIntegrityViolationException error) {
            // UNIQUE(event_id, name): request song song tạo cùng hạng.
            throw alreadyExist();
        }
    }

    @Override
    public TicketTypeResponse updateType(Actor organizer, UUID typeId, UpdateTicketTypeRequest request) {
        TicketType current = types.findById(typeId).orElseThrow(TicketTypeServiceImpl::typeNotFound);
        requireOwner(organizer, eventClient.getInternal(current.getEventId()));
        return tx.execute(status -> {
            TicketType type = types.findByIdForUpdate(typeId).orElseThrow(TicketTypeServiceImpl::typeNotFound);
            if (request.price() != null) {
                // Không ảnh hưởng giá snapshot của reservation/order đã tạo.
                type.setPrice(request.price());
            }
            int rows = request.rowCount() == null ? type.getRowCount() : request.rowCount();
            int perRow = request.seatsPerRow() == null ? type.getSeatsPerRow() : request.seatsPerRow();
            changeLayout(type, rows, perRow);
            if (request.status() != null) {
                type.setStatus(request.status());
            }
            return responses(List.of(type)).get(0);
        });
    }

    /**
     * Sơ đồ ghế (C12): thêm hàng luôn được. Bớt hàng hoặc đổi số ghế mỗi hàng chỉ khi các ghế bị bỏ chưa từng thuộc
     * reservation nào (reservation_seats giữ lịch sử nên không xóa được), ngược lại SEAT_LAYOUT_LOCKED.
     */
    private void changeLayout(TicketType type, int rows, int perRow) {
        Instant now = clock.instant();
        if (perRow != type.getSeatsPerRow()) {
            if (reservationSeats.existsHistoryFromRow(type.getId(), 1)) {
                throw layoutLocked("Seats per row cannot change after seats of this zone were held or sold");
            }
            seats.deleteRowsAfter(type.getId(), 0);
            type.setSeatsPerRow(perRow);
            type.setRowCount(0);
        }
        if (rows < type.getRowCount()) {
            if (reservationSeats.existsHistoryFromRow(type.getId(), rows + 1)) {
                throw layoutLocked("Rows with held or sold seats cannot be removed");
            }
            seats.deleteRowsAfter(type.getId(), rows);
        } else if (rows > type.getRowCount()) {
            addRows(type, type.getRowCount() + 1, rows, now);
        }
        type.setRowCount(rows);
        type.setTotalQuota(rows * type.getSeatsPerRow());
    }

    private void addRows(TicketType type, int fromRow, int toRow, Instant now) {
        List<Seat> created = new ArrayList<>();
        for (int row = fromRow; row <= toRow; row++) {
            for (int number = 1; number <= type.getSeatsPerRow(); number++) {
                created.add(Seat.of(type, row, number, now));
            }
        }
        seats.saveAll(created);
    }

    @Override
    public List<TicketTypeResponse> listPublicTypes(UUID eventId) {
        requirePublic(eventClient.getInternal(eventId));
        return responses(types.findByEventId(eventId));
    }

    @Override
    public SeatMapResponse getSeatMap(UUID typeId) {
        TicketType type = types.findById(typeId).orElseThrow(TicketTypeServiceImpl::typeNotFound);
        requirePublic(eventClient.getInternal(type.getEventId()));
        Map<Integer, SeatMapResponse.Row> rows = new LinkedHashMap<>();
        for (Seat seat : seats.findByTicketTypeIdOrderByRowIndexAscSeatNumberAsc(typeId)) {
            rows.computeIfAbsent(seat.getRowIndex(),
                            index -> new SeatMapResponse.Row(index, seat.getRowLabel(), new ArrayList<>()))
                    .seats().add(new SeatMapResponse.SeatItem(seat.getId(), seat.getSeatNumber(), seat.getLabel(),
                            seat.getStatus()));
        }
        return new SeatMapResponse(type.getId(), type.getName(), type.getPrice(), List.copyOf(rows.values()),
                clock.instant());
    }

    @Override
    public TicketConfigurationResponse getConfiguration(UUID eventId) {
        List<TicketType> sellable = types.findByEventId(eventId).stream()
                .filter(type -> type.getStatus() == TicketTypeStatus.ACTIVE && type.getTotalQuota() > 0)
                .toList();
        long capacity = sellable.stream().mapToLong(TicketType::getTotalQuota).sum();
        return new TicketConfigurationResponse(eventId, sellable.size(), capacity, !sellable.isEmpty());
    }

    @Override
    public List<EventCapacityResponse> getCapacityByEvents(List<UUID> eventIds) {
        Set<UUID> ids = new LinkedHashSet<>(eventIds);
        if (ids.isEmpty() || ids.size() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "eventIds must contain 1..100 ids");
        }
        Map<UUID, Long> capacity = new HashMap<>();
        for (TicketTypeRepository.EventCapacityRow row : types.sumCapacityByEventIds(ids)) {
            capacity.put(row.getEventId(), row.getCapacity());
        }
        return ids.stream().map(id -> new EventCapacityResponse(id, capacity.getOrDefault(id, 0L))).toList();
    }

    /** Thứ tự hiển thị VIP, PREMIUM, STANDARD, kèm số ghế theo trạng thái (một query cho mọi zone). */
    private List<TicketTypeResponse> responses(List<TicketType> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<SeatStatus, Long>> counts = new HashMap<>();
        for (SeatRepository.SeatCountRow row : seats.countByTypeAndStatus(list.stream().map(TicketType::getId).toList())) {
            counts.computeIfAbsent(row.getTicketTypeId(), key -> new EnumMap<>(SeatStatus.class))
                    .put(row.getStatus(), row.getTotal());
        }
        return list.stream().sorted(Comparator.comparing(TicketType::getName))
                .map(type -> TicketTypeResponse.from(type, counts.getOrDefault(type.getId(), Map.of())))
                .toList();
    }

    private static void requirePublic(InternalEventResponse event) {
        if (!event.isPubliclyVisible()) {
            // Sự kiện chưa công bố coi như không tồn tại với người xem công khai.
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found");
        }
    }

    private static void requireOwner(Actor organizer, InternalEventResponse event) {
        if (!event.organizerId().equals(organizer.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER, "Event belongs to another organizer");
        }
    }

    private static BusinessException typeNotFound() {
        return new BusinessException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Ticket type not found");
    }

    private static BusinessException layoutLocked(String message) {
        return new BusinessException(ErrorCode.SEAT_LAYOUT_LOCKED, message);
    }

    private static BusinessException alreadyExist() {
        return new BusinessException(ErrorCode.TICKET_TYPES_ALREADY_EXIST,
                "Ticket type already exists for this event");
    }
}
