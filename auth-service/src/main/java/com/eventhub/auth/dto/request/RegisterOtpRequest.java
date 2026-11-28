package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/register/otp: gửi mã OTP 6 số tới email sắp đăng ký. */
public record RegisterOtpRequest(@NotBlank @Email @Size(max = 255) String email) {
}
