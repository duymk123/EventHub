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
                               Long createdTimestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoleRepresentation(String name) {}

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
     * Tạo user đã bật, email dùng làm username. Keycloak tự gán role mặc định của realm (CUSTOMER).
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
                "emailVerified", true,
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
        } catch (HttpClientErrorException error) {
            throw KeycloakTokenClient.unavailable();
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
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found");
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }

    /**
     * Xóa user, dùng để hoàn tác khi đăng ký tạo được user Keycloak nhưng không tạo được hồ sơ. Lỗi được nuốt
     * để không che lỗi gốc; caller ghi log.
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
        try {
            RoleRepresentation[] roles = withAdminToken(token -> restClient.get()
                    .uri(usersPath + "/{id}/role-mappings/realm/composite", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(RoleRepresentation[].class));
            return roles == null ? List.of() : java.util.Arrays.stream(roles).map(RoleRepresentation::name).toList();
        } catch (HttpClientErrorException.NotFound error) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found");
        } catch (RestClientException error) {
            throw KeycloakTokenClient.unavailable();
        }
    }
}
