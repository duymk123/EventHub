package com.eventhub.analytics.service;

import com.eventhub.analytics.dto.response.DashboardSnapshotResponse;
import com.eventhub.analytics.repository.DashboardSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/**
 * Đọc snapshot mới phục vụ push sau commit. REQUIRES_NEW tạo transaction đọc mới khi callback AFTER_COMMIT còn
 * chạy trong ngữ cảnh transaction trước.
 */
@Service
public class DashboardReadService {
    private final DashboardSnapshotRepository repository;

    public DashboardReadService(DashboardSnapshotRepository repository) {
        this.repository = repository;
    }

    /**
     * Mở transaction đọc REQUIRES_NEW, lấy snapshot vừa commit và map DTO. Callback AFTER_COMMIT gọi qua bean
     * này để nhận ngữ cảnh đọc mới.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public DashboardSnapshotResponse getForPush(UUID eventId) {
        var snapshot = repository.findByEventId(eventId).orElseThrow();
        return new DashboardSnapshotResponse(eventId, snapshot.getTotalRevenue(), snapshot.getTotalTicketsSold(),
                snapshot.getCurrentAttendance(), snapshot.getTotalCheckedIn(), snapshot.getLastUpdated());
    }
}
