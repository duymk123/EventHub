# analytics-service

Thiết kế: [services.md](../services.md), [API](../docs/api/analytics-service-openapi.yaml), [quyết định BC_32_V2](../docs/architecture/source-decisions.md). Database: eventhub_analytics.

Chỉ order.paid cộng sales, order.refunded ghi refund; không cộng ticket.issued hoặc trừ ticket.cancelled lần hai. Phân hạng vé cần ticketTypeId/name và tiền hoàn từng hạng. Claim messageId + UPSERT + log/snapshot cùng transaction. Attendance phải chịu check-out đến trước check-in, không giảm/clamp đơn thuần. GET dashboard tải ban đầu, WS /ws/dashboard có Gateway route riêng. CONNECT xác minh JWT, SUBSCRIBE kiểm tra owner/Admin, chặn client SEND vào topic server; session cần hết hạn theo token. Gọi Event Service để phân quyền và xác minh tồn tại; cache ownership chỉ ngắn hạn có invalidation. Quy tắc ngày báo cáo/thuần-gộp còn C09.

## Chạy và kiểm tra

Mở pom.xml ở root bằng IntelliJ, chọn JDK 21+ (language level 21). Dependency thực tế xem pom.xml của module; không coi ví dụ trong tài liệu là dependency đã được thêm.

Chạy lệnh từ **root repository**, nơi có Maven Wrapper:

```powershell
.\mvnw.cmd -pl analytics-service -am verify
.\mvnw.cmd -pl analytics-service spring-boot:run
```

Nếu Wrapper PowerShell lỗi ở khâu tìm Maven cache, dùng Maven 3.9.x đã cài với cùng tham số; chưa sửa Wrapper trong lượt tài liệu này. Cấu hình datasource qua DB_HOST/PORT/NAME/USERNAME/PASSWORD; [Docker.md](../Docker.md) có port local. Context test profile test hiện không kết nối DB, không chứng minh API/DB/Kafka đã hoạt động.

Đã có Flyway V1 và ddl-auto: validate. Không sửa migration đã áp dụng; bổ sung schema bằng version mới.
