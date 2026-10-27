# order-service

Thiết kế: [services.md](../services.md), [API](../docs/api/order-service-openapi.yaml), [quyết định BC_32_V2](../docs/architecture/source-decisions.md). Database: eventhub_order.

Sinh orderId → reserve → lưu Order/items/holders; một holder mỗi vé. Giá/hạn từ Ticket, không từ client. Payment ID provider có thể NULL trước phản hồi. Webhook phải xác minh chữ ký và đối chiếu amount/currency/transaction, dedup theo provider + provider_event_id; payload_hash không thay chữ ký. CAS với scheduler, chỉ một nhánh được hoàn quota. PAID rồi confirm tạm lỗi: job retry bền vững, chỉ order.paid sau phát vé. PAID nhưng reservation EXPIRED cần quy tắc C05/C06, không tự phát vé. Refund kiểm tra điều kiện >=48h lúc yêu cầu và duyệt; provider lỗi không hủy vé. Chính sách amount/từng vé/quyền duyệt C04 chưa chốt.

## Chạy và kiểm tra

Mở pom.xml ở root bằng IntelliJ, chọn JDK 21+ (language level 21). Dependency thực tế xem pom.xml của module; không coi ví dụ trong tài liệu là dependency đã được thêm.

Chạy lệnh từ **root repository**, nơi có Maven Wrapper:

```powershell
.\mvnw.cmd -pl order-service -am verify
.\mvnw.cmd -pl order-service spring-boot:run
```

Nếu Wrapper PowerShell lỗi ở khâu tìm Maven cache, dùng Maven 3.9.x đã cài với cùng tham số; chưa sửa Wrapper trong lượt tài liệu này. Cấu hình datasource qua DB_HOST/PORT/NAME/USERNAME/PASSWORD; [Docker.md](../Docker.md) có port local. Context test profile test hiện không kết nối DB, không chứng minh API/DB/Kafka đã hoạt động.

Module còn scaffold Hibernate update, chưa có migration nghiệp vụ; chuyển sang migration trước môi trường chung.
