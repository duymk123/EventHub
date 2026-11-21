package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for refreshing access token using refresh token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCRefreshCommand {
    private String refreshToken;
}