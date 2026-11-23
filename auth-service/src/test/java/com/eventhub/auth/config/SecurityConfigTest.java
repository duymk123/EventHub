package com.eventhub.auth.config;

import com.eventhub.auth.controller.AdminUserController;
import com.eventhub.auth.service.AdminUserService;
import com.eventhub.auth.controller.AuthController;
import com.eventhub.auth.controller.UserProfileController;
import com.eventhub.auth.exception.GlobalExceptionHandler;
import com.eventhub.auth.service.AuthService;
import com.eventhub.auth.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AdminUserController.class, UserProfileController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityConfigTest {
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TARGET = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder decoder;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    UserProfileService userProfileService;
    @MockitoBean
    AdminUserService adminUserService;

    @BeforeEach
    void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "admin" -> user(ADMIN_ID, "ADMIN");
            case "customer" -> user(UUID.randomUUID(), "CUSTOMER");
            case "organizer" -> user(UUID.randomUUID(), "ORGANIZER");
            default -> throw new BadJwtException("bad");
        });
    }

    @Test
    void adminUserApisRequireAdmin() throws Exception {
        String lock = "/api/v1/admin/users/" + TARGET + "/lock";
        mvc.perform(patch(lock)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(patch(lock).header("Authorization", "Bearer customer")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch(lock).header("Authorization", "Bearer organizer")).andExpect(status().isForbidden());
        mvc.perform(patch(lock).header("Authorization", "Bearer admin")).andExpect(status().isNoContent());
        verify(authService).lockUser(ADMIN_ID, TARGET);

        mvc.perform(patch("/api/v1/admin/users/" + TARGET + "/role").header("Authorization", "Bearer customer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/" + TARGET + "/role").header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ORGANIZER\"}"))
                .andExpect(status().isNoContent());
        verify(authService).assignRole(eq(ADMIN_ID), eq(TARGET), eq(com.eventhub.auth.common.BusinessRole.ORGANIZER));
    }

    @Test
    void meRequiresTokenAndUnknownPathsDenied() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer customer")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
    }

    private static Jwt user(UUID subject, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "RS256").issuedAt(now).expiresAt(now.plusSeconds(300))
                .subject(subject.toString()).claim("realm_access", Map.of("roles", List.of(role))).build();
    }
}
