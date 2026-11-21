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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Đọc danh sách/chi tiết tài khoản cho ADMIN.
 *
 * <p>Mọi điều kiện lọc đều đẩy xuống Keycloak, không duyệt danh sách ở tầng Java:
 * <ul>
 *   <li>{@code search} → tham số {@code search} của {@code /users}</li>
 *   <li>trạng thái → tham số {@code enabled=true|false} của {@code /users} và {@code /users/count}</li>
 *   <li>role → {@code /roles/{role}/users} và {@code /roles/{role}/users/count}</li>
 * </ul>
 * Nhờ vậy {@code totalElements} luôn là tổng thật của điều kiện lọc, không phải tổng của cả realm.
 *
 * <p>Chỉ còn một chỗ phải duyệt: {@code /roles/{role}/users} không nhận {@code search}, nên khi có cả role và từ
 * khóa thì phải lấy các trang của role rồi lọc tên tại chỗ (xem {@link #listByRoleAndSearch}).
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
        Boolean enabled = enabledFilter(query.status());
        if (query.role() != null && query.search() != null && !query.search().isBlank()) {
            return listByRoleAndSearch(query, enabled);
        }
        if (query.role() != null) {
            List<KeycloakUserDetail> page =
                    userClient.listUsersByRole(query.role().name(), enabled, query.first(), query.size());
            int total = userClient.countUsersByRole(query.role().name(), enabled);
            return PageResponse.of(toResponses(page, query.role().name()), query.page(), query.size(), total);
        }
        List<KeycloakUserDetail> page = userClient.searchUsers(query.search(), enabled, query.first(), query.size());
        int total = userClient.countUsers(query.search(), enabled);
        return PageResponse.of(toResponses(page, null), query.page(), query.size(), total);
    }

    /**
     * Role + từ khóa: {@code /roles/{role}/users} không nhận {@code search}, nên lấy tối đa {@link #MAX_SCAN} user
     * của role rồi lọc tên tại chỗ. Chỉ chạm tới nhánh này khi ADMIN gõ cả hai điều kiện cùng lúc.
     */
    private PageResponse<UserDetailResponse> listByRoleAndSearch(UserListQuery query, Boolean enabled) {
        String needle = query.search().trim().toLowerCase(Locale.ROOT);
        List<KeycloakUserDetail> matched = new ArrayList<>();
        int scanned = 0;
        while (scanned < MAX_SCAN) {
            List<KeycloakUserDetail> batch =
                    userClient.listUsersByRole(query.role().name(), enabled, scanned, SCAN_BATCH);
            for (KeycloakUserDetail user : batch) {
                if (matchesSearch(user, needle)) {
                    matched.add(user);
                }
            }
            scanned += batch.size();
            if (batch.size() < SCAN_BATCH) {
                break;
            }
        }
        if (scanned >= MAX_SCAN) {
            log.warn("Role+search scan stopped at {} records for query {}", MAX_SCAN, query);
        }
        int from = Math.min(query.first(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return PageResponse.of(toResponses(matched.subList(from, to), query.role().name()),
                query.page(), query.size(), matched.size());
    }

    /** Quy ước lọc trạng thái của API admin → tham số enabled của Keycloak; null nghĩa là không lọc. */
    private static Boolean enabledFilter(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "ACTIVE" -> Boolean.TRUE;
            case "LOCKED" -> Boolean.FALSE;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "status: must be ACTIVE or LOCKED");
        };
    }

    private static boolean matchesSearch(KeycloakUserDetail user, String needle) {
        return contains(user.username(), needle) || contains(user.email(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Map cả trang. Khi lọc theo role thì role đã biết sẵn từ endpoint đã gọi — không cần thêm một lệnh
     * {@code /role-mappings} cho từng user nữa.
     */
    private List<UserDetailResponse> toResponses(List<KeycloakUserDetail> users, String knownRole) {
        if (users.isEmpty()) {
            return List.of();
        }
        Map<String, String> fullNames = profiles.findAllByKeycloakSubIn(
                        users.stream().map(KeycloakUserDetail::id).toList()).stream()
                .collect(Collectors.toMap(UserProfile::getKeycloakSub, UserProfile::getFullName, (a, b) -> a));
        return users.stream().map(user -> toResponse(user, knownRole, fullNames.get(user.id()))).toList();
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

    @Override
    public UserDetailResponse getUserDetail(UUID userId) {
        KeycloakUserDetail user = userClient.getUserDetail(userId);
        List<String> roles = userClient.effectiveRealmRoles(userId);
        String role = BUSINESS_ROLES.stream().filter(roles::contains).findFirst().orElse(null);
        String fullName = profiles.findByKeycloakSub(user.id()).map(UserProfile::getFullName).orElse(null);
        return toResponse(user, role, fullName);
    }

    /**
     * Dựng DTO. {@code knownRole} và {@code knownFullName} do caller chuẩn bị sẵn theo lô — hàm này không gọi
     * Keycloak hay DB, nhờ vậy một trang N user chỉ tốn O(1) lệnh gọi thay vì O(N).
     */
    private static UserDetailResponse toResponse(KeycloakUserDetail user, String knownRole, String knownFullName) {
        String fullName = knownFullName != null ? knownFullName : joinName(user.lastName(), user.firstName());
        return new UserDetailResponse(UUID.fromString(user.id()), user.username(), user.email(), fullName, knownRole,
                user.enabled() ? "ACTIVE" : "LOCKED", user.emailVerified(),
                user.requiredActions() == null ? List.of() : List.copyOf(user.requiredActions()),
                user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp()));
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
