package com.eventhub.order.client;

import com.eventhub.order.dto.CreateOrderRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class TicketClient {

    private final RestClient restClient;

    public TicketClient(
            RestClient.Builder builder,
            @Value("${ticket-service.base-url:http://localhost:8083}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Reservation reserve(UUID orderId, UUID eventId, List<ReserveItem> items, Instant expiresAt) {
        ReserveRequest request = new ReserveRequest(orderId, eventId, expiresAt, items);
        Reservation response = restClient.post()
                .uri("/internal/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Reservation.class);
        if (response == null) {
            throw new IllegalStateException("Ticket service returned an empty reservation");
        }
        return response;
    }

    public void release(UUID reservationId) {
        restClient.post()
                .uri("/internal/reservations/{id}/release", reservationId)
                .retrieve()
                .toBodilessEntity();
    }

    public record ReserveRequest(UUID orderId, UUID eventId, Instant expiresAt, List<ReserveItem> items) {
    }

    public record ReserveItem(UUID ticketTypeId, int quantity) {
    }

    public record Reservation(
            UUID reservationId,
            UUID orderId,
            String status,
            Instant expiresAt,
            List<ReservedItem> items
    ) {
    }

    public record ReservedItem(UUID ticketTypeId, String name, int quantity, java.math.BigDecimal unitPrice) {
    }
}
