package com.eventhub.order.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phản hồi IPN theo định dạng VNPay: RspCode 00 xác nhận, 01 không thấy đơn, 02 đã xử lý, 04 sai số tiền,
 * 97 sai chữ ký, 99 lỗi khác.
 */
public record VnpayIpnAck(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {
}
