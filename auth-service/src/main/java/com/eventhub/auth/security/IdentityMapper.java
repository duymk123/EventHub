package com.eventhub.auth.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps JWT to Spring Security Authentication and extracts Actor for business logic.
 * - Converts JWT realm_access.roles to GrantedAuthority (ROLE_* prefix)
 * - Extracts userId (sub claim) and business role for service layer authorization
 */
@Component
public class IdentityMapper implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Extract Actor (userId + businessRole) from JWT for service layer.
     * Throws if JWT is missing required claims.
     */
    public Actor toActor(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("JWT missing subject claim");
        }

        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT subject is not a valid UUID: " + sub);
        }

        BusinessRole role = extractBusinessRole(jwt);
        if (role == null) {
            throw new IllegalArgumentException("JWT missing valid business role (CUSTOMER/ORGANIZER/ADMIN)");
        }

        return new Actor(userId, role);
    }

    /**
     * Extract business role from realm_access.roles claim.
     * Returns first matching business role (CUSTOMER, ORGANIZER, ADMIN).
     */
    private BusinessRole extractBusinessRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null || roles.isEmpty()) {
            return null;
        }

        return roles.stream()
                .filter(r -> r.equals("CUSTOMER") || r.equals("ORGANIZER") || r.equals("ADMIN"))
                .map(BusinessRole::valueOf)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extract all realm roles as GrantedAuthority for Spring Security.
     * Adds ROLE_ prefix for standard Spring Security role checking.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null) {
            return List.of();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }
}