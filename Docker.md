# EventHub — hướng dẫn kỹ thuật

## Chạy cả nhóm bằng Docker Compose

Cần cài Docker Desktop (Windows/macOS) hoặc Docker Engine kèm Docker Compose plugin (Linux). Không cần cài Java hay MySQL trên máy để chạy Compose.

Mỗi thành viên cần có file `.env` ở thư mục gốc, chứa `MYSQL_ROOT_PASSWORD` với mật khẩu MySQL local của mình. Compose tự đọc file này; không cần thêm `--env-file` hay chạy lệnh copy mỗi lần khởi động. `.env` bị Git bỏ qua nên mỗi thành viên tạo file này một lần sau khi clone. `.env.example` là file tham khảo; có thể mở nó để lấy dòng cấu hình rồi tự tạo `.env`.

Chạy từ thư mục gốc dự án:

```powershell
docker compose up --build -d
```

Compose khởi chạy một container cho mỗi backend service và một MySQL container riêng cho từng service. Mỗi MySQL chỉ tạo database tương ứng, dùng cổng `3306` bên trong Docker và một host port riêng để kết nối từ DBeaver. Các host port được cố định từ `33101` đến `33107`, tránh trùng với project khác đang dùng `33061`–`33065`. Nếu máy đã dùng một port này, đổi port tương ứng trực tiếp trong `docker-compose.yml`.

Docker tự tạo bảy named volume trên máy từng thành viên; không cần chạy `docker volume create` hoặc chia sẻ volume qua Git. `docker compose down` giữ dữ liệu. `docker compose down -v` xóa cả bảy database volume.

Trong DBeaver, kết nối host `127.0.0.1`, username `root`, mật khẩu trong `.env`, rồi dùng port và database tương ứng:

| Service | Database | Host port mặc định | Docker service name |
| --- | --- | ---: | --- |
| Auth | `eventhub_auth` | `33101` | `auth-db` |
| Event | `eventhub_event` | `33102` | `event-db` |
| Ticket | `eventhub_ticket` | `33103` | `ticket-db` |
| Order | `eventhub_order` | `33104` | `order-db` |
| Notification | `eventhub_notification` | `33105` | `notification-db` |
| Analytics | `eventhub_analytics` | `33106` | `analytics-db` |
| API Gateway | `eventhub_gateway` | `33107` | `api-gateway-db` |

Ví dụ: để kết nối database Auth, nhập host `127.0.0.1`, port `33101`, username `root`, mật khẩu trong `.env`, database `eventhub_auth`. Backend service kết nối tới hostname Docker tương ứng (ví dụ `auth-db`) trên cổng `3306`.

Các ứng dụng Spring Boot lắng nghe trên cổng `8080` bên trong từng container. Host ports lần lượt là `8080` cho API Gateway và `8081`–`8086` cho các backend; ví dụ `8081:8080` nghĩa là truy cập Auth từ máy ở `localhost:8081`, còn bên trong container ứng dụng nghe cổng `8080`. Host port phải khác nhau giữa các container đang chạy trên cùng máy.

Tài khoản `root` được dùng cho phát triển local theo yêu cầu hiện tại. Không dùng cấu hình này cho môi trường production.

Xem trạng thái bằng `docker compose ps`; xem log MySQL bằng `docker compose logs -f auth-db` hoặc log ứng dụng bằng `docker compose logs -f auth-service`. Dừng container mà giữ dữ liệu bằng `docker compose down`. Muốn xóa toàn bộ database local và tạo lại từ đầu, dùng `docker compose down -v`.

Đặt SQL `INSERT` mẫu trong `infrastructure/mysql/seed`, mỗi service một file, rồi nạp vào đúng DB container sau khi service đã khởi động. Xem hướng dẫn trong thư mục seed. Cơ chế init mặc định của MySQL chỉ chạy khi volume mới; không dùng cơ chế đó cho `INSERT` cần chạy sau Hibernate.

Mỗi `application.yml` chứa trực tiếp cấu hình datasource MySQL; Spring Boot tự đọc file này khi ứng dụng khởi động trong Docker. Hibernate dùng `ddl-auto: update` để tạo/cập nhật bảng từ entity. Repo hiện chưa có entity `@Entity`, nên Hibernate chưa có bảng nào để sinh cho đến khi nhóm thêm entity. Đây là cấu hình tiện cho phát triển; khi schema ổn định, nên quản lý thay đổi bằng migration.

## Cấu trúc chính

- Mỗi thư mục `*-service` là một Spring Boot service, có Dockerfile riêng.
- `api-gateway` là gateway backend; cổng mặc định 8080.
- `docker-compose.yml` định nghĩa một instance cho mỗi backend service và một MySQL container/volume riêng tương ứng.
- `.env.example` là mẫu cấu hình; mỗi thành viên tạo file `.env` riêng.
- `infrastructure/mysql/seed` chứa hướng dẫn và SQL dữ liệu mẫu cho từng service.

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
