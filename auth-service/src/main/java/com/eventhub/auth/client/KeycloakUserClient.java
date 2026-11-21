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

    /** User Keycloak, chỉ các trường Auth Service cần. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakUser(String id, String email, String firstName, String lastName, boolean enabled,
                               boolean emailVerified, Long createdTimestamp) {}

    /** Role realm; Admin API cần cả id và name khi thêm/xóa role mapping. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleRepresentation(String id, String name) {}

    private final RestClient restClient;
    private final KeycloakTokenClient tokens;
    private final String usersPath;

    public KeycloakUserClient(@Value("${eventhub.keycloak.server-url}") String serverUrl,
                              @Value("${eventhub.keycloak.realm}") String realm,
                              KeycloakTokenClient tokens) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(serverUrl).requestFactory(factory).build();
        this.tokens = tokens;
        this.usersPath = "/admin/realms/" + realm + "/users";
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
     * Tạo user đã bật, email dùng làm username, email CHƯA xác minh (auth-service gửi mail xác minh sau đó).
     * Role nghiệp vụ không gán ở đây: realm không còn đưa CUSTOMER vào role mặc định, caller gán trực tiếp.
     * Email trùng → EMAIL_ALREADY_EXISTS; mật khẩu không đạt chính sách realm → VALIDATION_ERROR.
     *
     * @return id user mới (cũng là sub trong JWT).
     */
    public UUID createUser(String email, String password, String firstName, String lastName) {
        Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", false,
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
     * Nhờ Keycloak gửi mail xác minh (SMTP cấu hình trong realm, dev dùng Mailpit). Link trỏ tới KC_HOSTNAME.
     * Phải gắn required action VERIFY_EMAIL trước: nếu không, mail vẫn được gửi nhưng bấm link không đánh dấu
     * emailVerified và không có bước xác nhận nào để user hoàn tất.
     */
    public void sendVerifyEmail(UUID userId) {
        send(userId, "", "PUT", Map.of("emailVerified", false, "requiredActions", List.of("VERIFY_EMAIL")));
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
}
