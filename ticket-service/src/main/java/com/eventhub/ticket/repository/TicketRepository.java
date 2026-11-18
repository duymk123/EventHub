package com.eventhub.ticket.repository;

import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.common.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByOwnerUserIdOrderByIssuedAtDesc(UUID ownerUserId);
    List<Ticket> findByReservationId(UUID reservationId);
    List<Ticket> findByReservationIdOrderByIdAsc(UUID reservationId);
    Optional<Ticket> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Ticket> findByQrCodeValue(String qrCodeValue);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Ticket> findByOrderIdOrderByIdAsc(UUID orderId);
    long countByTicketTypeIdAndStatus(UUID ticketTypeId, TicketStatus status);
}
