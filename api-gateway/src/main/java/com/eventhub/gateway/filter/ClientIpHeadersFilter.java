package com.eventhub.gateway.filter;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Gateway 4.3 không có trusted-proxies thì RemoveForwardedHeadersFilter xóa mọi Forwarded/X-Forwarded-* khi gửi
 * xuống service. Filter này chạy sau cùng và đặt lại X-Forwarded-For = IP kết nối thật (do RequestContextWebFilter
 * ghi nhận), để Order có vnp_IpAddr đúng. Không bao giờ dùng giá trị client tự gửi.
 */
@Component
public class ClientIpHeadersFilter implements HttpHeadersFilter, Ordered {

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        String clientIp = exchange.getAttribute(RequestContextWebFilter.CLIENT_IP_ATTRIBUTE);
        if (clientIp == null) {
            return input;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(input);
        headers.set("X-Forwarded-For", clientIp);
        return headers;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
