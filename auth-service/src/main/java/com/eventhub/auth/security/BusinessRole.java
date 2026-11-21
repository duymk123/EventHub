package com.eventhub.auth.security;

/**
 * Business roles in EventHub application.
 * These are mapped to Keycloak realm roles.
 * Keycloak owns identity; Auth Service maps realm_access.roles to these business roles.
 */
public enum BusinessRole {
    CUSTOMER,
    ORGANIZER,
    ADMIN
}