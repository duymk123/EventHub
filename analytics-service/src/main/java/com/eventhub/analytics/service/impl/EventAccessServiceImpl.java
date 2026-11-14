package com.eventhub.analytics.service.impl;

import com.eventhub.analytics.client.EventClient;
import com.eventhub.analytics.client.InternalEventResponse;
import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.common.BusinessRole;
import com.eventhub.analytics.common.ErrorCode;
import com.eventhub.analytics.exception.BusinessException;
import com.eventhub.analytics.service.EventAccessContext;
import com.eventhub.analytics.service.EventAccessService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Xác minh người gọi được phép xem số liệu phân tích cho một sự kiện.
 * ADMIN — được cấp quyền bất kể quyền sở hữu.
 * ORGANIZER — được cấp quyền chỉ khi là chủ sở hữu sự kiện.
 * CUSTOMER — bị từ chối.
 * Chạy trước bất kỳ truy vấn DB nào để không giữ kết nối DB khi chờ HTTP từ Event Service.
 */
@Service
public class EventAccessServiceImpl implements EventAccessService {

    private final EventClient eventClient;

    public EventAccessServiceImpl(EventClient eventClient) {
        this.eventClient = eventClient;
    }

    /**
     * Xác nhận người dùng có thể xem số liệu phân tích cho sự kiện.
     *
     * @return ngữ cảnh chứa eventId và organizerId
     * @throws BusinessException 403 cho CUSTOMER hoặc ORGANIZER không phải chủ sở hữu
     * @throws BusinessException 404 nếu sự kiện không tồn tại
     * @throws BusinessException 503 nếu Event Service không khả dụng
     */
    @Override
    public EventAccessContext assertCanView(Actor actor, UUID eventId) {
        BusinessRole role = actor.role();
        if (role == BusinessRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "CUSTOMER cannot view analytics");
        }

        InternalEventResponse event = eventClient.get(eventId);

        if (role == BusinessRole.ADMIN) {
            return new EventAccessContext(event.id(), event.organizerId());
        }

        // ORGANIZER phải là chủ sở hữu
        if (!event.organizerId().equals(actor.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER,
                    "You are not the organizer of this event");
        }
        return new EventAccessContext(event.id(), event.organizerId());
    }
}
