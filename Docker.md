# EventHub — chạy local và quản lý schema

## Khởi động

Cài Docker Desktop/Engine và Compose. Tạo .env ở root với MYSQL_ROOT_PASSWORD=<mật khẩu local>; không commit secret. Keycloak đã được chọn làm nơi đăng nhập/cấp token; realm/client/issuer/audience/JWK và xác thực nội bộ còn chờ cấu hình, chưa thêm khóa giả định. Mailpit local không cần tài khoản SMTP.

```powershell
docker compose up --build -d
docker compose ps
docker compose logs kafka-init
```

Compose hiện có 7 MySQL database scaffold, 7 backend scaffold, Kafka KRaft một node (không Zookeeper), Kafka UI và Mailpit. Backend chưa có API nghiệp vụ; api-gateway chưa có route. Port container của mọi backend là 8080.

| Service | Database | Port HTTP host | Port MySQL host |
|---|---|---:|---:|
| auth-service | eventhub_auth | 8081 | 33101 |
| event-service | eventhub_event | 8082 | 33102 |
| ticket-service | eventhub_ticket | 8083 | 33103 |
| order-service | eventhub_order | 8084 | 33104 |
| notification-service | eventhub_notification | 8085 | 33105 |
| analytics-service | eventhub_analytics | 8086 | 33106 |
| api-gateway | eventhub_gateway | 8080 | 33107 |

DBeaver: host 127.0.0.1, user root, password từ .env, database/port như bảng. Trong Docker, backend dùng tên DB service (ví dụ notification-db) và port 3306. Tài khoản root này dành cho local.

## Kafka và email dev

| Hạ tầng | Trong Docker | Trên máy |
|---|---|---|
| Kafka | kafka:9092 | localhost:29092 |
| Kafka UI | kafka-ui:8080 | http://localhost:8089 |
| Mailpit SMTP | mailpit:1025 | localhost:1025 |
| Mailpit web | mailpit:8025 | http://localhost:8025 |

Các port Kafka/UI/Mailpit bind loopback. Mailpit bắt thư để xem trên web, không gửi ra hộp thư thật; hiện không có volume nên thư có thể mất khi tạo lại container.

Kafka dùng apache/kafka:3.8.1, broker/controller KRaft kết hợp cho dev, listener theo [mẫu chính thức Apache Kafka 3.8.1](https://github.com/apache/kafka/blob/3.8.1/docker/examples/docker-compose-files/cluster/combined/plaintext/docker-compose.yml). Kafka có named volume kafka_data. Cấu hình một node/PLAINTEXT dành cho local.

kafka-init đợi broker healthy rồi chạy infrastructure/kafka/init-topics.sh: 7 topic nghiệp vụ và 2 DLQ, 3 partition/topic, replication factor 1. --if-not-exists chạy lại an toàn nhưng không đổi partition của topic cũ. Ticket/order/notification/analytics đợi init thành công. [Hợp đồng Kafka](docs/kafka/events.md).

```powershell
docker compose up -d kafka kafka-init kafka-ui mailpit
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe
```

Ứng dụng ngoài Docker dùng SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:29092; Compose đặt kafka:9092. Notification dùng MAIL_HOST/PORT/USERNAME/PASSWORD, mặc định ngoài Docker localhost:1025, Compose mailpit:1025. MAIL_SMTP_AUTH/MAIL_SMTP_STARTTLS mặc định false cho Mailpit; SMTP khác phải cấu hình phù hợp. Analytics EVENT_SERVICE_URL trong Compose http://event-service:8080, ngoài Docker http://localhost:8082. Credential gọi internal API đang chờ auth-service chốt.

## Flyway, UUID và seed

Notification/analytics dùng flyway-core + flyway-mysql, migration src/main/resources/db/migration/V1__init.sql, ddl-auto: validate. Flyway tạo schema trước; Hibernate chỉ kiểm tra mapping. Mọi PK/tham chiếu ID dùng UUID CHAR(36), kể cả gateId; Hibernate preferred_uuid_jdbc_type=CHAR. Loại vé VIP/PREMIUM/STANDARD.

V1 tạo schema nền theo kế hoạch; entity/repository/API sẽ được bổ sung ở bước nghiệp vụ. Seed nội dung template sẽ thêm khi triển khai notification core. Các service khác vẫn dùng Hibernate update trong scaffold, chưa chuyển sang Flyway ở thay đổi này.

V1 dành cho schema rỗng. DB đã có bảng: sao lưu, đối chiếu schema và lập migration/baseline có kiểm soát; không tự bật baseline-on-migrate. Không sửa V1 đã áp dụng; thay đổi tiếp dùng V2/V3. docs/database/*/init.sql là schema tham chiếu, không phải migration; bản mới không DROP TABLE. Không chạy vào DB đang có dữ liệu hoặc chạy song song Flyway.

Nạp INSERT sau khi schema/service khởi động thành công. Xem [seed README](infrastructure/mysql/seed/README.md). Seed dùng UUID cố định, không ID số/AUTO_INCREMENT.

## Log và dữ liệu

```powershell
docker compose logs -f notification-service analytics-service
docker compose logs kafka kafka-init
docker compose down
```

docker compose down giữ 7 volume MySQL và volume Kafka. docker compose down -v xóa toàn bộ các volume đó, chỉ dùng khi chủ động bỏ dữ liệu local. Không cần reset volume để áp dụng thay đổi cấu hình thông thường.

## Build backend

Java 21, có thể build bằng JDK 23; Maven Wrapper quản lý Maven. Từ root:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -pl notification-service,analytics-service -am verify
.\mvnw.cmd -pl notification-service spring-boot:run
```

Test profile scaffold không nạp DB; context test không thay thế kiểm tra tích hợp MySQL/Kafka/SMTP. Chạy ngoài Docker cần đặt DB_HOST/PORT/NAME/USERNAME/PASSWORD phù hợp; application.yml lấy DB_PASSWORD từ môi trường.

## Keycloak, Redis và khoảng cách với thiết kế V2

Keycloak quản lý identity/token theo quyết định mới. Compose hiện **chưa có Keycloak/Redis**, chưa có realm import, client, role mapper hay issuer/JWK. Không tự dùng JWT_SECRET hoặc EVENTHUB_JWT_KEY thay Keycloak. Phiên bản, cổng, DB Keycloak, realm/client và credential S2S sẽ bổ sung ở thay đổi triển khai sau khi cấu hình được chốt.

Sáu service nghiệp vụ không đồng nghĩa bảy DB nghiệp vụ: eventhub_gateway cùng datasource/JPA chỉ là scaffold cũ, chưa có yêu cầu dữ liệu Gateway. eventhub_auth không phải DB Keycloak; không seed credentials/refresh token vào đó. Đã giữ Auth facade UI/API riêng; profile DB chỉ thêm khi có dữ liệu ngoài identity Keycloak. Redis chỉ hỗ trợ lock/cache; reservation/counters vẫn ở MySQL.

Gateway mục tiêu route REST /api/v1/** và WS /ws/dashboard qua ws://analytics-service:8080; /internal/** không expose. Trong Docker mọi backend nghe 8080. [Schema đã đối chiếu](docs/database/README.md), [quyết định nguồn](docs/architecture/source-decisions.md).
