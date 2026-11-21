package com.eventhub.auth.config;

import com.eventhub.auth.security.IdentityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for Auth Service.
 * - Stateless JWT authentication (Keycloak issued tokens)
 * - Public endpoints for auth flows (register, login, refresh, logout, password reset, email verification)
 * - Authenticated endpoints for user self-service (/auth/me/**)
 * - Admin endpoints for user management (/admin/** requires ADMIN role)
 * - IdentityMapper bean for JWT -> Actor conversion
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        // Public auth endpoints
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification").permitAll()
                        // User self-service endpoints (require authentication)
                        .requestMatchers("/api/v1/auth/me/**").authenticated()
                        // Admin endpoints (require ADMIN role)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                );

        return http.build();
    }

    /**
     * JWT Authentication Converter for Spring Security.
     * Maps realm_access.roles to ROLE_* authorities.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObject = realmAccess.get("roles");
                if (rolesObject instanceof List<?> roles) {
                    roles.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .forEach(authorities::add);
                }
            }

            return new JwtAuthenticationToken(jwt, authorities);
        };
    }

    /**
     * IdentityMapper for extracting Actor (userId + businessRole) from JWT.
     * Used in service layer for authorization decisions.
     */
    @Bean
    public IdentityMapper identityMapper() {
        return new IdentityMapper();
    }
}