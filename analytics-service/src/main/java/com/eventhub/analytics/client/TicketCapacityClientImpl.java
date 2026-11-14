package com.eventhub.analytics.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Client S2S tới Ticket Service. Dùng chung ServiceTokenProvider với EventClient; timeout ngắn vì đây là dữ liệu
 * phụ của bảng thống kê.
 */
@Component
public class TicketCapacityClientImpl implements TicketCapacityClient {
    private static final Logger log = LoggerFactory.getLogger(TicketCapacityClientImpl.class);

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;

    public TicketCapacityClientImpl(@Value("${eventhub.ticket-service.url:http://localhost:8083}") String baseUrl,
                                    ServiceTokenProvider tokens) {
        this.tokens = tokens;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * Trả map eventId → capacity. Mọi lỗi (mạng, token, HTTP, body sai) chỉ ghi WARN và trả map rỗng.
     */
    @Override
    public Map<UUID, Integer> getCapacity(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        if (eventIds.size() > MAX_BATCH) {
            throw new IllegalArgumentException("At most " + MAX_BATCH + " event ids per batch");
        }
        String ids = eventIds.stream().map(UUID::toString).distinct().collect(Collectors.joining(","));
        try {
            List<EventCapacityResponse> body = restClient.get()
                    .uri(uri -> uri.path("/internal/ticket-types/capacity").queryParam("eventIds", ids).build())
                    .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EventCapacityResponse>>() {});
            Map<UUID, Integer> result = new HashMap<>();
            if (body != null) {
                for (EventCapacityResponse item : body) {
                    if (item != null && item.eventId() != null && item.capacity() != null) {
                        result.put(item.eventId(), item.capacity());
                    }
                }
            }
            return result;
        } catch (RuntimeException ex) {
            // Không log URL/token; capacity chỉ là thông tin phụ nên không làm hỏng response.
            log.warn("Ticket capacity unavailable: {}", ex.getClass().getSimpleName());
            return Map.of();
        }
    }
}
