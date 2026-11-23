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
import java.util.ArrayList;
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
    private static final Set<String> PASS_THROUGH = Set.of("SEAT_TAKEN", "SEAT_NOT_FOUND", "TICKET_TYPE_NOT_ON_SALE",
            "TICKET_TYPE_NOT_FOUND", "EVENT_NOT_FOUND", "EVENT_SERVICE_UNAVAILABLE", "VALIDATION_ERROR",
            "RESERVATION_NOT_FOUND", "RESERVATION_RELEASED_OR_EXPIRED", "TICKET_ALREADY_CHECKED_IN", "TICKET_NOT_FOUND");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservedItem(UUID ticketTypeId, String name, int quantity, BigDecimal unitPrice,
                               List<ReservedSeat> seats) {
    }

    /** C12: ghế Ticket đã giữ, kèm nhãn snapshot (vd B-07). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservedSeat(UUID seatId, String seatLabel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reservation(UUID reservationId, UUID orderId, String status, Instant expiresAt,
                              Instant eventStartTime, List<ReservedItem> items) {
    }

    /** C12: giữ đúng các ghế khách chọn của một zone. */
    public record ReserveItem(UUID ticketTypeId, List<UUID> seatIds) {
    }

    private record ReserveRequest(UUID orderId, UUID eventId, Instant expiresAt, List<ReserveItem> items) {
    }

    /** C12: một vé cho một ghế; ownerUserId là buyer (C02). */
    public record ConfirmHolder(UUID ticketTypeId, UUID seatId, String holderName, String holderEmail,
                                UUID ownerUserId) {
    }

    private record ConfirmRequest(String customerEmail, List<ConfirmHolder> holders) {
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
     * Phát vé cho reservation sau khi đơn PAID (C06). Ticket idempotent: reservation đã CONFIRMED trả lại bộ vé cũ,
     * nên gọi lại khi lỗi tạm thời là an toàn.
     */
    public void confirm(UUID reservationId, String customerEmail, List<ConfirmHolder> holders) {
        ConfirmRequest body = new ConfirmRequest(customerEmail, holders);
        withToken(token -> restClient.post().uri("/internal/reservations/{id}/confirm", reservationId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * C05 rút gọn: hủy mọi vé của đơn và trả ghế trước khi gọi VNPay hoàn tiền. Ticket idempotent (vé đã hủy trả
     * kết quả cũ); có vé đã quét → TICKET_ALREADY_CHECKED_IN.
     */
    public void cancelByOrder(UUID orderId) {
        withToken(token -> restClient.post().uri("/internal/tickets/cancel-by-order/{orderId}", orderId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity());
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
                            message instanceof String text ? text : value, takenSeatIds(body.get("takenSeatIds")));
                }
            } catch (Exception ignored) {
                // Body không phải ErrorResponse: coi như lỗi tích hợp.
            }
        }
        log.warn("Ticket service returned {} for an internal call", error.getStatusCode().value());
        return unavailable();
    }

    /** C12: chuyển tiếp danh sách ghế đã mất của SEAT_TAKEN; phần tử không phải UUID thì bỏ qua. */
    private static List<UUID> takenSeatIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (Object item : list) {
            try {
                ids.add(UUID.fromString(String.valueOf(item)));
            } catch (IllegalArgumentException ignored) {
                // Bỏ phần tử sai định dạng thay vì làm hỏng cả lỗi nghiệp vụ.
            }
        }
        return ids;
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.TICKET_SERVICE_UNAVAILABLE, "Ticket service unavailable");
    }
}
