package com.eventhub.ticket.service.impl;

import com.eventhub.ticket.client.EventClient;
import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.BusinessRole;
import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.common.ScanType;
import com.eventhub.ticket.common.TicketStatus;
import com.eventhub.ticket.dto.request.GateScanRequest;
import com.eventhub.ticket.dto.response.GateScanResponse;
import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.TicketScan;
import com.eventhub.ticket.exception.BusinessException;
import com.eventhub.ticket.repository.TicketRepository;
import com.eventhub.ticket.repository.TicketScanRepository;
import com.eventhub.ticket.service.GateService;
import com.eventhub.ticket.service.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GateServiceImpl implements GateService {
    private final TicketRepository tickets;
    private final TicketScanRepository scans;
    private final EventClient eventClient;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GateServiceImpl(TicketRepository tickets, TicketScanRepository scans, EventClient eventClient,
                           OutboxWriter outbox, TransactionTemplate tx, Clock clock) {
        this.tickets = tickets;
        this.scans = scans;
        this.eventClient = eventClient;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public GateScanResponse scan(Actor scanner, GateScanRequest request, ScanType scanType) {
        Ticket ticket = tickets.findByQrCodeValue(request.qrCodeValue().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_QR, "QR code is not valid"));
        if (!ticket.getTicketType().getEventId().equals(request.eventId())) {
            throw new BusinessException(ErrorCode.TICKET_WRONG_EVENT, "Ticket belongs to another event");
        }
        if (scanner.role() == BusinessRole.ORGANIZER
                && !eventClient.getInternal(request.eventId()).organizerId().equals(scanner.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER, "Event belongs to another organizer");
        }
        TicketStatus from = scanType == ScanType.CHECK_IN ? TicketStatus.ISSUED : TicketStatus.CHECKED_IN;
        TicketStatus to = scanType == ScanType.CHECK_IN ? TicketStatus.CHECKED_IN : TicketStatus.CHECKED_OUT;

        return tx.execute(status -> {
            // CAS: hai cổng quét cùng QR thì chỉ một bên đổi được trạng thái, bên kia nhận INVALID_TICKET_STATE.
            if (tickets.compareAndSetStatus(ticket.getId(), from, to) != 1) {
                throw new BusinessException(ErrorCode.INVALID_TICKET_STATE,
                        "Ticket is not eligible for " + scanType);
            }
            Instant now = clock.instant();
            TicketScan scan = new TicketScan();
            scan.setTicket(tickets.getReferenceById(ticket.getId()));
            scan.setGateId(request.gateId());
            scan.setScanType(scanType);
            scan.setScannedAt(now);
            scans.save(scan);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("ticketId", ticket.getId());
            fields.put("ticketTypeId", ticket.getTicketType().getId());
            fields.put("gateId", request.gateId());
            fields.put("scannedAt", now);
            String topic = scanType == ScanType.CHECK_IN ? "ticket.checked-in" : "ticket.checked-out";
            outbox.append(topic, ticket.getId(), request.eventId(), now, fields);
            return new GateScanResponse(ticket.getId(), to, scanType, now);
        });
    }
}
