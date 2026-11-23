package com.eventhub.order.service;

import com.eventhub.order.client.VnpayClient;
import com.eventhub.order.common.PaymentStatus;
import com.eventhub.order.dto.response.VnpayIpnAck;
import com.eventhub.order.entity.Payment;
import com.eventhub.order.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * IPN của VNPay (C06): kết quả thanh toán chính thức. Thứ tự kiểm: chữ ký (97) → payment theo vnp_TxnRef (01) →
 * số tiền (04) → đã xử lý (02) → áp kết quả (00). Luôn trả HTTP 200 kèm RspCode để VNPay không gửi lại vô hạn.
 */
@Service
public class VnpayIpnService {
    private static final Logger log = LoggerFactory.getLogger(VnpayIpnService.class);

    private final VnpayClient vnpay;
    private final PaymentRepository payments;
    private final PaymentResultService results;

    public VnpayIpnService(VnpayClient vnpay, PaymentRepository payments, PaymentResultService results) {
        this.vnpay = vnpay;
        this.payments = payments;
        this.results = results;
    }

    public VnpayIpnAck handle(Map<String, String> params) {
        try {
            if (!vnpay.isConfigured() || !vnpay.verifySignature(params)) {
                return new VnpayIpnAck("97", "Invalid Checksum");
            }
            String txnRef = params.get("vnp_TxnRef");
            Payment payment = txnRef == null ? null
                    : payments.findByProviderAndProviderTxnRef(VnpayClient.PROVIDER, txnRef).orElse(null);
            if (payment == null) {
                return new VnpayIpnAck("01", "Order not found");
            }
            if (!amountMatches(params.get("vnp_Amount"), payment.getAmount())) {
                return new VnpayIpnAck("04", "Invalid amount");
            }
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return new VnpayIpnAck("02", "Order already confirmed");
            }
            String responseCode = params.get("vnp_ResponseCode");
            String transactionNo = params.get("vnp_TransactionNo");
            boolean success = "00".equals(responseCode) && "00".equals(params.get("vnp_TransactionStatus"));
            var result = new PaymentResultService.ProviderResult(success, responseCode,
                    success ? transactionNo : null, success ? params.get("vnp_PayDate") : null);
            var webhook = new PaymentResultService.WebhookRecord(txnRef + ":" + (transactionNo == null ? "-" : transactionNo),
                    VnpayClient.payloadHash(params));
            PaymentResultService.Outcome outcome = results.apply(payment.getId(), result, webhook);
            return outcome == PaymentResultService.Outcome.ALREADY_PROCESSED
                    ? new VnpayIpnAck("02", "Order already confirmed")
                    : new VnpayIpnAck("00", "Confirm Success");
        } catch (DataIntegrityViolationException error) {
            // UNIQUE(provider, provider_event_id): IPN lặp song song, bản kia đã xử lý.
            return new VnpayIpnAck("02", "Order already confirmed");
        } catch (RuntimeException error) {
            log.warn("VNPay IPN processing failed: {}", error.getClass().getSimpleName());
            return new VnpayIpnAck("99", "Unknown error");
        }
    }

    private static boolean amountMatches(String vnpAmount, BigDecimal amount) {
        if (vnpAmount == null) {
            return false;
        }
        try {
            return Long.parseLong(vnpAmount) == amount.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            return false;
        }
    }
}
