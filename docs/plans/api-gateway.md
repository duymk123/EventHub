# Kế hoạch triển khai API Gateway

Cập nhật 2026-10-31. Gateway là thành phần hạ tầng thứ bảy, bên cạnh sáu service nghiệp vụ. Đây là **kế hoạch đề xuất, chưa triển khai**; tài liệu nhập môn và sơ đồ luồng ở [API Gateway từ đầu](../architecture/api-gateway.md). Tuân thủ [hợp đồng chung](00-cross-service-contracts.md), [quyết định nguồn](../architecture/source-decisions.md) và [các điểm còn mở](../project-clarifications.md).

## 1. Hiện trạng và kết quả cần đạt

| Hiện có | Cần thay đổi khi thực hiện plan |
|---|---|
| `api-gateway/pom.xml`: MVC, JPA, MySQL | Chuyển sang Gateway Server WebFlux, Security Resource Server, Actuator; bỏ MVC/JPA/MySQL khỏi Gateway. |
| `application.yml`: datasource/JPA, cổng mặc định 8080 | Bỏ datasource/JPA, thêm downstream URI, JWT, CORS, timeout và giới hạn request theo môi trường. |
| Application trong `com.eventhub.gateway` | Giữ package này; thêm config/filter/security/exception, không thêm entity/repository. |
| Compose có `api-gateway-db`, DB env và phụ thuộc DB | Bỏ phụ thuộc/env DB và service DB của Gateway khi triển khai, cập nhật tài liệu hạ tầng. Giữ volume dữ liệu cũ, không chạy xóa volume. |
| Chưa có route/security/filter | Route đủ 47 REST operations public trong OpenAPI; 5 operations internal không đưa lên Gateway. WS là route riêng. |

Không thay phiên bản stack chỉ vì viết plan. Boot `3.5.16` hiện tại tương ứng Cloud `2025.0.x` theo [bảng Spring](https://spring.io/projects/spring-cloud/); chọn và khóa service release BOM cụ thể lúc triển khai, kiểm tra hỗ trợ trước khi dùng môi trường thật. Import `spring-cloud-dependencies` qua dependencyManagement, ưu tiên scope module Gateway để tránh tác động sáu service khác. Thêm `spring-cloud-starter-gateway-server-webflux`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`; phiên bản để BOM/Boot quản lý. Redis reactive chỉ thêm khi làm rate limit phân tán.

## 2. Hợp đồng routing

Giữ nguyên `/api/v1`, body/query/status và schema phân trang của owner. Không tự gom response thành DTO chung. Dùng route theo method/path cụ thể trong bảng; các đường dẫn dưới **đã gồm prefix**. Không dùng catch-all `/api/v1/**` hoặc route tự động từ discovery để mở endpoint ngoài hợp đồng.

| Route ID đề xuất | Method + path | Owner | Chính sách đầu vào |
|---|---|---|---|
| auth-public | POST `/api/v1/auth/register`, `/login`, `/refresh`, `/logout` dưới cùng `/api/v1/auth` | Auth | Không yêu cầu access JWT; Auth vẫn kiểm tra dữ liệu/refresh token/session. |
| auth-me | GET `/api/v1/auth/me` | Auth | Business actor hợp lệ. |
| auth-password | POST `/api/v1/auth/change-password` | Auth | Business actor hợp lệ. |
| auth-admin | PATCH `/api/v1/admin/users/{userId}/lock`, `/unlock`, `/role` dưới cùng userId | Auth | ADMIN. |
| event-public | GET `/api/v1/events`, `/api/v1/events/{id}` | Event | Public; Event chỉ trả PUBLISHED. |
| event-create | POST `/api/v1/events` | Event | ORGANIZER. |
| event-update | PUT `/api/v1/events/{id}` | Event | ORGANIZER + owner tại Event. |
| event-transition | POST `/api/v1/events/{id}/submit`, `/publish`, `/close` dưới cùng id | Event | ORGANIZER + owner/trạng thái tại Event. |
| event-organizer | GET `/api/v1/organizer/events` | Event | ORGANIZER. |
| event-review | PATCH `/api/v1/admin/events/{id}/review` | Event | ADMIN. |
| category-public | GET `/api/v1/categories` | Event | Public. |
| category-write | POST `/api/v1/categories`; PUT, DELETE `/api/v1/categories/{id}` | Event | ADMIN. |
| ticket-types-public | GET `/api/v1/events/{eventId}/ticket-types` | Ticket | Public theo contract; Ticket kiểm tra event được phép xem. |
| ticket-types-write | POST `/api/v1/ticket-types`; PUT `/api/v1/ticket-types/{id}` | Ticket | ORGANIZER; owner tại Ticket qua Event. |
| tickets-personal | GET `/api/v1/tickets/me`, `/api/v1/tickets/{id}/qr` | Ticket | CUSTOMER; quyền owner theo C02, không chốt thay C02. |
| gate-scan | POST `/api/v1/gate/check-in`, `/api/v1/gate/check-out` | Ticket | GateActor riêng theo C07; chưa chốt thì đóng route. |
| orders-create | POST `/api/v1/orders` | Order | CUSTOMER. |
| orders-read | GET `/api/v1/orders/me`, `/api/v1/orders/{id}` | Order | CUSTOMER + owner tại Order. |
| order-payment | POST `/api/v1/orders/{id}/payment` | Order | CUSTOMER + owner/trạng thái tại Order. |
| payment-webhook | POST `/api/v1/payments/webhook/{provider}` (draft C06) | Order | Không access JWT; provider được cấu hình, Order xác minh chữ ký. |
| refunds-request | POST `/api/v1/orders/{id}/refunds` | Order | CUSTOMER + owner/điều kiện tại Order. |
| refunds-review | GET `/api/v1/admin/refunds`; POST `/api/v1/admin/refunds/{id}/approve`, `/reject` dưới cùng id | Order | C04 chọn chỉ ADMIN giai đoạn đầu; Gateway và Order đều kiểm tra, không cấp quyền reviewer cho ORGANIZER. |
| notifications-read | GET `/api/v1/notifications/me`, `/api/v1/notifications/me/unread-count` | Notification | Business actor hợp lệ, service lọc user hiện tại. |
| notifications-mark | PUT `/api/v1/notifications/{id}/read` | Notification | Business actor hợp lệ + owner. |
| notification-templates | GET `/api/v1/admin/notification-templates`, `/api/v1/admin/notification-templates/{code}`; PUT `/api/v1/admin/notification-templates/{code}` | Notification | ADMIN. |
| analytics-rest | GET `/api/v1/analytics/events/{id}/sales`, `/attendance`, `/dashboard` dưới cùng id | Analytics | ORGANIZER/ADMIN, owner/tồn tại tại Analytics qua Event. |
| analytics-ws | Upgrade `/ws/dashboard` | Analytics | Origin/giới hạn kết nối ở handshake; JWT CONNECT và quyền SUBSCRIBE tại Analytics. |

Tên rút gọn `/login`, `/publish` trong một ô luôn nối với prefix được ghi trong ô; khi code phải khai báo đầy đủ từng path. Route có nhiều method/path phải tách khi các tổ hợp không cùng hợp lệ, tránh mở PUT collection hoặc POST item ngoài bảng. 47 public operations gồm Auth 9, Event 13, Ticket 7, Order 9, Notification 6, Analytics 3. Năm internal operations gồm Event 1 và Ticket 4; các API internal đề xuất mới cũng bị chặn.

Đặt `ticket-types-public` trước route events rộng nếu sau này dùng wildcard; ưu tiên exact patterns để giảm chồng lấn. Không route `/api/v1/admin/**` về một owner. Endpoint chưa implement ở downstream vẫn có thể trả 404; có route không chứng minh tính năng đã hoàn tất.

## 3. Địa chỉ downstream và mạng

| Biến đề xuất | Gateway chạy trên host theo cổng local | Gateway trong Compose |
|---|---|---|
| `AUTH_SERVICE_URI` | `http://localhost:8081` | `http://auth-service:8080` |
| `EVENT_SERVICE_URI` | `http://localhost:8082` | `http://event-service:8080` |
| `TICKET_SERVICE_URI` | `http://localhost:8083` | `http://ticket-service:8080` |
| `ORDER_SERVICE_URI` | `http://localhost:8084` | `http://order-service:8080` |
| `NOTIFICATION_SERVICE_URI` | `http://localhost:8085` | `http://notification-service:8080` |
| `ANALYTICS_SERVICE_URI` | `http://localhost:8086` | `http://analytics-service:8080` |
| `ANALYTICS_WS_URI` | `ws://localhost:8086` | `ws://analytics-service:8080` |

Đây là biến dự kiến phải nối vào config, hiện chưa tồn tại. `SERVER_PORT=8080` cho Gateway. Không dùng Eureka/`lb://` khi chưa có discovery/load balancer. Địa chỉ đích đến từ config tin cậy, không nhận URL downstream từ client.

Compose hiện publish cổng service/DB để dev; topology triển khai phải chỉ expose Gateway và các entry point identity cần thiết. Service-to-service dùng DNS nội bộ và S2S, không vòng về public Gateway. Chặn network và security service cùng nhau bảo vệ `/internal/**`. Khi bỏ DB Gateway không xóa volume đang có. Cập nhật `Docker.md`, `docs/local-development.md`, README cùng thay đổi hạ tầng thực tế, không ghi rằng đã bỏ DB ở lượt viết tài liệu này.

## 4. Pipeline security và filter

Các bước dưới là thứ tự logic; thứ tự thực thi cần kiểm tra ở WebFlux, không nhét mọi xử lý vào GlobalFilter. Spring Security WebFilter chạy trước handler routing; GlobalFilter chỉ áp khi đã match route theo [tài liệu Spring](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/global-filters.html).

1. WebFilter đầu vào giới hạn header/request theo config, tạo correlation ID, loại header identity tự khai. ID do Gateway sinh để không đưa giá trị log tùy ý của client vào hệ thống. Trả ID trên response kể cả lỗi security/không match route.
2. Chặn `/internal/**`, `/api/v1/internal/**` và quản trị không public. Chặn biến thể đường dẫn mã hóa/traversal bằng policy chuẩn hóa/reject nhất quán với router; không chỉ dùng kiểm tra chuỗi prefix rồi cho rewrite tùy ý.
3. CORS xử lý preflight hợp lệ trước yêu cầu JWT; chỉ origin/method/header được cấu hình, không mở mọi OPTIONS như một business endpoint.
4. Security chọn ngoại lệ public theo **method + path**; REST còn lại kiểm tra JWT và business role. GateActor dùng policy riêng sau C07. Handshake WS là ngoại lệ hẹp, Analytics xử lý CONNECT.
5. Route theo allowlist ở mục 2; không match thì không proxy. Forward Authorization gốc và correlation ID; không biến token người dùng thành token service. Service tự xác minh token/audience phù hợp cho API của mình.
6. Áp timeout/limit theo route, log metadata, chuyển response giữ contract. Chỉ lỗi do Gateway sinh mới được Gateway chuẩn hóa.

JWT cần kiểm tra chữ ký/thuật toán, issuer, audience được cấu hình, exp/nbf và subject UUID/role mapping đã chốt. Không mặc định claim `roles` phẳng; chỉ ánh xạ CUSTOMER/ORGANIZER/ADMIN, role kỹ thuật không tự thành business role. Mapping thiếu/sai phải fail closed. Không giả ADMIN có quyền trên mọi endpoint khi contract không cho phép.

Chuỗi REST dùng `SecurityWebFilterChain` stateless và Resource Server. Nếu API chỉ nhận Bearer trong header, có thể tắt CSRF cho chuỗi đó sau khi xác nhận không dùng cookie tự gửi để xác thực. Nếu flow Auth chuyển sang cookie/session phải thiết kế lại CSRF; không sao chép cấu hình tắt toàn cục sang flow mới. Giữ Bearer gốc khi proxy; không cần OAuth2 client/TokenRelay chỉ để chuyển header đã có. TokenRelay dành cho nhu cầu OAuth2 client khác nếu sau này bổ sung.

Không ghi Authorization, refresh token, password, QR, chữ ký hoặc payload nhạy cảm trong access log. Loại `X-User-Id`, `X-Role` và các header identity tự khai theo danh sách cấu hình; loại header proxy do client gửi ở rìa, chỉ tin `Forwarded`/`X-Forwarded-*` từ proxy đã khai báo. Khi đứng sau TLS proxy, kiểm tra external scheme/host để không tạo redirect URL sai.

## 5. CORS, timeout, giới hạn và lỗi

Origin là cấu hình frontend cụ thể theo môi trường; không đoán cổng frontend trong repo. Cho phép các method thực tế, header `Authorization`, `Content-Type` và header idempotency khi contract đã chốt; expose correlation ID. Dùng một lớp làm chủ CORS tại rìa để tránh header trùng từ downstream. CORS phải có trên response lỗi từ Gateway. Kiểm tra preflight không match Method predicate theo [hướng dẫn Spring](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/cors-configuration.html).

Giá trị khởi đầu **đề xuất để đo và điều chỉnh**, không phải SLA đã duyệt: connect timeout 2 giây, response timeout REST 10 giây; payment/webhook chọn riêng sau C06. WS có handshake timeout, idle/heartbeat riêng, không áp response timeout REST 10 giây cho cả session. Giới hạn body/header phải hỗ trợ body chunked và tránh buffer toàn bộ webhook; nếu vượt giới hạn trả 413 thay vì sửa body. Giá trị cụ thể chọn sau khi có payload provider và nhu cầu tải lên.

Không bật retry tự động ở Gateway trong baseline, đặc biệt POST/PATCH/PUT/DELETE, webhook, gate scan. Nếu thêm retry cho GET phải giới hạn số lần/budget và kiểm tra method thực sự không có tác dụng phụ. Không fallback trả thành công, đơn giả, số liệu 0 hoặc vé giả khi downstream lỗi. Rate limit là giai đoạn sau: trả 429 + Retry-After khi phù hợp; user key lấy identity đã xác minh, anonymous dùng IP từ proxy đáng tin, nhiều instance cần bộ đếm chung. Webhook có chính sách riêng để không làm mất callback hợp lệ.

| Trường hợp | Status / xử lý mục tiêu |
|---|---|
| Thiếu/sai/hết hạn access token | 401 `UNAUTHORIZED`, giữ header Bearer challenge phù hợp. |
| Token hợp lệ nhưng thiếu quyền | 403 `FORBIDDEN`. |
| Internal/đường quản trị bị deny | 403 tại deny policy, tuyệt đối không proxy; WebFilter có thể reject request malformed bằng 400 trước đó. |
| Không có route | 404 `ROUTE_NOT_FOUND` khi qua được security; đường không được cấp quyền có thể bị security từ chối trước. |
| DNS/connect/connection refused | 503 `SERVICE_UNAVAILABLE`; lỗi giao thức/response upstream không hợp lệ map 502 `BAD_GATEWAY`. |
| Quá thời gian chờ downstream | 504 `GATEWAY_TIMEOUT`; không khẳng định nghiệp vụ đã rollback. |
| Body quá lớn / vượt rate limit | 413 `PAYLOAD_TOO_LARGE` / 429 `TOO_MANY_REQUESTS`. |
| Downstream trả 400/401/403/404/409/5xx hợp lệ | Giữ status/body contract của downstream, không biến mọi lỗi thành 500. |
| Provider webhook ACK / Keycloak OIDC | Giữ wire protocol đã chốt; không bọc tùy ý thành response ứng dụng. |

Các code Gateway trên là đề xuất cần thống nhất khi implement. Lỗi REST EventHub do Gateway sinh có đủ `code`, `message`, `timestamp` UTC, `path` gốc, không lộ host/stacktrace/credential. Lỗi trước khi webhook tới Order không được giả ACK thành công; adapter C06 quyết định phản hồi mà provider hiểu là cần retry. Lỗi STOMP sau Upgrade dùng ERROR/close phù hợp, không chèn ErrorResponse HTTP vào frame.

## 6. Các lớp và cấu hình dự kiến

| Thành phần trong `com.eventhub.gateway` | Trách nhiệm / method đề xuất |
|---|---|
| `config.GatewayRoutesConfig` | `RouteLocator routes(RouteLocatorBuilder builder)` với exact method/path; đọc URI từ properties đã validate. |
| `config.DownstreamProperties` | URI từng service, WS URI; fail startup nếu config sai scheme/thiếu bắt buộc. |
| `config.SecurityConfig` | `SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http)`; deny internal, public exceptions, roles, Resource Server, error handlers. |
| `security.BusinessRoleConverter` | JWT đã xác minh → authorities nghiệp vụ theo mapper cấu hình; không tin header role. |
| `config.CorsConfig` | Origin/method/header allowlist, preflight và response lỗi. |
| `filter.RequestContextWebFilter` | `Mono<Void> filter(ServerWebExchange, WebFilterChain)`; correlation ID, header hygiene từ sớm. |
| `filter.AccessLogGlobalFilter` | Route ID, method, path mẫu, status, latency, correlation ID; không log raw query/body/token. |
| `exception.GatewayErrorWriter` | Dùng chung serialize ErrorResponse cho entry point/access denied/hạ tầng, response chưa commit. |
| `exception.GatewayErrorWebExceptionHandler` | Xử lý lỗi WebFlux/routing; phân biệt timeout/connect/unknown route, không ghi đè response đã commit. |
| Actuator/metrics config | Health trên mạng quản trị, route latency/error rate/connection metrics, không public config/env/route management. |

Gateway dùng API reactive `Mono<Void>`; không tạo service/repository giả chỉ để giống module nghiệp vụ. Không gọi `.block()`, JPA hoặc SDK blocking trên event loop. Không có migration/ERD/Kafka consumer tại Gateway trong phạm vi này.

## 7. Trình tự triển khai và phụ thuộc

| Bước | Việc phải làm | Điều kiện hoàn tất |
|---|---|---|
| G1 — runtime | Chọn BOM tương thích, chuyển dependency MVC → WebFlux, bỏ DB config/phụ thuộc Compose của Gateway. | Module build, khởi động không cần MySQL Gateway; không thay dữ liệu volume. |
| G2 — routing | Khai báo URI host/Docker; route theo bảng, giữ prefix/query, deny internal/default deny. | Kiểm đủ 47 operations public theo hợp đồng; 5 internal không bao giờ tới downstream qua Gateway. |
| G3 — REST security | Chốt Keycloak issuer/audience/role/sub; Resource Server tại Gateway và service; header hygiene/CORS/ErrorResponse. | Chạy thử public/JWT/role/owner/preflight, không tin header giả. Chưa có Keycloak thì chỉ xác nhận routing, chưa tuyên bố security hoàn tất. |
| G4 — payment/gate | Provider C06 và GateActor C07 để sau; C04 chọn ADMIN-only refund, C05 hold/verify/bù có thiết kế. | Webhook giữ chữ ký và Order xác minh; quyền refund ADMIN tại Gateway/service. Route provider/gate chưa đủ adapter/identity chưa bật. |
| G5 — WS | Route ws riêng, Origin, CONNECT auth/timeout/expiry/SUBSCRIBE ở Analytics. | Browser nối được, sai JWT/owner bị từ chối, hết hạn đóng session, reconnect GET hội tụ. |
| G6 — vận hành | Timeout/size/rate policies, health/metrics, TLS/proxy trust, hạn chế expose service. | Quan sát lỗi/latency được, downstream chậm không gây retry ghi hoặc thành công giả. |

G1–G2 có thể bắt đầu ngay với public endpoint đã có hoặc downstream tạm phục vụ kiểm tra trong môi trường dev; không thêm mock auth cấp JWT riêng. G3 song song Auth/Event. G4 đi cùng Order/Ticket và quyết định còn mở. G5 đi cùng Analytics, không chờ tới cuối mới thiết kế cơ chế token trình duyệt.

## 8. Kiểm chứng thủ công và tiêu chí bàn giao

Theo quy ước dự án ngày 2026-10-28, không tạo file/profile/dependency test tự động. Checklist này dùng để build/kiểm tra tĩnh/chạy thử khi implement; lượt viết tài liệu không yêu cầu khởi động backend.

1. Chạy `.\mvnw.cmd -pl api-gateway -am package` sau thay đổi code; kiểm tra dependency tree không còn MVC/JPA/MySQL tại Gateway, đúng Cloud BOM. Khởi động Gateway khi DB Gateway dừng vẫn được.
2. Đối chiếu từng method/path với sáu OpenAPI, kiểm route đích bằng log correlation ID; riêng events ticket types → Ticket và từng nhóm admin → đúng owner. Query page/sort không mất, response không bị đổi shape.
3. Public GET không token; me/password/admin không bị mở theo `/auth/**`; refresh/logout không cần access JWT còn hạn nhưng token/session đầu vào vẫn được Auth kiểm tra.
4. Thử JWT giả/sai issuer/sai audience/hết hạn, role thiếu/role kỹ thuật; gửi header giả ADMIN/userId; kiểm owner bằng hai người dùng khác nhau. Thử JWK rotation/Keycloak không truy cập được, không bypass xác minh.
5. Gọi internal bằng path gốc, thêm prefix, đường mã hóa/traversal và method khác; chứng minh downstream không nhận request. Gọi trực tiếp service public/internal cũng phải bị security phù hợp chặn nếu thiếu credential.
6. Preflight origin đúng/sai, Authorization/content type, response 401/403/504 có CORS/correlation ID; không có header CORS trùng. Kiểm proxy headers giả và external URL sau TLS.
7. Downstream dừng/chậm/trả lỗi: 503/504/body chuẩn; status lỗi hợp lệ của service giữ nguyên. Timeout POST không phát sinh request thứ hai; kiểm body quá lớn, chunked và response đã commit.
8. Với sandbox C06: provider gửi webhook đúng/sai chữ ký/lặp, bytes/query/form giữ nguyên; Order dedup và ACK đúng protocol. Gateway lỗi không ACK thành công thay Order. Gate route chỉ bật khi có policy C07.
9. Trên trình duyệt thật: handshake đúng path/Origin, CONNECT token hợp lệ/thiếu/sai, SUBSCRIBE event người khác, SEND topic server, hết hạn, reconnect. REST vẫn hoạt động khi WS gặp lỗi; không log token trong URL.
10. Xác minh cổng quản trị không public, log không chứa token/password/QR; chạy nhiều Gateway có health/metrics và rate policy nhất quán nếu đã bật rate limit.

Bàn giao kèm bằng chứng build/chạy thử, config mẫu không chứa secret, route inventory, phụ thuộc chưa chốt và hướng dẫn chạy host/Docker. Không đánh dấu G3/G4/G5 xong chỉ vì ứng dụng khởi động hoặc có route.
