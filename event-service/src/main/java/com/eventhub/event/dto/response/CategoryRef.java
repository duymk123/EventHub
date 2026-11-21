package com.eventhub.event.dto.response;

import com.eventhub.event.entity.Category;

import java.util.UUID;

public record CategoryRef(UUID id, String name) {
    public static CategoryRef from(Category category) {
        return new CategoryRef(category.getId(), category.getName());
    }
}
