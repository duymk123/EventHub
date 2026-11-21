package com.eventhub.auth.security;

import java.util.UUID;

/**
 * Authenticated actor (user) extracted from JWT.
 * Contains userId (Keycloak sub claim) and business role.
 * Used for authorization decisions in services.
 */
public record Actor(UUID userId, BusinessRole role) {
}