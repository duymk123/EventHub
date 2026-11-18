package com.eventhub.ticket.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sinh giá trị QR opaque: 32 byte ngẫu nhiên mạnh, base64url (43 ký tự). Không suy ra từ ticketId hay thời gian
 * (plan Ticket §5); ảnh QR do Notification dựng khi gửi email.
 */
@Component
public class QrTokenGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
