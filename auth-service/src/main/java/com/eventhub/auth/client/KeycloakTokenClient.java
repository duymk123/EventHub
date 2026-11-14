package com.eventhub.auth.client;

import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.response.TokenResponse;
import com.eventhub.auth.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Gọi endpoint OpenID Connect của realm bằng client confidential của Auth Service: đăng nhập (password grant),
 * refresh, logout và lấy token quản trị (client_credentials) cho KeycloakUserClient.
 * Không log mật khẩu hay token; lỗi Keycloak được đổi sang ErrorCode của EventHub.
 */
@Component
public class KeycloakTokenClient {

    /** Phản hồi token endpoint của Keycloak. */
    record KeycloakToken(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         @JsonProperty("token_type") String tokenType,
                         @JsonProperty("expires_in") long expiresIn,
                         @JsonProperty("refresh_expires_in") long refreshExpiresIn) {}

    private final RestClient restClient;
    private final String realmPath;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock = Clock.systemUTC();

    private String adminToken;
    private Instant adminTokenRefreshAt = Instant.EPOCH;

    public KeycloakTokenClient(@Value("${eventhub.keycloak.server-url}") String serverUrl,
                               @Value("${eventhub.keycloak.realm}") String realm,
                               @Value("${eventhub.keycloak.client-id}") String clientId,
                               @Value("${eventhub.keycloak.client-secret}") String clientSecret) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(serverUrl).requestFactory(factory).build();
        this.realmPath = "/realms/" + realm + "/protocol/openid-connect";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Đăng nhập bằng email/mật khẩu. Sai thông tin → INVALID_CREDENTIALS; tài khoản bị khóa → ACCOUNT_LOCKED.
     */
    public TokenResponse login(String email, String password) {
        MultiValueMap<String, String> form = clientForm("password");
        form.add("username", email);
        form.add("password", password);
        form.add("scope", "openid");
        try {
            return toResponse(postToken(form));
        } catch (HttpClientErrorException error) {
            String description = describe(error);
            if (description.contains("disabled") || description.contains("locked")) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Account is locked");
            }
            if (error.getStatusCode().is4xxClientError() && description.contains("not fully set up")) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Account requires further setup");
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
    }

    /**
     * Đổi refresh token lấy cặp token mới; realm bật xoay vòng nên refresh token cũ bị vô hiệu sau lần dùng đầu.
     */
    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientForm("refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            return toResponse(postToken(form));
        } catch (HttpClientErrorException error) {
            throw refreshError(error);
        }
    }

    /**
     * Kết thúc phiên tại Keycloak bằng refresh token; access token đã cấp vẫn còn hạn tới khi hết 5 phút.
     */
    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = clientForm(null);
        form.add("refresh_token", refreshToken);
        try {
            restClient.post().uri(realmPath + "/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException error) {
            throw refreshError(error);
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    /**
     * Token quản trị (client_credentials của service account), cache tới 30 giây trước khi hết hạn.
     */
    public synchronized String adminToken() {
        if (adminToken != null && clock.instant().isBefore(adminTokenRefreshAt)) {
            return adminToken;
        }
        try {
            KeycloakToken token = postToken(clientForm("client_credentials"));
            adminToken = token.accessToken();
            adminTokenRefreshAt = clock.instant().plusSeconds(Math.max(0, token.expiresIn() - 30));
            return adminToken;
        } catch (HttpClientErrorException error) {
            // Sai client secret hoặc client chưa bật service account: lỗi cấu hình, không phải lỗi người dùng.
            throw unavailable();
        }
    }

    /**
     * Bỏ token quản trị đang cache, dùng khi Keycloak trả 401 (khởi động lại, đổi khóa ký, token bị thu hồi).
     */
    public synchronized void invalidateAdminToken() {
        adminToken = null;
        adminTokenRefreshAt = Instant.EPOCH;
    }

    private KeycloakToken postToken(MultiValueMap<String, String> form) {
        try {
            KeycloakToken token = restClient.post().uri(realmPath + "/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakToken.class);
            if (token == null || token.accessToken() == null) {
                throw unavailable();
            }
            return token;
        } catch (HttpClientErrorException error) {
            throw error;
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private MultiValueMap<String, String> clientForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (grantType != null) {
            form.add("grant_type", grantType);
        }
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private static TokenResponse toResponse(KeycloakToken token) {
        return new TokenResponse(token.accessToken(), token.refreshToken(),
                token.tokenType() == null ? "Bearer" : token.tokenType(), token.expiresIn(), token.refreshExpiresIn());
    }

    private static BusinessException refreshError(HttpClientErrorException error) {
        String description = describe(error);
        if (description.contains("reuse")) {
            return new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED, "Refresh token was already used");
        }
        if (description.contains("not active") || description.contains("expired")) {
            return new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED, "Refresh token expired");
        }
        return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token");
    }

    private static String describe(HttpClientErrorException error) {
        return error.getResponseBodyAsString().toLowerCase(Locale.ROOT);
    }

    static BusinessException unavailable() {
        return new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE, "Identity provider unavailable");
    }
}
