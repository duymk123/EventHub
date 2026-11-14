package com.eventhub.analytics.service;

import com.eventhub.analytics.client.EventClient;
import com.eventhub.analytics.client.InternalEventResponse;
import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.common.BusinessRole;
import com.eventhub.analytics.common.ErrorCode;
import com.eventhub.analytics.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ngữ cảnh trả về bởi EventAccessService sau khi xác minh người gọi được phép xem số liệu.
 *
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param organizerId UUID người tổ chức sở hữu sự kiện lấy từ Event Service.
 */
public record EventAccessContext(UUID eventId, UUID organizerId) {}
