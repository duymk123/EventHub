package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakUser;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationOtpServiceImplTest {
    private KeycloakUserClient users;
    private OtpMailSender mail;
    private RegistrationOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(KeycloakUserClient.class);
        mail = mock(OtpMailSender.class);
        // Cooldown 0 để test gửi lại mã ngay; các giá trị khác như cấu hình thật.
        service = new RegistrationOtpServiceImpl(users, mail, Duration.ofMinutes(5), Duration.ZERO, 5, 5,
                Duration.ofMinutes(30));
    }

    private String sendAndCaptureCode(String email) {
        service.sendCode(email);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail, org.mockito.Mockito.atLeastOnce()).send(eq(email.trim().toLowerCase()), code.capture(), any());
        return code.getValue();
    }

    @Test
    void correctCodeReturnsTokenBoundToEmail() {
        String code = sendAndCaptureCode("A@Gmail.com");
        assertThat(code).matches("\\d{6}");

        String token = service.verifyCode("a@gmail.com", code).verificationToken();

        service.requireVerified("A@gmail.com", token);
        assertCode(() -> service.requireVerified("other@gmail.com", token), ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        // Mã đã dùng không xác thực lại được.
        assertCode(() -> service.verifyCode("a@gmail.com", code), ErrorCode.OTP_EXPIRED);
        service.consume(token);
        assertCode(() -> service.requireVerified("a@gmail.com", token), ErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }

    @Test
    void wrongCodeFiveTimesCancelsTheCode() {
        String code = sendAndCaptureCode("a@gmail.com");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertCode(() -> service.verifyCode("a@gmail.com", wrong), ErrorCode.OTP_INVALID);
        }
        assertCode(() -> service.verifyCode("a@gmail.com", wrong), ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        assertCode(() -> service.verifyCode("a@gmail.com", code), ErrorCode.OTP_EXPIRED);
    }

    @Test
    void registeredEmailGetsNoCode() {
        when(users.findByEmailExact("a@gmail.com"))
                .thenReturn(new KeycloakUser("id", "a@gmail.com", "A", "B", true, true, 0L));

        assertCode(() -> service.sendCode("a@gmail.com"), ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(mail, never()).send(anyString(), anyString(), any());
    }

    @Test
    void failedMailDoesNotLeaveAUsableCode() {
        doThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED, "smtp"))
                .when(mail).send(anyString(), anyString(), any());

        assertCode(() -> service.sendCode("a@gmail.com"), ErrorCode.EMAIL_DELIVERY_FAILED);
        assertCode(() -> service.verifyCode("a@gmail.com", "123456"), ErrorCode.OTP_EXPIRED);
    }

    @Test
    void resendIsLimitedPerHour() {
        for (int i = 0; i < 5; i++) {
            service.sendCode("a@gmail.com");
        }
        assertCode(() -> service.sendCode("a@gmail.com"), ErrorCode.TOO_MANY_REQUESTS);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
