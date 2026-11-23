package com.eventhub.order.service;

import com.eventhub.order.common.Actor;
import com.eventhub.order.common.RefundStatus;
import com.eventhub.order.dto.response.RefundResponse;

import java.util.List;
import java.util.UUID;

/**
 * Hoàn tiền C04 + C05 rút gọn: khách yêu cầu hoàn cả đơn 70%, ADMIN duyệt → Ticket hủy vé và trả ghế → VNPay hoàn
 * một phần → đơn REFUNDED + order.refunded. VNPay lỗi thì FAILED, ADMIN thử lại.
 */
public interface RefundService {

    /** CUSTOMER chủ đơn: đơn PAID đã phát vé, còn ≥ 48 giờ trước sự kiện, chưa có yêu cầu nào ngoài REJECTED. */
    RefundResponse request(Actor customer, UUID orderId, String reason);

    /** Các yêu cầu hoàn của một đơn (chủ đơn), mới nhất trước. */
    List<RefundResponse> listForOrder(Actor customer, UUID orderId);

    /** ADMIN, lọc theo trạng thái nếu có, mới nhất trước. */
    List<RefundResponse> listForAdmin(RefundStatus status);

    /**
     * ADMIN duyệt REQUESTED. Dưới 48 giờ trước sự kiện thì tự chuyển REJECTED (C04 kiểm cả lúc duyệt); còn lại chạy
     * hủy vé rồi gọi VNPay ngay trong request.
     */
    RefundResponse approve(Actor admin, UUID refundId, String clientIp);

    /** ADMIN từ chối REQUESTED kèm lý do. */
    RefundResponse reject(Actor admin, UUID refundId, String reason);

    /** ADMIN thử lại FAILED: hủy vé (idempotent) rồi gọi VNPay với vnp_RequestId mới. */
    RefundResponse retry(Actor admin, UUID refundId, String clientIp);
}
