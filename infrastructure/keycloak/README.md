# Keycloak cho EventHub

Keycloak giữ tài khoản, mật khẩu, phiên đăng nhập và role (CUSTOMER / ORGANIZER / ADMIN). Frontend và Postman **không gọi Keycloak trực tiếp**: auth-service có API `/api/v1/auth/*` và tự gọi Keycloak phía sau (client confidential `eventhub-auth-service`). auth-service lưu thêm hồ sơ nghiệp vụ (họ tên, SĐT, avatar) theo `keycloak_sub`. Các service khác chỉ kiểm tra token.

Cấu hình realm nằm ở [`eventhub-realm.json`](eventhub-realm.json). Container Keycloak tự nạp file này khi khởi động (`--import-realm`); realm đã tồn tại thì **bỏ qua**, không ghi đè dữ liệu.

## Chạy

Cần: Docker Desktop, MySQL 8 cài trên máy (cổng 3306, tài khoản root/root) cho auth-service.

**Bước 1: Keycloak.** Ở thư mục gốc repo:

```
docker compose up -d keycloak-db keycloak
docker logs -f eventhub-keycloak
```

Chờ dòng `Listening on: http://0.0.0.0:8080` (cổng máy là 8180), Ctrl+C để thoát xem log. Dữ liệu Keycloak nằm trong Postgres của container `eventhub-keycloak-db` (volume `keycloak_db_data`).

**Bước 2: auth-service.** Bấm Run `AuthServiceApplication` trong IntelliJ như bình thường. Không cần profile hay biến môi trường: `application.yml` mặc định dùng MySQL `localhost:3306` (root/root, tự tạo `eventhub_auth`) và Keycloak `localhost:8180`. Mật khẩu root MySQL khác `root` thì đặt biến `DB_PASSWORD` cho auth-service.

Tắt: `docker compose stop keycloak keycloak-db` (giữ dữ liệu). Xóa sạch để nạp lại realm từ đầu: `docker compose rm -sf keycloak keycloak-db` rồi `docker volume rm eventhub_keycloak_db_data`.

## Địa chỉ và tài khoản dev

| Mục | Giá trị |
|---|---|
| Trang quản trị Keycloak | http://localhost:8180/admin — `admin` / `admin_dev_password` |
| Issuer (cấu hình service) | `http://localhost:8180/realms/eventhub` (cố định bằng `KC_HOSTNAME`, kể cả khi service trong Docker gọi `keycloak:8080`) |
| Audience | `eventhub-api`: mapper `eventhub-api-audience` trên mọi client thêm vào access token; mọi service kiểm tra (`EVENTHUB_JWT_AUDIENCE`, để trống thì bỏ qua) |
| Mail | Keycloak gửi mail xác minh qua Mailpit (`mailpit:1025`); xem mail tại http://localhost:8025 (`docker compose up -d mailpit`) |
| Client của auth-service | `eventhub-auth-service` (confidential, secret dev `eventhub-auth-service-dev-secret`; service account có quyền `manage-users`) |
| Client của frontend | `eventhub-frontend` (public, dự phòng nếu sau này đăng nhập bằng trang Keycloak) |
| Client service-to-service | `eventhub-order-service` (Order → Ticket), `eventhub-ticket-service` (Ticket → Event), `eventhub-analytics-service` (Analytics → Event/Ticket), `eventhub-event-service` (Event → Ticket, kiểm tra cấu hình vé trước publish); confidential, chỉ `client_credentials`, secret dev `<clientId>-dev-secret`. Service nhận lời gọi `/internal/**` đổi claim `azp` thành role riêng của từng service và chỉ mở đúng API service đó cần (vd analytics không gọi được reserve/confirm của ticket) |
| Dữ liệu Keycloak | Postgres trong container `eventhub-keycloak-db` (database `keycloak`) |
| Hồ sơ nghiệp vụ | MySQL local, `eventhub_auth.user_profiles` |

| Tài khoản test | Mật khẩu | Role |
|---|---|---|
| `admin@eventhub.local` | `Admin@123` | ADMIN |
| `organizer@eventhub.local` | `Organizer@123` | ORGANIZER |
| `customer@eventhub.local` | `Customer@123` | CUSTOMER |

User đăng ký qua `POST /api/v1/auth/register` được auth-service gán **CUSTOMER trực tiếp**. Role mặc định `default-roles-eventhub` **không** chứa business role nào, nên khi ADMIN đổi role (`PATCH /api/v1/admin/users/{id}/role`) chỉ cần thay một mapping. Mỗi user chỉ mang **một** business role: mọi service từ chối (401) token có hai role nghiệp vụ.

User mới có `emailVerified=false`; auth-service gắn required action `VERIFY_EMAIL` rồi mới gửi mail xác minh. **Chưa xác minh thì chưa đăng nhập được**: Keycloak trả `invalid_grant` "Account is not fully set up" (auth-service map thành `ACCOUNT_LOCKED`, 403). Mail ở http://localhost:8025 → mở link → trang "Xác thực địa chỉ email" → bấm **"Nhấp vào đây để tiếp tục"** (chỉ mở link chưa đủ) → `emailVerified=true`, sau đó đăng nhập bình thường. Mail hết hạn/lạc: ADMIN xóa required action `VERIFY_EMAIL` trên trang quản trị (xóa xong user đăng nhập được ngay) hoặc user nhờ ADMIN gửi lại mail qua `resendVerificationEmail` khi đã có token (nên đăng nhập lại trước khi gọi, vì mỗi lần lấy token tài khoản bị khóa sẽ tốn một lượt brute-force).

Chỉ dành cho môi trường dev: mật khẩu ở trên công khai trong repo.

## Thử bằng Postman

Cần chạy **cả hai**: Keycloak (Docker, ở trên) rồi auth-service. Body là JSON.

| Bước | Request | Body | Kết quả |
|---|---|---|---|
| Đăng ký | `POST http://localhost:8081/api/v1/auth/register` | `{"email":"a@gmail.com","password":"Matkhau@123","fullName":"Nguyễn Văn A","phoneNumber":"0901234567"}` (`phoneNumber` không bắt buộc) | 201: user mới role CUSTOMER, tự tạo hồ sơ |
| Đăng nhập | `POST http://localhost:8081/api/v1/auth/login` | `{"email":"a@gmail.com","password":"Matkhau@123"}` | `accessToken` (5 phút), `refreshToken` |
| Xem tài khoản | `GET http://localhost:8081/api/v1/auth/me` | Authorization: Bearer `accessToken` | id, email, fullName, role, status, emailVerified |
| Hồ sơ | `GET / PATCH http://localhost:8081/api/v1/me/profile` | Bearer token; PATCH `{"fullName":"..."}` | Tài khoản test nạp từ realm chưa có hồ sơ: tạo bằng `PUT` cùng đường dẫn, body `{"fullName":"..."}` |
| Gửi lại mail xác minh | `POST http://localhost:8081/api/v1/auth/verify-email/resend` | Bearer token | 204; đã xác minh → 409 `EMAIL_ALREADY_VERIFIED` |
| Đổi mật khẩu | `POST http://localhost:8081/api/v1/auth/change-password` | `{"currentPassword":"...","newPassword":"..."}` | 204; sai mật khẩu cũ → 400 `CURRENT_PASSWORD_INCORRECT` |
| Khóa / mở khóa (ADMIN) | `PATCH http://localhost:8081/api/v1/admin/users/{userId}/lock` hoặc `/unlock` | Bearer token ADMIN | 204; khóa thì thu hồi luôn refresh token |
| Đổi role (ADMIN) | `PATCH http://localhost:8081/api/v1/admin/users/{userId}/role` | `{"role":"ORGANIZER"}` | 204; người dùng nhận role mới ở lần refresh tiếp theo |
| Làm mới token | `POST http://localhost:8081/api/v1/auth/refresh` | `{"refreshToken":"..."}` | Cặp token mới; refresh token cũ **mất hiệu lực** |
| Đăng xuất | `POST http://localhost:8081/api/v1/auth/logout` | `{"refreshToken":"..."}` | 204 |

Lỗi trả dạng `{code, message, timestamp, path}` với `code`: `VALIDATION_ERROR` (400), `EMAIL_ALREADY_EXISTS` / `PHONE_ALREADY_EXISTS` (409), `INVALID_CREDENTIALS` (401), `ACCOUNT_LOCKED` (403), `INVALID_REFRESH_TOKEN` / `REFRESH_TOKEN_EXPIRED` / `REFRESH_TOKEN_REUSED` (401), `CURRENT_PASSWORD_INCORRECT` (400), `PROFILE_NOT_FOUND` (404), `PROFILE_ALREADY_EXISTS` / `EMAIL_ALREADY_VERIFIED` / `ACCOUNT_ALREADY_LOCKED` / `ACCOUNT_ALREADY_ACTIVE` / `CANNOT_MODIFY_OWN_ACCOUNT` / `ROLE_MAPPING_CONFLICT` (409), `AUTH_PROVIDER_UNAVAILABLE` (503: Keycloak chưa chạy hoặc cấu hình client sai).

## Sửa cấu hình realm

Sửa `eventhub-realm.json` không tự áp dụng cho realm đã có. **Bản sửa phân quyền ngày 2026-11-19 bắt buộc nạp lại realm** vì nó thêm audience `eventhub-api`, tách CUSTOMER khỏi role mặc định, thêm client `eventhub-event-service` và SMTP Mailpit, và tắt tự đăng ký trên trang Keycloak. Với realm cũ, mọi service trả 401 vì token thiếu audience; user cũ mang CUSTOMER qua role mặc định nên đổi role sẽ báo `ROLE_MAPPING_CONFLICT`. Muốn nạp lại: vào trang quản trị xóa realm `eventhub` rồi `docker compose restart keycloak` (mất toàn bộ user trong realm), hoặc sửa trực tiếp trên trang quản trị rồi export lại vào file. Hai lưu ý đã gặp khi dựng file này:

- Role mặc định phải khai báo trong `roles.realm` kèm `composites`; Keycloak bỏ qua `composites` đặt trong khóa `defaultRole`.
- Từ Keycloak 26.8, Admin REST API chỉ nhận token có audience do client scope `kc-admin-api` thêm vào. Client `eventhub-auth-service` phải có `kc-admin-api` trong `defaultClientScopes`, nếu không mọi lệnh tạo user trả 401 (auth-service báo `AUTH_PROVIDER_UNAVAILABLE`).
