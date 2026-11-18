package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.common.TicketStatus;
import com.eventhub.ticket.dto.response.CancelResponse;
import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.TicketRepository;
import com.eventhub.ticket.repository.TicketTypeRepository;
import com.eventhub.ticket.service.OutboxWriter;
import com.eventhub.ticket.service.TicketCancellationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class TicketCancellationServiceImpl implements TicketCancellationService {
    private final TicketRepository tickets;
    private final TicketTypeRepository types;
    private final OutboxWriter outbox;
    private final Clock clock;

    public TicketCancellationServiceImpl(TicketRepository tickets, TicketTypeRepository types, OutboxWriter outbox,
                                         Clock clock) {
        this.tickets = tickets;
        this.types = types;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CancelResponse cancelByOrder(UUID orderId) {
        List<Ticket> orderTickets = tickets.findByOrderIdForUpdate(orderId);
        if (orderTickets.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND, "No tickets for this order");
        }
        boolean used = orderTickets.stream().anyMatch(ticket -> ticket.getStatus() == TicketStatus.CHECKED_IN
                || ticket.getStatus() == TicketStatus.CHECKED_OUT);
        if (used) {
            // Cả đơn hoặc không: một vé đã dùng thì không hủy vé nào.
            throw new BusinessException(ErrorCode.TICKET_ALREADY_CHECKED_IN, "A ticket of this order was already used");
        }
        List<Ticket> toCancel = orderTickets.stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.ISSUED).toList();
        if (toCancel.isEmpty()) {
            return new CancelResponse(orderId, 0, "CANCELLED");
        }

        Instant now = clock.instant();
        Map<UUID, Integer> perType = new TreeMap<>();
        for (Ticket ticket : toCancel) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticket.setCancelledAt(now);
            perType.merge(ticket.getTicketType().getId(), 1, Integer::sum);
        }
        for (TicketType type : types.findAllByIdsForUpdate(perType.keySet())) {
            int quantity = perType.get(type.getId());
            if (type.getSoldQuantity() < quantity) {
                throw new IllegalStateException("Sold counter below cancelled quantity for type " + type.getId());
            }
            type.setSoldQuantity(type.getSoldQuantity() - quantity);
            type.setAvailableQuantity(type.getAvailableQuantity() + quantity);
        }

        List<Map<String, Object>> ticketPayload = new ArrayList<>();
        for (Ticket ticket : toCancel) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ticketId", ticket.getId());
            entry.put("ticketTypeId", ticket.getTicketType().getId());
            ticketPayload.add(entry);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", orderId);
        fields.put("tickets", ticketPayload);
        fields.put("cancelledAt", now);
        outbox.append("ticket.cancelled", orderId, toCancel.get(0).getTicketType().getEventId(), now, fields);
        return new CancelResponse(orderId, toCancel.size(), "CANCELLED");
    }
}
