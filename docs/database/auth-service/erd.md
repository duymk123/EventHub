# Ranh giới dữ liệu Auth

```mermaid
erDiagram
    KEYCLOAK ||--o{ USER_PROFILES : "optional projection"
    AUTH_SERVICE ||--|| KEYCLOAK : "facade API / OIDC / Admin REST"
    ADMIN_UI ||--|| AUTH_SERVICE : "quản trị user qua facade API"
    
    KEYCLOAK {
        uuid sub PK "subject (userId)"
        string email UK
        string username
        boolean enabled "ACTIVE/LOCKED"
        json realm_roles "CUSTOMER/ORGANIZER/ADMIN"
        datetime created_at
        datetime updated_at
    }
    
    USER_PROFILES {
        uuid id PK "internal UUID"
        string keycloak_sub UK "Keycloak sub"
        string full_name
        string phone_number UK
        string avatar_url
        datetime create_at
        datetime update_at
        string created_by
        string updated_by
        boolean deleted
    }
    
    AUTH_SERVICE {
        string facade_endpoints "POST /auth/register, /auth/login, /auth/refresh, /auth/logout, /auth/me, /auth/change-password"
        string admin_endpoints "GET/PATCH /api/v1/admin/users/*, POST /admin/users/{id}/reset-password"
        string profile_endpoints "GET/PUT/PATCH /api/v1/me/profile (optional)"
    }
    
    ADMIN_UI {
        string features "Danh sách user, chi tiết, khóa/mở khóa, gán role, reset password, quản lý session"
        string note "Không embed Keycloak Admin Console. Tự triển khai Next.js. Gọi Auth Service facade API."
    }
```

> **Lưu ý**: Bảng `user_profiles` là optional projection, đã implement sẵn. Keycloak schema nội bộ không vẽ chi tiết (do Keycloak tự quản lý). Admin UI là frontend riêng, không dùng Keycloak WebUI.