# Auth Service ↔ Keycloak: Request Flow Diagrams

Mỗi diagram minh họa luồng request từ **Frontend → Auth Service (Facade) → Keycloak** cho từng use case. Auth Service **không tự ký JWT, không lưu password/refresh token** — chỉ proxy gọi Keycloak Admin REST API và OIDC endpoints.

---

## 1. Đăng ký (Register) — `POST /api/v1/auth/register`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/register {email, password, fullName}
    AS->>AS: Validate request (email format, pass length)
    AS->>KC: POST /admin/realms/{realm}/users<br/>{email, username, enabled:true, credentials:[{type:password, value, temporary:false}], realmRoles:[CUSTOMER]}
    Note over KC: Tạo user, hash password, gán realm role CUSTOMER
    KC-->>AS: 201 Created + Location: /users/{uuid}
    AS->>KC: GET /admin/realms/{realm}/users/{uuid}
    KC-->>AS: UserRepresentation (id, email, createdTimestamp, realmRoles, enabled)
    AS->>AS: Map → UserResponse (id=sub, email, fullName, role=CUSTOMER, status=ACTIVE, createdAt)
    AS-->>FE: 201 Created + UserResponse
```

**Key Points:**
- Auth Service **không hash password** — gửi plaintext qua TLS tới Keycloak Admin API
- Keycloak tự hash (PBKDF2/Argon2 theo config), lưu credential
- Role `CUSTOMER` gán mặc định ở Auth Service, **không tin role từ request FE**
- `userId` trả về = Keycloak `sub` (UUID)

---

## 2. Đăng nhập (Login) — `POST /api/v1/auth/login`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/login {email, password}
    AS->>AS: Validate request
    Note over AS, KC: Chọn 1 trong 2 flow (cấu hình realm/client):<br/>A. Resource Owner Password Credentials Grant (ROPC)<br/>B. Authorization Code + PKCE (redirect FE→KC login page→callback)
    alt Flow A: ROPC (Direct Grant)
        AS->>KC: POST /realms/{realm}/protocol/openid-connect/token<br/>grant_type=password&client_id=...&username=email&password=...
    else Flow B: Authorization Code
        FE->>KC: Redirect to KC login page (Auth chỉ trả URL)
        KC-->>FE: Login form → User submit
        KC->>FE: Redirect back với `code`
        FE->>AS: POST /auth/login {code}
        AS->>KC: POST /token grant_type=authorization_code&code=...
    end
    KC-->>AS: TokenResponse {access_token, refresh_token, expires_in, token_type}
    Note over KC: Access token JWT chứa: sub (UUID), realm_access.roles, email, email_verified
    AS->>AS: Map → TokenResponse (không verify JWT, chỉ forward)
    AS-->>FE: 200 OK + TokenResponse
```

**Key Points:**
- **Flow A (ROPC)**: FE gửi email/password thẳng → Auth proxy tới KC token endpoint. **Cần bật Direct Access Grants trên client KC**.
- **Flow B (Auth Code + PKCE)**: An toàn hơn, FE redirect về KC login page. Auth chỉ proxy code exchange.
- Keycloak **cấp token**, Auth **không ký JWT**.
- Access token JWT chứa `sub` (UUID), `realm_access.roles`, `email`, `email_verified`.

---

## 3. Refresh Token — `POST /api/v1/auth/refresh`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/refresh {refreshToken}
    AS->>KC: POST /realms/{realm}/protocol/openid-connect/token<br/>grant_type=refresh_token&client_id=...&refresh_token=...
    Note over KC: Kiểm tra refresh token hợp lệ, rotation/reuse theo config KC
    alt Valid
        KC-->>AS: TokenResponse mới {access_token, refresh_token, expires_in}
        AS-->>FE: 200 OK + TokenResponse mới
    else Invalid/Expired/Reused
        KC-->>AS: 400/401 error
        AS-->>FE: 401 INVALID_REFRESH_TOKEN hoặc REFRESH_TOKEN_EXPIRED/REUSED
    end
```

**Key Points:**
- Auth **không lưu refresh token local** — hoàn toàn delegate cho Keycloak
- Rotation/reuse policy do Keycloak config (không phải code Auth)
- Nếu reused → Keycloak revoke toàn bộ token family

---

## 4. Đăng xuất (Logout) — `POST /api/v1/auth/logout`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/logout {refreshToken}
    Note over AS: 2 cách implement (chọn 1 theo config KC):
    alt Cách 1: OIDC End Session (revoke refresh token)
        AS->>KC: POST /realms/{realm}/protocol/openid-connect/logout<br/>client_id=...&refresh_token=...
    else Cách 2: Admin API Revoke (cần service account quyền)
        AS->>KC: GET /admin/realms/{realm}/users/{userId} (lấy userId từ refresh token introspect)
        AS->>KC: POST /admin/realms/{realm}/users/{userId}/logout
    end
    KC-->>AS: 204 No Content
    AS-->>FE: 204 No Content
```

**Key Points:**
- Không chỉ xóa token FE — phải revoke ở Keycloak
- Access token đã cấp vẫn hợp lực đến khi hết hạn (trừ khi config token revocation + resource server check introspection)

---

## 5. Lấy Profile (Me) — `GET /api/v1/auth/me`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: GET /auth/me<br/>Authorization: Bearer <access_token>
    AS->>AS: Spring Security verify JWT (JWK từ KC, issuer, audience, exp)
    AS->>AS: Extract sub (UUID) từ JWT principal
    Note over AS: 2 cách lấy profile (chọn 1):
    alt Cách 1: UserInfo Endpoint (nhẹ, chuẩn OIDC)
        AS->>KC: GET /realms/{realm}/protocol/openid-connect/userinfo<br/>Authorization: Bearer <access_token>
        KC-->>AS: {sub, email, name, preferred_username, realm_access.roles, email_verified}
    else Cách 2: Admin API (đầy đủ, cần service account)
        AS->>KC: GET /admin/realms/{realm}/users/{sub}
        KC-->>AS: UserRepresentation (full profile)
    end
    AS->>AS: Map → UserResponse (id=sub, email, fullName, role, status, createdAt)
    AS-->>FE: 200 OK + UserResponse
```

**Key Points:**
- **Không query DB local** — Keycloak là source of truth
- `role` = map từ `realm_access.roles` (chỉ lấy CUSTOMER/ORGANIZER/ADMIN)
- `status` = map từ `enabled` (true=ACTIVE, false=LOCKED)
- `createdAt` = `createdTimestamp` từ Keycloak

---

## 6. Đổi Mật Khẩu (User Self) — `POST /api/v1/auth/change-password`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/change-password {currentPassword, newPassword}<br/>Authorization: Bearer <access_token>
    AS->>AS: Verify JWT, extract sub
    Note over AS: 2 cách (chọn 1 theo config):
    alt Cách 1: Account API (user tự đổi, cần current password)
        AS->>KC: PUT /realms/{realm}/account/password<br/>{type:password, value:newPassword, temporary:false}<br/>Authorization: Bearer <access_token>
    else Cách 2: Admin API (cần service account, verify current password thủ công)
        AS->>KC: POST /realms/{realm}/protocol/openid-connect/token<br/>grant_type=password&username=...&password=currentPassword (verify)
        AS->>KC: PUT /admin/realms/{realm}/users/{sub}/reset-password<br/>{type:password, value:newPassword, temporary:false}
    end
    KC-->>AS: 204 No Content
    AS-->>FE: 204 No Content
```

**Key Points:**
- **Không so sánh hash local** — delegate verify cho Keycloak
- Cách 1 (Account API): User tự đổi, cần current password, dùng access token của user
- Cách 2 (Admin API): Cần service account, Auth tự verify current password trước

---

## 7. Quên Mật Khẩu (Forgot Password) — `POST /api/v1/auth/forgot-password`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/forgot-password {email}
    AS->>KC: POST /admin/realms/{realm}/users?email={email}&exact=true
    KC-->>AS: [UserRepresentation]
    alt User tồn tại
        AS->>KC: PUT /admin/realms/{realm}/users/{id}/execute-actions-email<br/>["UPDATE_PASSWORD"]<br/>(hoặc dùng Account API send verify email)
        KC-->>AS: 204
        Note over KC: Gửi email chứa link reset password (token reset, expiry config KC)
    else User không tồn tại
        Note over AS: Vẫn trả 204 (tránh user enumeration)
    end
    AS-->>FE: 204 No Content
```

**Key Points:**
- **Luôn trả 204** dù email có tồn tại hay không (security: anti-enumeration)
- Keycloak tự xử lý email template, token reset, expiry
- Link reset redirect về FE `/reset-password?token=...`

---

## 8. Reset Mật Khẩu (User) — `POST /api/v1/auth/reset-password`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Frontend
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /auth/reset-password {resetToken, newPassword}
    AS->>KC: PUT /admin/realms/{realm}/users/{id}/reset-password<br/>{type:password, value:newPassword, temporary:false}<br/>(cần userId — lấy từ token introspect hoặc decode resetToken)
    KC-->>AS: 204
    AS-->>FE: 204 No Content
```

---

## 9. Admin: Danh Sách User — `GET /api/v1/admin/users`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Admin UI
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: GET /admin/users?page=0&size=20&search=abc&role=ORGANIZER&status=ACTIVE<br/>Authorization: Bearer <admin_access_token>
    AS->>AS: Verify JWT, check role=ADMIN
    AS->>KC: GET /admin/realms/{realm}/users<br/>?first=0&max=20&search=abc&briefRepresentation=false
    KC-->>AS: [UserRepresentation]
    AS->>AS: Filter by role/status (KC search hạn chế), map → UserResponse[]
    AS-->>FE: 200 OK + Page<UserResponse>
```

**Key Points:**
- Chỉ ADMIN truy cập (Spring Security `@PreAuthorize("hasRole('ADMIN')")`)
- Keycloak Admin API search giới hạn → Auth Service filter thêm ở memory
- Pagination: `first`/`max` mapping từ `page`/`size`

---

## 10. Admin: Chi Tiết User — `GET /api/v1/admin/users/{userId}`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Admin UI
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: GET /admin/users/{userId}<br/>Authorization: Bearer <admin_access_token>
    AS->>AS: Verify JWT, check ADMIN
    AS->>KC: GET /admin/realms/{realm}/users/{userId}
    KC-->>AS: UserRepresentation (full: id, email, username, firstName, lastName, emailVerified, enabled, createdTimestamp, realmRoles, clientRoles, attributes)
    AS->>AS: Map → UserDetailResponse (+ emailVerified, lastLoginAt, username)
    AS-->>FE: 200 OK + UserDetailResponse
```

---

## 11. Admin: Khóa/Mở Khóa — `PATCH /admin/users/{userId}/lock|unlock`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Admin UI
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: PATCH /admin/users/{userId}/lock<br/>Authorization: Bearer <admin_access_token>
    AS->>AS: Verify ADMIN
    AS->>KC: GET /admin/realms/{realm}/users/{userId}
    KC-->>AS: UserRepresentation
    AS->>KC: PUT /admin/realms/{realm}/users/{userId}<br/>{enabled: false}  (lock) / {enabled: true} (unlock)
    KC-->>AS: 204
    Note over AS: Cần revoke sessions?
    AS->>KC: POST /admin/realms/{realm}/users/{userId}/logout
    KC-->>AS: 204
    AS-->>FE: 204 No Content
```

**Key Points:**
- Lock = `enabled=false` + revoke sessions
- Unlock = `enabled=true`
- Idempotent: gọi lại không lỗi

---

## 12. Admin: Gán Role — `PATCH /admin/users/{userId}/role`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Admin UI
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: PATCH /admin/users/{userId}/role {role: ORGANIZER}<br/>Authorization: Bearer <admin_access_token>
    AS->>AS: Verify ADMIN, validate role ∈ {CUSTOMER, ORGANIZER, ADMIN}
    AS->>KC: GET /admin/realms/{realm}/roles/{role}
    KC-->>AS: RoleRepresentation (id, name)
    AS->>KC: GET /admin/realms/{realm}/users/{userId}/role-mappings/realm
    KC-->>AS: Current roles
    AS->>KC: DELETE /admin/realms/{realm}/users/{userId}/role-mappings/realm<br/>(xóa business roles cũ: CUSTOMER, ORGANIZER, ADMIN)
    AS->>KC: POST /admin/realms/{realm}/users/{userId}/role-mappings/realm<br/>[{id, name: newRole}]
    KC-->>AS: 204
    AS-->>FE: 204 No Content
```

**Key Points:**
- Chỉ map **business roles** (CUSTOMER/ORGANIZER/ADMIN)
- **Không xóa role kỹ thuật** của Keycloak (default-roles, offline_access, uma_authorization...)
- Xóa business roles cũ → thêm role mới (replace)

---

## 13. Admin: Reset Password User Khác — `POST /admin/users/{userId}/reset-password`

```mermaid
sequenceDiagram
    autonumber
    actor FE as Admin UI
    participant AS as Auth Service
    participant KC as Keycloak

    FE->>AS: POST /admin/users/{userId}/reset-password {action: SEND_EMAIL}<br/>Authorization: Bearer <admin_access_token>
    AS->>AS: Verify ADMIN
    alt SEND_EMAIL
        AS->>KC: PUT /admin/realms/{realm}/users/{userId}/execute-actions-email<br/>["UPDATE_PASSWORD"]
        KC-->>AS: 204 (gửi email reset)
    else SET_TEMPORARY_PASSWORD
        AS->>KC: PUT /admin/realms/{realm}/users/{userId}/reset-password<br/>{type:password, value:tempPass, temporary:true}
        KC-->>AS: 204
        AS-->>FE: 200 OK {temporaryPassword: "..."}
    end
```

---

## 14. Admin/User: Quản Lý Session

```mermaid
sequenceDiagram
    autonumber
    actor FE as Admin/User
    participant AS as Auth Service
    participant KC as Keycloak

    Note over FE, KC: GET sessions
    FE->>AS: GET /admin/users/{userId}/sessions (admin) hoặc GET /auth/me/sessions (user)
    AS->>KC: GET /admin/realms/{realm}/users/{userId}/sessions
    KC-->>AS: [SessionRepresentation]
    AS->>AS: Map → UserSessionsResponse
    AS-->>FE: 200 OK + Sessions[]

    Note over FE, KC: DELETE session (revoke)
    FE->>AS: DELETE /admin/users/{userId}/sessions (admin all) hoặc DELETE /auth/me/sessions/{sessionId} (user one)
    AS->>KC: DELETE /admin/realms/{realm}/users/{userId}/sessions (all) hoặc DELETE /admin/realms/{realm}/sessions/{sessionId} (one)
    KC-->>AS: 204
    AS-->>FE: 204
```

---

## Tổng Hợp: Mapping Endpoint ↔ Keycloak API

| Auth Endpoint | Keycloak Admin API / OIDC Endpoint | Ghi chú |
|---------------|-------------------------------------|---------|
| `POST /auth/register` | `POST /admin/users` + `GET /admin/users/{id}` | Tạo user + gán role CUSTOMER |
| `POST /auth/login` | `POST /protocol/openid-connect/token` (password/code) | Proxy grant flow |
| `POST /auth/refresh` | `POST /protocol/openid-connect/token` (refresh_token) | Delegate rotation cho KC |
| `POST /auth/logout` | `POST /protocol/openid-connect/logout` HOẶC `POST /admin/users/{id}/logout` | Revoke session |
| `GET /auth/me` | `GET /protocol/openid-connect/userinfo` HOẶC `GET /admin/users/{id}` | Lấy profile từ KC |
| `POST /auth/change-password` | `PUT /account/password` HOẶC `PUT /admin/users/{id}/reset-password` | User self-service |
| `POST /auth/forgot-password` | `PUT /admin/users/{id}/execute-actions-email` ["UPDATE_PASSWORD"] | Gửi email reset |
| `POST /auth/reset-password` | `PUT /admin/users/{id}/reset-password` | Đặt pass mới bằng token |
| `POST /auth/verify-email` | `PUT /admin/users/{id}/execute-actions-email` ["VERIFY_EMAIL"] | Xác thực email |
| `POST /auth/resend-verification` | `PUT /admin/users/{id}/execute-actions-email` ["VERIFY_EMAIL"] | Gửi lại email verify |
| `GET /auth/me/sessions` | `GET /admin/users/{id}/sessions` | Session của mình |
| `DELETE /auth/me/sessions/{id}` | `DELETE /admin/sessions/{id}` | Revoke 1 session |
| `GET /admin/users` | `GET /admin/users` (paginated) | List + filter |
| `GET /admin/users/{id}` | `GET /admin/users/{id}` | Chi tiết user |
| `PATCH /admin/users/{id}/lock` | `PUT /admin/users/{id}` {enabled:false} + `POST /admin/users/{id}/logout` | Disable + revoke |
| `PATCH /admin/users/{id}/unlock` | `PUT /admin/users/{id}` {enabled:true} | Enable |
| `PATCH /admin/users/{id}/role` | `DELETE/POST /admin/users/{id}/role-mappings/realm` | Replace business role |
| `POST /admin/users/{id}/reset-password` | `PUT /admin/users/{id}/execute-actions-email` HOẶC `PUT /admin/users/{id}/reset-password` | Admin reset |
| `GET /admin/users/{id}/sessions` | `GET /admin/users/{id}/sessions` | Xem session user |
| `DELETE /admin/users/{id}/sessions` | `DELETE /admin/users/{id}/sessions` | Revoke all sessions |

---

## Cấu Hình Cần Thiết Lập Trên Keycloak (Checklist)

| Cấu hình | Mục đích | Trạng thái |
|----------|----------|------------|
| Realm `eventhub` | Namespace identity | 🔲 Chưa tạo |
| Client `eventhub-auth` (confidential) | Auth Service gọi Admin API | 🔲 Chưa tạo |
| Client `eventhub-frontend` (public) | FE login (Auth Code + PKCE) | 🔲 Chưa tạo |
| Service Account cho `eventhub-auth` | Quyền `manage-users`, `view-users`, `view-realm` | 🔲 Chưa cấp |
| Realm Roles: `CUSTOMER`, `ORGANIZER`, `ADMIN` | Business roles | 🔲 Chưa tạo |
| Protocol Mapper: `business_role` → `realm_access.roles` | Map role vào JWT claim | 🔲 Chưa tạo |
| Protocol Mapper: `sub` → UUID | Đảm bảo sub là UUID | 🔲 Kiểm tra |
| Email template: `verifyEmail`, `resetPassword` | Tùy chỉnh email FE | 🔲 Chưa cấu hình |
| Direct Access Grants: **TẮT** (nếu dùng Auth Code) | Bảo mật | 🔲 Cần tắt |
| Token settings: Access token lifespan, Refresh token reuse | TTL, rotation policy | 🔲 Chưa cấu hình |
| Brute force detection | Bảo vệ login | 🔲 Chưa bật |

---

## Lưu Ý Triển khai Auth Service

1. **Không tự tạo JWT** — chỉ forward token từ Keycloak
2. **Không lưu password/refresh token** — delegate hoàn toàn cho Keycloak
3. **Spring Security Resource Server** verify JWT bằng JWK từ `issuer-uri` (`/.well-known/openid-configuration`)
4. **IdentityMapper** convert JWT → `Actor(UUID userId, BusinessRole role)` cho các service khác
5. **Error mapping**: Keycloak error → EventHub `ErrorResponse` (code, message, timestamp, path)
6. **Timeout**: HTTP client config timeout rõ ràng cho mỗi gọi Keycloak (connect 2s, read 5s)
7. **Retry**: Chỉ retry idempotent (GET), không retry POST/PUT/DELETE
8. **Audit log**: Ghi log admin action (who, what, when) trước khi gọi Keycloak
