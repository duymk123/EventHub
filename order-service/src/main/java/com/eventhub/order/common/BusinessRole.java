package com.eventhub.order.common;

/**
 * Business role EventHub, ánh xạ từ realm role Keycloak. Mỗi tài khoản đúng một role.
 */
public enum BusinessRole {
    CUSTOMER,
    ORGANIZER,
    ADMIN
}
