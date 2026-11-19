# Auth Facade và Keycloak

Auth Service là facade API/UI tài khoản riêng, gọi Keycloak qua Admin REST API và OIDC endpoints. Keycloak quản lý hoàn toàn identity, credentials, session, token và role mapping. Auth Service không tự ký JWT, không lưu `password_hash`, `refresh_tokens`, `users_roles` hay bảng `users` local.

Keycloak quản lý schema identity riêng bằng migration của sản phẩm; EventHub dùng API/OIDC, không đọc SQL/FK sang DB Keycloak. `userId` trong EventHub = Keycloak `sub` (UUID), mapping 1-1, không cần bảng mapping local.

---

## Hiện trạng dữ liệu

- Database `eventhub_auth` chưa có bảng identity (users, roles, tokens).
- Bảng `user_profiles` là optional projection cho profile nghiệp vụ riêng (avatar, phone, full_name tách Keycloak). **Đã implement sẵn entity/repository/service/controller/mapper.** Chỉ dùng khi FE yêu cầu profile mở rộng.
- CUSTOMER/ORGANIZER/ADMIN và ACTIVE/LOCKED là mapping ứng dụng từ Keycloak (realm roles + `enabled`), không phải cột DB local.
- Không seed credentials local. Realm/client, role mapper, grant flow cho UI riêng, quyền Admin API/service account, TTL và S2S là cấu hình tích hợp Keycloak.

---

## Ranh giới dữ liệu

| Yếu tố | Nguồn | Auth Service xử lý |
|--------|-------|---------------------|
| Tạo user | Keycloak Admin API | Proxy `POST /admin/realms/{realm}/users` → trả `UserResponse` |
| Xác thực | Keycloak Token Endpoint | Proxy `POST /realms/{realm}/protocol/openid-connect/token` → trả `TokenResponse` |
| Refresh token | Keycloak Token Endpoint | Proxy `grant_type=refresh_token` → trả `TokenResponse` |
| Logout | Keycloak End Session | Proxy `POST /realms/{realm}/protocol/openid-connect/logout` hoặc Admin API revoke |
| Đổi mật khẩu | Keycloak Admin API / Account API | Proxy `PUT /admin/realms/{realm}/users/{id}/reset-password` hoặc Account API |
| Khóa/mở khóa | Keycloak Admin API | Proxy `PUT /admin/realms/{realm}/users/{id}` (`enabled`=false/true) |
| Gán role | Keycloak Admin API | Proxy realm role mappings (chỉ CUSTOMER/ORGANIZER/ADMIN) |
| Lấy profile (me) | Keycloak User Info / Admin API | Proxy `GET /realms/{realm}/protocol/openid-connect/userinfo` hoặc Admin GET user |
| Profile nghiệp vụ (avatar, phone) | `user_profiles` table | CRUD qua `UserProfileService` (optional) |

**Không có**: bảng users local, password_hash, refresh_tokens, users_roles, JWT signing key, token TTL config.

---

## Admin UI quản lý user (Yêu cầu bắt buộc)

> **Keycloak WebUI CHỈ dùng cho test/dev.** Sản phẩm cuối **CÓ ADMIN UI RIÊNG** (trong EventHub Frontend) để quản trị viên vận hành tài khoản user đầy đủ.

**Các tính năng Admin UI cần hỗ trợ (gọi Auth Service facade API):**

| Tính năng | Endpoint Auth Service | Mô tả |
|-----------|----------------------|-------|
| Danh sách user (phân trang, tìm kiếm) | `GET /api/v1/admin/users` | Lấy từ Keycloak qua Admin API, map về DTO |
| Xem chi tiết user | `GET /api/v1/admin/users/{userId}` | Profile + role + status + timestamps |
| Khóa tài khoản | `PATCH /api/v1/admin/users/{userId}/lock` | Disable user + revoke sessions ở Keycloak |
| Mở khóa tài khoản | `PATCH /api/v1/admin/users/{userId}/unlock` | Enable user |
| Gán/chuyển role | `PATCH /api/v1/admin/users/{userId}/role` | Chỉ CUSTOMER/ORGANIZER/ADMIN (business roles) |
| Reset mật khẩu (admin) | `POST /api/v1/admin/users/{userId}/reset-password` | Gửi email reset hoặc set temporary password |
| Xem session/đăng xuất tất cả | `GET/DELETE /api/v1/admin/users/{userId}/sessions` | Quản trị session hoạt động |

**Yêu cầu kỹ thuật:**
- Admin UI tự triển khai (Next.js), **không embed Keycloak Admin Console**
- Tất cả thao tác qua Auth Service facade → Keycloak Admin API (service account)
- Phân quyền: chỉ ADMIN (role trong token) mới truy cập `/api/v1/admin/**`
- Audit log: ghi nhận admin nào thực hiện hành động gì, khi nào