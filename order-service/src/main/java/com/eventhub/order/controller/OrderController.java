package com.eventhub.order.controller;

import com.eventhub.order.dto.OrderPageResponse;
import com.eventhub.order.dto.OrderResponse;
import com.eventhub.order.dto.CreateOrderRequest;
import com.eventhub.order.service.OrderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/me")
    public OrderPageResponse getMyOrders(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
        }

        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        var orders = orderService.getCustomerOrders(customerId, pageable)
                .map(OrderResponse::from);
        return OrderPageResponse.from(orders);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @PathVariable UUID id
    ) {
        return OrderResponse.from(orderService.getCustomerOrder(customerId, id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.status(201)
                .body(OrderResponse.from(orderService.createOrder(customerId, request)));
    }
}
