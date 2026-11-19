package com.eventhub.event.service.impl;

import com.eventhub.event.common.ErrorCode;
import com.eventhub.event.dto.request.CategoryRequest;
import com.eventhub.event.dto.response.CategoryResponse;
import com.eventhub.event.entity.Category;
import com.eventhub.event.exception.BusinessException;
import com.eventhub.event.repository.CategoryRepository;
import com.eventhub.event.repository.EventRepository;
import com.eventhub.event.service.CategoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Kiểm tra trùng tên/đang dùng trước để trả lỗi rõ; UNIQUE(name) và FK events.category_id ở DB là chốt cuối khi
 * hai request chạy song song.
 */
@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categories;
    private final EventRepository events;

    public CategoryServiceImpl(CategoryRepository categories, EventRepository events) {
        this.categories = categories;
        this.events = events;
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = normalize(request.name());
        if (categories.existsByName(name)) {
            throw nameExists();
        }
        Category category = Category.builder().name(name).description(blankToNull(request.description())).build();
        try {
            return CategoryResponse.from(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException error) {
            throw nameExists();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllByOrderByNameAscIdAsc().stream().map(CategoryResponse::from).toList();
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID categoryId, CategoryRequest request) {
        Category category = categories.findById(categoryId).orElseThrow(CategoryServiceImpl::notFound);
        String name = normalize(request.name());
        if (categories.existsByNameAndIdNot(name, categoryId)) {
            throw nameExists();
        }
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        try {
            return CategoryResponse.from(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException error) {
            throw nameExists();
        }
    }

    @Override
    @Transactional
    public void deleteUnused(UUID categoryId) {
        Category category = categories.findById(categoryId).orElseThrow(CategoryServiceImpl::notFound);
        if (events.existsByCategoryId(categoryId)) {
            throw inUse();
        }
        try {
            categories.delete(category);
            categories.flush();
        } catch (DataIntegrityViolationException error) {
            // Event được tạo cùng lúc với lệnh xóa: FK RESTRICT chặn.
            throw inUse();
        }
    }

    private static String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found");
    }

    private static BusinessException nameExists() {
        return new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS, "Category name already exists");
    }

    private static BusinessException inUse() {
        return new BusinessException(ErrorCode.CATEGORY_IN_USE, "Category is used by at least one event");
    }
}
