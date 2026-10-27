# Plan code auth-service

Ngày 2026-10-28. Đọc [quy ước chung](00-cross-service-contracts.md), [OpenAPI Auth](../api/auth-service-openapi.yaml), [ranh giới dữ liệu](../database/auth-service/erd.md). Đã chốt **UI/API riêng, Auth gọi Keycloak**; không tự cấp JWT, không xây kho mật khẩu/refresh token thứ hai.

## 1. Phạm vi và cấu trúc

`controller/AuthController, AdminUserController`; `service/AuthService, UserAdministrationService`; `service/impl/*Impl`; `client/KeycloakTokenClient, KeycloakUserClient`; `client/impl/*`; `security/IdentityMapper, BusinessPrincipal`; DTO request/response, config và exception.

Hiện chỉ Application/context test. Chưa có realm/client/grant/JWK/issuer/audience/admin permission/role mapper cụ thể. Các client interface bên dưới là adapter đề xuất, không tuyên bố endpoint provider đã được cấu hình. Không tự bật Direct Access Grants chỉ để form login hiện tại hoạt động.

## 2. Service interface và các hàm

| Interface / signature đề xuất | Hành vi |
|---|---|
| `AuthService.register(RegisterRequest request): UserResponse` | Validate dữ liệu → Keycloak tạo user → gán CUSTOMER theo quyền adapter → trả profile. Không tin role từ request. Không DB transaction local bọc chuỗi HTTP. |
| `AuthService.login(LoginRequest request): TokenResponse` | Xác thực tại Keycloak bằng grant/code flow được cấu hình; chỉ chuẩn hóa token response, không tự ký JWT. Request email/password hiện là draft; cập nhật nếu chọn exchange code. |
| `AuthService.refresh(RefreshRequest request): TokenResponse` | Gọi token client, trả expiresIn/token thực tế. Rotation/reuse do Keycloak cấu hình, không đọc bảng refresh local. |
| `AuthService.logout(RefreshRequest request): void` | Kết thúc session theo cơ chế đã chọn; Keycloak kiểm tra refresh token/client context, không chỉ xóa dữ liệu frontend. API hiện không đòi access token còn hạn để logout; trả 204 theo hợp đồng. |
| `AuthService.getMe(Actor actor): UserResponse` | Từ identity đã xác minh, lấy profile cần thiết qua Keycloak/adapter; không nhận userId khác trong query. |
| `AuthService.changePassword(Actor actor, ChangePasswordRequest request): void` | Kiểm tra/current re-authentication bằng Keycloak flow được cấp quyền, cập nhật credential tại provider; không so password hash local. Thu hồi session theo chính sách được cấu hình. |
| `UserAdministrationService.lockUser(Actor admin, UUID userId): void` | ADMIN → disable user và thu hồi session tại Keycloak. Gọi lặp idempotent; không hứa mọi JWT offline mất hiệu lực ngay. |
| `UserAdministrationService.unlockUser(Actor admin, UUID userId): void` | ADMIN → enable user, giữ role/profile hiện có. 404 nếu không tồn tại. |
| `UserAdministrationService.assignRole(Actor admin, UUID userId, AssignRoleRequest request): void` | Chuyển đúng một business role CUSTOMER/ORGANIZER/ADMIN; không xóa role kỹ thuật của Keycloak. Cần xử lý trạng thái trung gian nếu provider cần nhiều request. |

ServiceImpl là orchestrator; không tạo `@Transactional` local giả để ngụ ý các request Keycloak rollback cùng nhau. Nếu register tạo user xong nhưng gán CUSTOMER lỗi: trả lỗi có thể phục hồi, xác định user đã tạo và retry gán role; không tạo tài khoản/email thứ hai. Việc disable tạm hoặc bù trừ user phải theo cấu hình/chiến lược đã thống nhất, không xóa tài khoản đã sử dụng.

## 3. Adapter Keycloak

| Interface / method | Kết quả và lưu ý |
|---|---|
| `KeycloakTokenClient.authenticate(LoginCommand command): ProviderTokenSet` | Command cụ thể phụ thuộc flow; timeout rõ, credential/token không log. |
| `refresh(String refreshToken): ProviderTokenSet` | Gọi provider; dùng mã lỗi provider để phân biệt invalid/expired/unavailable. |
| `endSession(SessionCommand command): void` | Adapter chọn logout/revoke phù hợp, không hardcode kiểu logout cũ. |
| `KeycloakUserClient.createUser(NewUserCommand command): ProviderUser` | Lấy ID user từ phản hồi provider và parse UUID khi mapping dùng UUID; email conflict map 409. |
| `getUser(UUID userId): ProviderUser` | 404 khác 503; identity mapping thống nhất mọi service. |
| `setEnabled(UUID userId, boolean enabled): void` | Dùng bởi lock/unlock, idempotent. |
| `revokeSessions(UUID userId): void` | Session revocation theo quyền admin/service-account được cấp. |
| `replaceBusinessRole(UUID userId, BusinessRole role): void` | Tách business roles khỏi roles hệ thống; retry phải hội tụ về một business role, fail closed nếu mapping mơ hồ. |
| `changeCredential(UUID userId, CredentialCommand command): void` | Không lưu credential vào DB/log/error; kiểm tra re-auth tại tầng đúng flow. |
| `IdentityMapper.toActor(VerifiedIdentity identity): Actor` | Validate subject UUID và business role; không mặc định CUSTOMER nếu token thiếu role. |

`ProviderTokenSet`/`ProviderUser` chỉ là DTO adapter nội bộ; API trả `TokenResponse/UserResponse` đúng OpenAPI, không lộ representation admin hoặc credential của Keycloak.

## 4. Repository và database

**Giai đoạn hiện tại không cần repository JPA identity.** Không tạo `UserRepository`, `RoleRepository`, `RefreshTokenRepository` chỉ để đủ pattern. Keycloak là owner dữ liệu này, EventHub không truy cập SQL Keycloak.

Nếu sau này có profile nghiệp vụ ngoài Keycloak được yêu cầu, mới thêm migration + entity `UserProfile(id UUID, keycloakSubject UUID UNIQUE, ...)` hoặc dùng id=sub sau chốt mapping. Khi đó `UserProfileRepository.findByKeycloakSubject(UUID): Optional<UserProfile>`, `save(UserProfile)` chỉ lưu profile, không password/token hoặc quyền authoritative. Đây là mở rộng có điều kiện, chưa phải bảng cần code bây giờ. Datasource hiện có trong scaffold không phải bằng chứng cần bảng users local.

## 5. API → service

Prefix public `/api/v1`.

| Method / path | Service |
|---|---|
| POST `/auth/register` | `register` |
| POST `/auth/login` | `login` |
| POST `/auth/refresh` | `refresh` |
| POST `/auth/logout` | `logout` |
| GET `/auth/me` | `getMe` |
| POST `/auth/change-password` | `changePassword` |
| PATCH `/admin/users/{userId}/lock` | `lockUser` |
| PATCH `/admin/users/{userId}/unlock` | `unlockUser` |
| PATCH `/admin/users/{userId}/role` | `assignRole` |

DTO request: RegisterRequest/LoginRequest/RefreshRequest/ChangePasswordRequest/AssignRoleRequest; response TokenResponse/UserResponse. Validate UUID path đúng 36 ký tự ở hợp đồng và parse tại adapter; status ACTIVE/LOCKED là mapping provider.enabled, không bảng local. Không hardcode TTL access/refresh.

## 6. Lỗi, kiểm tra và thứ tự code

ErrorCode dự kiến: EMAIL_ALREADY_EXISTS 409, INVALID_CREDENTIALS 401, ACCOUNT_LOCKED 403, INVALID_REFRESH_TOKEN 401, USER_NOT_FOUND 404, FORBIDDEN 403, IDENTITY_PROVIDER_UNAVAILABLE 503. Các code expired/reuse chỉ trả khi provider thực sự cho biết, không suy từ mọi 400 của Keycloak. OpenAPI hiện còn mô tả cũ rằng reuse luôn thu hồi cả token family; trước code cần đồng bộ mô tả đó với chính sách Keycloak thực tế, không tự triển khai bảng family local để làm đúng một câu mô tả cũ. Wire error OIDC ở adapter chuyển thành ErrorResponse EventHub cho endpoint facade.

1. Chốt adapter commands/config mapping cùng người phụ trách Keycloak, xây DTO và interface; cấu hình fail fast khi thiếu issuer/client, không dùng khóa giả.
2. Client/timeout/error mapping bằng fake HTTP provider; sau đó facade và controllers/security.
3. Kiểm tra register không tự chọn ADMIN, login không tự ký token, refresh/logout đúng provider, me đúng subject, khóa/role chỉ ADMIN và lỗi role partial/retry.
4. Test tích hợp Keycloak trong môi trường test riêng khi có realm/client: subject UUID, role mapper, phiên hết hạn, disable/session revoke, email verification. Không dùng context test hiện có để kết luận integration đạt.

Đã có thể code interfaces/DTO/client error mapping với fixture. Phần grant/key/realm/S2S và nguồn email C02 vẫn cần cấu hình đã hẹn; không đặt một quyết định mới thay chủ dự án trong plan.
