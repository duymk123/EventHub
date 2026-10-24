# EventHub — hướng dẫn kỹ thuật

## Chạy cả nhóm bằng Docker Compose

Cần cài Docker Desktop (Windows/macOS) hoặc Docker Engine kèm Docker Compose plugin (Linux). Không cần cài Java hay MySQL trên máy để chạy Compose.

Mỗi thành viên tạo file cấu hình riêng từ file mẫu ở thư mục gốc:

```powershell
Copy-Item .env.example .env
```

Mở `.env` và đặt `MYSQL_ROOT_PASSWORD`, `DB_USERNAME`, `DB_PASSWORD` theo máy của mình. Tên đăng nhập app mặc định là `eventhub`; có thể giữ nguyên và chỉ đổi mật khẩu. Không commit file `.env`; Git đã bỏ qua file này. `.env.example` không chứa mật khẩu thật và được commit để thành viên mới có thể copy.

Chạy từ thư mục gốc dự án:

```powershell
docker compose up --build -d
```

Compose tự tạo container MySQL, volume named `eventhub_mysql_data`, bảy database riêng cho các service, rồi khởi chạy ứng dụng sau khi MySQL khỏe mạnh. Volume nằm trong vùng quản lý của Docker trên máy từng thành viên; không cần chạy `docker volume create` và không cần chia sẻ volume qua Git.

Xem trạng thái bằng `docker compose ps`; log bằng `docker compose logs -f mysql` hoặc `docker compose logs -f auth-service`. Dừng container mà giữ dữ liệu bằng `docker compose down`. Muốn xóa database local và tạo lại từ đầu, dùng `docker compose down -v` (xóa toàn bộ dữ liệu trong volume).

Các file trong `infrastructure/mysql/init` chỉ chạy khi volume database được khởi tạo lần đầu. Thêm database vào đây nếu thêm service có schema riêng. Nếu sau này thêm dữ liệu mẫu dạng SQL, cần nạp sau khi Hibernate đã tạo bảng; script khởi tạo của MySQL chạy trước khi ứng dụng khởi động.

Trong profile `mysql`, Hibernate dùng `ddl-auto: update` để tạo/cập nhật bảng từ entity khi chạy local. Repo hiện chưa có entity `@Entity`, nên Hibernate chưa có bảng nào để sinh cho đến khi nhóm thêm entity. Đây là cấu hình tiện cho phát triển; khi schema ổn định, nên quản lý thay đổi bằng migration.

## Cấu trúc chính

- Mỗi thư mục `*-service` là một Spring Boot service, có Dockerfile riêng.
- `api-gateway` là gateway backend; cổng mặc định 8080.
- `docker-compose.yml` định nghĩa các service, mạng nội bộ, MySQL và volume.
- `.env.example` là mẫu cấu hình; mỗi thành viên tự tạo `.env` riêng.
- `infrastructure/mysql/init` chứa script khởi tạo các database MySQL lần đầu.

## Build backend bằng Maven

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

`api-gateway` hiện là ứng dụng Spring Boot với cùng bộ dependency được yêu cầu; chưa có định tuyến.
Chưa thêm Spring Security, JWT, Spring Cloud Gateway, Kafka client, Redis client hoặc API nghiệp vụ.
`docker-compose.yml` khởi chạy MySQL cùng các service; Dockerfile từng module build với context thư mục gốc EventHub.

Tài liệu tham khảo:
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://maven.apache.org/tools/wrapper/index.html
