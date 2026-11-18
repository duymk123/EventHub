package com.eventhub.ticket.service;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.ScanType;
import com.eventhub.ticket.dto.request.GateScanRequest;
import com.eventhub.ticket.dto.response.GateScanResponse;

/**
 * Soát vé một lần vào, một lần ra, không tái nhập (Hình 2.55–2.56). Người quét tạm thời là ORGANIZER chủ sự
 * kiện hoặc ADMIN cho tới khi C07 chốt cơ chế thiết bị cổng.
 */
public interface GateService {

    /** CHECK_IN: ISSUED → CHECKED_IN; CHECK_OUT: CHECKED_IN → CHECKED_OUT. CAS + log + outbox một transaction. */
    GateScanResponse scan(Actor scanner, GateScanRequest request, ScanType scanType);
}
