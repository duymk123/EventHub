package com.eventhub.event.repository;

import com.eventhub.event.common.EventStatus;
import com.eventhub.event.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mọi truy vấn đọc entity để trả DTO đều kèm category (join fetch hoặc @EntityGraph): DTO map sau khi
 * transaction đóng (open-in-view: false), nên category lazy chưa nạp sẽ ném LazyInitializationException.
 * Method khai báo @Query không nhận @EntityGraph, nên các method đó viết join fetch.
 */
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** Chi tiết public: chỉ trả event đúng trạng thái, không lộ event chưa publish. */
    @EntityGraph(attributePaths = "category")
    Optional<Event> findByIdAndStatus(UUID id, EventStatus status);

    @Query("select e from Event e join fetch e.category where e.id = :id")
    Optional<Event> findWithCategoryById(@Param("id") UUID id);

    /**
     * Khóa hàng cho mọi chuyển trạng thái/sửa event; hai request đồng thời xếp hàng thay vì ghi đè nhau.
     * Có @Query nên join fetch không đổi ngữ nghĩa khóa: khóa áp dụng cho entity gốc Event.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e join fetch e.category where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "category")
    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Event> findByOrganizerIdAndStatus(UUID organizerId, EventStatus status, Pageable pageable);

    /** EVT-11: ADMIN xem cả hàng đợi duyệt (thường lọc SUBMITTED), cùng fetch category như trang organizer. */
    @EntityGraph(attributePaths = "category")
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);

    /** INT-03: id mọi event của organizer, mọi trạng thái. */
    @Query("select e.id from Event e where e.organizerId = :organizerId")
    List<UUID> findIdsByOrganizerId(@Param("organizerId") UUID organizerId);

    boolean existsByCategoryId(UUID categoryId);
}
