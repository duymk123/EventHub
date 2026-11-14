# Keycloak cho EventHub

Keycloak giữ tài khoản, mật khẩu, phiên đăng nhập và role (CUSTOMER / ORGANIZER / ADMIN). Frontend và Postman **không gọi Keycloak trực tiếp**: auth-service có API `/api/v1/auth/*` và tự gọi Keycloak phía sau (client confidential `eventhub-auth-service`). auth-service lưu thêm hồ sơ nghiệp vụ (họ tên, SĐT, avatar) theo `keycloak_sub`. Các service khác chỉ kiểm tra token.

Cấu hình realm nằm ở [`eventhub-realm.json`](eventhub-realm.json), dùng chung cho cả chạy local và Docker. Keycloak tự nạp file này khi khởi động; realm đã tồn tại thì **bỏ qua**, không ghi đè dữ liệu.

## Chạy local không cần Docker

Cần: Java 21+, MySQL 8 cài trên máy (cổng 3306).

```powershell
# Từ thư mục gốc repo. Lần đầu tự tải Keycloak (~170 MB) vào .local/ và tạo database `keycloak`.
powershell -ExecutionPolicy Bypass -File infrastructure\keycloak\start-keycloak-local.ps1 -DbPassword <mat-khau-root-mysql>
```

Có thể đặt biến môi trường `KC_LOCAL_DB_PASSWORD` thay cho `-DbPassword` (mặc định `root`). Chờ dòng `Listening on: http://localhost:8180`.

Sau đó chạy auth-service với profile `local` (MySQL local, database `eventhub_auth` tự tạo):

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"; .\mvnw -pl auth-service spring-boot:run
```

Trong IntelliJ: Run Configuration → Active profiles: `local`. Đổi mật khẩu MySQL bằng biến `DB_PASSWORD`.

## Địa chỉ và tài khoản dev

| Mục | Giá trị |
|---|---|
| Trang quản trị Keycloak | http://localhost:8180/admin — `admin` / `admin_dev_password` |
| Issuer (cấu hình service) | `http://localhost:8180/realms/eventhub` |
| Client của auth-service | `eventhub-auth-service` (confidential, secret dev `eventhub-auth-service-dev-secret`; service account có quyền `manage-users`) |
| Client của frontend | `eventhub-frontend` (public, dự phòng nếu sau này đăng nhập bằng trang Keycloak) |
| Dữ liệu Keycloak | MySQL local, database `keycloak` (bảng `user_entity`, `user_role_mapping`, …) |
| Hồ sơ nghiệp vụ | MySQL local, `eventhub_auth.user_profiles` |

| Tài khoản test | Mật khẩu | Role |
|---|---|---|
| `admin@eventhub.local` | `Admin@123` | ADMIN |
| `organizer@eventhub.local` | `Organizer@123` | ORGANIZER |
| `customer@eventhub.local` | `Customer@123` | CUSTOMER |

User đăng ký qua `POST /api/v1/auth/register` tự nhận **CUSTOMER** (role mặc định `default-roles-eventhub`). Mỗi user chỉ mang **một** business role: analytics-service từ chối token có hai role nghiệp vụ.

Chỉ dành cho môi trường dev: mật khẩu ở trên công khai trong repo.

## Thử bằng Postman

Cần chạy **cả hai**: Keycloak (script ở trên) rồi auth-service. Body là JSON.

| Bước | Request | Body | Kết quả |
|---|---|---|---|
| Đăng ký | `POST http://localhost:8081/api/v1/auth/register` | `{"email":"a@gmail.com","password":"Matkhau@123","fullName":"Nguyễn Văn A","phoneNumber":"0901234567"}` (`phoneNumber` không bắt buộc) | 201: user mới role CUSTOMER, tự tạo hồ sơ |
| Đăng nhập | `POST http://localhost:8081/api/v1/auth/login` | `{"email":"a@gmail.com","password":"Matkhau@123"}` | `accessToken` (5 phút), `refreshToken` |
| Xem tài khoản | `GET http://localhost:8081/api/v1/auth/me` | Authorization: Bearer `accessToken` | id, email, fullName, role, status |
| Hồ sơ | `GET / PATCH http://localhost:8081/api/v1/me/profile` | Bearer token; PATCH `{"fullName":"..."}` | |
| Làm mới token | `POST http://localhost:8081/api/v1/auth/refresh` | `{"refreshToken":"..."}` | Cặp token mới; refresh token cũ **mất hiệu lực** |
| Đăng xuất | `POST http://localhost:8081/api/v1/auth/logout` | `{"refreshToken":"..."}` | 204 |

Lỗi trả dạng `{code, message, timestamp, path}` với `code`: `VALIDATION_ERROR` (400), `EMAIL_ALREADY_EXISTS` / `PHONE_ALREADY_EXISTS` (409), `INVALID_CREDENTIALS` (401), `ACCOUNT_LOCKED` (403), `INVALID_REFRESH_TOKEN` / `REFRESH_TOKEN_EXPIRED` / `REFRESH_TOKEN_REUSED` (401), `AUTH_PROVIDER_UNAVAILABLE` (503: Keycloak chưa chạy hoặc cấu hình client sai).

## Sửa cấu hình realm

Sửa `eventhub-realm.json` không tự áp dụng cho realm đã có. Muốn nạp lại trên máy local: vào trang quản trị xóa realm `eventhub` rồi khởi động lại Keycloak (mất toàn bộ user trong realm), hoặc sửa trực tiếp trên trang quản trị rồi export lại vào file. Hai lưu ý đã gặp khi dựng file này:

- Role mặc định phải khai báo trong `roles.realm` kèm `composites`; Keycloak bỏ qua `composites` đặt trong khóa `defaultRole`.
- Từ Keycloak 26.8, Admin REST API chỉ nhận token có audience do client scope `kc-admin-api` thêm vào. Client `eventhub-auth-service` phải có `kc-admin-api` trong `defaultClientScopes`, nếu không mọi lệnh tạo user trả 401 (auth-service báo `AUTH_PROVIDER_UNAVAILABLE`).

## Chạy bằng Docker

`docker compose up -d keycloak-db keycloak` dùng Postgres trong Docker và cũng nạp `eventhub-realm.json`. Dữ liệu hai cách chạy độc lập nhau (MySQL local và Postgres Docker).
