package com.eventhub.auth.dto.request;

import com.eventhub.auth.common.BusinessRole;

/**
 * Tham số lọc của GET /api/v1/admin/users. Truyền qua query string nên không dùng bean validation mà chuẩn hóa
 * giá trị trong controller (page âm, size quá lớn được kẹp về khoảng hợp lệ).
 *
 * @param page số trang, bắt đầu từ 0.
 * @param size số bản ghi mỗi trang, tối đa {@link #MAX_SIZE}.
 * @param search chuỗi tìm theo email/họ tên/username; null hoặc rỗng là không lọc.
 * @param role chỉ lấy user mang đúng business role này.
 * @param status ACTIVE hoặc LOCKED.
 */
public record UserListQuery(int page, int size, String search, BusinessRole role, String status) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    /** Chuẩn hóa giá trị từ query string về khoảng an toàn. */
    public static UserListQuery of(Integer page, Integer size, String search, BusinessRole role, String status) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        String safeStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (safeStatus != null && !safeStatus.equals("ACTIVE") && !safeStatus.equals("LOCKED")) {
            safeStatus = null;
        }
        return new UserListQuery(safePage, safeSize, search, role, safeStatus);
    }

    /** Số bản ghi bỏ qua, dùng làm tham số first của Keycloak. */
    public int first() {
        return page * size;
    }
}
