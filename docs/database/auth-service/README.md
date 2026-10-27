# Auth facade và Keycloak

Đã chốt: giữ giao diện/API tài khoản EventHub riêng, **Auth Service gọi Keycloak** để quản lý đăng ký/đăng nhập/token/session/password và tài khoản. Không dùng mô hình Auth tự băm mật khẩu/cấp JWT ở Bảng 2.1–2.2. Không tạo password_hash, refresh_tokens hoặc users_roles cạnh tranh với Keycloak.

Keycloak quản lý schema identity riêng bằng migration của sản phẩm; EventHub dùng API/OIDC, không đọc SQL/FK sang DB Keycloak. Quy tắc UUID ứng dụng không áp đặt lên schema nội bộ do Keycloak quản lý.

Auth hiện là scaffold có datasource eventhub_auth nhưng chưa có entity. Giữ Auth facade không đồng nghĩa phải có DB profile: chỉ thêm nếu có dữ liệu nghiệp vụ riêng ngoài Keycloak; lúc đó id UUID và Keycloak subject mapping phải được định nghĩa rõ. Đề xuất kỹ thuật dùng thẳng sub UUID làm userId EventHub để tránh mapping thứ hai; cần kiểm tra cấu hình realm/import user khi triển khai.

CUSTOMER/ORGANIZER/ADMIN và trạng thái tài khoản do Keycloak quản lý; nếu có profile projection thì nó không là nguồn cấp quyền. Không seed credentials local. Realm/client, role mapper, grant flow cho UI riêng, quyền Admin API/service account, TTL và S2S còn chờ cấu hình, như đã thống nhất người phụ trách sẽ báo lại.
