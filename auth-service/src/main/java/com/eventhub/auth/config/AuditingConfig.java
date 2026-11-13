package com.eventhub.auth.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Cấu hình JPA Auditing để tự động ghi nhận người tạo và người cập nhật dữ liệu.
@Configuration 
@EnableJpaAuditing (auditorAwareRef = "auditorAware")
public class AuditingConfig {
     @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            // Nếu request chưa xác thực, ghi nhận SYSTEM.
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("SYSTEM");
            }

            // Lưu Keycloak sub làm người tạo/cập nhật dữ liệu.
            return Optional.of(authentication.getName());
        };
    }
}
