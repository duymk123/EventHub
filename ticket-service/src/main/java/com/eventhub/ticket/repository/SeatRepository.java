package com.eventhub.ticket.repository;

import com.eventhub.ticket.common.SeatStatus;
import com.eventhub.ticket.entity.Seat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Ghế theo zone (C12). Mọi đổi trạng thái là UPDATE có điều kiện trên đúng các dòng liên quan và trả số dòng đổi
 * được; caller so số dòng để biết có tranh chấp hay không. Không khóa ticket_types nên hai đơn chỉ chặn nhau khi
 * chọn trùng ghế.
 */
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @EntityGraph(attributePaths = "ticketType")
    List<Seat> findByIdIn(Collection<UUID> ids);

    List<Seat> findByTicketTypeIdOrderByRowIndexAscSeatNumberAsc(UUID ticketTypeId);

    /** AVAILABLE → HELD cho đúng các ghế được chọn. Danh sách id nên sắp xếp để thứ tự khóa ổn định. */
    @Modifying(flushAutomatically = true)
    @Query("update Seat s set s.status = com.eventhub.ticket.common.SeatStatus.HELD, s.reservationId = :reservationId, "
            + "s.updatedAt = :now where s.id in :ids and s.status = com.eventhub.ticket.common.SeatStatus.AVAILABLE")
    int holdSeats(@Param("ids") Collection<UUID> ids, @Param("reservationId") UUID reservationId,
                  @Param("now") Instant now);

    /** Confirm: HELD → SOLD cho ghế của reservation. */
    @Modifying(flushAutomatically = true)
    @Query("update Seat s set s.status = com.eventhub.ticket.common.SeatStatus.SOLD, s.updatedAt = :now "
            + "where s.reservationId = :reservationId and s.status = com.eventhub.ticket.common.SeatStatus.HELD")
    int sellByReservation(@Param("reservationId") UUID reservationId, @Param("now") Instant now);

    /** Release/expire: HELD → AVAILABLE cho ghế của reservation, đúng một lần. */
    @Modifying(flushAutomatically = true)
    @Query("update Seat s set s.status = com.eventhub.ticket.common.SeatStatus.AVAILABLE, s.reservationId = null, "
            + "s.updatedAt = :now where s.reservationId = :reservationId "
            + "and s.status = com.eventhub.ticket.common.SeatStatus.HELD")
    int releaseByReservation(@Param("reservationId") UUID reservationId, @Param("now") Instant now);

    /** Hủy vé (hoàn tiền): SOLD → AVAILABLE để bán lại. */
    @Modifying(flushAutomatically = true)
    @Query("update Seat s set s.status = com.eventhub.ticket.common.SeatStatus.AVAILABLE, s.reservationId = null, "
            + "s.updatedAt = :now where s.id in :ids and s.status = com.eventhub.ticket.common.SeatStatus.SOLD")
    int freeSeats(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    /** Số ghế theo zone và trạng thái cho danh sách zone (một query cho cả sự kiện). */
    @Query("select s.ticketType.id as ticketTypeId, s.status as status, count(s) as total from Seat s "
            + "where s.ticketType.id in :typeIds group by s.ticketType.id, s.status")
    List<SeatCountRow> countByTypeAndStatus(@Param("typeIds") Collection<UUID> typeIds);

    long countByTicketTypeIdAndStatusNot(UUID ticketTypeId, SeatStatus status);

    @Modifying(flushAutomatically = true)
    @Query("delete from Seat s where s.ticketType.id = :typeId and s.rowIndex > :rowIndex")
    int deleteRowsAfter(@Param("typeId") UUID typeId, @Param("rowIndex") int rowIndex);

    interface SeatCountRow {
        UUID getTicketTypeId();

        SeatStatus getStatus();

        Long getTotal();
    }
}
