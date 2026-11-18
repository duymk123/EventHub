package com.eventhub.ticket.repository;

import com.eventhub.ticket.entity.TicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventId(UUID eventId);

    /**
     * Khóa nhiều hạng vé theo thứ tự khóa chính (ORDER BY id), mọi giao dịch khóa cùng thứ tự nên không deadlock
     * khi hai đơn chọn cùng các hạng theo thứ tự khác nhau. Caller kiểm tra đủ số ID.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id in :ids order by t.id")
    List<TicketType> findAllByIdsForUpdate(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") UUID id);

    /** C10: tổng total_quota theo sự kiện, gồm cả hạng INACTIVE. */
    @Query("select t.eventId as eventId, sum(t.totalQuota) as capacity from TicketType t "
            + "where t.eventId in :eventIds group by t.eventId")
    List<EventCapacityRow> sumCapacityByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    interface EventCapacityRow {
        UUID getEventId();

        Long getCapacity();
    }
}
