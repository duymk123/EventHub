package com.eventhub.event.config;

import com.eventhub.event.common.Actor;
import com.eventhub.event.common.BusinessRole;
import com.eventhub.event.controller.AdminEventController;
import com.eventhub.event.controller.CategoryController;
import com.eventhub.event.controller.EventController;
import com.eventhub.event.controller.InternalEventController;
import com.eventhub.event.controller.OrganizerEventController;
import com.eventhub.event.exception.GlobalExceptionHandler;
import com.eventhub.event.service.CategoryService;
import com.eventhub.event.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận quyền của event-service: chạy SecurityFilterChain và CallerAuthenticationConverter thật, chỉ giả
 * JwtDecoder (token "customer"/"organizer"/... được giải mã thành JWT có role tương ứng).
 */
@WebMvcTest(controllers = {EventController.class, AdminEventController.class, OrganizerEventController.class,
        CategoryController.class, InternalEventController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityConfigTest {
    private static final UUID ORGANIZER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String EVENT_JSON = """
            {"title":"Show","categoryId":"33333333-3333-4333-8333-333333333333",
             "startTime":"2099-01-01T10:00:00Z","endTime":"2099-01-01T12:00:00Z"}""";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder decoder;
    @MockitoBean
    EventService eventService;
    @MockitoBean
    CategoryService categoryService;

    @BeforeEach
    void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "customer" -> user(UUID.randomUUID(), "CUSTOMER");
            case "organizer" -> user(ORGANIZER_ID, "ORGANIZER");
            case "admin" -> user(UUID.randomUUID(), "ADMIN");
            case "two-roles" -> user(UUID.randomUUID(), "CUSTOMER", "ORGANIZER");
            case "ticket-svc" -> service("eventhub-ticket-service");
            case "analytics-svc" -> service("eventhub-analytics-service");
            case "order-svc" -> service("eventhub-order-service");
            default -> throw new BadJwtException("bad");
        });
        when(eventService.searchPublic(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        when(eventService.getMine(any(), any(), any())).thenReturn(Page.empty());
        when(eventService.getAll(any(), any())).thenAnswer(call -> Page.empty(call.getArgument(1)));
    }

    @Test
    void publicReadsNeedNoToken() throws Exception {
        mvc.perform(get("/api/v1/events")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/events/" + EVENT_ID)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    void createEventRequiresOrganizerAndUsesJwtSubject() throws Exception {
        mvc.perform(json(post("/api/v1/events"), EVENT_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(json(post("/api/v1/events"), EVENT_JSON).header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(json(post("/api/v1/events"), EVENT_JSON).header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/events"), EVENT_JSON).header("Authorization", "Bearer organizer"))
                .andExpect(status().isCreated());
        verify(eventService).create(eq(new Actor(ORGANIZER_ID, BusinessRole.ORGANIZER)), any());
    }

    @Test
    void lifecycleActionsAreOrganizerOnly() throws Exception {
        for (String action : List.of("submit", "publish", "close")) {
            mvc.perform(post("/api/v1/events/" + EVENT_ID + "/" + action).header("Authorization", "Bearer admin"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/events/" + EVENT_ID + "/" + action).header("Authorization", "Bearer organizer"))
                    .andExpect(status().isOk());
        }
        mvc.perform(json(put("/api/v1/events/" + EVENT_ID), EVENT_JSON).header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", "Bearer organizer"))
                .andExpect(status().isOk());
    }

    @Test
    void adminListIsAdminOnlyAndReadsStatusFilter() throws Exception {
        mvc.perform(get("/api/v1/admin/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/events").header("Authorization", "Bearer organizer"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // Token service không có business role → đã xác thực nhưng không có ROLE_ADMIN.
        mvc.perform(get("/api/v1/admin/events").header("Authorization", "Bearer ticket-svc"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/events").param("status", "SUBMITTED").param("page", "0").param("size", "5")
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(5));
        verify(eventService).getAll(eq(com.eventhub.event.common.EventStatus.SUBMITTED), any());
        // Query có status/size/page không được rơi vào handler PATCH /{id}/review.
        mvc.perform(patch("/api/v1/admin/events/" + EVENT_ID + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void reviewAndCategoryWritesAreAdminOnly() throws Exception {
        String review = "{\"decision\":\"APPROVE\"}";
        mvc.perform(json(patch("/api/v1/admin/events/" + EVENT_ID + "/review"), review)
                .header("Authorization", "Bearer organizer")).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/admin/events/" + EVENT_ID + "/review"), review)
                .header("Authorization", "Bearer admin")).andExpect(status().isOk());

        String category = "{\"name\":\"Music\"}";
        mvc.perform(json(post("/api/v1/categories"), category).header("Authorization", "Bearer organizer"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/categories"), category).header("Authorization", "Bearer admin"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/categories/" + EVENT_ID).header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/categories/" + EVENT_ID).header("Authorization", "Bearer admin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void internalApisOnlyForAllowedServices() throws Exception {
        mvc.perform(get("/internal/events/" + EVENT_ID).header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/events/" + EVENT_ID).header("Authorization", "Bearer ticket-svc"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/events/" + EVENT_ID).header("Authorization", "Bearer analytics-svc"))
                .andExpect(status().isOk());
        // Order không có trong danh sách và không có business role → token bị từ chối.
        mvc.perform(get("/internal/events/" + EVENT_ID).header("Authorization", "Bearer order-svc"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/internal/events/ids").param("organizerId", ORGANIZER_ID.toString())
                .header("Authorization", "Bearer ticket-svc")).andExpect(status().isForbidden());
        mvc.perform(get("/internal/events/ids").param("organizerId", ORGANIZER_ID.toString())
                .header("Authorization", "Bearer analytics-svc")).andExpect(status().isOk());
        verify(eventService).getInternalIdsByOrganizer(ORGANIZER_ID);
        mvc.perform(get("/internal/events").param("ids", EVENT_ID.toString())
                .header("Authorization", "Bearer analytics-svc")).andExpect(status().isOk());
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", "Bearer two-roles"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static Jwt user(UUID subject, String... roles) {
        return base().subject(subject.toString()).claim("azp", "eventhub-auth-service")
                .claim("realm_access", Map.of("roles", List.of(roles))).build();
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
