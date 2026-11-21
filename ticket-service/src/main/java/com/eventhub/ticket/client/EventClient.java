package com.eventhub.ticket.client;

import com.eventhub.ticket.common.ErrorCode;
import com.eventhub.ticket.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

/**
 * Gọi INT-01 GET /internal/events/{id} của event-service để lấy owner, trạng thái, tên và giờ bắt đầu.
 * Không tồn tại → EVENT_NOT_FOUND; lỗi mạng/timeout/5xx → EVENT_SERVICE_UNAVAILABLE (plan Ticket §5),
 * không đổi lỗi tạm thời thành lỗi nghiệp vụ vĩnh viễn.
 */
@Component
public class EventClient {
    private final RestClient restClient;
    private final ServiceTokenProvider tokens;

    public EventClient(@Value("${eventhub.event-service.base-url}") String baseUrl, ServiceTokenProvider tokens) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.tokens = tokens;
    }

    public InternalEventResponse getInternal(UUID eventId) {
        try {
            return fetch(eventId);
        } catch (HttpClientErrorException.Unauthorized error) {
            tokens.invalidate();
            try {
                return fetch(eventId);
            } catch (HttpClientErrorException.NotFound notFound) {
                throw eventNotFound();
            } catch (RuntimeException retryError) {
                throw unavailable();
            }
        } catch (HttpClientErrorException.NotFound error) {
            throw eventNotFound();
        } catch (BusinessException error) {
            throw error;
        } catch (RestClientException | IllegalStateException error) {
            throw unavailable();
        }
    }

    private InternalEventResponse fetch(UUID eventId) {
        InternalEventResponse event = restClient.get().uri("/internal/events/{id}", eventId)
                .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                .retrieve()
                .body(InternalEventResponse.class);
        if (event == null || event.organizerId() == null || event.status() == null) {
            throw unavailable();
        }
        return event;
    }

    private static BusinessException eventNotFound() {
        return new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found");
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE, "Event service unavailable");
    }
}
