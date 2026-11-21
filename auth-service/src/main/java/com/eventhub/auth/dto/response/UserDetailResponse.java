package com.eventhub.auth.dto.response;

import com.eventhub.auth.security.BusinessRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed user response for admin user detail view.
 * Includes email verification status and username.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailResponse {
    private UUID id;
    private String email;
    private String fullName;
    private BusinessRole role;
    private UserStatus status;
    private Instant createdAt;
    private boolean emailVerified;
    private Instant lastLoginAt;
    private String username;

    public enum UserStatus {
        ACTIVE, LOCKED
    }
}