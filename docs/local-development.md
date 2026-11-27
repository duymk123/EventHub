# Chạy service trên máy, Kafka và Redis bằng Docker

MySQL chạy trực tiếp trên máy tại `localhost:3306` cho **cả 6 service** (auth-service chuyển từ container `auth-db` cổng 33101 sang 3306 ngày 2026-11-24). Các service Java chạy bằng IntelliJ/Maven. File Compose riêng chạy Kafka, bước tạo topic, Redis và các giao diện quản lý; không yêu cầu mật khẩu MySQL Docker.

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

## Keycloak

auth-service cung cấp API đăng ký/đăng nhập (`/api/v1/auth/*`) và gọi Keycloak phía sau; Keycloak quản lý user, mật khẩu và role. Keycloak chạy bằng Docker (Postgres riêng), cổng 8180:

```
docker compose up -d keycloak-db keycloak
```

Chờ `docker logs -f eventhub-keycloak` hiện `Listening on`. Sau đó chạy auth-service bằng Shift+F10 như các service khác (không cần profile). Tài khoản test, cách gọi API bằng Postman và cách sửa realm: [infrastructure/keycloak/README.md](../infrastructure/keycloak/README.md).

## Chạy API Gateway và 6 service bằng Shift+F10

Cấu hình local nằm trực tiếp trong `src/main/resources/application.yml` của từng service; không cần profile `local` hoặc file YAML riêng. Bật MySQL trên máy với tài khoản `root`, mật khẩu `root`, chọn cấu hình chạy của service trong IntelliJ rồi nhấn Shift+F10.

| Service | Database | Cổng DB | Cổng HTTP |
|---|---|---:|---:|
| api-gateway | Không dùng DB | — | 8080 |
| auth-service | eventhub_auth | 3306 | 8081 |
| event-service | eventhub_event | 3306 (Flyway V1 + `validate`) | 8082 |
| ticket-service | eventhub_ticket | 3306 | 8083 |
| order-service | eventhub_order | 3306 | 8084 |
| notification-service | eventhub_notification | 3306 | 8085 |
| analytics-service | eventhub_analytics | 3306 | 8086 |

Connector/J tạo database nếu chưa tồn tại và tài khoản có quyền tạo database. **event-service, ticket-service và order-service** dùng Flyway `V1__init.sql` + `ddl-auto: validate` (`SPRING_FLYWAY_ENABLED` mặc định `true`): DB rỗng thì chỉ cần chạy service, Flyway tự tạo bảng. Gateway và auth dùng Hibernate `ddl-auto: update`; Flyway tắt ở Notification và Analytics. Kafka của Notification/Analytics vẫn dùng `localhost:29092`.

`ddl-auto: update` chỉ **thêm** bảng/cột còn thiếu; nó không xóa cột, không bỏ `NOT NULL`, không sửa `CHECK` hay kiểu cột. Vì vậy theo C15 (2026-11-22), Ticket và Order (cùng Event, PR #30) chuyển sang Flyway **bật mặc định**: `spring.flyway.enabled: true`, `ddl-auto: validate`, schema lấy từ `db/migration/V1__init.sql` (bằng DDL tham chiếu trong `docs/database/<service>/init.sql`). Khi chuyển lần đầu, xóa database dev cũ (chỉ có dữ liệu thử) để Flyway tạo lại từ đầu:

```sql
DROP DATABASE IF EXISTS eventhub_ticket;
DROP DATABASE IF EXISTS eventhub_order;
DROP DATABASE IF EXISTS eventhub_event;
```

Sau đó chạy lại event-service, ticket-service và order-service; Flyway tạo bảng và ghi lịch sử vào `flyway_schema_history`. Mọi thay đổi schema sau này là file mới `V2__…`, `V3__…`; không sửa file đã chạy.

> ⚠️ **event-service: đừng bật `validate` trên DB cũ chưa đồng bộ.** DB do Hibernate `update` tạo từ trước phải baseline có kiểm soát — quy trình ở [`EVENT-STATUS.md` §7](../event-service/EVENT-STATUS.md#7-nâng-cấp-database-cũ-ddl-auto-update--flyway). Biến `SPRING_JPA_HIBERNATE_DDL_AUTO`/tham số dòng lệnh ghi đè `application.yml` nên dễ vô tình quay lại `update`. Đã áp `V1` thì không sửa `V1__init.sql`, thay đổi schema dùng `V2__*.sql`.

Username/password mặc định là `root/root`. Có thể ghi đè bằng `DB_USERNAME`/`DB_PASSWORD` khi cần.

auth-service dùng chung MySQL trên máy (3306) như các service khác; database `eventhub_auth` tự tạo, bảng `user_profiles` do Hibernate `update` tạo. Ai vẫn muốn dùng container `auth-db` (cổng 33101) thì thêm `DB_PORT=33101` vào Run Configuration của auth-service và chạy `docker compose up -d auth-db`. Nếu auth-service báo `Communications link failure`, `Access denied for user 'root'@...` hoặc `Unable to determine Dialect`, kiểm tra MySQL đang chạy và biến `DB_PORT`/`DB_PASSWORD` trước khi nghĩ tới lỗi code. Nếu trước đây đã khai báo `SPRING_PROFILES_ACTIVE=local`, có thể bỏ biến này. Kiểm tra và xóa/sửa các biến `DB_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` hoặc `SPRING_DATASOURCE_*` trong Run Configuration nếu chúng ghi đè cấu hình local. Các biến `SPRING_JPA_HIBERNATE_DDL_AUTO` và `SPRING_FLYWAY_ENABLED` cũng có thể ghi đè YAML.

Analytics hiện chưa có Redis dependency hoặc nghiệp vụ dùng Redis. Các service có tích hợp Spring Data Redis có thể kết nối bằng `spring.data.redis.host: localhost` và `spring.data.redis.port: 6379`.

## VNPay sandbox (C06)

Order thanh toán qua VNPay sandbox. Nhóm tự đăng ký tài khoản merchant sandbox của VNPay để nhận `vnp_TmnCode` và `vnp_HashSecret`; **không commit** hai giá trị này. Khai báo trong Run Configuration của order-service:

| Biến | Ý nghĩa |
|---|---|
| `VNPAY_TMN_CODE` | Mã website merchant sandbox |
| `VNPAY_HASH_SECRET` | Khóa ký HMAC-SHA512 |
| `VNPAY_PAY_URL` | URL trang thanh toán sandbox (`…/paymentv2/vpcpay.html`) |
| `VNPAY_API_URL` | URL API `querydr`/`refund` sandbox (`…/merchant_webapi/api/transaction`) |
| `VNPAY_RETURN_URL` | Trang kết quả của frontend, ví dụ `http://localhost:3000/checkout/result` |

IPN (`GET /api/v1/payments/vnpay/ipn`) cần URL public để VNPay gọi tới. Khi chạy local có hai cách: mở tunnel (ngrok, cloudflared) trỏ vào order-service và khai báo URL IPN trong trang quản trị merchant sandbox; hoặc không dùng IPN, để trang kết quả gọi `POST /api/v1/orders/{id}/payment/verify` (Order hỏi lại VNPay bằng `querydr`). Thanh toán thử bằng thẻ test do VNPay công bố cho môi trường sandbox.

## Chạy frontend

Sau khi Gateway (8080) và các service đã chạy:

```bash
cd front-end
cp .env.example .env.local   # NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
npm install
npm run dev                  # http://localhost:3000
```

Mở đúng `http://localhost:3000`, không dùng `127.0.0.1`: refresh token nằm trong cookie `eh_refresh` (`SameSite=Strict`, C13) nên frontend và Gateway phải cùng host `localhost`, nếu không tải lại trang sẽ mất phiên. CORS của Gateway mặc định cho `http://localhost:3000` (`GATEWAY_CORS_ALLOWED_ORIGINS`). Trang kết quả VNPay là `/checkout/result`, khớp `VNPAY_RETURN_URL` ở trên.

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

Khi chuyển sang chạy toàn bộ bằng Docker sau này, Compose đã có biến kết nối tới MySQL Docker; database MySQL Docker độc lập với database trên máy. Nếu muốn quay lại quản lý schema bằng Flyway cho Notification/Analytics, cần cấu hình lại Flyway/Hibernate phù hợp. Không bật Flyway trực tiếp trên database đã được Hibernate tạo bảng mà chưa đối chiếu schema và xử lý baseline/migration (event-service đã làm đúng đường này, xem `EVENT-STATUS.md` §7).
