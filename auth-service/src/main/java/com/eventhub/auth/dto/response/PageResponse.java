package com.eventhub.auth.dto.response;

import java.util.List;
import java.util.function.Function;

/**
 * Một trang kết quả cho API danh sách. page bắt đầu từ 0.
 *
 * @param totalElements tổng số bản ghi khớp điều kiện, không phải số bản ghi trong trang.
 * @param totalPages số trang suy ra từ totalElements và size.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /** Đổi kiểu phần tử nhưng giữ nguyên thông tin phân trang, dùng sau khi lọc lại danh sách. */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
