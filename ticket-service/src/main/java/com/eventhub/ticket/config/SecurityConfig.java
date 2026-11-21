package com.eventhub.ticket.config;

import com.eventhub.ticket.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Resource server JWT (Keycloak) cho mọi API. Quyền theo plan Ticket §5 và OpenAPI:
 * public xem hạng vé; ORGANIZER cấu hình hạng vé; CUSTOMER xem vé/QR của mình; cổng soát vé tạm dùng
 * ORGANIZER/ADMIN tới khi C07 chốt cơ chế thiết bị; /internal/** mở theo từng service account (azp), service
 * này không gọi được API nội bộ dành cho service khác.
 */
@Configuration
public class SecurityConfig {
    static final String ROLE_ORDER = "SVC_ORDER";
    static final String ROLE_ANALYTICS = "SVC_ANALYTICS";
    static final String ROLE_EVENT = "SVC_EVENT";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Xác minh chữ ký bằng JWK của realm, issuer và thời hạn; audience chỉ kiểm khi được cấu hình. JWK tải lười
     * nên service khởi động được khi Keycloak chưa chạy.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${eventhub.security.issuer}") String issuer,
                          @Value("${eventhub.security.jwk-set-uri}") String jwkSetUri,
                          @Value("${eventhub.security.audience:}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        if (!audience.isBlank()) {
            OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null
                    && jwt.getAudience().contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
            validator = new DelegatingOAuth2TokenValidator<>(validator, audienceValidator);
        }
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    CallerAuthenticationConverter callerAuthenticationConverter(
            @Value("${eventhub.security.role-claim}") String roleClaim,
            @Value("${eventhub.security.internal-clients.order}") String orderClient,
            @Value("${eventhub.security.internal-clients.analytics}") String analyticsClient,
            @Value("${eventhub.security.internal-clients.event}") String eventClient) {
        Map<String, String> clients = new HashMap<>();
        clients.put(orderClient.trim(), ROLE_ORDER);
        clients.put(analyticsClient.trim(), ROLE_ANALYTICS);
        clients.put(eventClient.trim(), ROLE_EVENT);
        if (clients.size() != 3 || clients.containsKey("")) {
            throw new IllegalStateException("eventhub.security.internal-clients must list three distinct client ids");
        }
        return new CallerAuthenticationConverter(roleClaim, clients);
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, error) -> {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("UNAUTHORIZED", "Authentication required", Instant.now(), request.getRequestURI()));
        };
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, error) -> {
            response.setStatus(403);
            response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("FORBIDDEN", "Access denied", Instant.now(), request.getRequestURI()));
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                            CallerAuthenticationConverter converter,
                                            AuthenticationEntryPoint entryPoint,
                                            AccessDeniedHandler deniedHandler) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/ticket-types").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ticket-types").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/ticket-types/*").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tickets/me", "/api/v1/tickets/*/qr").hasRole("CUSTOMER")
                        // C07 (thiết bị cổng) chưa chốt: tạm cho ORGANIZER chủ sự kiện và ADMIN quét vé.
                        .requestMatchers(HttpMethod.POST, "/api/v1/gate/check-in", "/api/v1/gate/check-out")
                        .hasAnyRole("ORGANIZER", "ADMIN")
                        // Order: giữ/xác nhận/nhả chỗ và hủy vé theo đơn.
                        .requestMatchers(HttpMethod.POST, "/internal/reservations", "/internal/reservations/*/confirm",
                                "/internal/reservations/*/release", "/internal/tickets/cancel-by-order/*")
                        .hasRole(ROLE_ORDER)
                        // Analytics (C10): chỉ đọc sức chứa.
                        .requestMatchers(HttpMethod.GET, "/internal/ticket-types/capacity").hasRole(ROLE_ANALYTICS)
                        // Event (C08): kiểm tra cấu hình vé trước khi publish.
                        .requestMatchers(HttpMethod.GET, "/internal/events/*/ticket-types/configuration").hasRole(ROLE_EVENT)
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
