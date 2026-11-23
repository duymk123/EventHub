package com.eventhub.ticket.repository;

import com.eventhub.ticket.entity.ReservationSeat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, UUID> {

    @EntityGraph(attributePaths = {"seat", "seat.ticketType"})
    List<ReservationSeat> findByReservationIdOrderBySeatLabelAsc(UUID reservationId);

    /**
     * Ghế của zone (từ hàng fromRow trở đi) đã từng thuộc một reservation: không xóa được vì reservation_seats giữ
     * lịch sử, nên không được bớt hàng hoặc đổi số ghế mỗi hàng.
     */
    @Query("select count(rs) > 0 from ReservationSeat rs where rs.seat.ticketType.id = :typeId "
            + "and rs.seat.rowIndex >= :fromRow")
    boolean existsHistoryFromRow(@Param("typeId") UUID typeId, @Param("fromRow") int fromRow);
}
