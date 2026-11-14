package com.eventhub.analytics.service;

import com.eventhub.analytics.common.Actor;

import java.util.UUID;

/**
 * Xác minh sự kiện tồn tại và quyền của người dùng để xem số liệu phân tích.
 */
public interface EventAccessService {

    EventAccessContext assertCanView(Actor actor, UUID eventId);
}


