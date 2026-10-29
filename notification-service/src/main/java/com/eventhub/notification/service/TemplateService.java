package com.eventhub.notification.service;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.dto.request.TemplateRequest;
import com.eventhub.notification.dto.response.TemplateResponse;

import java.util.List;

/**
 * Quản lý template thông báo, chỉ dành cho quản trị viên.
 */
public interface TemplateService {

    List<TemplateResponse> list(Actor actor);

    TemplateResponse get(Actor actor, String code, NotificationChannel channel);

    TemplateResponse upsert(Actor actor, String code, TemplateRequest request);
}
