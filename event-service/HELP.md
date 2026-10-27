# event-service

Thiết kế: [services.md](../services.md), [API](../docs/api/event-service-openapi.yaml), [quyết định BC_32_V2](../docs/architecture/source-decisions.md). Database: eventhub_event.

Chỉ owner ORGANIZER được sửa/submit/publish/close; ADMIN review. Chỉ DRAFT/REJECTED được sửa, sửa REJECTED đưa về DRAFT. Public chỉ PUBLISHED. CLOSED ngừng bán, không tự hủy vé. Loại vé/quota thuộc Ticket, không zones A–D. venue chưa có trong baseline, chờ C03; review_reason là bổ sung kỹ thuật lưu lý do review.

## Chạy và kiểm tra

Mở pom.xml ở root bằng IntelliJ, chọn JDK 21+ (language level 21). Dependency thực tế xem pom.xml của module; không coi ví dụ trong tài liệu là dependency đã được thêm.

Chạy lệnh từ **root repository**, nơi có Maven Wrapper:

```powershell
.\mvnw.cmd -pl event-service -am verify
.\mvnw.cmd -pl event-service spring-boot:run
```

Nếu Wrapper PowerShell lỗi ở khâu tìm Maven cache, dùng Maven 3.9.x đã cài với cùng tham số; chưa sửa Wrapper trong lượt tài liệu này. Cấu hình datasource qua DB_HOST/PORT/NAME/USERNAME/PASSWORD; [Docker.md](../Docker.md) có port local. Đã bỏ file/profile/dependency test theo yêu cầu chủ dự án; build không thay thế chạy thử API/DB/Kafka.

Module còn scaffold Hibernate update, chưa có migration nghiệp vụ; chuyển sang migration trước môi trường chung.
