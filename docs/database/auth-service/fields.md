# Giải thích các trường dữ liệu Auth Service

Database: `eventhub_auth`. Auth Service là facade API/UI tài khoản riêng, gọi Keycloak qua Admin REST API và OIDC endpoints. Keycloak quản lý hoàn toàn identity, credentials, session, token và role mapping. EventHub không truy cập trực tiếp bảng nội bộ của Keycloak, không tạo bảng `users`, `password_hash`, `refresh_tokens`, `users_roles` local.

Nguồn đối chiếu: [tài liệu ranh giới Auth](README.md), [quyết định nguồn](../../architecture/source-decisions.md), [plan Auth](../../plans/auth-service.md), [OpenAPI Auth](../../api/auth-service-openapi.yaml).

---

## 1. Không có bảng `users` local

| Thông tin | Xử lý |
|-----------|-------|
| **userId** (PK ứng dụng) | = Keycloak `sub` (UUID). Mapping 1-1, không cần bảng mapping. Mọi service dùng `userId` này làm `customer_id`, `organizer_id`, `recipient_user_id`... |
| **email** | Lấy từ Keycloak (`email` claim hoặc `/admin/realms/{realm}/users/{id}`). Không lưu local. |
| **role** (CUSTOMER/ORGANIZER/ADMIN) | Lấy từ Keycloak realm roles (qua Protocol Mapper đưa vào token claim `business_role` hoặc `realm_access.roles`). Không lưu local. |
| **status** (ACTIVE/LOCKED) | Mapping từ Keycloak `enabled` (true=ACTIVE, false=LOCKED). Không lưu local. |
| **password / credentials** | Hoàn toàn thuộc Keycloak. Auth facade không nhận, log, hash, so sánh password. |
| **refresh token / session** | Hoàn toàn thuộc Keycloak. Auth facade chỉ proxy `logout`/`refresh` về Keycloak. |

---

## 2. Bảng `user_profiles` (Optional projection)

Bảng projection tùy chọn cho profile nghiệp vụ riêng (avatar, phone, full_name tách Keycloak). Chỉ dùng khi FE yêu cầu profile mở rộng. **Đã implement sẵn entity/repository/service/controller/mapper.**

| Trường | Kiểu | Ý nghĩa |
|--------|------|---------|
| `id` | CHAR(36) PK | UUID nội bộ EventHub (khác `keycloak_sub`) |
| `keycloak_sub` | VARCHAR(255) UNIQUE NOT NULL | Keycloak subject ID (sub) — dùng để join với Keycloak |
| `full_name` | VARCHAR(100) | Tên hiển thị (có thể khác Keycloak) |
| `phone_number` | VARCHAR(20) UNIQUE | SĐT liên hệ |
| `avatar_url` | VARCHAR(500) | Link avatar |
| `create_at`, `update_at`, `created_by`, `updated_by`, `deleted` | Audit fields | BaseEntity |

> **Lưu ý**: Nếu FE không cần profile tách biệt, bỏ qua bảng này và chỉ dùng thông tin từ Keycloak (`email`, `name`, `roles` trong token).

---

## 3. Phân biệt dữ liệu Auth, Keycloak và các service khác

| Thông tin | Nguồn sự thật | Cách các service dùng |
|-----------|---------------|----------------------|
| `userId` | Keycloak `sub` (UUID) | Mọi service dùng làm FK mềm (customer_id, organizer_id...) |
| `email` | Keycloak `email` claim | Order snapshot `customer_email` lúc tạo đơn |
| `role` | Keycloak realm role (business) | Gateway/Auth map → `Actor(UUID, BusinessRole)` |
| `status` | Keycloak `enabled` | Auth facade map ACTIVE/LOCKED |
| `password/token/session` | Keycloak only | Không bao giờ ra khỏi Keycloak |
| `full_name`, `phone`, `avatar` | `user_profiles` (nếu có) / Keycloak | FE hiển thị profile; Order/Notification dùng email từ Keycloak |