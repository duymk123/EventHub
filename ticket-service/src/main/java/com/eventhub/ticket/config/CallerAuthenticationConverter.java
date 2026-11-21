package com.eventhub.ticket.config;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.BusinessRole;
import com.eventhub.ticket.common.ServiceCaller;
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
 * Chuyển JWT đã xác minh thành principal:
 * <ul>
 *   <li>Token client_credentials của service trong danh sách cho phép (claim azp) → {@link ServiceCaller},
 *   authority ROLE_SVC_ORDER / ROLE_SVC_ANALYTICS / ROLE_SVC_EVENT, chỉ dùng cho /internal/**.</li>
 *   <li>Token người dùng → {@link Actor} với đúng một business role, authority ROLE_&lt;role&gt;.</li>
 * </ul>
 * Không đọc userId/role từ header hay body (plan chung §1).
 */
public class CallerAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

    private final String roleClaim;
    /** clientId (azp) → tên role service, vd eventhub-order-service → SVC_ORDER. */
    private final Map<String, String> internalClients;

    public CallerAuthenticationConverter(String roleClaim, Map<String, String> internalClients) {
        this.roleClaim = roleClaim;
        this.internalClients = Map.copyOf(internalClients);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt.getExpiresAt() == null) {
            throw invalid();
        }
        String clientId = jwt.getClaimAsString("azp");
        if (clientId != null && internalClients.containsKey(clientId)) {
            // Mỗi service một role riêng để SecurityConfig chỉ mở đúng nhóm /internal/** service đó cần.
            return UsernamePasswordAuthenticationToken.authenticated(new ServiceCaller(clientId), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + internalClients.get(clientId))));
        }
        Set<BusinessRole> roles = businessRoles(jwt);
        if (roles.size() != 1 || jwt.getSubject() == null || !jwt.getSubject().matches(UUID_PATTERN)) {
            throw invalid();
        }
        BusinessRole role = roles.iterator().next();
        return UsernamePasswordAuthenticationToken.authenticated(new Actor(UUID.fromString(jwt.getSubject()), role),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
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
                    // Role kỹ thuật của Keycloak (offline_access, default-roles-…) không phải business role.
                }
            }
        }
        return roles;
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException("invalid_token");
    }
}
