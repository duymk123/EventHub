package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakUserDetail;
import com.eventhub.auth.common.BusinessRole;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.request.AdminResetPasswordRequest;
import com.eventhub.auth.dto.request.UserListQuery;
import com.eventhub.auth.dto.response.PageResponse;
import com.eventhub.auth.dto.response.TemporaryPasswordResponse;
import com.eventhub.auth.dto.response.UserDetailResponse;
import com.eventhub.auth.entity.UserProfile;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.repository.UserProfileRepository;
import com.eventhub.auth.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Đọc danh sách/chi tiết tài khoản cho ADMIN.
 *
 * <p>Keycloak Admin API tìm được theo từ khóa nhưng lọc theo business role phải suy ra từ role mapping của từng
 * user, nên nếu có lọc role thì duyệt theo trang cho tới khi đủ số bản ghi cần. Giới hạn số trang duyệt để một
 * bộ lọc hiếm không kéo theo hàng nghìn lệnh gọi mạng.
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {
    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

    /** Số user lấy mỗi lượt khi phải duyệt để lọc theo role. */
    private static final int SCAN_BATCH = 100;
    /** Trần số bản ghi duyệt cho một truy vấn có lọc role. */
    private static final int MAX_SCAN = 2000;
    private static final List<String> BUSINESS_ROLES = List.of("ADMIN", "ORGANIZER", "CUSTOMER");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeycloakUserClient userClient;
    private final UserProfileRepository profiles;

    public AdminUserServiceImpl(KeycloakUserClient userClient, UserProfileRepository profiles) {
        this.userClient = userClient;
        this.profiles = profiles;
    }

    @Override
    public PageResponse<UserDetailResponse> listUsers(UserListQuery query) {
        if (query.role() != null) {
            return listFilteredByRole(query);
        }
        if (query.status() != null) {
            // Trạng thái là enabled/disabled và Keycloak không lọc được bằng query param, nên phải duyệt để đếm
            // đúng. Đếm theo trang của Keycloak sẽ ra tổng của mọi user chứ không phải tổng user bị khóa.
            return listFilteredByStatus(query);
        }
        List<KeycloakUserDetail> page = userClient.searchUsers(query.search(), query.first(), query.size());
        int total = userClient.countUsers(query.search());
        return PageResponse.of(page.stream().map(this::toResponse).toList(), query.page(), query.size(), total);
    }

    /** Lọc theo trạng thái: duyệt toàn bộ (có giới hạn) để totalElements đúng với điều kiện lọc. */
    private PageResponse<UserDetailResponse> listFilteredByStatus(UserListQuery query) {
        List<UserDetailResponse> matched = collectMatching(query, user -> matchesStatus(user, query.status()));
        return slice(matched, query);
    }

    /**
     * Lọc theo business role cần đọc role của từng user. Duyệt theo lô từ đầu danh sách, giữ những user khớp, rồi
     * cắt ra đúng trang yêu cầu.
     */
    private PageResponse<UserDetailResponse> listFilteredByRole(UserListQuery query) {
        List<UserDetailResponse> matched = collectMatching(query,
                user -> query.role().name().equals(user.role()) && matchesStatus(user, query.status()));
        return slice(matched, query);
    }

    /**
     * Duyệt user theo lô (tối đa {@link #MAX_SCAN} bản ghi) và giữ những bản ghi thỏa điều kiện. Duyệt hết để
     * totalElements phản ánh đúng số user khớp, không phải số user trên trang của Keycloak.
     */
    private List<UserDetailResponse> collectMatching(UserListQuery query,
                                                     java.util.function.Predicate<UserDetailResponse> condition) {
        List<UserDetailResponse> matched = new java.util.ArrayList<>();
        int scanned = 0;
        while (scanned < MAX_SCAN) {
            List<KeycloakUserDetail> batch = userClient.searchUsers(query.search(), scanned, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (KeycloakUserDetail user : batch) {
                UserDetailResponse response = toResponse(user);
                if (condition.test(response)) {
                    matched.add(response);
                }
            }
            scanned += batch.size();
            if (batch.size() < SCAN_BATCH) {
                break;
            }
        }
        if (scanned >= MAX_SCAN) {
            log.warn("User list scan stopped at {} records for query {}", MAX_SCAN, query);
        }
        return matched;
    }

    /** Cắt đúng trang yêu cầu từ danh sách đã lọc. */
    private static PageResponse<UserDetailResponse> slice(List<UserDetailResponse> matched, UserListQuery query) {
        int from = Math.min(query.first(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return PageResponse.of(List.copyOf(matched.subList(from, to)), query.page(), query.size(), matched.size());
    }

    @Override
    public UserDetailResponse getUserDetail(UUID userId) {
        return toResponse(userClient.getUserDetail(userId));
    }

    @Override
    public TemporaryPasswordResponse resetPassword(UUID adminId, UUID userId, AdminResetPasswordRequest request) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT, "Admins cannot modify their own account");
        }
        userClient.getUser(userId);
        if (request.action() == AdminResetPasswordRequest.Action.SEND_EMAIL) {
            userClient.sendUpdatePasswordEmail(userId);
            return new TemporaryPasswordResponse(userId, null);
        }
        String temporary = temporaryPassword();
        userClient.resetPasswordTemporarily(userId, temporary);
        // Thu hồi phiên cũ: mật khẩu vừa đổi, người đang giữ refresh token cũ không được tiếp tục phiên đó.
        userClient.logoutAllSessions(userId);
        log.info("Admin {} set a temporary password for user {}", adminId, userId);
        return new TemporaryPasswordResponse(userId, temporary);
    }

    private UserDetailResponse toResponse(KeycloakUserDetail user) {
        UUID id = UUID.fromString(user.id());
        List<String> roles = userClient.effectiveRealmRoles(id);
        String role = BUSINESS_ROLES.stream().filter(roles::contains).findFirst().orElse(null);
        String fullName = profiles.findByKeycloakSub(user.id())
                .map(UserProfile::getFullName)
                .orElseGet(() -> joinName(user.lastName(), user.firstName()));
        return new UserDetailResponse(id, user.username(), user.email(), fullName, role,
                user.enabled() ? "ACTIVE" : "LOCKED", user.emailVerified(),
                user.requiredActions() == null ? List.of() : List.copyOf(user.requiredActions()),
                user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp()));
    }

    private static boolean matchesStatus(UserDetailResponse user, String status) {
        return status == null || status.equals(user.status());
    }

    private static String joinName(String lastName, String firstName) {
        return ((lastName == null ? "" : lastName) + " " + (firstName == null ? "" : firstName)).trim();
    }

    /**
     * Mật khẩu tạm đủ mạnh cho chính sách realm: chữ hoa, chữ thường, số và ký tự đặc biệt, dài 16 ký tự.
     * Không dùng ký tự dễ nhầm khi đọc lại (0/O, 1/l/I) để ADMIN chép cho user không sai.
     */
    static String temporaryPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String symbols = "!@#$%&*?";
        String all = upper + lower + digits + symbols;
        StringBuilder password = new StringBuilder(16);
        password.append(pick(upper)).append(pick(lower)).append(pick(digits)).append(pick(symbols));
        while (password.length() < 16) {
            password.append(pick(all));
        }
        // Xáo để vị trí nhóm ký tự không đoán được.
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
