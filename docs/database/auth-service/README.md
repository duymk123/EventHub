# Auth facade và Keycloak

Đã chốt: giữ giao diện/API tài khoản EventHub riêng, **Auth Service gọi Keycloak** để quản lý đăng ký/đăng nhập/token/session/password và tài khoản. Không dùng mô hình Auth tự băm mật khẩu/cấp JWT ở Bảng 2.1–2.2. Không tạo password_hash, refresh_tokens hoặc users_roles cạnh tranh với Keycloak.

Keycloak quản lý schema identity riêng bằng migration của sản phẩm; EventHub dùng API/OIDC, không đọc SQL/FK sang DB Keycloak. Quy tắc UUID ứng dụng không áp đặt lên schema nội bộ do Keycloak quản lý.

Đối chiếu code ngày 2026-10-29: Auth có datasource eventhub_auth và entity User/BaseEntity ánh xạ users cùng các trường audit; chưa có DDL/migration Auth được duyệt. [Giải thích từng trường và khác biệt với tài liệu cũ](fields.md). Giữ Auth facade không tự phê duyệt DB profile hay biến local users thành nguồn mật khẩu/token; phạm vi dữ liệu riêng và mapping với Keycloak cần được xác định rõ. Đề xuất dùng sub UUID làm userId cần kiểm tra cấu hình; @UuidGenerator trong code hiện không tự đồng bộ sub.

CUSTOMER/ORGANIZER/ADMIN và trạng thái tài khoản do Keycloak quản lý; nếu có profile projection thì nó không là nguồn cấp quyền. Không seed credentials local. Realm/client, role mapper, grant flow cho UI riêng, quyền Admin API/service account, TTL và S2S còn chờ cấu hình, như đã thống nhất người phụ trách sẽ báo lại.
