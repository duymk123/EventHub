package com.eventhub.analytics.repository;

import com.eventhub.analytics.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository JPA truy cập entity AttendanceLog khi cần đọc dữ liệu điểm danh. ProjectionRepository hiện thực
 * phần ghi log cùng transaction projection.
 */
@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, UUID> {
}
