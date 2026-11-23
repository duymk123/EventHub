package com.eventhub.auth.service;

import com.eventhub.auth.dto.request.AdminResetPasswordRequest;
import com.eventhub.auth.dto.request.UserListQuery;
import com.eventhub.auth.dto.response.PageResponse;
import com.eventhub.auth.dto.response.TemporaryPasswordResponse;
import com.eventhub.auth.dto.response.UserDetailResponse;

import java.util.UUID;

/**
 * Truy vấn và thao tác tài khoản cho trang quản trị (chỉ ADMIN, SecurityConfig chặn /api/v1/admin/**).
 * Khóa/mở khóa và đổi role vẫn nằm ở {@link AuthService} như trước.
 */
public interface AdminUserService {

    /** Danh sách tài khoản có phân trang và lọc theo từ khóa, role, trạng thái. */
    PageResponse<UserDetailResponse> listUsers(UserListQuery query);

    /** Chi tiết một tài khoản; không tồn tại → USER_NOT_FOUND. */
    UserDetailResponse getUserDetail(UUID userId);

    /**
     * Đặt lại mật khẩu của user khác: gửi mail cho user hoặc trả mật khẩu tạm một lần.
     * ADMIN không được thao tác trên chính mình (CANNOT_MODIFY_OWN_ACCOUNT).
     */
    TemporaryPasswordResponse resetPassword(UUID adminId, UUID userId, AdminResetPasswordRequest request);
}
