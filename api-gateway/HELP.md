# API Gateway

Hiện là module scaffold Spring Web/JPA, chưa có route hoặc security. Thiết kế mục tiêu: điểm vào REST /api/v1/**, route WS riêng /ws/dashboard → analytics-service qua ws://analytics-service:8080. /internal/** không public. Auth facade giữ UI/API tài khoản riêng và gọi Keycloak. Keycloak cung cấp token; Gateway và service kiểm tra token theo cấu hình issuer/audience/roles còn chờ chốt.

eventhub_gateway và datasource/JPA trong Compose là dư từ scaffold, chưa có nhu cầu DB nghiệp vụ trong BC_32_V2. Không tạo bảng auth/token/Gateway session để tự thay Keycloak; chỉ bỏ DB/dependency bằng thay đổi triển khai riêng đã kiểm tra.

Build từ root: .\mvnw.cmd -pl api-gateway -am verify. Đã bỏ file/profile/dependency test; build không xác nhận route hoạt động. Xem [Docker.md](../Docker.md), [services.md](../services.md), [các quyết định](../docs/architecture/source-decisions.md).
