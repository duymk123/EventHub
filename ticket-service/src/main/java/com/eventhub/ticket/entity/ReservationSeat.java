package com.eventhub.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.UUID;

/**
 * Ghế thuộc một lần giữ chỗ (C12). seats.reservation_id bị xóa khi ghế được trả nên bảng này giữ lịch sử; confirm đọc
 * bảng này để phát đúng một vé cho mỗi ghế.
 */
@Entity
@Table(name = "reservation_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_seat", columnNames = {"reservation_id", "seat_id"}),
        indexes = @Index(name = "idx_reservation_seat_seat", columnList = "seat_id"))
@Getter @Setter @NoArgsConstructor
public class ReservationSeat {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    /** Nhãn chụp lại lúc giữ, không đổi khi organizer sửa sơ đồ sau này. */
    @Column(name = "seat_label", nullable = false, length = 8)
    private String seatLabel;
}
