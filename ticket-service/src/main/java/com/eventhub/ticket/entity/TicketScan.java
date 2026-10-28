package com.eventhub.ticket.entity;

import com.eventhub.ticket.common.ScanType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_scans", indexes = @Index(name = "idx_scan_ticket", columnList = "ticket_id, scanned_at"))
@Getter @Setter @NoArgsConstructor
public class TicketScan {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "gate_id", nullable = false, length = 36)
    private UUID gateId;
    @Enumerated(EnumType.STRING) @Column(name = "scan_type", nullable = false, length = 10)
    private ScanType scanType;
    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt;
}
