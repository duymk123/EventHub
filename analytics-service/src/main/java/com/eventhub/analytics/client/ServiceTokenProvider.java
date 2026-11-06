package com.eventhub.analytics.client;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Lấy access token service bằng OAuth2 client credentials và cache đến gần hạn sử dụng. Token này phục vụ lời
 * gọi Analytics tới internal Event.
 */
@Component
public class ServiceTokenProvider {
    private final RestClient client;
    private final Environment environment;
    private final Clock clock;
    private String accessToken;
    private Instant refreshAt = Instant.EPOCH;

    public ServiceTokenProvider(Environment environment, Clock clock) {
        this.environment = environment;
        this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Trả token cache còn trước refreshAt hoặc gọi token endpoint bằng client credentials. synchronized gom các
     * thread về một lần refresh, refreshAt sớm hơn thời hạn provider 30 giây.
     */
    public synchronized String getToken() {
        // Các request dùng lại token còn trước mốc refresh để giảm số lần gọi Keycloak.
        if (accessToken != null && clock.instant().isBefore(refreshAt)) {
            return accessToken;
        }
        String endpoint = environment.getProperty("eventhub.s2s.token-uri", "");
        String clientId = environment.getProperty("eventhub.s2s.client-id", "");
        String secret = environment.getProperty("eventhub.s2s.client-secret", "");
        if (endpoint.isBlank() || clientId.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("Service identity configuration is incomplete");
        }
        var form = new LinkedMultiValueMap<String, String>();
        // Service tự xác thực bằng client credentials cấu hình, token trả về dùng gọi internal Event.
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        String scope = environment.getProperty("eventhub.s2s.scope", "");
        if (!scope.isBlank()) {
            form.add("scope", scope);
        }
        Map<?, ?> result = client.post().uri(endpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        if (result == null || !(result.get("access_token") instanceof String token) || token.isBlank()
                || !"Bearer".equalsIgnoreCase(String.valueOf(result.get("token_type")))
                || !(result.get("expires_in") instanceof Number lifetime) || lifetime.longValue() <= 0) {
            throw new IllegalStateException("Invalid service identity response");
        }
        accessToken = token;
        // Chừa 30 giây trước thời hạn provider, token có TTL ngắn sẽ được refresh sớm hơn.
        refreshAt = clock.instant().plusSeconds(Math.max(0, lifetime.longValue() - 30));
        return token;
    }
}
