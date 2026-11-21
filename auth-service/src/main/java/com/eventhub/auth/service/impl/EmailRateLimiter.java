package com.eventhub.auth.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn tần suất gửi mail cho các endpoint công khai (forgot-password, verify-email/resend).
 *
 * <p>Đếm theo <b>email đích</b> chứ không theo IP: mục tiêu là chống việc dùng hệ thống làm công cụ spam hộp thư
 * của một người, và IP dễ đổi còn email đích thì không.
 *
 * <p>Bộ đếm nằm trong bộ nhớ tiến trình. Với một instance thì đúng; nếu chạy nhiều instance thì mỗi instance đếm
 * riêng, giới hạn thực tế sẽ là bội số của số instance. Khi cần chặt hơn thì thay bằng Redis.
 */
@Component
public class EmailRateLimiter {
    private final Clock clock;
    private final Duration window;
    private final int maxPerWindow;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public EmailRateLimiter(@Value("${eventhub.auth.email-rate-limit.window:PT15M}") Duration window,
                            @Value("${eventhub.auth.email-rate-limit.max-per-window:3}") int maxPerWindow) {
        this.clock = Clock.systemUTC();
        this.window = window;
        this.maxPerWindow = maxPerWindow;
    }

    /**
     * Ghi nhận một lần gửi cho {@code key}. Trả {@code false} nếu đã vượt giới hạn trong cửa sổ hiện tại.
     */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Counter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(window))) {
                return new Counter(now, 1);
            }
            return new Counter(existing.windowStart(), existing.count() + 1);
        });
        if (counter.count() > maxPerWindow) {
            return false;
        }
        if (counters.size() > 10_000) {
            pruneExpired(now);
        }
        return true;
    }

    /** Dọn bộ đếm đã hết cửa sổ để map không phình khi bị quét bằng email ngẫu nhiên. */
    private void pruneExpired(Instant now) {
        counters.entrySet().removeIf(entry -> now.isAfter(entry.getValue().windowStart().plus(window)));
    }

    private record Counter(Instant windowStart, int count) {}
}
