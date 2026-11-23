package com.eventhub.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT đã xác minh → đúng một business role (CUSTOMER/ORGANIZER/ADMIN) đọc từ claim cấu hình, mặc định
 * realm_access.roles. Role kỹ thuật của Keycloak bị bỏ qua; thiếu role hoặc mang nhiều business role thì không có
 * authority nào (fail closed), các route cần role sẽ trả 403. Không đọc role từ header.
 */
public class BusinessRoleConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {
    private static final Set<String> BUSINESS_ROLES = Set.of("CUSTOMER", "ORGANIZER", "ADMIN");

    private final String[] claimPath;

    public BusinessRoleConverter(String roleClaim) {
        this.claimPath = roleClaim.split("\\.");
    }

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        List<String> roles = rolesOf(jwt).stream().filter(BUSINESS_ROLES::contains).distinct().toList();
        List<GrantedAuthority> authorities = roles.size() == 1
                ? List.of(new SimpleGrantedAuthority("ROLE_" + roles.get(0)))
                : List.of();
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
    }

    private Collection<String> rolesOf(Jwt jwt) {
        Object node = jwt.getClaims();
        for (String key : claimPath) {
            if (!(node instanceof Map<?, ?> map)) {
                return List.of();
            }
            node = map.get(key);
        }
        if (!(node instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
