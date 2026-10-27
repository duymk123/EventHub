# ticket-service

Thiết kế: [services.md](../services.md), [API](../docs/api/ticket-service-openapi.yaml), [quyết định BC_32_V2](../docs/architecture/source-decisions.md). Database: eventhub_ticket.

ticket_types gộp quota/counters; ba tên VIP/PREMIUM/STANDARD. Reserve 3 phút trong DB transaction với khóa hàng/UPDATE điều kiện, UNIQUE(order_id), giá snapshot. Confirm CAS và phát vé/QR trong cùng transaction, lặp trả kết quả cũ. Release CONFIRMED trả 409; RELEASED/EXPIRED gọi lại không trả quota hai lần. Check-in/out CAS và ticket_scans cùng transaction. Không tái nhập. gateId UUID nhưng owner/quyền thiết bị chờ C07. Cancel sau refund chỉ vé chưa dùng và >=48 giờ; khoảng trống sau provider refund chờ C05. Redis hỗ trợ khóa/cache, DB là nguồn chính.

## Chạy và kiểm tra

Mở pom.xml ở root bằng IntelliJ, chọn JDK 21+ (language level 21). Dependency thực tế xem pom.xml của module; không coi ví dụ trong tài liệu là dependency đã được thêm.

Chạy lệnh từ **root repository**, nơi có Maven Wrapper:

```powershell
.\mvnw.cmd -pl ticket-service -am verify
.\mvnw.cmd -pl ticket-service spring-boot:run
```

Nếu Wrapper PowerShell lỗi ở khâu tìm Maven cache, dùng Maven 3.9.x đã cài với cùng tham số; chưa sửa Wrapper trong lượt tài liệu này. Cấu hình datasource qua DB_HOST/PORT/NAME/USERNAME/PASSWORD; [Docker.md](../Docker.md) có port local. Đã bỏ file/profile/dependency test theo yêu cầu chủ dự án; build không thay thế chạy thử API/DB/Kafka.

Module còn scaffold Hibernate update, chưa có migration nghiệp vụ; chuyển sang migration trước môi trường chung.
