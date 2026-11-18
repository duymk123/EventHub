package com.eventhub.ticket.common;

/**
 * Service nội bộ đã xác thực bằng token client_credentials của Keycloak; clientId lấy từ claim azp.
 */
public record ServiceCaller(String clientId) {
}
