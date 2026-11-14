package com.eventhub.analytics.client;

import com.eventhub.analytics.common.ErrorCode;
import com.eventhub.analytics.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * INT-02 (C10): GET /internal/events?ids=a,b,... Bỏ các phần tử thiếu id; mọi lỗi HTTP/token/response đều là
     * 503 vì bảng thống kê không được hiển thị tên giả.
     */
    @Override
    public Map<UUID, InternalEventResponse> getBatch(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        if (eventIds.size() > MAX_BATCH) {
            throw new IllegalArgumentException("At most " + MAX_BATCH + " event ids per batch");
        }
        String ids = eventIds.stream().map(UUID::toString).distinct().collect(Collectors.joining(","));
        try {
            List<InternalEventResponse> body = restClient.get()
                    .uri(uri -> uri.path("/internal/events").queryParam("ids", ids).build())
                    .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<InternalEventResponse>>() {});
            if (body == null) {
                throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE, "Invalid event service response");
            }
            Map<UUID, InternalEventResponse> result = new HashMap<>();
            for (InternalEventResponse event : body) {
                if (event != null && event.id() != null) {
                    result.put(event.id(), event);
                }
            }
            return result;
        } catch (RestClientException | IllegalStateException ex) {
            throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE, "Event service unavailable");
        }
    }

    /**
     * INT-03 (C10): GET /internal/events/ids?organizerId=. Body null hoặc lỗi HTTP/token là 503: không được đoán
     * phạm vi rỗng vì ORGANIZER sẽ thấy số 0 sai thay vì lỗi.
     */
    @Override
    public List<UUID> getIdsByOrganizer(UUID organizerId) {
        try {
            List<UUID> body = restClient.get()
                    .uri(uri -> uri.path("/internal/events/ids").queryParam("organizerId", organizerId).build())
                    .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<UUID>>() {});
            if (body == null) {
                throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE, "Invalid event service response");
            }
            return body.stream().filter(java.util.Objects::nonNull).distinct().toList();
        } catch (RestClientException | IllegalStateException ex) {
            throw new BusinessException(ErrorCode.EVENT_SERVICE_UNAVAILABLE, "Event service unavailable");
        }
    }
}
