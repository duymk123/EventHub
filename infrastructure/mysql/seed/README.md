# Dữ liệu mẫu EventHub

Thư mục hiện chỉ có hướng dẫn, **chưa có file seed SQL**. Dữ liệu mẫu đi sau migration, không tự tạo bảng bằng seed và không dựa vào scaffold Hibernate tự sinh schema khi chưa có entity.

Notification/Analytics: Flyway chạy migration trước Hibernate validate. Event/Ticket/Order chưa có migration nghiệp vụ; hoàn thành schema/mapping trước khi nạp dữ liệu. [Schema tham chiếu](../../../docs/database/README.md) không phải script nâng cấp DB có sẵn.

Không seed users/password_hash/refresh_tokens vào eventhub_auth: identity thuộc Keycloak theo quyết định mới. User/role mẫu phải tạo/import qua Keycloak sau khi realm/client/mapping được chốt. Auth facade đã chốt giữ UI/API riêng; profile DB chỉ thêm khi có nhu cầu dữ liệu ngoài Keycloak. Không seed DB nghiệp vụ Gateway.

Khi thêm seed thật, dùng file riêng mỗi service và UUID cố định, không ID số. Tham chiếu mềm user/event/type/order phải nhất quán; loại vé VIP/PREMIUM/STANDARD. Dòng order_holders tương ứng từng vé, không gộp holder vào order_items. Không dùng QR/token/mật khẩu thật trong fixture. Template có id UUID và UNIQUE(code,channel).

Chỉ nạp INSERT sau khi migration tương ứng sẵn sàng. Ví dụ **sau khi đã tạo file seed event-service.sql**:

~~~powershell
Get-Content -Raw .\infrastructure\mysql\seed\event-service.sql | docker compose exec -T event-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" eventhub_event'
~~~

Chạy trong root repo. Lệnh không tự tạo schema; dùng tài khoản và database đúng service. Không in biến mật khẩu ra log. Thiết kế seed chạy lại có kiểm soát với khóa cố định; tránh INSERT IGNORE làm che lỗi FK/constraint. Không đổi migration V1 đã áp dụng hoặc bật baseline tự động để vượt lỗi schema. Backup/đối chiếu trước khi migrate dữ liệu có sẵn.
