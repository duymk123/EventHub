package com.eventhub.order.controller;

import com.eventhub.order.dto.OrderPageResponse;
import com.eventhub.order.dto.OrderResponse;
import com.eventhub.order.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
        var orders = orderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable)
                .map(OrderResponse::from);
        return OrderPageResponse.from(orders);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(
            @RequestHeader(USER_ID_HEADER) UUID customerId,
            @PathVariable UUID id
    ) {
        return orderRepository.findByIdAndCustomerId(id, customerId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @PostMapping
    public ResponseEntity<Void> createOrder() {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Order creation requires Ticket service reservation and server-side pricing"
        );
    }
}
