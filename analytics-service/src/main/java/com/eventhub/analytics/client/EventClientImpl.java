package com.eventhub.analytics.client;

import com.eventhub.analytics.common.ErrorCode;
import com.eventhub.analytics.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Gọi endpoint nội bộ của Event Service để xác minh sự kiện tồn tại và quyền sở hữu.
 * ADMIN được cấp quyền truy cập bất kể quyền sở hữu; ORGANIZER phải là chủ sở hữu.
 * CUSTOMER bị từ chối.
 */
@Component
public class EventClientImpl implements EventClient {

    private final RestClient restClient;

    public EventClientImpl(@Value("${eventhub.event-service.url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public InternalEventResponse get(UUID eventId) {
        try {
            ResponseEntity<InternalEventResponse> response = restClient.get()
                    .uri("/internal/events/{id}", eventId)
                    .retrieve()
                    .toEntity(InternalEventResponse.class);
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found: " + eventId);
        } catch (RestClientException ex) {
            // Lỗi từ phía upstream — không tự tạo số 0 để che giấu lỗi
            throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE,
                    "Event service unavailable: " + ex.getMessage());
        }
    }
}
