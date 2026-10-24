# Dữ liệu khởi tạo

Đặt các câu lệnh `INSERT` cho từng service trong file riêng tại đây, ví dụ:

- `auth-service.sql` → database `eventhub_auth`
- `event-service.sql` → database `eventhub_event`
- `ticket-service.sql` → database `eventhub_ticket`
- `order-service.sql` → database `eventhub_order`
- `notification-service.sql` → database `eventhub_notification`
- `analytics-service.sql` → database `eventhub_analytics`
- `api-gateway.sql` → database `eventhub_gateway`

Chạy `docker compose up --build -d` trước và chờ service tương ứng khởi động để Hibernate tạo bảng. Sau đó nạp file vào database container tương ứng, ví dụ trong PowerShell:

```powershell
Get-Content -Raw .\infrastructure\mysql\seed\auth-service.sql | docker compose exec -T auth-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" eventhub_auth'
```

Với service khác, đổi `auth-service.sql`, `auth-db` và `eventhub_auth` theo bảng sau:

| File SQL | Container DB | Database |
| --- | --- | --- |
| `auth-service.sql` | `auth-db` | `eventhub_auth` |
| `event-service.sql` | `event-db` | `eventhub_event` |
| `ticket-service.sql` | `ticket-db` | `eventhub_ticket` |
| `order-service.sql` | `order-db` | `eventhub_order` |
| `notification-service.sql` | `notification-db` | `eventhub_notification` |
| `analytics-service.sql` | `analytics-db` | `eventhub_analytics` |
| `api-gateway.sql` | `api-gateway-db` | `eventhub_gateway` |

Lệnh này chỉ chạy file SQL một lần; dữ liệu đã nạp sẽ lưu trong volume riêng của DB đó và vẫn còn sau `docker compose down`. Khi volume mới được tạo, mỗi thành viên cần nạp lại dữ liệu mẫu. Viết lệnh `INSERT` theo cách chạy lặp lại an toàn nếu có thể, ví dụ dùng khóa duy nhất kết hợp `INSERT IGNORE` hoặc `ON DUPLICATE KEY UPDATE`.

MySQL tạo database rỗng lúc khởi tạo container; Hibernate tạo bảng khi service khởi động. Vì vậy nạp các câu lệnh `INSERT` sau khi service đã khởi động.
