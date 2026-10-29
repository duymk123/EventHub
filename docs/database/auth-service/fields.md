# Giải thích các trường dữ liệu Auth Service

Database cấu hình mặc định: `eventhub_auth`. Theo quyết định nghiệp vụ, Keycloak quản lý đăng nhập, mật khẩu, token, session và danh tính; Auth là API trung gian phục vụ giao diện EventHub. EventHub không truy cập trực tiếp bảng nội bộ của Keycloak.

Nguồn đối chiếu: [tài liệu ranh giới Auth](README.md), [quyết định nguồn](../../architecture/source-decisions.md), [User.java](../../../auth-service/src/main/java/com/eventhub/auth/entity/User.java), [BaseEntity.java](../../../auth-service/src/main/java/com/eventhub/auth/entity/BaseEntity.java), [plan Auth](../../plans/auth-service.md). Xem [cách đọc kiểu dữ liệu và NULL](../README.md#cach-doc-tu-dien-truong).

Điểm khác tài liệu cũ: repo hiện đã có entity User ánh xạ bảng users và kế thừa BaseEntity. Chưa có init.sql/migration Auth xác định schema local được duyệt. Vì vậy bảng dưới giải thích **mapping hiện có trong code**, không coi đây là DDL chuẩn đã chốt hoặc cấu trúc database đang chạy. Không bổ sung schema Keycloak giả định.

## 1. `users` — Dữ liệu tài khoản local được khai báo trong entity

| Trường | Kiểu / khai báo trong entity | Ý nghĩa dễ hiểu và giới hạn hiện tại | Ví dụ minh họa |
|---|---|---|---|
| `id` | UUID Java, PK, length=36, nullable=false | ID dòng user local. Comment muốn đồng bộ với sub Keycloak nhưng @UuidGenerator tự sinh UUID, không tự gọi Keycloak hoặc bảo đảm bằng sub. Cơ chế đồng bộ danh tính cần hoàn thiện. | UUID tài khoản U |
| `email` | String, nullable=false, unique=true | Email tài khoản trong bản local. Không tự chứng minh địa chỉ đã được xác minh; việc lấy email chuẩn từ claim/adapter còn cần cấu hình. | `lan@example.com` |
| `full_name` | String, length=100, nullable=false | Tên đầy đủ để hiển thị hồ sơ. Không phải username/token hoặc vai trò phân quyền. | `Nguyễn Lan` |
| `role` | String, length=50; Java mặc định CUSTOMER | Vai trò nghiệp vụ được dự định đồng bộ: CUSTOMER khách mua, ORGANIZER tổ chức, ADMIN quản trị. Bản local không tự trở thành nguồn cấp quyền thay Keycloak. | `CUSTOMER` |
| `status` | String, length=50; Java mặc định ACTIVE | Trạng thái tài khoản dự định đồng bộ, ví dụ ACTIVE/LOCKED trong hợp đồng Auth. Khóa dòng local không tự thu hồi mọi token Keycloak. | `ACTIVE` |
| `create_at` | LocalDateTime từ BaseEntity, @CreatedDate | Thời điểm tạo bản ghi local; tên annotation là create_at, không phải created_at. | `2026-10-29T02:00:00` |
| `update_at` | LocalDateTime từ BaseEntity, @LastModifiedDate | Thời điểm cập nhật bản ghi local gần nhất. Không phải thời điểm token hết hạn. | `2026-10-29T03:00:00` |
| `created_by` | String từ BaseEntity, @CreatedBy | Danh tính tạo dòng user nếu auditing cung cấp. Không phải mật khẩu hoặc chữ ký token. | UUID người tạo dạng chuỗi, hoặc NULL nếu chưa có auditor |
| `updated_by` | String từ BaseEntity, @LastModifiedBy | Danh tính thực hiện thay đổi gần nhất nếu auditing cung cấp. | UUID người quản trị sửa hồ sơ |
| `deleted` | boolean từ BaseEntity; Java mặc định false | Cờ xóa mềm nếu nghiệp vụ triển khai. Đánh dấu true không tự xóa tài khoản Keycloak hoặc tự chặn login. | `false` |

Các trường role/status và các trường kế thừa trong BaseEntity không khai báo nullable=false trong @Column. Điều đó khác id/email/full_name đã khai báo rõ; cần đối chiếu schema thực tế trước khi kết luận toàn bộ ràng buộc SQL. Default CUSTOMER/ACTIVE/false ở đây là khởi tạo Java, chưa phải SQL DEFAULT.

User hiện chưa ép kiểu UUID bằng @JdbcTypeCode(CHAR) và application.yml Auth chưa có cấu hình preferred_uuid_jdbc_type=CHAR. Annotation length=36 không đủ để khẳng định kiểu vật lý đã là CHAR(36). Quy ước ID ứng dụng vẫn là UUID CHAR(36); tài liệu không tự sửa mapping hoặc đổi ID local thành sub.

BaseEntity là mapped superclass, không phải bảng base_entity. Auditing cần được cấu hình; LocalDateTime không chứa offset/múi giờ. Có entity User chưa chứng minh đã có luồng đồng bộ Keycloak, tạo/khóa tài khoản hoặc migration bảo toàn ID.

## 2. Phân biệt dữ liệu Auth, Keycloak và các service khác

| Thông tin | Đọc như thế nào |
|---|---|
| userId | ID tài khoản EventHub sử dụng trong customer_id, organizer_id, recipient_user_id…; mapping với sub cần được xác định nhất quán. |
| sub | Claim định danh subject trong token Keycloak; không phải tên một cột đã được khai báo trong users hiện tại. |
| role | Quyền nghiệp vụ cần lấy từ danh tính đã xác minh và role mapper; không tin role do request body gửi lên. |
| password/refresh token/session | Do Keycloak quản lý; entity users local không có các cột password_hash hoặc refresh_token. |
| holder_name/holder_email | Thông tin người dùng vé trong Order; người này có thể khác tài khoản mua vé và không bắt buộc có user local riêng. |
| owner_user_id | ID tài khoản sở hữu vé ở Ticket; không phải tên hiển thị full_name. Quy tắc ai là owner còn C02. |

Ví dụ U đăng nhập và tạo đơn O: Order lưu customer_id=U, Event có thể lưu organizer_id của một tài khoản khác A, còn order_holders lưu tên Lan là người đi sự kiện. Các service giữ các ID mềm, không tạo FK hoặc JOIN sang users/Keycloak của service khác.

## 3. Các giới hạn cần nhớ khi đọc tài liệu cũ

[README Auth](README.md) và [ERD ranh giới](erd.md) mô tả kiến trúc Keycloak, có đoạn cũ nói chưa có entity local. File này bổ sung hiện trạng User/BaseEntity đã có trong code; chưa có quyết định mới biến bản local thành hệ thống mật khẩu/token riêng. Chưa có cơ sở để liệt kê những bảng nội bộ Keycloak như bảng ứng dụng EventHub.

Nguồn email, fallback email người nhận và quyền sở hữu vé còn [C02](../../project-clarifications.md). Realm/client, mapping sub/role và cơ chế đồng bộ profile cần hoàn thiện theo [plan Auth](../../plans/auth-service.md). Xem [API Auth](../../api/auth-service-openapi.yaml) để hiểu dữ liệu giao tiếp, không dùng tên field API để suy rằng đã có cột tương ứng trong local DB.
