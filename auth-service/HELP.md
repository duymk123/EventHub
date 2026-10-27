# auth-service

Thiết kế: [services.md](../services.md), [API](../docs/api/auth-service-openapi.yaml), [quyết định BC_32_V2](../docs/architecture/source-decisions.md). Database: eventhub_auth (scaffold; DB identity thuộc Keycloak).

Keycloak sở hữu tài khoản, credentials, session, refresh và cấp token. Auth là facade UI/API tài khoản riêng gọi Keycloak theo xác nhận mới. Không tạo password_hash hoặc refresh_tokens riêng. Role nghiệp vụ CUSTOMER/ORGANIZER/ADMIN; map claim và userId/sub chờ cấu hình. Giữ các API tài khoản dưới dạng facade. Grant flow/role mapping/Admin API cần cấu hình; không tự ký JWT.

## Chạy và kiểm tra

Mở pom.xml ở root bằng IntelliJ, chọn JDK 21+ (language level 21). Dependency thực tế xem pom.xml của module; không coi ví dụ trong tài liệu là dependency đã được thêm.

Chạy lệnh từ **root repository**, nơi có Maven Wrapper:

```powershell
.\mvnw.cmd -pl auth-service -am verify
.\mvnw.cmd -pl auth-service spring-boot:run
```

Nếu Wrapper PowerShell lỗi ở khâu tìm Maven cache, dùng Maven 3.9.x đã cài với cùng tham số; chưa sửa Wrapper trong lượt tài liệu này. Cấu hình datasource qua DB_HOST/PORT/NAME/USERNAME/PASSWORD; [Docker.md](../Docker.md) có port local. Context test profile test hiện không kết nối DB, không chứng minh API/DB/Kafka đã hoạt động.

Module còn scaffold Hibernate update, chưa có migration nghiệp vụ; chuyển sang migration trước môi trường chung.
