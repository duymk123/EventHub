package com.eventhub.ticket.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_qr_code", columnNames = "qr_code_value"),
        indexes = {
                @Index(name = "idx_tickets_owner", columnList = "owner_user_id, issued_at"),
                @Index(name = "idx_tickets_order", columnList = "order_id")
        })
@Getter @Setter @NoArgsConstructor
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "order_id", nullable = false, length = 36)
    private UUID orderId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "owner_user_id", nullable = false, length = 36)
    private UUID ownerUserId;
    @Column(name = "holder_name", length = 100)
    private String holderName;
    @Column(name = "qr_code_value", nullable = false, unique = true, length = 255)
    private String qrCodeValue;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.ISSUED;
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
