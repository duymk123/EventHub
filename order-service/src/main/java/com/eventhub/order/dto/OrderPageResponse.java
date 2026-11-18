package com.eventhub.order.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static OrderPageResponse from(Page<OrderResponse> page) {
        return new OrderPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
