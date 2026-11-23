package com.eventhub.ticket.entity;

import com.eventhub.ticket.common.SeatLabels;
import com.eventhub.ticket.common.SeatStatus;

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

/**
 * Một ghế của một zone (C12). Sinh từ row_count × seats_per_row khi organizer cấu hình hạng vé. Giữ ghế là UPDATE
 * có điều kiện status = AVAILABLE trên đúng các dòng được chọn (SeatRepository.holdSeats), không khóa ticket_types.
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_seat_position",
                columnNames = {"ticket_type_id", "row_index", "seat_number"}),
        indexes = {
                @Index(name = "idx_seat_type_status", columnList = "ticket_type_id, status"),
                @Index(name = "idx_seat_reservation", columnList = "reservation_id")
        })
@Getter @Setter @NoArgsConstructor
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "row_label", nullable = false, length = 3)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 10)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /** Reservation đang giữ hoặc đã mua ghế; NULL khi AVAILABLE (CHECK trong DDL). */
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "reservation_id", length = 36)
    private UUID reservationId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Seat of(TicketType type, int rowIndex, int seatNumber, Instant now) {
        Seat seat = new Seat();
        seat.setTicketType(type);
        seat.setRowIndex(rowIndex);
        seat.setRowLabel(SeatLabels.rowLabel(rowIndex));
        seat.setSeatNumber(seatNumber);
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setUpdatedAt(now);
        return seat;
    }

    public String getLabel() {
        return SeatLabels.seatLabel(rowLabel, seatNumber);
    }
}
