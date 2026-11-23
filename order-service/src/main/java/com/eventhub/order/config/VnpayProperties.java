package com.eventhub.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình VNPay sandbox (C06), lấy từ biến môi trường VNPAY_*. Không có toString để hash secret không lọt vào log.
 */
@ConfigurationProperties(prefix = "eventhub.vnpay")
public class VnpayProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String payUrl = "";
    private String apiUrl = "";
    private String returnUrl = "";
    private String version = "2.1.0";
    private String locale = "vn";

    /** Thiếu mã website hoặc secret thì không tạo được thanh toán (PAYMENT_PROVIDER_UNAVAILABLE). */
    public boolean isConfigured() {
        return !isBlank(tmnCode) && !isBlank(hashSecret) && !isBlank(payUrl) && !isBlank(returnUrl);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getTmnCode() {
        return tmnCode;
    }

    public void setTmnCode(String tmnCode) {
        this.tmnCode = tmnCode;
    }

    public String getHashSecret() {
        return hashSecret;
    }

    public void setHashSecret(String hashSecret) {
        this.hashSecret = hashSecret;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
