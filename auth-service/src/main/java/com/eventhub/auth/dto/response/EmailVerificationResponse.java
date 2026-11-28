package com.eventhub.auth.dto.response;

/**
 * Kết quả xác thực OTP thành công.
 *
 * @param verificationToken gửi kèm POST /api/v1/auth/register để chứng minh email đã được xác thực.
 * @param expiresIn số giây token còn hiệu lực.
 */
public record EmailVerificationResponse(String verificationToken, long expiresIn) {
}
