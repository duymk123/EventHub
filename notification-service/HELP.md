# notification-service

Thiết kế: [services.md](../services.md), [API](../docs/api/notification-service-openapi.yaml), [quyết định BC_32_V2](../docs/architecture/source-decisions.md). Database: eventhub_notification.

Consume ticket.issued, order.failed, order.refunded. EMAIL và IN_APP; PUSH/reminder để giai đoạn sau. Render title/content và lưu snapshot; QR PNG nhúng cid. Không lộ email/QR/payload ra GET notifications. Dedup từng messageId:channel:recipient, transaction ngắn, SMTP ngoài transaction. Chỉ processed khi mọi target thành công. SMTP có thể gửi trùng nếu crash giữa send và markSent. Nguồn customerEmail/holder fallback C02, venue C03. API page/unread-count/PUT template là bổ sung kỹ thuật có nhãn trong OpenAPI.

## Chạy và kiểm tra

Mở pom.xml ở root bằng IntelliJ, chọn JDK 21+ (language level 21). Dependency thực tế xem pom.xml của module; không coi ví dụ trong tài liệu là dependency đã được thêm.

Chạy lệnh từ **root repository**, nơi có Maven Wrapper:

```powershell
.\mvnw.cmd -pl notification-service -am verify
.\mvnw.cmd -pl notification-service spring-boot:run
```

Nếu Wrapper PowerShell lỗi ở khâu tìm Maven cache, dùng Maven 3.9.x đã cài với cùng tham số; chưa sửa Wrapper trong lượt tài liệu này. Cấu hình datasource qua DB_HOST/PORT/NAME/USERNAME/PASSWORD; [Docker.md](../Docker.md) có port local. Đã bỏ file/profile/dependency test theo yêu cầu chủ dự án; build không thay thế chạy thử API/DB/Kafka.

Đã có Flyway V1 và ddl-auto: validate. Không sửa migration đã áp dụng; bổ sung schema bằng version mới.
