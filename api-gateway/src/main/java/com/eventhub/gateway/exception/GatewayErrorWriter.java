package com.eventhub.gateway.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Ghi lỗi do chính Gateway sinh theo format chung {code, message, timestamp, path} (docs/plans/00-cross-service-
 * contracts.md). Không lộ host downstream, stacktrace hay credential. Lỗi hợp lệ của service đi qua nguyên vẹn,
 * không qua lớp này.
 */
public final class GatewayErrorWriter {
    private GatewayErrorWriter() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + escape(message) + "\",\"timestamp\":\""
                + Instant.now() + "\",\"path\":\"" + escape(exchange.getRequest().getPath().value()) + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
