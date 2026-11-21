package com.eventhub.event.service;

import com.eventhub.event.dto.request.CategoryRequest;
import com.eventhub.event.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Danh mục sự kiện (plan Event §3). Ghi chỉ ADMIN (SecurityConfig), đọc công khai.
 */
public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    List<CategoryResponse> list();

    CategoryResponse update(UUID categoryId, CategoryRequest request);

    /** Chỉ xóa danh mục chưa có event tham chiếu; có event → CATEGORY_IN_USE. */
    void deleteUnused(UUID categoryId);
}
