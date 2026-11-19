package com.eventhub.event.config;

import com.eventhub.event.common.ErrorResponse;
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
 * Resource server JWT (Keycloak). Quyền theo docs/api/event-service-openapi.yaml:
 * xem/tìm event PUBLISHED và danh mục là công khai; ORGANIZER tạo/sửa/nộp/publish/đóng event của mình (owner
 * kiểm ở service); ADMIN duyệt event và quản lý danh mục; /internal/** chỉ cho service account được liệt kê.
 */
@Configuration
public class SecurityConfig {
    static final String ROLE_TICKET = "SVC_TICKET";
    static final String ROLE_ANALYTICS = "SVC_ANALYTICS";

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
            @Value("${eventhub.security.internal-clients.ticket}") String ticketClient,
            @Value("${eventhub.security.internal-clients.analytics}") String analyticsClient) {
        Map<String, String> clients = new HashMap<>();
        clients.put(ticketClient.trim(), ROLE_TICKET);
        clients.put(analyticsClient.trim(), ROLE_ANALYTICS);
        if (clients.size() != 2 || clients.containsKey("")) {
            throw new IllegalStateException("eventhub.security.internal-clients must list two distinct client ids");
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
                        // Công khai: tìm/xem event PUBLISHED, danh sách danh mục.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/categories")
                        .permitAll()
                        // ORGANIZER: vòng đời event của mình.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/events/*").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/submit", "/api/v1/events/*/publish",
                                "/api/v1/events/*/close").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/organizer/events").hasRole("ORGANIZER")
                        // ADMIN: duyệt event, quản lý danh mục.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/events/*/review").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/*").hasRole("ADMIN")
                        // Nội bộ: path literal /ids trước /{id}. Ticket chỉ cần INT-01; Analytics dùng INT-01/02/03.
                        .requestMatchers(HttpMethod.GET, "/internal/events/ids", "/internal/events")
                        .hasRole(ROLE_ANALYTICS)
                        .requestMatchers(HttpMethod.GET, "/internal/events/*").hasAnyRole(ROLE_TICKET, ROLE_ANALYTICS)
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
