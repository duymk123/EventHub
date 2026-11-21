package com.eventhub.order.controller;

import com.eventhub.order.common.Actor;
import com.eventhub.order.dto.request.CreateOrderRequest;
import com.eventhub.order.dto.response.OrderResponse;
import com.eventhub.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Đơn hàng của CUSTOMER đang đăng nhập; customerId lấy từ JWT (Gateway giữ nguyên prefix /api/v1).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal Actor actor, @Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(actor, request);
    }

    /** Mặc định createdAt,desc rồi id,desc; size tối đa 100. */
    @GetMapping("/me")
    public Page<OrderResponse> getMine(@AuthenticationPrincipal Actor actor,
                                       @PageableDefault(size = 20)
                                       @SortDefault.SortDefaults({
                                               @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC),
                                               @SortDefault(sort = "id", direction = Sort.Direction.DESC)})
                                       Pageable pageable) {
        return orderService.getMine(actor, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse getDetail(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return orderService.getDetail(actor, id);
    }
}
