package com.eventhub.auth.service.impl;

import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Gửi thư chứa mã OTP đăng ký qua SMTP cấu hình bằng MAIL_* (mặc định Mailpit, có thể là Gmail). */
@Component
public class OtpMailSender {
    private static final Logger log = LoggerFactory.getLogger(OtpMailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public OtpMailSender(JavaMailSender mailSender,
                         @Value("${eventhub.auth.mail-from:noreply@eventhub.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String code, Duration ttl) {
        long minutes = ttl.toMinutes();
        String text = "Mã xác thực EventHub của bạn là: " + code + "\n\n"
                + "Mã có hiệu lực trong " + minutes + " phút. Không chia sẻ mã này cho bất kỳ ai.\n"
                + "Nếu bạn không đăng ký tài khoản EventHub, hãy bỏ qua thư này.";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;color:#111">
                  <h2 style="margin:0 0 12px">Xác thực email EventHub</h2>
                  <p>Nhập mã dưới đây vào trang đăng ký để xác thực email của bạn:</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:8px;margin:20px 0;color:#7c3aed">%s</p>
                  <p>Mã có hiệu lực trong %d phút. Không chia sẻ mã này cho bất kỳ ai.</p>
                  <p style="color:#666;font-size:13px">Nếu bạn không đăng ký tài khoản EventHub, hãy bỏ qua thư này.</p>
                </div>
                """.formatted(code, minutes);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "EventHub");
            helper.setTo(to);
            helper.setSubject("Mã xác thực EventHub: " + code);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException error) {
            // Không log mã OTP; chỉ log loại lỗi SMTP để chẩn đoán cấu hình.
            log.warn("Could not send registration OTP email: {}", error.getMessage());
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED, "Could not send verification code");
        }
    }
}
