package com.eventhub.auth.dto.response;

/**
 * Token do Keycloak cấp, Auth Service chỉ đổi tên trường cho đúng hợp đồng API, không tự ký token.
 *
 * @param expiresIn Số giây access token còn hiệu lực (realm đặt 300).
 * @param refreshExpiresIn Số giây refresh token còn hiệu lực.
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                            long refreshExpiresIn) {}
