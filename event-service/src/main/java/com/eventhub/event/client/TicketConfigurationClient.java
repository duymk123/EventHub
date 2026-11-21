package com.eventhub.event.client;

import com.eventhub.event.common.ErrorCode;
import com.eventhub.event.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

/**
 * Preflight publish C08: GET /internal/events/{eventId}/ticket-types/configuration của ticket-service.
 * Mọi lỗi mạng/token/5xx/response sai → TICKET_SERVICE_UNAVAILABLE, không đoán là "đã sẵn sàng".
 */
@Component
public class TicketConfigurationClient {

    /** Phản hồi của ticket-service; chỉ cần readyToPublish. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketConfiguration(UUID eventId, int activeTypeCount, long activeCapacity,
                                      Boolean readyToPublish) {}

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;

    public TicketConfigurationClient(@Value("${eventhub.ticket-service.base-url}") String baseUrl,
                                     ServiceTokenProvider tokens) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.tokens = tokens;
    }

    public boolean isReadyToPublish(UUID eventId) {
        try {
            return fetch(eventId);
        } catch (HttpClientErrorException.Unauthorized error) {
            tokens.invalidate();
            try {
                return fetch(eventId);
            } catch (RuntimeException retryError) {
                throw unavailable();
            }
        } catch (BusinessException error) {
            throw error;
        } catch (RestClientException | IllegalStateException error) {
            throw unavailable();
        }
    }

    private boolean fetch(UUID eventId) {
        TicketConfiguration body = restClient.get()
                .uri("/internal/events/{eventId}/ticket-types/configuration", eventId)
                .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                .retrieve()
                .body(TicketConfiguration.class);
        if (body == null || body.readyToPublish() == null || !eventId.equals(body.eventId())) {
            throw unavailable();
        }
        return body.readyToPublish();
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.TICKET_SERVICE_UNAVAILABLE, "Ticket service unavailable");
    }
}
