# EventHub — hướng dẫn kỹ thuật ban đầu

Đây là hướng dẫn build/chạy khung backend. README tổng quan sẽ viết sau khi có đặc tả.

- Spring Boot 3.5.16; mã nguồn và bytecode Java 21; build được bằng JDK 23.
- Maven 3.9.16 qua Maven Wrapper 3.3.4 chính thức.
- Mỗi module có Spring Web, Lombok, Spring Data JPA, MySQL Driver và Spring Boot Test (test scope).
- Parent POM quản lý phiên bản và cấu hình Lombok annotation processor, bao gồm khi build với JDK 23.
- Test nạp Mockito agent ngay khi khởi động JVM để tránh phụ thuộc vào dynamic attach trên JDK 21+.

Mở `pom.xml` ở thư mục này trong IntelliJ và chọn SDK/Maven Runner JRE là JDK 23, language level Java 21.
Thư mục `.idea`/file `.iml` do IDE tạo; `target` do Maven tạo khi build.

Build và test toàn bộ backend trong PowerShell:

```powershell
$env:JAVA_HOME = 'D:\Java' # Điều chỉnh theo vị trí JDK 23.
.\mvnw.cmd clean verify
```

Chạy riêng một service: vào thư mục service rồi chạy `.\mvnw.cmd spring-boot:run`.
Xem `HELP.md` trong module để cấu hình MySQL trước khi chạy.
Các test context ban đầu không kiểm tra tích hợp MySQL.

| Module | Cổng mặc định | Database ban đầu |
| --- | --- | --- |
| api-gateway | 8080 | eventhub_gateway |
| auth-service | 8081 | eventhub_auth |
| event-service | 8082 | eventhub_event |
| ticket-service | 8083 | eventhub_ticket |
| order-service | 8084 | eventhub_order |
| notification-service | 8085 | eventhub_notification |
| analytics-service | 8086 | eventhub_analytics |

Cổng/database là cấu hình ban đầu, có thể thay bằng biến môi trường.
`api-gateway` hiện là ứng dụng Spring Boot với cùng bộ dependency được yêu cầu; chưa có định tuyến.
Chưa thêm Spring Security, JWT, Spring Cloud Gateway, Kafka client, Redis client hoặc API nghiệp vụ.
`docker-compose.yml` vẫn là khung; Dockerfile từng module build với context thư mục gốc EventHub.

Tài liệu tham khảo:
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://maven.apache.org/tools/wrapper/index.html
