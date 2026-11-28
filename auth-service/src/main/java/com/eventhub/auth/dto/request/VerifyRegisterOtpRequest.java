package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/register/otp/verify: kiểm tra mã OTP người dùng nhập. */
public record VerifyRegisterOtpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {

    @Override
    public String toString() {
        return "VerifyRegisterOtpRequest[email=" + email + "]";
    }
}
