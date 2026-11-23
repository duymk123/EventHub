package com.eventhub.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Lỗi phát sinh tại Gateway (plan api-gateway §5): service chưa chạy / không kết nối được → 503
 * SERVICE_UNAVAILABLE; quá thời gian chờ → 504 GATEWAY_TIMEOUT (không khẳng định downstream đã rollback);
 * không có route → 404 ROUTE_NOT_FOUND. Response lỗi hợp lệ của service không đi qua đây nên giữ nguyên.
 * Chạy trước handler mặc định của Spring Boot (order -2).
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        if (hasCause(error, ConnectException.class) || hasCause(error, UnknownHostException.class)
                || hasCause(error, io.netty.channel.ConnectTimeoutException.class)) {
            log.warn("Downstream unavailable for {}: {}", exchange.getRequest().getPath().value(),
                    error.getClass().getSimpleName());
            return GatewayErrorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Service is temporarily unavailable, please retry");
        }
        if (hasCause(error, TimeoutException.class)) {
            return timeout(exchange);
        }
        if (error instanceof ResponseStatusException status) {
            HttpStatusCode code = status.getStatusCode();
            if (code.value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                return timeout(exchange);
            }
            if (code.value() == HttpStatus.NOT_FOUND.value()) {
                return GatewayErrorWriter.write(exchange, code, "ROUTE_NOT_FOUND", "No route for this path");
            }
            if (code.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                return GatewayErrorWriter.write(exchange, code, "SERVICE_UNAVAILABLE",
                        "Service is temporarily unavailable, please retry");
            }
            if (code.is4xxClientError()) {
                return GatewayErrorWriter.write(exchange, code, "BAD_REQUEST",
                        status.getReason() == null ? "Bad request" : status.getReason());
            }
        }
        log.error("Gateway error on {}", exchange.getRequest().getPath().value(), error);
        return GatewayErrorWriter.write(exchange, HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "Upstream error");
    }

    private static Mono<Void> timeout(ServerWebExchange exchange) {
        return GatewayErrorWriter.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
                "Service did not respond in time; the operation may or may not have completed");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
