package com.eventhub.auth.dto.response;

import com.eventhub.auth.security.BusinessRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * User response for /auth/me and admin list.
 * Contains essential user info from Keycloak + mapped business role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private BusinessRole role;
    private UserStatus status;
    private Instant createdAt;

    public enum UserStatus {
        ACTIVE, LOCKED
    }
}