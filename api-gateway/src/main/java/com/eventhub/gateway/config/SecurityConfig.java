package com.eventhub.gateway.config;

import com.eventhub.gateway.exception.GatewayErrorWriter;
import com.eventhub.gateway.security.BusinessRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security tại rìa (plan api-gateway §4). Luật theo method + path chép đúng SecurityConfig của từng service, để
 * request sai quyền dừng ở Gateway; service vẫn tự kiểm lại (owner, trạng thái). /internal/** luôn bị chặn, đường
 * không khai báo bị từ chối. Chỉ nhận Bearer trong header nên tắt CSRF; khi Auth chuyển refresh token sang cookie
 * (C13) thì chỉ các endpoint /api/v1/auth/refresh, /logout dùng cookie và cần xem lại CSRF cho riêng chúng.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    private static final String ADMIN = "ADMIN";
    private static final String ORGANIZER = "ORGANIZER";
    private static final String CUSTOMER = "CUSTOMER";
    private static final String[] BUSINESS = {CUSTOMER, ORGANIZER, ADMIN};

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
                                                  @Value("${eventhub.security.role-claim}") String roleClaim) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, error) -> {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            return GatewayErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Authentication required");
        };
        ServerAccessDeniedHandler forbidden = (exchange, error) ->
                GatewayErrorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth -> auth
                        // ---- không bao giờ đi qua Gateway
                        .pathMatchers("/internal/**", "/api/v1/internal/**").denyAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers("/actuator/**").denyAll()

                        // ---- Auth
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/forgot-password")
                        .permitAll()
                        .pathMatchers("/api/v1/admin/users", "/api/v1/admin/users/**").hasRole(ADMIN)
                        .pathMatchers("/api/v1/auth/**", "/api/v1/me/**").hasAnyRole(BUSINESS)

                        // ---- Event
                        .pathMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/categories")
                        .permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/events").hasRole(ORGANIZER)
                        .pathMatchers(HttpMethod.PUT, "/api/v1/events/*").hasRole(ORGANIZER)
                        .pathMatchers(HttpMethod.POST, "/api/v1/events/*/submit", "/api/v1/events/*/publish",
                                "/api/v1/events/*/close").hasRole(ORGANIZER)
                        .pathMatchers(HttpMethod.GET, "/api/v1/organizer/events").hasRole(ORGANIZER)
                        .pathMatchers(HttpMethod.GET, "/api/v1/admin/events").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/admin/events/*/review").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/categories").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.PUT, "/api/v1/categories/*").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/categories/*").hasRole(ADMIN)

                        // ---- Ticket
                        .pathMatchers(HttpMethod.GET, "/api/v1/events/*/ticket-types", "/api/v1/ticket-types/*/seats")
                        .permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/ticket-types").hasRole(ORGANIZER)
                        .pathMatchers(HttpMethod.PUT, "/api/v1/ticket-types/*").hasRole(ORGANIZER)
                        .pathMatchers(HttpMethod.GET, "/api/v1/tickets/me", "/api/v1/tickets/*/qr").hasRole(CUSTOMER)
                        .pathMatchers(HttpMethod.POST, "/api/v1/gate/check-in", "/api/v1/gate/check-out")
                        .hasAnyRole(ORGANIZER, ADMIN)

                        // ---- Order (IPN VNPay public, tin cậy bằng chữ ký HMAC tại Order)
                        .pathMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/ipn").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/*/payment",
                                "/api/v1/orders/*/payment/verify", "/api/v1/orders/*/cancel",
                                "/api/v1/orders/*/refunds").hasRole(CUSTOMER)
                        .pathMatchers(HttpMethod.GET, "/api/v1/orders/me", "/api/v1/orders/*",
                                "/api/v1/orders/*/refunds").hasRole(CUSTOMER)
                        .pathMatchers(HttpMethod.GET, "/api/v1/admin/refunds", "/api/v1/admin/payments/reconciliation")
                        .hasRole(ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/refunds/*/approve",
                                "/api/v1/admin/refunds/*/reject", "/api/v1/admin/refunds/*/retry",
                                "/api/v1/admin/payments/*/resolve").hasRole(ADMIN)

                        // ---- Notification
                        .pathMatchers("/api/v1/admin/notification-templates", "/api/v1/admin/notification-templates/**")
                        .hasRole(ADMIN)
                        .pathMatchers("/api/v1/notifications/**").hasAnyRole(BUSINESS)

                        // ---- Analytics (handshake WS public; JWT kiểm ở STOMP CONNECT tại Analytics)
                        .pathMatchers(HttpMethod.GET, "/ws/dashboard").permitAll()
                        .pathMatchers("/api/v1/analytics/**").hasAnyRole(ADMIN, ORGANIZER)

                        .anyExchange().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(new BusinessRoleConverter(roleClaim)))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    /** Cùng cách xác minh với các service: RS256, issuer, audience nếu cấu hình, exp/nbf mặc định. */
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${eventhub.security.issuer}") String issuer,
                                  @Value("${eventhub.security.jwk-set-uri}") String jwkSetUri,
                                  @Value("${eventhub.security.audience:}") String audience) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
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

    /**
     * Gateway là nơi duy nhất trả header CORS. allowCredentials để frontend gửi cookie refresh token (C13), nên
     * origin phải liệt kê cụ thể.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${eventhub.gateway.cors-allowed-origins}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.contains("*")) {
            throw new IllegalArgumentException("CORS with credentials requires explicit origins");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowed);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept"));
        cors.setExposedHeaders(List.of("X-Request-Id", "WWW-Authenticate"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
