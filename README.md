## 🐳 Báo cáo Cấu hình Docker & Port Mapping (EventHub Microservices)

Hệ thống **EventHub** sử dụng **Docker Compose** để đóng gói và vận hành toàn bộ các vi dịch vụ cùng hệ thống cơ sở hạ tầng (mySQL, Kafka, Zookeeper).

---

### 1. Bảng Tổng hợp Port Mapping

| STT | Tên Service / Container | Port Container (Internal) | Port Host (External) | Giao thức | Vai trò & Chức năng chính |
| :---: | :--- | :---: | :---: | :---: | :--- |
| **1** | `api-gateway` | `8080` | `8080` | HTTP | **API Gateway** — Cổng vào duy nhất, điều hướng request từ Client |
| **2** | `auth-service` | `8081` | `8081` | HTTP | **Xác thực** — Đăng nhập, đăng ký, cấp phát & xoay vòng JWT Token |
| **3** | `event-service` | `8082` | `8082` | HTTP | **Quản lý Sự kiện** — Danh mục, thông tin sự kiện & 4 loại khán đài A/B/C/D |
| **4** | `ticket-service` | `8083` | `8083` | HTTP | **Quản lý Vé** — Quota vé, giữ chỗ 3 phút (Reserve), phát hành & quét QR |
| **5** | `order-service` | `8084` | `8084` | HTTP | **Đơn hàng & Thanh toán** — Đặt vé, gọi Payment Webhook & Scheduler hủy đơn |
| **6** | `notification-service` | `8085` | `8085` | HTTP | **Thông báo** — Gửi Email đính kèm mã QR vé và thông báo In-app |
| **7** | `analytics-service` | `8086` | `8086` | HTTP / WS | **Thống kê & Realtime** — Báo cáo doanh số & đẩy Dashboard qua WebSocket |
| **8** | `postgres-db` | `5432` | `5432` | TCP | **PostgreSQL** — Cơ sở dữ liệu lưu trữ cho các dịch vụ |
| **9** | `kafka` | `9092` | `9092` | TCP | **Kafka Event Backbone** — Message broker cho truyền thông bất đồng bộ |
| **10** | `zookeeper` | `2181` | `2181` | TCP | **Zookeeper** — Điều phối cụm Kafka Cluster |

---

### 2. Chi tiết Cấu hình Môi trường các Service

#### 🔹 API Gateway (`api-gateway`)

* **Host Port:** `8080`
* **Biến môi trường chính:**
  * `SPRING_CLOUD_GATEWAY_ROUTES`: Định tuyến truy cập tới các dịch vụ phía sau (`http://auth-service:8081`, `http://order-service:8084`, v.v.).

#### 🔹 Auth Service (`auth-service`)

* **Host Port:** `8081`
* **Biến môi trường chính:**
  * `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres-db:5432/eventhub_auth`
  * `JWT_SECRET`: Khóa mã hóa chuỗi Access Token / Refresh Token.

#### 🔹 Event Service (`event-service`)

* **Host Port:** `8082`
* **Biến môi trường chính:**
  * `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres-db:5432/eventhub_event`

#### 🔹 Ticket Service (`ticket-service`)

* **Host Port:** `8083`
* **Biến môi trường chính:**
  * `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres-db:5432/eventhub_ticket`
  * `SPRING_KAFKA_BOOTSTRAP_SERVERS`: `kafka:9092`

#### 🔹 Order Service (`order-service`)

* **Host Port:** `8084`
* **Biến môi trường chính:**
  * `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres-db:5432/eventhub_order`
  * `TICKET_SERVICE_URL`: `http://ticket-service:8083`
  * `SPRING_KAFKA_BOOTSTRAP_SERVERS`: `kafka:9092`

#### 🔹 Notification Service (`notification-service`)

* **Host Port:** `8085`
* **Biến môi trường chính:**
  * `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres-db:5432/eventhub_notification`
  * `SPRING_MAIL_HOST`: Server SMTP cấu hình gửi mail (ví dụ `smtp.gmail.com`).

#### 🔹 Analytics Service (`analytics-service`)

* **Host Port:** `8086`
* **Biến môi trường chính:**
  * `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres-db:5432/eventhub_analytics`
  * `SPRING_KAFKA_BOOTSTRAP_SERVERS`: `kafka:9092`

---

### 3. Hướng dẫn Lệnh Thao tác với Docker

```bash
# 1. Khởi chạy toàn bộ hệ thống dưới dạng Background (Detached mode)
docker compose up -d --build

# 2. Kiểm tra danh sách các Container đang chạy và Port mapping
docker compose ps

# 3. Xem log thời gian thực của toàn bộ hệ thống
docker compose logs -f

# 4. Xem log của riêng một dịch vụ cụ thể (Ví dụ: order-service)
docker compose logs -f order-service

# 5. Dừng và gỡ bỏ toàn bộ container
docker compose down
```
