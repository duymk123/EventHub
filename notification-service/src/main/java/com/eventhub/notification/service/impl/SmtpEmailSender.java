package com.eventhub.notification.service.impl;

import com.eventhub.notification.service.EmailSender;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Adapter gửi MIME email bằng JavaMailSender. Khi payload có tickets, lớp này tạo PNG QR bằng ZXing và nhúng
 * ảnh theo Content-ID trong HTML.
 */
@Component
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailSender(JavaMailSender sender, Environment environment) {
        this.sender = sender;
        this.from = environment.getRequiredProperty("eventhub.notification.mail-from");
    }

    /**
     * Tạo MIME message gồm người gửi, người nhận, subject và HTML đã render. Với mỗi vé trong payload, tạo PNG
     * và gắn ảnh inline theo cid, JavaMailSender gửi qua SMTP có timeout cấu hình.
     */
    @Override
    public void send(String recipient, String subject, String html, Map<String, Object> payload) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            // HTML ở đây là snapshot đã render, tham số true chọn MIME nội dung HTML.
            helper.setText(html, true);
            Object tickets = payload.get("tickets");
            if (tickets instanceof List<?> list) {
                for (Object item : list) {
                    Map<?, ?> ticket = (Map<?, ?>) item;
                    // cid trùng giá trị trong model để thẻ img của HTML tham chiếu đúng ảnh nhúng.
                    helper.addInline((String) ticket.get("cid"),
                            new ByteArrayResource(png((String) ticket.get("qrCodeValue"))), "image/png");
                }
            }
            // Đây là lời gọi SMTP thực tế, NotificationDeliveryService quản lý lease quanh bước này.
            sender.send(message);
        } catch (Exception error) {
            // Đổi lỗi SMTP thành thông điệp an toàn trước khi ghi log và trạng thái giao nhận.
            throw new IllegalStateException("Email delivery failed");
        }
    }

    /**
     * Mã hóa giá trị QR opaque do Ticket cấp bằng ZXing thành ảnh đen trắng 256x256 rồi trả byte PNG cho ảnh
     * inline.
     */
    private byte[] png(String value) throws Exception {
        // Tạo ảnh từ cùng token QR mà Ticket đã cấp cho vé.
        var matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 256, 256);
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 256; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
            }
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }
}
