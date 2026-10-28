# Chạy service trên máy, Kafka và Redis bằng Docker

MySQL chạy trực tiếp trên máy tại `localhost:3306`. Các service Java chạy bằng IntelliJ/Maven. File Compose riêng chạy Kafka, bước tạo topic, Redis và các giao diện quản lý; không yêu cầu mật khẩu MySQL Docker.

## Khởi động hạ tầng

Mở Docker Desktop, chạy từ thư mục gốc dự án:

```powershell
docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.infra.yml ps --all
docker compose -f docker-compose.infra.yml logs kafka-init
docker compose -f docker-compose.infra.yml exec redis redis-cli ping
```

Kafka dùng `localhost:29092`; Redis dùng `localhost:6379`, không mật khẩu, chỉ mở cổng trên loopback. `kafka-init` tạo các topic cần thiết rồi thoát với mã 0. Kafka và Redis lưu dữ liệu trong named volume.

| Giao diện | Địa chỉ | Kết nối được cấu hình sẵn |
|---|---|---|
| Kafka UI | http://localhost:8089 | Cluster `eventhub-local`, broker `kafka:9092` |
| Redis Insight | http://localhost:5540 | Database `eventhub-local`, Redis `redis:6379` |

Hai giao diện chỉ mở cổng trên loopback. Redis Insight có màn hình điều khoản khi mở lần đầu; kết nối từ biến môi trường được nạp sau khi người dùng chấp nhận điều khoản. Redis Insight lưu cấu hình trong volume `redisinsight_data`; nếu cần thêm kết nối thủ công, dùng host `redis`, port `6379`, không mật khẩu vì UI chạy trong mạng Docker. Xem [cấu hình Kafka UI](https://github.com/provectus/kafka-ui/blob/master/documentation/compose/kafka-ui.yaml) và [image Redis Insight chính thức](https://hub.docker.com/r/redis/redisinsight).

File hạ tầng dùng cùng project/network/volume Kafka với `docker-compose.yml` hiện tại. Không thêm `--remove-orphans` khi chạy file này nếu project còn các container khác.

## Chạy API Gateway và 6 service bằng Shift+F10

Cấu hình local nằm trực tiếp trong `src/main/resources/application.yml` của từng service; không cần profile `local` hoặc file YAML riêng. Bật MySQL trên máy với tài khoản `root`, mật khẩu `root`, chọn cấu hình chạy của service trong IntelliJ rồi nhấn Shift+F10.

| Service | Database trên localhost:3306 | Cổng HTTP |
|---|---|---:|
| api-gateway | eventhub_gateway | 8080 |
| auth-service | eventhub_auth | 8081 |
| event-service | eventhub_event | 8082 |
| ticket-service | eventhub_ticket | 8083 |
| order-service | eventhub_order | 8084 |
| notification-service | eventhub_notification | 8085 |
| analytics-service | eventhub_analytics | 8086 |

Connector/J tạo database nếu chưa tồn tại và tài khoản có quyền tạo database. Gateway và cả 6 service dùng Hibernate `ddl-auto: update` để tạo/cập nhật bảng từ entity; Flyway tắt ở Notification và Analytics. Kafka của Notification/Analytics vẫn dùng `localhost:29092`.

Username/password mặc định là `root/root`. Có thể ghi đè bằng `DB_USERNAME`/`DB_PASSWORD` khi cần. Nếu trước đây đã khai báo `SPRING_PROFILES_ACTIVE=local`, có thể bỏ biến này. Kiểm tra và xóa/sửa các biến `DB_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` hoặc `SPRING_DATASOURCE_*` trong Run Configuration nếu chúng ghi đè cấu hình local. Các biến `SPRING_JPA_HIBERNATE_DDL_AUTO` và `SPRING_FLYWAY_ENABLED` cũng có thể ghi đè YAML.

Analytics hiện chưa có Redis dependency hoặc nghiệp vụ dùng Redis. Các service có tích hợp Spring Data Redis có thể kết nối bằng `spring.data.redis.host: localhost` và `spring.data.redis.port: 6379`.

## Lỗi cổng đã được sử dụng

Các cổng HTTP local giữ đúng bảng README: Gateway 8080, Auth 8081, Event 8082, Ticket 8083, Order 8084, Notification 8085, Analytics 8086. Biến `SERVER_PORT`, tham số `--server.port` hoặc VM option `-Dserver.port` có thể ghi đè giá trị trong YAML.

Nếu gặp `Port 8085 was already in use`, kiểm tra tiến trình/container đang chiếm cổng:

```powershell
netstat.exe -ano -p tcp | Select-String ':8085\s'
docker.exe ps --filter publish=8085 --format '{{.Names}} {{.Ports}}'
```

Nếu là một bản Notification đã chạy trong IntelliJ, dừng bản cũ trước khi chạy lại. Nếu là container của dự án khác, chỉ dừng container đó khi không cần dùng; không dừng toàn bộ Docker vì Kafka/Redis vẫn cần chạy. Khi giữ đúng cổng README, cần giải phóng cổng đang bị ứng dụng khác chiếm.

## Dừng hạ tầng

```powershell
docker compose -f docker-compose.infra.yml stop
```

Khi chuyển sang chạy toàn bộ bằng Docker sau này, Compose đã có biến kết nối tới MySQL Docker; database MySQL Docker độc lập với database trên máy. Nếu muốn quay lại quản lý schema bằng Flyway cho Notification/Analytics, cần cấu hình lại Flyway/Hibernate phù hợp. Không bật Flyway trực tiếp trên database đã được Hibernate tạo bảng mà chưa đối chiếu schema và xử lý baseline/migration.
