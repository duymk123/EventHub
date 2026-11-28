package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.RegisterOtpRequest;
import com.eventhub.auth.dto.request.VerifyRegisterOtpRequest;
import com.eventhub.auth.dto.response.EmailVerificationResponse;
import com.eventhub.auth.service.RegistrationOtpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xác thực email bằng OTP ngay trên form đăng ký, không cần mở link trong mail (công khai, chưa có token).
 */
@RestController
@RequestMapping("/api/v1/auth/register/otp")
public class RegistrationOtpController {

    private final RegistrationOtpService otpService;

    public RegistrationOtpController(RegistrationOtpService otpService) {
        this.otpService = otpService;
    }

    /** POST /api/v1/auth/register/otp {email} → 204, mã 6 số được gửi tới email (hiệu lực 5 phút). */
    @PostMapping
    public ResponseEntity<Void> send(@Valid @RequestBody RegisterOtpRequest request) {
        otpService.sendCode(request.email());
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/auth/register/otp/verify {email, code} → 200 {verificationToken, expiresIn}. */
    @PostMapping("/verify")
    public ResponseEntity<EmailVerificationResponse> verify(@Valid @RequestBody VerifyRegisterOtpRequest request) {
        return ResponseEntity.ok(otpService.verifyCode(request.email(), request.code()));
    }
}
