package com.eventhub.analytics.repository;

import com.eventhub.analytics.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, UUID> {
}
