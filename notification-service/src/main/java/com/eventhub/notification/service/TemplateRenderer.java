package com.eventhub.notification.service;

import com.samskivert.mustache.Mustache;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Kiểm tra và render mẫu Mustache bằng model của sự kiện. Biến được escape HTML, lỗi render được chuyển thành
 * thông báo an toàn trước khi đi vào cơ chế retry.
 */
@Component
public class TemplateRenderer {
    /**
     * Biên dịch thử mẫu và từ chối các cú pháp biến Mustache bỏ escape HTML. TemplateServiceImpl gọi bước này
     * trước khi lưu mẫu ADMIN gửi lên.
     */
    public void validate(String template) {
        if (template.contains("{{{") || template.matches("(?s).*\\{\\{\\s*&.*")) {
            throw new IllegalArgumentException("Unescaped template variables are not allowed");
        }
        Mustache.compiler().escapeHTML(true).compile(template);
    }

    /**
     * Điền model vào mẫu với HTML escaping mặc định. Khi lỗi, ném thông báo chung để cơ chế retry xử lý mà
     * không đưa model chứa email/QR vào log.
     */
    public String render(String template, Map<String, Object> model) {
        try {
            // Biên dịch thử mẫu và từ chối các cú pháp biến Mustache bỏ escape HTML. TemplateServiceImpl gọi
            // bước này trước khi lưu mẫu ADMIN gửi lên.
            validate(template);
            return Mustache.compiler().escapeHTML(true).compile(template).execute(model);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Notification template rendering failed");
        }
    }
}
