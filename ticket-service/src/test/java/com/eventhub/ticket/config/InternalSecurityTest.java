package com.eventhub.ticket.config;

import com.eventhub.ticket.controller.InternalReservationController;
import com.eventhub.ticket.controller.InternalTicketController;
import com.eventhub.ticket.exception.GlobalExceptionHandler;
import com.eventhub.ticket.service.ReservationService;
import com.eventhub.ticket.service.TicketCancellationService;
import com.eventhub.ticket.service.TicketTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mỗi service account chỉ gọi được nhóm /internal/** của mình (azp → ROLE_SVC_*), token người dùng bị chặn.
 */
@WebMvcTest(controllers = {InternalReservationController.class, InternalTicketController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InternalSecurityTest {
    private static final String ID = UUID.randomUUID().toString();

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder decoder;
    @MockitoBean
    ReservationService reservationService;
    @MockitoBean
    TicketCancellationService cancellationService;
    @MockitoBean
    TicketTypeService ticketTypeService;

    @BeforeEach
    void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "order" -> service("eventhub-order-service");
            case "analytics" -> service("eventhub-analytics-service");
            case "event" -> service("eventhub-event-service");
            case "admin" -> base().subject(UUID.randomUUID().toString()).claim("azp", "eventhub-auth-service")
                    .claim("realm_access", Map.of("roles", List.of("ADMIN"))).build();
            default -> throw new BadJwtException("bad");
        });
    }

    @Test
    void releaseAndCancelOnlyForOrder() throws Exception {
        expect(post("/internal/reservations/" + ID + "/release"), "order", 200);
        expect(post("/internal/reservations/" + ID + "/release"), "analytics", 403);
        expect(post("/internal/reservations/" + ID + "/release"), "event", 403);
        expect(post("/internal/reservations/" + ID + "/release"), "admin", 403);
        expect(post("/internal/tickets/cancel-by-order/" + ID), "order", 200);
        expect(post("/internal/tickets/cancel-by-order/" + ID), "analytics", 403);
    }

    @Test
    void capacityOnlyForAnalytics() throws Exception {
        expect(get("/internal/ticket-types/capacity").param("eventIds", ID), "analytics", 200);
        expect(get("/internal/ticket-types/capacity").param("eventIds", ID), "order", 403);
    }

    @Test
    void configurationOnlyForEvent() throws Exception {
        expect(get("/internal/events/" + ID + "/ticket-types/configuration"), "event", 200);
        expect(get("/internal/events/" + ID + "/ticket-types/configuration"), "order", 403);
        expect(get("/internal/events/" + ID + "/ticket-types/configuration"), "analytics", 403);
    }

    private void expect(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                        String token, int status) throws Exception {
        mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().is(status));
    }

    private static Jwt service(String clientId) {
        return base().subject(UUID.randomUUID().toString()).claim("azp", clientId)
                .claim("realm_access", Map.of("roles", List.of("offline_access"))).build();
    }

    private static Jwt.Builder base() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "RS256").issuedAt(now).expiresAt(now.plusSeconds(300));
    }
}
