package com.eventhub.notification.service.impl;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Seed các mẫu mặc định sau khi Spring khởi tạo singleton và trước khi consumer bắt đầu nhận record. UUID seed
 * ổn định theo code/channel, nội dung mẫu đã có được giữ lại.
 */
@Component
public class TemplateInitializer implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;

    public TemplateInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Callback Spring chạy sau khi khởi tạo singleton, seed ba mã template cho hai kênh trước giai đoạn
     * listener container bắt đầu hoạt động.
     */
    @Override
    public void afterSingletonsInstantiated() {
        // Tạo UUID ổn định theo code/channel và INSERT mẫu nếu chưa có. ON DUPLICATE KEY giữ nội dung bản ghi
        // đang có để bảo toàn chỉnh sửa của ADMIN.
        seed("ORDER_FAILED", "Đơn hàng chưa hoàn tất", "Đơn {{orderId}} chưa hoàn tất. Lý do {{reason}}.");
        // Tạo UUID ổn định theo code/channel và INSERT mẫu nếu chưa có. ON DUPLICATE KEY giữ nội dung bản ghi
        // đang có để bảo toàn chỉnh sửa của ADMIN.
        seed("ORDER_REFUNDED", "Đơn hàng đã hoàn tiền", "Đơn {{orderId}} đã hoàn {{amount}} VND.");
        // Tạo UUID ổn định theo code/channel và INSERT mẫu nếu chưa có. ON DUPLICATE KEY giữ nội dung bản ghi
        // đang có để bảo toàn chỉnh sửa của ADMIN.
        seed("TICKET_ISSUED", "Vé của bạn đã sẵn sàng", "Vé cho {{eventTitle}} đã sẵn sàng. Đơn {{orderId}}.");
    }

    /**
     * Tạo UUID ổn định theo code/channel và INSERT mẫu nếu chưa có. ON DUPLICATE KEY giữ nội dung bản ghi đang
     * có để bảo toàn chỉnh sửa của ADMIN.
     */
    private void seed(String code, String subject, String content) {
        for (String channel : new String[]{"IN_APP", "EMAIL"}) {
            String body = content;
            if ("EMAIL".equals(channel)) {
                body = "<p>" + content + "</p>";
                if ("TICKET_ISSUED".equals(code)) {
                    body += "<p>{{eventStartTime}} {{venue}}</p>{{#tickets}}"
                            + "<p>{{holderName}} — {{ticketTypeName}}</p><img src=\"cid:{{cid}}\" alt=\"QR\">{{/tickets}}";
                }
            }
            // Tên code/channel cho UUID seed ổn định qua các lần restart.
            String id = UUID.nameUUIDFromBytes((code + ":" + channel).getBytes(StandardCharsets.UTF_8)).toString();
            // Nhánh trùng unique giữ mẫu ADMIN đang có trong database.
            jdbc.update("""
                    INSERT INTO notification_templates(id, code, channel, subject, body_template, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE id = id
                    """, id, code, channel, subject, body, Timestamp.from(Instant.now()));
        }
    }
}
