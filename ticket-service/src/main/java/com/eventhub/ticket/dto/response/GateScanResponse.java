package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.ScanType;
import com.eventhub.ticket.common.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record GateScanResponse(UUID ticketId, TicketStatus status, ScanType scanType, Instant scannedAt) {
}
