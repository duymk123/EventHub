package com.eventhub.ticket.repository;

import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByOwnerUserIdOrderByIssuedAtDesc(UUID ownerUserId);
    List<Ticket> findByReservationId(UUID reservationId);
    long countByTicketTypeIdAndStatus(UUID ticketTypeId, TicketStatus status);
}
