package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.response.EmailVerificationResponse;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.service.RegistrationOtpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP đăng ký giữ trong bộ nhớ tiến trình (giống {@link EmailRateLimiter}): mã sống vài phút, auth-service khởi động
 * lại thì người dùng chỉ cần bấm "Lấy mã" lần nữa. Chạy nhiều instance thì cần chuyển sang Redis/DB.
 *
 * <p>Chỉ lưu SHA-256 của mã, so sánh thời gian hằng; sai quá {@code maxAttempts} lần thì hủy mã để không dò được
 * 10^6 tổ hợp. Gửi lại mã phải cách nhau {@code resendCooldown} và tối đa {@code maxSendsPerHour} lần mỗi giờ cho
 * một email, để endpoint công khai này không bị dùng spam hộp thư người khác.
 */
@Service
public class RegistrationOtpServiceImpl implements RegistrationOtpService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeycloakUserClient userClient;
    private final OtpMailSender mailSender;
    private final Clock clock = Clock.systemUTC();
    private final Duration codeTtl;
    private final Duration resendCooldown;
    private final Duration tokenTtl;
    private final int maxAttempts;
    private final EmailRateLimiter hourlyLimiter;

    private final Map<String, PendingCode> pending = new ConcurrentHashMap<>();
    private final Map<String, VerifiedEmail> verified = new ConcurrentHashMap<>();

    public RegistrationOtpServiceImpl(KeycloakUserClient userClient, OtpMailSender mailSender,
                                      @Value("${eventhub.auth.otp.ttl:PT5M}") Duration codeTtl,
                                      @Value("${eventhub.auth.otp.resend-cooldown:PT60S}") Duration resendCooldown,
                                      @Value("${eventhub.auth.otp.max-attempts:5}") int maxAttempts,
                                      @Value("${eventhub.auth.otp.max-sends-per-hour:5}") int maxSendsPerHour,
                                      @Value("${eventhub.auth.otp.verification-ttl:PT30M}") Duration tokenTtl) {
        this.userClient = userClient;
        this.mailSender = mailSender;
        this.codeTtl = codeTtl;
        this.resendCooldown = resendCooldown;
        this.maxAttempts = maxAttempts;
        this.tokenTtl = tokenTtl;
        this.hourlyLimiter = new EmailRateLimiter(Duration.ofHours(1), maxSendsPerHour);
    }

    @Override
    public void sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();
        PendingCode previous = pending.get(email);
        if (previous != null && now.isBefore(previous.sentAt().plus(resendCooldown))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Please wait before requesting a new code");
        }
        // Đăng ký đã báo EMAIL_ALREADY_EXISTS, nên báo sớm ở đây không lộ thêm thông tin.
        if (userClient.findByEmailExact(email) != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        }
        if (!hourlyLimiter.tryAcquire(email)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Too many verification codes requested");
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        // Mã mới thay mã cũ; gửi lỗi thì bỏ mã để người dùng thử lại ngay, không phải chờ cooldown.
        pending.put(email, new PendingCode(hash(email, code), now, now.plus(codeTtl), 0));
        try {
            mailSender.send(email, code, codeTtl);
        } catch (RuntimeException error) {
            pending.remove(email);
            throw error;
        }
        pruneExpired(now);
    }

    @Override
    public EmailVerificationResponse verifyCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();
        PendingCode current = pending.get(email);
        if (current == null || now.isAfter(current.expiresAt())) {
            pending.remove(email);
            throw new BusinessException(ErrorCode.OTP_EXPIRED, "Verification code expired, request a new one");
        }
        if (!MessageDigest.isEqual(current.codeHash(), hash(email, code.trim()))) {
            int attempts = current.attempts() + 1;
            if (attempts >= maxAttempts) {
                pending.remove(email);
                throw new BusinessException(ErrorCode.OTP_TOO_MANY_ATTEMPTS, "Too many wrong codes, request a new one");
            }
            pending.put(email, new PendingCode(current.codeHash(), current.sentAt(), current.expiresAt(), attempts));
            throw new BusinessException(ErrorCode.OTP_INVALID, "Verification code is incorrect");
        }
        pending.remove(email);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verified.put(token, new VerifiedEmail(email, now.plus(tokenTtl)));
        return new EmailVerificationResponse(token, tokenTtl.toSeconds());
    }

    @Override
    public void requireVerified(String rawEmail, String verificationToken) {
        VerifiedEmail entry = verificationToken == null ? null : verified.get(verificationToken);
        if (entry == null || !entry.email().equals(normalize(rawEmail)) || clock.instant().isAfter(entry.expiresAt())) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED, "Email must be verified with a code first");
        }
    }

    @Override
    public void consume(String verificationToken) {
        if (verificationToken != null) {
            verified.remove(verificationToken);
        }
    }

    private void pruneExpired(Instant now) {
        if (pending.size() + verified.size() < 1_000) {
            return;
        }
        pending.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        verified.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Gắn email vào hash để mã của email này không khớp với bản ghi của email khác. */
    private static byte[] hash(String email, String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest((email + ":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record PendingCode(byte[] codeHash, Instant sentAt, Instant expiresAt, int attempts) {}

    private record VerifiedEmail(String email, Instant expiresAt) {}
}
