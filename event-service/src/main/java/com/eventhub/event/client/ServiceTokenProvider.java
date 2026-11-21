package com.eventhub.event.client;

import org.springframework.beans.factory.annotation.Value;
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
 * Token service-to-service (client_credentials của client eventhub-event-service), cache tới 30 giây trước
 * khi hết hạn. Không log token.
 */
@Component
public class ServiceTokenProvider {
    private final RestClient client;
    private final Clock clock;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant refreshAt = Instant.EPOCH;

    public ServiceTokenProvider(Clock clock,
                                @Value("${eventhub.s2s.token-uri}") String tokenUri,
                                @Value("${eventhub.s2s.client-id}") String clientId,
                                @Value("${eventhub.s2s.client-secret}") String clientSecret) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.clock = clock;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getToken() {
        if (accessToken != null && clock.instant().isBefore(refreshAt)) {
            return accessToken;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<?, ?> result = client.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        if (result == null || !(result.get("access_token") instanceof String token) || token.isBlank()
                || !(result.get("expires_in") instanceof Number lifetime) || lifetime.longValue() <= 0) {
            throw new IllegalStateException("Invalid service token response");
        }
        accessToken = token;
        refreshAt = clock.instant().plusSeconds(Math.max(0, lifetime.longValue() - 30));
        return token;
    }

    /** Bỏ token cache khi bên nhận trả 401 (Keycloak khởi động lại, đổi khóa ký). */
    public synchronized void invalidate() {
        accessToken = null;
        refreshAt = Instant.EPOCH;
    }
}
