package com.eventhub.order.config;

import com.eventhub.order.common.ErrorResponse;
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

/**
 * Resource server JWT (Keycloak). Đơn hàng chỉ dành cho CUSTOMER; customerId lấy từ JWT, không từ header.
 * C06: IPN VNPay public (tin cậy bằng chữ ký); khởi tạo/đối soát thanh toán và hủy đơn (C19) cho CUSTOMER;
 * hoàn tiền (C04/C05): CUSTOMER yêu cầu, ADMIN duyệt/từ chối/thử lại; danh sách đối soát (C21) cho ADMIN.
 * Đường khác bị chặn.
 */
@Configuration
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

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
    ActorAuthenticationConverter actorAuthenticationConverter(@Value("${eventhub.security.role-claim}") String roleClaim) {
        return new ActorAuthenticationConverter(roleClaim);
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
                                            ActorAuthenticationConverter converter,
                                            AuthenticationEntryPoint entryPoint,
                                            AccessDeniedHandler deniedHandler) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/ipn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/payment", "/api/v1/orders/*/payment/verify",
                                "/api/v1/orders/*/cancel")
                        .hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/refunds").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/refunds").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/payments/reconciliation", "/api/v1/admin/refunds")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/payments/*/resolve",
                                "/api/v1/admin/refunds/*/approve", "/api/v1/admin/refunds/*/reject",
                                "/api/v1/admin/refunds/*/retry")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/me", "/api/v1/orders/*").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
