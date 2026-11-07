package com.eventhub.ticket.repository;

import com.eventhub.ticket.entity.TicketScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketScanRepository extends JpaRepository<TicketScan, UUID> {
    List<TicketScan> findByTicketIdOrderByScannedAtAsc(UUID ticketId);
}
