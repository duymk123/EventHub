package com.eventhub.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Một dòng log cho mỗi request đã khớp route: requestId, method, path (không query vì IPN chứa chữ ký), route,
 * status, thời gian. Không ghi header Authorization, cookie hay body.
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger("eventhub.gateway.access");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            var status = exchange.getResponse().getStatusCode();
            log.info("requestId={} {} {} route={} status={} {}ms",
                    exchange.getAttribute(RequestContextWebFilter.REQUEST_ID_ATTRIBUTE),
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                    route == null ? "-" : route.getId(), status == null ? "-" : status.value(),
                    (System.nanoTime() - start) / 1_000_000);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
