package com.eventhub.notification.config;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.common.BusinessRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;

/**
 * Chuyển Jwt đã được JwtDecoder xác minh thành Authentication có principal kiểu Actor. Đường dẫn claim role do
 * cấu hình quyết định, tài khoản cần đúng một business role hợp lệ.
 */
public class ActorAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final String roleClaim;

    public ActorAuthenticationConverter(String roleClaim) {
        this.roleClaim = roleClaim;
    }

    /**
     * Tách role theo đường dẫn cấu hình, lọc các business role và kiểm tra subject UUID cùng claim exp. Tạo
     * Actor và ROLE_ authority khi có đúng một business role, dữ liệu thiếu/sai làm xác thực thất bại.
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (roleClaim.isBlank() || jwt.getExpiresAt() == null) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token");
        }
        Object value = jwt.getClaims();
        for (String component : roleClaim.split("\\.")) {
            value = value instanceof Map<?, ?> map ? map.get(component) : null;
        }
        Collection<?> values = value instanceof Collection<?> collection
                ? collection : value instanceof String role ? List.of(role) : List.of();
        Set<BusinessRole> roles = new HashSet<>();
        for (Object candidate : values) {
            if (candidate instanceof String role) {
                try {
                    roles.add(BusinessRole.valueOf(role));
                } catch (IllegalArgumentException ignored) {
                    // Bỏ qua role kỹ thuật của Keycloak khi xác định quyền nghiệp vụ EventHub.
                }
            }
        }
        try {
            if (roles.size() != 1 || jwt.getSubject() == null
                    || !jwt.getSubject().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                throw new IllegalArgumentException();
            }
            BusinessRole role = roles.iterator().next();
            Actor actor = new Actor(UUID.fromString(jwt.getSubject()), role);
            return UsernamePasswordAuthenticationToken.authenticated(actor, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        } catch (IllegalArgumentException error) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token");
        }
    }
}
