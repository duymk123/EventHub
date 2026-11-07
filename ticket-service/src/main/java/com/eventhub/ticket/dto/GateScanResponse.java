package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.TicketScan;
import com.eventhub.ticket.entity.enums.ScanType;
import com.eventhub.ticket.entity.enums.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record GateScanResponse(
        UUID ticketId,
        TicketStatus status,
        ScanType scanType,
        Instant scannedAt
) {
    public static GateScanResponse from(TicketScan scan) {
        return new GateScanResponse(
                scan.getTicket().getId(),
                scan.getTicket().getStatus(),
                scan.getScanType(),
                scan.getScannedAt()
        );
    }
}
