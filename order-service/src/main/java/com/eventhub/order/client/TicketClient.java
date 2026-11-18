package com.eventhub.order.client;

import com.eventhub.order.common.ErrorCode;
import com.eventhub.order.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gọi Ticket Service qua /internal/** (DTO theo docs/api/ticket-service-openapi.yaml) bằng token service account
 * eventhub-order-service. Lỗi nghiệp vụ của Ticket giữ nguyên code; timeout/5xx là lỗi tạm thời
 * (TICKET_SERVICE_UNAVAILABLE), không coi là "không còn vé".
 */
@Component
public class TicketClient {
    private static final Logger log = LoggerFactory.getLogger(TicketClient.class);
    /** Code của Ticket được chuyển nguyên cho client của Order. */
    private static final Set<String> PASS_THROUGH = Set.of("INSUFFICIENT_TICKETS", "TICKET_TYPE_NOT_ON_SALE",
            "TICKET_TYPE_NOT_FOUND", "EVENT_NOT_FOUND", "EVENT_SERVICE_UNAVAILABLE", "VALIDATION_ERROR");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservedItem(UUID ticketTypeId, String name, int quantity, BigDecimal unitPrice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reservation(UUID reservationId, UUID orderId, String status, Instant expiresAt,
                              List<ReservedItem> items) {
    }

    public record ReserveItem(UUID ticketTypeId, int quantity) {
    }

    private record ReserveRequest(UUID orderId, UUID eventId, Instant expiresAt, List<ReserveItem> items) {
    }

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public TicketClient(@Value("${eventhub.ticket-service.base-url}") String baseUrl, ServiceTokenProvider tokens,
                        ObjectMapper mapper) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /**
     * Reserve idempotent theo orderId nên được thử lại một lần khi lỗi tạm thời, vẫn cùng orderId (plan Order §2:
     * không reserve bằng ID mới chỉ vì timeout).
     */
    public Reservation reserve(UUID orderId, UUID eventId, List<ReserveItem> items, Instant expiresAt) {
        ReserveRequest body = new ReserveRequest(orderId, eventId, expiresAt, items);
        Supplier<Reservation> call = () -> withToken(token -> restClient.post().uri("/internal/reservations")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Reservation.class));
        Reservation reservation;
        try {
            reservation = call.get();
        } catch (BusinessException error) {
            if (error.getErrorCode() != ErrorCode.TICKET_SERVICE_UNAVAILABLE) {
                throw error;
            }
            reservation = call.get();
        }
        if (reservation == null || reservation.reservationId() == null || reservation.expiresAt() == null
                || reservation.items() == null) {
            throw unavailable();
        }
        return reservation;
    }

    /**
     * Bù trừ khi lưu đơn lỗi hoặc đơn hết hạn. Lỗi chỉ ghi log: reservation có cùng hạn với đơn nên worker của
     * Ticket vẫn tự trả quota khi hết hạn.
     */
    public void releaseQuietly(UUID reservationId) {
        try {
            withToken(token -> restClient.post().uri("/internal/reservations/{id}/release", reservationId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity());
        } catch (RuntimeException error) {
            log.warn("Release of reservation {} failed, Ticket expiry will return the quota: {}", reservationId,
                    error instanceof BusinessException business ? business.getErrorCode() : error.getClass().getSimpleName());
        }
    }

    private <T> T withToken(java.util.function.Function<String, T> call) {
        try {
            try {
                return call.apply(tokens.getToken());
            } catch (HttpClientErrorException.Unauthorized error) {
                tokens.invalidate();
                return call.apply(tokens.getToken());
            }
        } catch (HttpStatusCodeException error) {
            throw translate(error);
        } catch (RestClientException | IllegalStateException error) {
            throw unavailable();
        }
    }

    private BusinessException translate(HttpStatusCodeException error) {
        if (error.getStatusCode().is4xxClientError() || error.getStatusCode().value() == 503) {
            try {
                Map<?, ?> body = mapper.readValue(error.getResponseBodyAsByteArray(), Map.class);
                Object code = body.get("code");
                if (code instanceof String value && PASS_THROUGH.contains(value)) {
                    Object message = body.get("message");
                    return new BusinessException(ErrorCode.valueOf(value),
                            message instanceof String text ? text : value);
                }
            } catch (Exception ignored) {
                // Body không phải ErrorResponse: coi như lỗi tích hợp.
            }
        }
        log.warn("Ticket service returned {} for an internal call", error.getStatusCode().value());
        return unavailable();
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.TICKET_SERVICE_UNAVAILABLE, "Ticket service unavailable");
    }
}
