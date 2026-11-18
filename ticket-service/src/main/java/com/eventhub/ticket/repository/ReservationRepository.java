package com.eventhub.ticket.repository;

import com.eventhub.ticket.common.ReservationStatus;
import com.eventhub.ticket.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Idempotency reserve; UNIQUE(order_id) là chốt cuối khi hai request cùng orderId chạy song song. */
    @EntityGraph(attributePaths = {"items", "items.ticketType"})
    Optional<Reservation> findByOrderId(UUID orderId);

    @EntityGraph(attributePaths = {"items", "items.ticketType"})
    Optional<Reservation> findWithItemsById(UUID id);

    /** Tuần tự hóa confirm/release/expire trên cùng reservation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    /** Lấy một lô ID đến hạn (index status, expires_at); mỗi ID được xử lý trong transaction riêng. */
    @Query("select r.id from Reservation r where r.status = :status and r.expiresAt <= :now order by r.expiresAt")
    List<UUID> findDueIds(@Param("status") ReservationStatus status, @Param("now") Instant now, Pageable page);
}
