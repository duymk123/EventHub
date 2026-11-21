package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.dto.response.QrResponse;
import com.eventhub.ticket.dto.response.TicketResponse;
import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.TicketRepository;
import com.eventhub.ticket.service.TicketQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketQueryServiceImpl implements TicketQueryService {
    private final TicketRepository tickets;

    public TicketQueryServiceImpl(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getMine(Actor customer) {
        return tickets.findByOwnerUserIdOrderByIssuedAtDescIdAsc(customer.userId()).stream()
                .map(TicketResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QrResponse getQr(Actor customer, UUID ticketId) {
        Ticket ticket = tickets.findWithTypeById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Ticket not found"));
        if (!ticket.getOwnerUserId().equals(customer.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER, "Ticket belongs to another user");
        }
        return new QrResponse(ticket.getId(), ticket.getQrCodeValue());
    }
}
