package com.eventhub.ticket.service;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.dto.request.CreateTicketTypesRequest;
import com.eventhub.ticket.dto.request.UpdateTicketTypeRequest;
import com.eventhub.ticket.dto.response.EventCapacityResponse;
import com.eventhub.ticket.dto.response.SeatMapResponse;
import com.eventhub.ticket.dto.response.TicketConfigurationResponse;
import com.eventhub.ticket.dto.response.TicketTypeResponse;

import java.util.List;
import java.util.UUID;

/**
 * Cấu hình hạng vé và tồn kho (plan Ticket §2).
 */
public interface TicketTypeService {

    /** ORGANIZER chủ sự kiện tạo 1..3 hạng; tên không trùng, quota được bằng 0 (C08). */
    List<TicketTypeResponse> createTypes(Actor organizer, CreateTicketTypesRequest request);

    /** Đổi giá/quota/trạng thái; quota mới không nhỏ hơn reserved + sold. */
    TicketTypeResponse updateType(Actor organizer, UUID typeId, UpdateTicketTypeRequest request);

    /**
     * Hạng vé của sự kiện; số lượng chỉ tham khảo, reserve mới là bước quyết định. Sự kiện chưa công khai chỉ
     * ORGANIZER chủ sự kiện và ADMIN đọc được (để cấu hình/duyệt), viewer null là khách chưa đăng nhập.
     */
    List<TicketTypeResponse> listTypes(UUID eventId, Actor viewer);

    /** C12: sơ đồ ghế của một zone (public, sự kiện phải đang công khai). */
    SeatMapResponse getSeatMap(UUID typeId);

    /** C10: sức chứa theo sự kiện cho Analytics, 1..100 id, id chưa có hạng vé trả 0. */
    List<EventCapacityResponse> getCapacityByEvents(List<UUID> eventIds);

    /** C08: Event Service kiểm tra trước publish; sự kiện chưa có hạng vé trả readyToPublish=false. */
    TicketConfigurationResponse getConfiguration(UUID eventId);
}
