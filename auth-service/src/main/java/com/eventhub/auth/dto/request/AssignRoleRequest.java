package com.eventhub.auth.dto.request;

import com.eventhub.auth.common.BusinessRole;
import jakarta.validation.constraints.NotNull;

/**
 * PATCH /api/v1/admin/users/{userId}/role.
 */
public record AssignRoleRequest(@NotNull BusinessRole role) {
}
