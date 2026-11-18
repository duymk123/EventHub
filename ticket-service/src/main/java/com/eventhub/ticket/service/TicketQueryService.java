package com.eventhub.ticket.service;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.dto.response.QrResponse;
import com.eventhub.ticket.dto.response.TicketResponse;

import java.util.List;
import java.util.UUID;

/**
 * Vé của người mua. C02: owner là buyer; holderEmail không cấp quyền xem vé.
 */
public interface TicketQueryService {

    List<TicketResponse> getMine(Actor customer);

    /** QR chỉ cho chủ vé; giá trị lưu sẵn lúc phát hành, không sinh mới mỗi lần GET. */
    QrResponse getQr(Actor customer, UUID ticketId);
}
