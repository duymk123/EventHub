package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.client.EventClient;
import com.eventhub.ticket.client.InternalEventResponse;
import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.common.TicketTypeName;
import com.eventhub.ticket.common.TicketTypeStatus;
import com.eventhub.ticket.dto.request.CreateTicketTypesRequest;
import com.eventhub.ticket.dto.request.TicketTypeInput;
import com.eventhub.ticket.dto.request.UpdateTicketTypeRequest;
import com.eventhub.ticket.dto.response.EventCapacityResponse;
import com.eventhub.ticket.dto.response.TicketTypeResponse;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.TicketTypeRepository;
import com.eventhub.ticket.service.TicketTypeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gọi Event Service (HTTP) ngoài transaction, chỉ mở transaction ngắn khi ghi để không giữ lock DB trong lúc chờ
 * mạng.
 */
@Service
public class TicketTypeServiceImpl implements TicketTypeService {
    private final TicketTypeRepository types;
    private final EventClient eventClient;
    private final TransactionTemplate tx;

    public TicketTypeServiceImpl(TicketTypeRepository types, EventClient eventClient, TransactionTemplate tx) {
        this.types = types;
        this.eventClient = eventClient;
        this.tx = tx;
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
                    type.setTotalQuota(input.totalQuota());
                    type.setAvailableQuantity(input.totalQuota());
                    type.setReservedQuantity(0);
                    type.setSoldQuantity(0);
                    type.setStatus(TicketTypeStatus.ACTIVE);
                    return type;
                }).toList();
                return sorted(types.saveAllAndFlush(created));
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
            if (request.totalQuota() != null) {
                int committed = type.getReservedQuantity() + type.getSoldQuantity();
                if (request.totalQuota() < committed) {
                    throw new BusinessException(ErrorCode.QUOTA_BELOW_SOLD_RESERVED,
                            "totalQuota must be at least reserved + sold (" + committed + ")");
                }
                type.setTotalQuota(request.totalQuota());
                type.setAvailableQuantity(request.totalQuota() - committed);
            }
            if (request.status() != null) {
                type.setStatus(request.status());
            }
            return TicketTypeResponse.from(type);
        });
    }

    @Override
    public List<TicketTypeResponse> listPublicTypes(UUID eventId) {
        InternalEventResponse event = eventClient.getInternal(eventId);
        if (!event.isPubliclyVisible()) {
            // Sự kiện chưa công bố coi như không tồn tại với người xem công khai.
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found");
        }
        return sorted(types.findByEventId(eventId));
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

    private static void requireOwner(Actor organizer, InternalEventResponse event) {
        if (!event.organizerId().equals(organizer.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER, "Event belongs to another organizer");
        }
    }

    /** Thứ tự hiển thị VIP, PREMIUM, STANDARD. */
    private static List<TicketTypeResponse> sorted(List<TicketType> list) {
        return list.stream().sorted(Comparator.comparing(TicketType::getName))
                .map(TicketTypeResponse::from).toList();
    }

    private static BusinessException typeNotFound() {
        return new BusinessException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Ticket type not found");
    }

    private static BusinessException alreadyExist() {
        return new BusinessException(ErrorCode.TICKET_TYPES_ALREADY_EXIST,
                "Ticket type already exists for this event");
    }
}
