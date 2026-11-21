package com.eventhub.auth.common;

/**
 * Business role EventHub, trùng tên realm role Keycloak. Mỗi tài khoản đúng một role.
 */
public enum BusinessRole {
    CUSTOMER,
    ORGANIZER,
    ADMIN;

    public static boolean isBusinessRole(String name) {
        for (BusinessRole role : values()) {
            if (role.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
