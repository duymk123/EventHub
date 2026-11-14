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
    private final ServiceTokenProvider tokens;

    public EventClientImpl(@Value("${eventhub.event-service.url:http://localhost:8082}") String baseUrl,
                           ServiceTokenProvider tokens) {
        this.tokens = tokens;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Gửi Bearer token service tới internal Event, kiểm tra response có đúng eventId và organizerId. Map event
     * thiếu thành 404, lỗi HTTP/token/response thành 503 với thông điệp an toàn.
     */
    @Override
    public InternalEventResponse get(UUID eventId) {
        try {
            ResponseEntity<InternalEventResponse> response = restClient.get()
                    .uri("/internal/events/{id}", eventId)
                    // Credential là service token lấy từ provider, Event Service kiểm tra quyền gọi internal.
                    .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                    .retrieve()
                    .toEntity(InternalEventResponse.class);
            InternalEventResponse body = response.getBody();
            // Đối chiếu response trước khi dùng organizerId làm căn cứ phân quyền.
            if (body == null || !eventId.equals(body.id()) || body.organizerId() == null) {
                throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE, "Invalid event service response");
            }
            return body;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found: " + eventId);
        } catch (RestClientException | IllegalStateException ex) {
            // Lỗi từ phía upstream — không tự tạo số 0 để che giấu lỗi
            throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE,
                    "Event service unavailable");
        }
    }
}
