package com.eventhub.ticket.repository;

import com.eventhub.ticket.common.TicketStatus;
import com.eventhub.ticket.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /** GET /tickets/me: mảng theo API, mới nhất trước (index owner_user_id, issued_at). */
    @EntityGraph(attributePaths = "ticketType")
    List<Ticket> findByOwnerUserIdOrderByIssuedAtDescIdAsc(UUID ownerUserId);

    /** Confirm lặp trả đúng bộ vé cũ, không tạo QR mới. */
    @EntityGraph(attributePaths = "ticketType")
    List<Ticket> findByReservationIdOrderByIdAsc(UUID reservationId);

    @EntityGraph(attributePaths = "ticketType")
    Optional<Ticket> findWithTypeById(UUID id);

    /** QR là chuỗi opaque UNIQUE, không ép UUID. */
    @EntityGraph(attributePaths = "ticketType")
    Optional<Ticket> findByQrCodeValue(String qrCodeValue);

    /** Khóa toàn bộ vé của đơn theo thứ tự ID trước khi hủy. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t join fetch t.ticketType where t.orderId = :orderId order by t.id")
    List<Ticket> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    /**
     * CAS trạng thái vé: chỉ một cổng thắng khi hai cổng quét cùng QR. Trả số dòng đổi (0 = thua/sai trạng thái).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Ticket t set t.status = :to where t.id = :id and t.status = :from")
    int compareAndSetStatus(@Param("id") UUID id, @Param("from") TicketStatus from, @Param("to") TicketStatus to);
}
