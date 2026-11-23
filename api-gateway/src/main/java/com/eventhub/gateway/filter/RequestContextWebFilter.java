package com.eventhub.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

/**
 * Chạy đầu tiên, trước Spring Security (plan api-gateway §4 bước 1):
 * <ul>
 *   <li>Sinh X-Request-Id mới cho mỗi request (không nhận giá trị client), gắn vào request đi xuống và response,
 *   kể cả response lỗi 401/403.</li>
 *   <li>Xóa header danh tính/proxy do client tự khai (X-User-Id, X-Role, X-Forwarded-*, Forwarded...).</li>
 *   <li>Ghi nhận IP kết nối thật; ClientIpHeadersFilter đặt X-Forwarded-For từ đó, Order dùng làm vnp_IpAddr.</li>
 * </ul>
 * Khi đặt Gateway sau reverse proxy/TLS (nginx…), cần đổi sang tin X-Forwarded-For của proxy đó (plan §4, G6).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextWebFilter implements WebFilter {
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestContextWebFilter.class.getName() + ".requestId";
    public static final String CLIENT_IP_ATTRIBUTE = RequestContextWebFilter.class.getName() + ".clientIp";

    private static final List<String> UNTRUSTED_HEADERS = List.of(REQUEST_ID, "X-User-Id", "X-User-Email",
            "X-User-Roles", "X-Role", "X-Roles", "X-Real-IP", "Forwarded", "X-Forwarded-For", "X-Forwarded-Host",
            "X-Forwarded-Proto", "X-Forwarded-Port", "X-Forwarded-Prefix");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String clientIp = remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();

        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            UNTRUSTED_HEADERS.forEach(headers::remove);
            headers.set(REQUEST_ID, requestId);
        }).build();
        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        responseHeaders.set(REQUEST_ID, requestId);
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        if (clientIp != null) {
            // ClientIpHeadersFilter đặt X-Forwarded-For từ giá trị này lúc gửi xuống service.
            exchange.getAttributes().put(CLIENT_IP_ATTRIBUTE, clientIp);
        }
        return chain.filter(exchange.mutate().request(request).build());
    }
}
