package com.eventhub.order.client;

import com.eventhub.order.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventhub.order.config.VnpayProperties;
import com.eventhub.order.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

/**
 * VNPay 2.1.0 (C06): ký URL thanh toán, kiểm chữ ký IPN, gọi API querydr và refund (C05). Chữ ký là HMAC-SHA512 bằng hash secret;
 * không log secret, chữ ký hay URL đã ký.
 */
@Component
public class VnpayClient {
    public static final String PROVIDER = "VNPAY";
    private static final Logger log = LoggerFactory.getLogger(VnpayClient.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Định dạng thời gian của VNPay, giờ Việt Nam (GMT+7). */
    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VIETNAM);
    private static final String SECURE_HASH = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    /** Kết quả querydr đã kiểm checksum. */
    public record QueryResult(String responseCode, String message, String txnRef, String amount,
                              String transactionNo, String transactionStatus, String payDate) {
    }

    /** Lệnh hoàn tiền một phần (vnp_TransactionType = 03); amount là số tiền × 100. */
    public record RefundCommand(String requestId, String txnRef, String transactionNo, String transactionDate,
                                long amount, String createBy, String ipAddr, String orderInfo) {
    }

    /** responseCode CHECKSUM_MISMATCH khi phản hồi không khớp chữ ký: ADMIN phải kiểm trên cổng merchant. */
    public record RefundResult(String responseCode, String message, String transactionNo) {
    }

    private final VnpayProperties properties;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public VnpayClient(VnpayProperties properties, Clock clock, ObjectMapper mapper) {
        this.properties = properties;
        this.clock = clock;
        this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public VnpayProperties properties() {
        return properties;
    }

    public static String formatTime(Instant instant) {
        return VNP_TIME.format(instant);
    }

    /** URL vpcpay.html đã ký: query string và dữ liệu ký dùng cùng một chuỗi đã sắp xếp. */
    public String buildPaymentUrl(Map<String, String> params) {
        String data = hashData(params);
        return properties.getPayUrl() + "?" + data + "&" + SECURE_HASH + "=" + hmac(data);
    }

    /** Kiểm vnp_SecureHash của IPN/return URL; so sánh thời gian hằng. */
    public boolean verifySignature(Map<String, String> params) {
        String given = params.get(SECURE_HASH);
        if (given == null || given.isBlank()) {
            return false;
        }
        return constantTimeEquals(hmac(hashData(params)), given);
    }

    /** SHA-256 của dữ liệu ký, lưu vào payment_webhooks.payload_hash để đối chiếu mà không lưu nguyên payload. */
    public static String payloadHash(Map<String, String> params) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(hashData(params).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    /**
     * querydr: hỏi VNPay trạng thái giao dịch thanh toán. transactionDate là vnp_CreateDate đã gửi lúc tạo URL.
     * Lỗi mạng hoặc checksum phản hồi sai trả PAYMENT_PROVIDER_UNAVAILABLE.
     */
    public QueryResult queryTransaction(String txnRef, String transactionDate, String ipAddr) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = formatTime(clock.instant());
        String orderInfo = "Truy van giao dich " + txnRef;
        String data = String.join("|", requestId, properties.getVersion(), "querydr", properties.getTmnCode(),
                txnRef, transactionDate, createDate, ipAddr, orderInfo);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", properties.getVersion());
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", properties.getTmnCode());
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ipAddr);
        body.put(SECURE_HASH, hmac(data));

        Map<?, ?> response = callApi(body, "querydr");
        QueryResult result = new QueryResult(text(response, "vnp_ResponseCode"), text(response, "vnp_Message"),
                text(response, "vnp_TxnRef"), text(response, "vnp_Amount"), text(response, "vnp_TransactionNo"),
                text(response, "vnp_TransactionStatus"), text(response, "vnp_PayDate"));
        String given = text(response, SECURE_HASH);
        if (given.isEmpty()) {
            // Một số mã lỗi (vd sai checksum request) không kèm checksum; không bao giờ tin kết quả thành công thiếu chữ ký.
            if ("00".equals(result.responseCode())) {
                log.warn("VNPay querydr success response without checksum for txnRef {}", txnRef);
                throw unavailable();
            }
            return result;
        }
        String responseData = String.join("|", text(response, "vnp_ResponseId"), text(response, "vnp_Command"),
                result.responseCode(), result.message(), text(response, "vnp_TmnCode"), result.txnRef(),
                result.amount(), text(response, "vnp_BankCode"), result.payDate(), result.transactionNo(),
                text(response, "vnp_TransactionType"), result.transactionStatus(), text(response, "vnp_OrderInfo"),
                text(response, "vnp_PromotionCode"), text(response, "vnp_PromotionAmount"));
        if (!constantTimeEquals(hmac(responseData), given)) {
            log.warn("VNPay querydr response checksum mismatch for txnRef {} (responseCode {})", txnRef,
                    result.responseCode());
            throw unavailable();
        }
        return result;
    }

    /**
     * refund: hoàn một phần giao dịch đã thanh toán. transactionDate là vnp_CreateDate của URL thanh toán gốc (giống
     * querydr). Lỗi mạng trả PAYMENT_PROVIDER_UNAVAILABLE; phản hồi thành công mà sai chữ ký trả CHECKSUM_MISMATCH.
     */
    public RefundResult refund(RefundCommand command) {
        String createDate = formatTime(clock.instant());
        String transactionNo = command.transactionNo() == null ? "" : command.transactionNo();
        String amount = String.valueOf(command.amount());
        String data = String.join("|", command.requestId(), properties.getVersion(), "refund",
                properties.getTmnCode(), "03", command.txnRef(), amount, transactionNo, command.transactionDate(),
                command.createBy(), createDate, command.ipAddr(), command.orderInfo());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", command.requestId());
        body.put("vnp_Version", properties.getVersion());
        body.put("vnp_Command", "refund");
        body.put("vnp_TmnCode", properties.getTmnCode());
        body.put("vnp_TransactionType", "03");
        body.put("vnp_TxnRef", command.txnRef());
        body.put("vnp_Amount", amount);
        body.put("vnp_OrderInfo", command.orderInfo());
        body.put("vnp_TransactionNo", transactionNo);
        body.put("vnp_TransactionDate", command.transactionDate());
        body.put("vnp_CreateBy", command.createBy());
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", command.ipAddr());
        body.put(SECURE_HASH, hmac(data));

        Map<?, ?> response = callApi(body, "refund");
        String responseCode = text(response, "vnp_ResponseCode");
        String given = text(response, SECURE_HASH);
        if (given.isEmpty()) {
            return "00".equals(responseCode) ? new RefundResult("CHECKSUM_MISMATCH", "", "")
                    : new RefundResult(responseCode, text(response, "vnp_Message"), "");
        }
        String responseData = String.join("|", text(response, "vnp_ResponseId"), text(response, "vnp_Command"),
                responseCode, text(response, "vnp_Message"), text(response, "vnp_TmnCode"),
                text(response, "vnp_TxnRef"), text(response, "vnp_Amount"), text(response, "vnp_BankCode"),
                text(response, "vnp_PayDate"), text(response, "vnp_TransactionNo"),
                text(response, "vnp_TransactionType"), text(response, "vnp_TransactionStatus"),
                text(response, "vnp_OrderInfo"));
        if (!constantTimeEquals(hmac(responseData), given)) {
            log.warn("VNPay refund response checksum mismatch for txnRef {} (responseCode {})", command.txnRef(),
                    responseCode);
            return new RefundResult("CHECKSUM_MISMATCH", "", "");
        }
        return new RefundResult(responseCode, text(response, "vnp_Message"), text(response, "vnp_TransactionNo"));
    }

    /** vnp_CreateDate đã ký trong URL thanh toán; querydr/refund phải gửi đúng giá trị này. */
    public static String createDateOf(String paymentUrl) {
        String value = paymentUrl == null ? null : org.springframework.web.util.UriComponentsBuilder
                .fromUriString(paymentUrl).build().getQueryParams().getFirst("vnp_CreateDate");
        if (value == null) {
            throw new IllegalStateException("Payment URL has no vnp_CreateDate");
        }
        return value;
    }

    private Map<?, ?> callApi(Map<String, String> body, String command) {
        Map<?, ?> response;
        try {
            // Gửi JSON đã serialize (byte[]) để có Content-Length, không dùng chunked transfer với API VNPay.
            byte[] json = mapper.writeValueAsBytes(body);
            response = restClient.post().uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException | JsonProcessingException error) {
            log.warn("VNPay {} call failed: {}", command, error.getClass().getSimpleName());
            throw unavailable();
        }
        if (response == null) {
            throw unavailable();
        }
        return response;
    }

    /** Tham số vnp_* khác rỗng (trừ chữ ký), sắp xếp theo tên, giá trị URL-encode như mẫu Java của VNPay 2.1.0. */
    static String hashData(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        new TreeMap<>(params).forEach((key, value) -> {
            if (key.startsWith("vnp_") && !SECURE_HASH.equals(key) && !SECURE_HASH_TYPE.equals(key)
                    && value != null && !value.isEmpty()) {
                joiner.add(encode(key) + "=" + encode(value));
            }
        });
        return joiner.toString();
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA512 unavailable", error);
        }
    }

    private static boolean constantTimeEquals(String expectedHex, String givenHex) {
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                givenHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "VNPay is unavailable, please retry");
    }
}
