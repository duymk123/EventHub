package com.eventhub.order.service;

import com.eventhub.order.common.Actor;
import com.eventhub.order.dto.response.OrderResponse;
import com.eventhub.order.dto.response.PaymentResponse;
import com.eventhub.order.dto.response.ReconciliationItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thanh toán VNPay sandbox (C06, plan Order §10.3).
 */
public interface PaymentService {

    /**
     * Đơn PENDING_PAYMENT, còn hạn: tạo hoặc đọc lại Payment theo Idempotency-Key, trả URL vpcpay.html đã ký.
     */
    PaymentResponse initiate(Actor customer, UUID orderId, String idempotencyKey, String clientIp);

    /**
     * Khách quay về từ VNPay. returnParams là các tham số vnp_* VNPay gắn vào returnUrl (có thể rỗng): cùng dữ liệu
     * và cùng chữ ký HMAC với IPN, nên khi chữ ký đúng và vnp_TxnRef thuộc đơn này thì áp kết quả như IPN. Thiếu
     * hoặc sai chữ ký thì payment còn PENDING được hỏi lại bằng querydr. Frontend chỉ chuyển tiếp, không tự tin URL.
     */
    OrderResponse verify(Actor customer, UUID orderId, String clientIp, Map<String, String> returnParams);

    /** C21: tối đa 100 payment đã trừ tiền khách mà đơn không dùng, cho ADMIN hoàn tiền thủ công. */
    List<ReconciliationItem> reconciliation();

    /** C21: ADMIN đánh dấu khoản đối soát đã xử lý kèm ghi chú; khoản đó rời danh sách. */
    ReconciliationItem resolve(Actor admin, UUID paymentId, String note);
}
