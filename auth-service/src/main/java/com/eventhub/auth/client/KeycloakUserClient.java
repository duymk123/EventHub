package com.eventhub.auth.client;

import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Gọi Keycloak Admin REST API bằng token của service account (cần quyền realm-management manage-users,
 * view-users). Mật khẩu chỉ đi thẳng tới Keycloak, không lưu ở Auth Service.
 */
@Component
public class KeycloakUserClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KeycloakUserClient.class);

    /** Trần số bản ghi khi đếm user theo role (Keycloak không có endpoint count cho role). */
    private static final int COUNT_MAX = 5000;

    /** User Keycloak, chỉ các trường Auth Service cần. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakUser(String id, String email, String firstName, String lastName, boolean enabled,
                               boolean emailVerified, Long createdTimestamp) {}

    /**
     * User đầy đủ cho trang quản trị: thêm username và danh sách required action đang chờ (vd VERIFY_EMAIL).
     * Admin API không trả họ tên ghép nên caller tự ghép từ firstName/lastName.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakUserDetail(String id, String username, String email, String firstName, String lastName,
                                     boolean enabled, boolean emailVerified, Long createdTimestamp,
                                     List<String> requiredActions) {}

    /**
     * Một phiên đăng nhập do Keycloak quản lý. clients là map clientId → tên client, không phải số lượng, nên giữ
     * dạng Map để Jackson không lỗi khi user đăng nhập qua nhiều client.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakSession(String id, String username, String ipAddress, Long start, Long lastAccess,
                                  Map<String, String> clients) {}

    /** Role realm; Admin API cần cả id và name khi thêm/xóa role mapping. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleRepresentation(String id, String name) {}

    private final RestClient restClient;
    private final KeycloakTokenClient tokens;
    private final String usersPath;
    private final String realm;

    public KeycloakUserClient(@Value("${eventhub.keycloak.server-url}") String serverUrl,
                              @Value("${eventhub.keycloak.realm}") String realm,
                              KeycloakTokenClient tokens) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(serverUrl).requestFactory(factory).build();
        this.tokens = tokens;
        this.usersPath = "/admin/realms/" + realm + "/users";
        this.realm = realm;
    }

    /**
     * Chạy một lệnh Admin API với token quản trị; nếu Keycloak trả 401 thì xin token mới và thử lại đúng một lần.
     */
    private <T> T withAdminToken(Function<String, T> call) {
        try {
            return call.apply(tokens.adminToken());
        } catch (HttpClientErrorException.Unauthorized error) {
            tokens.invalidateAdminToken();
            return call.apply(tokens.adminToken());
        }
    }

    /**
     * Tạo user đã bật, email dùng làm username. Đăng ký qua OTP đã chứng minh sở hữu email nên truyền
     * emailVerified=true (Keycloak không gửi thêm mail xác minh).
     * Role nghiệp vụ không gán ở đây: realm không còn đưa CUSTOMER vào role mặc định, caller gán trực tiếp.
     * Email trùng → EMAIL_ALREADY_EXISTS; mật khẩu không đạt chính sách realm → VALIDATION_ERROR.
     *
     * @return id user mới (cũng là sub trong JWT).
     */
    public UUID createUser(String email, String password, String firstName, String lastName, boolean emailVerified) {
        Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", emailVerified,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        try {
            ResponseEntity<Void> response = withAdminToken(token -> restClient.post().uri(usersPath)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity());
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw KeycloakTokenClient.unavailable();
            }
            String path = location.getPath();
            return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        } catch (HttpClientErrorException.Conflict error) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        } catch (HttpClientErrorException.BadRequest error) {
            // Body lỗi của Keycloak chỉ chứa tên trường và mã lỗi (vd error-person-name-invalid-character), không có mật khẩu.
            log.warn("Keycloak rejected new user data: {}", error.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Account data rejected by identity provider");
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Đọc user theo id; không tồn tại → USER_NOT_FOUND.
     */
    public KeycloakUser getUser(UUID userId) {
        try {
            KeycloakUser user = withAdminToken(token -> restClient.get().uri(usersPath + "/{id}", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUser.class));
            if (user == null) {
                throw KeycloakTokenClient.unavailable();
            }
            return user;
        } catch (HttpClientErrorException.NotFound error) {
            throw notFound();
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Xóa user, dùng để hoàn tác khi đăng ký tạo được user Keycloak nhưng không hoàn tất được các bước sau. Lỗi
     * được nuốt để không che lỗi gốc; caller ghi log.
     */
    public boolean deleteUserQuietly(UUID userId) {
        try {
            withAdminToken(token -> restClient.delete().uri(usersPath + "/{id}", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity());
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Tên các realm role hiệu lực của user (đã mở rộng composite), dùng để xác định business role.
     */
    public List<String> effectiveRealmRoles(UUID userId) {
        return roles(userId, "/role-mappings/realm/composite").stream().map(RoleRepresentation::name).toList();
    }

    /** Realm role gán trực tiếp cho user (không mở rộng composite). */
    public List<RoleRepresentation> directRealmRoles(UUID userId) {
        return roles(userId, "/role-mappings/realm");
    }

    /**
     * Realm role còn gán được cho user. Dùng endpoint này thay vì GET /roles/{name} để service account chỉ cần
     * manage-users, không cần view-realm.
     */
    public List<RoleRepresentation> availableRealmRoles(UUID userId) {
        return roles(userId, "/role-mappings/realm/available");
    }

    public void addRealmRoles(UUID userId, Collection<RoleRepresentation> roles) {
        if (!roles.isEmpty()) {
            send(userId, "/role-mappings/realm", "POST", List.copyOf(roles));
        }
    }

    public void removeRealmRoles(UUID userId, Collection<RoleRepresentation> roles) {
        if (!roles.isEmpty()) {
            send(userId, "/role-mappings/realm", "DELETE", List.copyOf(roles));
        }
    }

    /** Bật/tắt tài khoản; Keycloak từ chối đăng nhập và refresh của user bị tắt. */
    public void setEnabled(UUID userId, boolean enabled) {
        send(userId, "", "PUT", Map.of("enabled", enabled));
    }

    /** Thu hồi mọi phiên (refresh token) của user; access token đã cấp vẫn còn hạn tới khi hết 5 phút. */
    public void logoutAllSessions(UUID userId) {
        send(userId, "/logout", "POST", null);
    }

    /**
     * Đặt mật khẩu mới (không tạm thời). Mật khẩu không đạt chính sách realm → VALIDATION_ERROR.
     */
    public void resetPassword(UUID userId, String newPassword) {
        send(userId, "/reset-password", "PUT", Map.of("type", "password", "value", newPassword, "temporary", false));
    }

    /**
     * Đặt mật khẩu TẠM THỜI: Keycloak bắt user đổi ở lần đăng nhập kế tiếp. Dùng khi ADMIN cần chuyển mật khẩu
     * cho user qua kênh khác; mật khẩu không đạt chính sách realm → VALIDATION_ERROR.
     */
    public void resetPasswordTemporarily(UUID userId, String temporaryPassword) {
        send(userId, "/reset-password", "PUT",
                Map.of("type", "password", "value", temporaryPassword, "temporary", true));
    }

    /**
     * Đọc user kèm username và required action, dùng cho trang quản trị. Không tồn tại → USER_NOT_FOUND.
     */
    public KeycloakUserDetail getUserDetail(UUID userId) {
        try {
            KeycloakUserDetail user = withAdminToken(token -> restClient.get().uri(usersPath + "/{id}", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUserDetail.class));
            if (user == null) {
                throw KeycloakTokenClient.unavailable();
            }
            return user;
        } catch (HttpClientErrorException.NotFound error) {
            throw notFound();
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Tìm user theo email chính xác. Dùng cho quên mật khẩu: không tìm thấy trả null để caller phản hồi giống hệt
     * trường hợp có tài khoản (chống dò email).
     */
    public KeycloakUser findByEmailExact(String email) {
        try {
            KeycloakUser[] users = withAdminToken(token -> restClient.get()
                    .uri(uri -> uri.path(usersPath).queryParam("email", email).queryParam("exact", true)
                            .queryParam("max", 1).build())
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUser[].class));
            return users == null || users.length == 0 ? null : users[0];
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Một trang user cho trang quản trị, đẩy hết điều kiện lọc xuống Keycloak.
     *
     * <p>Keycloak hỗ trợ sẵn:
     * <ul>
     *   <li>{@code search} — khớp username/email/họ tên (khớp theo prefix)</li>
     *   <li>{@code enabled=true|false} — lọc theo trạng thái</li>
     * </ul>
     * Còn lọc theo business role thì dùng {@link #listUsersByRole(String, Boolean, int, int)} vì role mapping không
     * phải thuộc tính của user.
     *
     * @param first vị trí bắt đầu (0-based), max số bản ghi mỗi trang.
     */
    public List<KeycloakUserDetail> searchUsers(String search, Boolean enabled, int first, int max) {
        try {
            KeycloakUserDetail[] users = withAdminToken(token -> restClient.get()
                    .uri(uri -> {
                        uri.path(usersPath).queryParam("first", first).queryParam("max", max)
                                .queryParam("briefRepresentation", false);
                        addUserFilters(uri, search, enabled);
                        return uri.build();
                    })
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUserDetail[].class));
            return users == null ? List.of() : List.of(users);
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /** Tổng số user khớp điều kiện lọc, lấy từ /users/count nên không phải tải toàn bộ user về đếm. */
    public int countUsers(String search, Boolean enabled) {
        try {
            Integer total = withAdminToken(token -> restClient.get()
                    .uri(uri -> {
                        uri.path(usersPath).path("/count");
                        addUserFilters(uri, search, enabled);
                        return uri.build();
                    })
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(Integer.class));
            return total == null ? 0 : total;
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Một trang user đang giữ realm role chỉ định.
     *
     * <p>Endpoint này <b>không</b> nhận {@code search} và cũng <b>bỏ qua</b> {@code enabled} (đã kiểm trên Keycloak
     * 26), nên hai điều kiện đó phải lọc tại chỗ. Caller chịu trách nhiệm giới hạn kích thước để không quét vô hạn.
     *
     * <p>Vì phải lọc tại chỗ nên {@code first}/{@code max} áp lên danh sách <i>sau khi lọc</i>, không phải tham số
     * phân trang của Keycloak.
     */
    public List<KeycloakUserDetail> listUsersByRole(String role, Boolean enabled, int first, int max) {
        try {
            KeycloakUserDetail[] users = withAdminToken(token -> restClient.get()
                    .uri(uri -> {
                        uri.path("/admin/realms/{realm}/roles/{role}/users")
                                .queryParam("first", 0).queryParam("max", COUNT_MAX)
                                .queryParam("briefRepresentation", false);
                        return uri.build(realm, role);
                    })
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUserDetail[].class));
            // briefRepresentation=false không được endpoint này tôn trọng ở mọi bản Keycloak: nếu thiếu
            // createdTimestamp (trường duy nhất không có trong brief representation) thì gọi lại từng user.
            List<KeycloakUserDetail> all = users == null ? List.of() : List.of(users);
            if (!all.stream().allMatch(KeycloakUserClient::hasDetail)) {
                all = fillDetails(all);
            }
            List<KeycloakUserDetail> filtered = enabled == null ? all
                    : all.stream().filter(user -> user.enabled() == enabled).toList();
            int from = Math.min(first, filtered.size());
            int to = Math.min(from + max, filtered.size());
            return filtered.subList(from, to);
        } catch (HttpClientErrorException.NotFound error) {
            throw new RoleNotFoundException(role);
        } catch (RestClientException error) {
            log.error("listUsersByRole({}) failed", role, error);
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Số user đang giữ realm role, cùng bộ lọc enabled.
     *
     * <p>Keycloak 26 <b>không có</b> {@code /roles/{role}/users/count} (trả 404), nên đếm bằng cách lấy danh sách
     * id với {@code briefRepresentation=true} và {@code max} lớn. Nhẹ hơn nhiều so với việc đọc từng user đầy đủ
     * rồi tự đếm ở tầng Java như trước.
     */
    public int countUsersByRole(String role, Boolean enabled) {
        try {
            KeycloakUserDetail[] users = withAdminToken(token -> restClient.get()
                    .uri(uri -> {
                        uri.path("/admin/realms/{realm}/roles/{role}/users")
                                .queryParam("first", 0).queryParam("max", COUNT_MAX)
                                .queryParam("briefRepresentation", true);
                        return uri.build(realm, role);
                    })
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakUserDetail[].class));
            int total = users == null ? 0
                    : (int) java.util.Arrays.stream(users)
                            .filter(user -> enabled == null || user.enabled() == enabled)
                            .count();
            if (total == COUNT_MAX) {
                log.warn("Role {} has at least {} users; totalElements may be under-reported", role, COUNT_MAX);
            }
            return total;
        } catch (HttpClientErrorException.NotFound error) {
            throw new RoleNotFoundException(role);
        } catch (RestClientException error) {
            log.error("countUsersByRole({}) failed", role, error);
            throw KeycloakTokenClient.unavailable();
        }
    }

    private static boolean hasDetail(KeycloakUserDetail user) {
        return user.createdTimestamp() != null;
    }

    private List<KeycloakUserDetail> fillDetails(List<KeycloakUserDetail> users) {
        return users.stream().map(user -> getUserDetail(UUID.fromString(user.id()))).toList();
    }

    private static void addUserFilters(UriBuilder uri, String search, Boolean enabled) {
        if (search != null && !search.isBlank()) {
            uri.queryParam("search", search.trim());
        }
        if (enabled != null) {
            uri.queryParam("enabled", enabled);
        }
    }

    /**
     * Nhờ Keycloak gửi mail đặt lại mật khẩu.
     *
     * <p><b>Không gắn requiredActions trước.</b> {@code execute-actions-email} đã mang sẵn danh sách action trong
     * link, Keycloak chỉ áp chúng khi user bấm link. Gắn trước thì user bị đặt vào trạng thái "chưa hoàn tất thiết
     * lập" NGAY LẬP TỨC, và vì endpoint forgot-password là công khai nên bất kỳ ai biết email cũng khoá được tài
     * khoản người khác (Keycloak trả {@code Account is not fully set up} dù mật khẩu đúng).
     */
    public void sendUpdatePasswordEmail(UUID userId) {
        send(userId, "/execute-actions-email", "PUT", List.of("UPDATE_PASSWORD"));
    }

    /** Phiên (phiên bản refresh token) đang hoạt động của user. */
    public List<KeycloakSession> sessions(UUID userId) {
        try {
            KeycloakSession[] sessions = withAdminToken(token -> restClient.get()
                    .uri(usersPath + "/{id}/sessions", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(KeycloakSession[].class));
            return sessions == null ? List.of() : List.of(sessions);
        } catch (HttpClientErrorException.NotFound error) {
            throw notFound();
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /** Thu hồi một phiên theo id; phiên đã hết hạn thì Keycloak không báo lỗi. */
    public void revokeSession(String sessionId) {
        try {
            withAdminToken(token -> restClient.delete().uri("/admin/realms/{realm}/sessions/{id}", realm, sessionId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound error) {
            // Phiên đã bị thu hồi hoặc hết hạn: coi như thành công để thao tác lặp lại không lỗi.
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Nhờ Keycloak gửi mail xác minh (SMTP cấu hình trong realm, dev dùng Mailpit). Link trỏ tới KC_HOSTNAME.
     *
     * <p><b>Không gắn requiredActions VERIFY_EMAIL trước.</b> {@code send-verify-email} đã làm việc đó trong link.
     * Gắn trước thì quyết định "chưa xác minh email vẫn đăng nhập được" bị phá: user vào trạng thái chưa hoàn tất
     * thiết lập và bị chặn ngay cả khi mật khẩu đúng. Trên main, việc chặn đặt vé khi chưa xác minh là do
     * order-service trả {@code EMAIL_NOT_VERIFIED}, không phải chặn đăng nhập ở đây.
     */
    public void sendVerifyEmail(UUID userId) {
        send(userId, "/send-verify-email", "PUT", null);
    }

    private List<RoleRepresentation> roles(UUID userId, String suffix) {
        try {
            RoleRepresentation[] roles = withAdminToken(token -> restClient.get()
                    .uri(usersPath + "/{id}" + suffix, userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(RoleRepresentation[].class));
            return roles == null ? List.of() : Arrays.asList(roles);
        } catch (HttpClientErrorException.NotFound error) {
            throw notFound();
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Lệnh ghi Admin API cho một user: 404 → USER_NOT_FOUND, 400 → VALIDATION_ERROR (chính sách mật khẩu…),
     * lỗi khác → AUTH_PROVIDER_UNAVAILABLE.
     */
    private void send(UUID userId, String suffix, String method, Object body) {
        try {
            withAdminToken(token -> {
                RestClient.RequestBodySpec request = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                        .uri(usersPath + "/{id}" + suffix, userId)
                        .headers(h -> h.setBearerAuth(token));
                if (body != null) {
                    request = request.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                return request.retrieve().toBodilessEntity();
            });
        } catch (HttpClientErrorException.NotFound error) {
            throw notFound();
        } catch (HttpClientErrorException.BadRequest error) {
            log.warn("Keycloak rejected {} {}: {}", method, suffix, error.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request rejected by identity provider");
        } catch (RestClientException error) {
            log.warn("Keycloak admin call {} {} failed: {}", method, suffix, error.getMessage());
            throw KeycloakTokenClient.unavailable();
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found");
    }

    /** Realm role không tồn tại (ADMIN gõ sai tên role) — 404 từ Keycloak. */
    static final class RoleNotFoundException extends RuntimeException {
        RoleNotFoundException(String role) {
            super("Realm role not found: " + role);
        }
    }
}
