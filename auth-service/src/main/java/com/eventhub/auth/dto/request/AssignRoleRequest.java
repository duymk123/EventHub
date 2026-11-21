package com.eventhub.auth.dto.request;

import com.eventhub.auth.security.BusinessRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assign role request (Admin only).
 * Admin assigns business role to user: CUSTOMER, ORGANIZER, ADMIN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequest {
    @NotNull
    private BusinessRole role;
}