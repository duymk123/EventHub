package com.eventhub.order.controller;

import jakarta.servlet.http.HttpServletRequest;

/** vnp_IpAddr: IP người thao tác; sau Gateway lấy phần tử đầu của X-Forwarded-For. */
final class ClientIp {
    private ClientIp() {
    }

    static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }
}
