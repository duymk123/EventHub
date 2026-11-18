package com.eventhub.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderExpiryScheduler {

    private final OrderService orderService;

    public OrderExpiryScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${order.expiry-delay-ms:10000}")
    public void expireOrders() {
        orderService.expirePendingOrders(Instant.now());
    }
}
