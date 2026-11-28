package com.eventhub.auth.service;

import com.eventhub.auth.dto.response.EmailVerificationResponse;

/**
 * Xác thực email bằng mã OTP trước khi đăng ký: gửi mã → người dùng nhập mã → nhận verificationToken →
 * POST /api/v1/auth/register kèm token. Tài khoản tạo ra đã được đánh dấu emailVerified.
 */
public interface RegistrationOtpService {

    /**
     * Gửi mã OTP 6 số tới email. Email đã có tài khoản → EMAIL_ALREADY_EXISTS; gửi lại quá sớm hoặc quá nhiều →
     * TOO_MANY_REQUESTS; SMTP lỗi → EMAIL_DELIVERY_FAILED.
     */
    void sendCode(String email);

    /** Kiểm tra mã. Sai → OTP_INVALID; hết hạn/chưa gửi → OTP_EXPIRED; sai quá số lần → OTP_TOO_MANY_ATTEMPTS. */
    EmailVerificationResponse verifyCode(String email, String code);

    /** Token phải còn hạn và đúng email, nếu không → EMAIL_VERIFICATION_REQUIRED. Không xóa token. */
    void requireVerified(String email, String verificationToken);

    /** Hủy token sau khi tài khoản đã được tạo, để không dùng lại được. */
    void consume(String verificationToken);
}
