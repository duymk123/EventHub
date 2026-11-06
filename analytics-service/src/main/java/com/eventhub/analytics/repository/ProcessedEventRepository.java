package com.eventhub.analytics.repository;

import com.eventhub.analytics.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository JPA truy cập dấu message đã xử lý. ProjectionRepository.claim hiện dùng INSERT trực tiếp để ràng
 * buộc unique quyết định quyền áp dụng message.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
