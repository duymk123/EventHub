package com.eventhub.auth.dto.response;

/**
 * C13: body của login/refresh chỉ có access token. Refresh token nằm trong cookie HttpOnly eh_refresh nên
 * JavaScript của frontend không đọc được.
 *
 * @param expiresIn Số giây access token còn hiệu lực (realm đặt 300); frontend dùng để hẹn refresh.
 */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static AccessTokenResponse from(TokenResponse token) {
        return new AccessTokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }
}
