package com.eventhub.auth.config;

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

@Configuration
public class SecurityConfig {

// Cấu hình bảo mật cho ứng dụng, bao gồm xác thực JWT và phân quyền truy cập API
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter
    ) throws Exception {

        http
            // vì API sử dụng Bearer Token, không dùng session đăng nhập -> có thể tắt CSRF (cross-site request forgery) để tránh lỗi 403 khi gửi request từ client
            .csrf(csrf -> csrf.disable())

            // mọi request đều phải xác thực, không sử dụng session để lưu thông tin đăng nhập
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Cấu hình phân quyền truy cập API
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()

                // Chỉ ADMIN được truy cập API quản trị
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Các API còn lại yêu cầu đăng nhập
                .anyRequest().authenticated()
            )

            // Xác minh JWT do Keycloak phát hành
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            );

        return http.build();
    }


// Chuyển đổi JWT thành AuthenticationToken, 
// ánh xạ các Realm Roles từ JWT thành GrantedAuthority để Spring Security có thể phân quyền truy cập API dựa trên vai trò của người dùng.
// Hay đơn giản là chuyển roles trong JWT thành ROLE_ten-role để Spring Security hiểu và phân quyền.    
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken>
    jwtAuthenticationConverter() {

        return jwt -> {
            Collection<GrantedAuthority> authorities =
                    new ArrayList<>();

            // Đọc Realm Roles từ JWT
            Map<String, Object> realmAccess =
                    jwt.getClaim("realm_access");

            if (realmAccess != null) {
                Object rolesObject = realmAccess.get("roles");

                if (rolesObject instanceof List<?> roles) {
                    roles.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(role -> new SimpleGrantedAuthority(
                                "ROLE_" + role
                        ))
                        .forEach(authorities::add);
                }
            }

            // Tạo JwtAuthenticationToken với các GrantedAuthority đã ánh xạ từ Realm Roles
            return new JwtAuthenticationToken(jwt, authorities);
        };
    }
}