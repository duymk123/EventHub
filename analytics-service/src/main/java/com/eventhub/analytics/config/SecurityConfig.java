package com.eventhub.analytics.config;

import com.eventhub.analytics.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import java.time.Clock;
import java.time.Instant;

/**
 * Cấu hình Resource Server cho REST, xác minh issuer, audience, chữ ký và thời hạn JWT rồi ánh xạ sang Actor.
 * Các bean entry point/access denied tạo JSON lỗi cho request bị từ chối ở security filter.
 */
@Configuration
public class SecurityConfig {
    /**
     * Cung cấp Clock UTC để các service dùng cùng cách lấy thời gian và có thể truyền đồng hồ kiểm soát được
     * khi chạy kiểm chứng.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Đọc issuer, audience, JWK URL và thuật toán cho phép. Decoder xác minh JWT bằng khóa công khai, kiểm tra
     * issuer/thời hạn và audience, cấu hình thiếu sẽ từ chối token.
     */
    @Bean
    JwtDecoder jwtDecoder(Environment environment) {
        String issuer = environment.getProperty("eventhub.security.issuer", "");
        String audience = environment.getProperty("eventhub.security.audience", "");
        String jwks = environment.getProperty("eventhub.security.jwk-set-uri", "");
        if (issuer.isBlank() || audience.isBlank() || jwks.isBlank()) {
            return token -> { throw new BadJwtException("Identity configuration is incomplete"); };
        }
        String algorithm = environment.getProperty("eventhub.security.algorithm", "RS256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks)
                .jwsAlgorithm(SignatureAlgorithm.from(algorithm)).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    /**
     * Tạo adapter dùng đường dẫn claim role của môi trường, chẳng hạn realm_access.roles. Kết quả adapter là
     * Authentication có principal Actor.
     */
    @Bean
    ActorAuthenticationConverter actorAuthenticationConverter(Environment environment) {
        return new ActorAuthenticationConverter(environment.getProperty("eventhub.security.role-claim", ""));
    }

    /**
     * Xử lý request chưa xác thực hoặc token không hợp lệ ở tầng filter. Trả 401 với Bearer challenge và đủ bốn
     * trường ErrorResponse.
     */
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

    /**
     * Xử lý tài khoản đã xác thực nhưng thiếu quyền ở tầng filter. Trả 403 theo cùng cấu trúc JSON lỗi của ứng
     * dụng.
     */
    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, error) -> {
            response.setStatus(403);
            response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("FORBIDDEN", "Access denied", Instant.now(), request.getRequestURI()));
        };
    }

    /**
     * Lắp chuỗi filter stateless cho REST Bearer JWT và các matcher quyền của service. Request được xác thực
     * trước khi controller đọc @AuthenticationPrincipal.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
            ActorAuthenticationConverter converter, AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler deniedHandler) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/ws/dashboard").permitAll()
                            .requestMatchers("/api/v1/analytics/**").hasAnyRole("ADMIN", "ORGANIZER")
                            .anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
