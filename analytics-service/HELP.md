# analytics-service — hướng dẫn chạy ban đầu

Module Spring Boot 3.5.16, mã nguồn Java 21. Có thể build bằng JDK 23.
Dependency: Spring Web, Lombok, Spring Data JPA, MySQL Driver; Spring Boot Test chỉ dùng khi test.

## IntelliJ IDEA

Mở `EventHub/pom.xml` dưới dạng Maven project để nhận đủ 7 module.
Chọn Project SDK và Maven Runner JRE là JDK 23, đặt language level là Java 21.
Nếu Lombok chưa được IDE nhận, kiểm tra annotation processing trong Java Compiler.
Chạy lớp `com.eventhub.analytics.AnalyticsServiceApplication`. Profile mặc định `no-db` chạy được khi chưa có MySQL.

## Build và test

Chạy trong thư mục `analytics-service` trên PowerShell:

```powershell
$env:JAVA_HOME = 'D:\Java' # Điều chỉnh nếu JDK 23 được cài ở nơi khác.
.\mvnw.cmd clean verify
```

Trên Linux/macOS: `chmod +x mvnw`, sau đó `./mvnw clean verify`.
Maven Wrapper tự tải Maven 3.9.16; lần chạy đầu cần mạng.
Test khởi động context với profile `test`, không kết nối MySQL.

## Cấu hình YAML và chạy ứng dụng

- `application.yml`: tên ứng dụng, cổng và profile mặc định `no-db`.
- `application-no-db.yml`: tắt tự cấu hình datasource/JPA khi chưa có database.
- `application-mysql.yml`: cấu hình MySQL tùy chọn, dùng khi database sẵn sàng.
- `src/test/resources/application-test.yml`: cấu hình test không kết nối database.

Chạy ngay khung ứng dụng: `.\mvnw.cmd spring-boot:run`, không cần biến MySQL.
Để chạy với database sau này, chọn profile `mysql`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
```


Tạo trước database `eventhub_analytics` và tài khoản có quyền truy cập database đó trên MySQL.
Đây là tên ban đầu, có thể đổi bằng `DB_NAME` khi có thiết kế dữ liệu.

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'eventhub_analytics'
$env:DB_USERNAME = 'eventhub'
$env:DB_PASSWORD = '<mật khẩu MySQL của bạn>'
.\mvnw.cmd spring-boot:run
```

Cổng mặc định: `8086`; đổi bằng `SERVER_PORT`. Có thể dùng `DB_URL` để thay toàn bộ JDBC URL.
Không commit mật khẩu. Spring Boot không tự đọc file `.env`; nhập biến trong shell hoặc IDE.
`ddl-auto=none`: ứng dụng không tự tạo/sửa bảng. Hiện chưa có entity, API nghiệp vụ hay migration.

## Docker

Build từ thư mục gốc **EventHub**, vì module dùng chung parent POM:

```powershell
docker build -f analytics-service/Dockerfile -t eventhub/analytics-service .
docker run --rm -p 8086:8086 -e SPRING_PROFILES_ACTIVE=no-db eventhub/analytics-service
```

Trong container, `DB_HOST` phải trỏ tới máy/container MySQL có thể truy cập được.
Nếu MySQL chạy trên máy Windows với Docker Desktop, dùng `DB_HOST=host.docker.internal`.
Docker build dùng JDK 23 và runtime dùng Java 21. Docker Compose đã cấu hình đủ 7 module, chạy profile `no-db`. Từ gốc EventHub: `docker compose up --build -d`.
