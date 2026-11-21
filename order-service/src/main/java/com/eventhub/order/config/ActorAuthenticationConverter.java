package com.eventhub.order.config;

import com.eventhub.order.common.Actor;
import com.eventhub.order.common.BusinessRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chuyển JWT đã xác minh thành {@link Actor}: sub UUID, đúng một business role, email khi email_verified.
 * Không đọc userId/role từ header hay body (plan chung §1).
 */
public class ActorAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

    private final String roleClaim;

    public ActorAuthenticationConverter(String roleClaim) {
        this.roleClaim = roleClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<BusinessRole> roles = businessRoles(jwt);
        if (jwt.getExpiresAt() == null || roles.size() != 1 || jwt.getSubject() == null
                || !jwt.getSubject().matches(UUID_PATTERN)) {
            throw new OAuth2AuthenticationException("invalid_token");
        }
        BusinessRole role = roles.iterator().next();
        String email = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) ? jwt.getClaimAsString("email") : null;
        Actor actor = new Actor(UUID.fromString(jwt.getSubject()), role, email);
        return UsernamePasswordAuthenticationToken.authenticated(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private Set<BusinessRole> businessRoles(Jwt jwt) {
        Object value = jwt.getClaims();
        for (String component : roleClaim.split("\\.")) {
            value = value instanceof Map<?, ?> map ? map.get(component) : null;
        }
        Collection<?> values = value instanceof Collection<?> collection
                ? collection : value instanceof String role ? List.of(role) : List.of();
        Set<BusinessRole> roles = new HashSet<>();
        for (Object candidate : values) {
            if (candidate instanceof String name) {
                try {
                    roles.add(BusinessRole.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // Role kỹ thuật của Keycloak không phải business role.
                }
            }
        }
        return roles;
    }
}
