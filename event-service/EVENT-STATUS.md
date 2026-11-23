# Event Service — Trạng thái hiện tại

Cập nhật: 2026-11-22. Đã chạy thật end-to-end (MySQL 8.4 + Keycloak + ticket-service), 14/14 unit test PASS, schema do **Flyway V1** quản lý với `ddl-auto: validate`.

---

## 1. Nghiệp vụ

Event Service quản lý **vòng đời một sự kiện** (concert, hội thảo…) từ lúc nhà tổ chức tạo nháp đến khi đóng bán vé.

**Điều service này sở hữu:**
- `categories` — danh mục sự kiện (Âm nhạc, Thể thao…), ADMIN quản lý
- `events` — sự kiện: tiêu đề, mô tả, danh mục, thời gian, trạng thái, lý do từ chối

**Điều service này KHÔNG sở hữu** (quan trọng, đừng nhầm):
- Loại vé, giá, tồn kho, zone A–D → **ticket-service**
- Đơn hàng, thanh toán → **order-service**
- Người dùng, mật khẩu, role → **auth-service/Keycloak**
- Địa điểm chi tiết → dự kiến **notification-service** (C03), event chỉ giữ text

`organizerId` là **khóa mềm** (UUID Keycloak sub), không có FK sang Auth. Không JOIN xuyên service.

### Máy trạng thái

Đây là máy trạng thái chuẩn của event-service; mọi tài liệu khác ([OpenAPI](../docs/api/event-service-openapi.yaml), [plan](../docs/plans/event-service.md), code `EventStatus`) phải khớp sơ đồ này.

```
   [tạo: ORGANIZER]                [sửa DRAFT/REJECTED]
          │                                  │
          ▼                                  │
   ┌─────────────┐   submit     ┌────────────┐│  (sửa REJECTED → DRAFT,
   │   DRAFT     │─────────────▶│ SUBMITTED  ││   xoá rejectReason)
   └─────────────┘              └────────────┘│
          ▲                         │   │     │
          │        APPROVE (ADMIN)  │   │ REJECT (ADMIN, bắt buộc reason)
          │                         ▼   ▼
          │                  ┌──────────┐ ┌──────────┐
          │                  │ APPROVED │ │ REJECTED │────┘
          │                  └──────────┘ └──────────┘
          │                         │ publish (C08: ticket-service readyToPublish=true)
          │                         ▼
          │                  ┌───────────┐  close   ┌────────┐
          └──────────────────│ PUBLISHED │─────────▶│ CLOSED │
             (không có đường │           │          └────────┘
              ngược)         └───────────┘
```

| Chuyển | Ai làm | Điều kiện | Ghi chú |
|---|---|---|---|
| ∅ → `DRAFT` | ORGANIZER | startTime ở tương lai, endTime > startTime | Luôn tạo ở DRAFT |
| `DRAFT` → `SUBMITTED` | ORGANIZER (owner) | Chưa tới giờ bắt đầu | **Không** kiểm cấu hình vé |
| `SUBMITTED` → `APPROVED` | ADMIN | — | Xoá `rejectReason` cũ |
| `SUBMITTED` → `REJECTED` | ADMIN | **Bắt buộc `reason`** | Lưu `rejectReason` |
| `APPROVED` → `PUBLISHED` | ORGANIZER (owner) | **C08**: ticket-service báo `readyToPublish=true` | Gọi Ticket **ngoài** transaction |
| `PUBLISHED` → `CLOSED` | ORGANIZER (owner) | — | Ngừng bán, **không** huỷ vé, **không** cấm check-in |
| `DRAFT`/`REJECTED` → sửa | ORGANIZER (owner) | Chỉ 2 trạng thái này | Sửa `REJECTED` = bắt đầu vòng duyệt mới, về `DRAFT` |

**Sửa event bị khoá ở mọi trạng thái khác** — `SUBMITTED`/`APPROVED`/`PUBLISHED`/`CLOSED` không sửa được (409 `INVALID_EVENT_STATE`).

**Public chỉ thấy `PUBLISHED`.** `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CLOSED` đều trả 404 với người ngoài.

---

## 2. Chức năng — 17 endpoint

### Public (không cần token)

| Method | Path | Chức năng |
|---|---|---|
| `GET` | `/api/v1/events` | Tìm event đã publish. Lọc `keyword` (title), `category`, `startDate`, `endDate`; sort `startTime/endTime/title/createdAt/updatedAt/status` |
| `GET` | `/api/v1/events/{id}` | Chi tiết event đã publish (404 nếu chưa/không tồn tại) |
| `GET` | `/api/v1/categories` | Danh sách danh mục, sort theo tên |

### ORGANIZER

| Method | Path | Chức năng |
|---|---|---|
| `POST` | `/api/v1/events` | Tạo event nháp |
| `PUT` | `/api/v1/events/{id}` | Sửa event (chỉ DRAFT/REJECTED, phải là owner) |
| `POST` | `/api/v1/events/{id}/submit` | Nộp duyệt |
| `POST` | `/api/v1/events/{id}/publish` | Mở bán (**gọi ticket-service preflight**) |
| `POST` | `/api/v1/events/{id}/close` | Đóng bán |
| `GET` | `/api/v1/organizer/events` | Event của tôi, lọc `status`, phân trang |

### ADMIN

| Method | Path | Chức năng |
|---|---|---|
| `GET` | `/api/v1/admin/events` | **EVT-11** — danh sách event chờ duyệt, lọc `status`, phân trang, sort mặc định `updatedAt,desc` |
| `PATCH` | `/api/v1/admin/events/{id}/review` | Duyệt / từ chối |
| `POST` | `/api/v1/categories` | Tạo danh mục |
| `PUT` | `/api/v1/categories/{id}` | Sửa danh mục |
| `DELETE` | `/api/v1/categories/{id}` | Xoá danh mục **chưa có event nào** |

### Nội bộ (service-to-service, `/internal/**`)

| Method | Path | Ai được gọi | Chức năng |
|---|---|---|---|
| `GET` | `/internal/events/{id}` | ticket, analytics | Đọc event **mọi trạng thái** |
| `GET` | `/internal/events/ids?organizerId=` | analytics | Toàn bộ id event của 1 organizer |
| `GET` | `/internal/events?ids=` | analytics | Batch tối đa 100 id |

**Phân quyền nội bộ làm theo `azp` (client id) của token**, không theo role: `eventhub-ticket-service` → chỉ `/{id}`; `eventhub-analytics-service` → cả 3. Token của `eventhub-order-service` bị từ chối (401).

---

## 3. Đã làm được gì — chứng minh bằng test thật

### Unit test: 14/14 PASS (`./mvnw -o test -pl event-service`)

`EventServiceImplTest` (7 test, Mockito, không cần DB):
- `fullLifecycle` — DRAFT→SUBMITTED→APPROVED→PUBLISHED→CLOSED
- `nonOwnerCannotChangeEvent` — organizer khác bị 403
- `rejectNeedsReasonAndEditingRejectedEventReturnsToDraft` — reason rỗng bị chặn; sửa REJECTED → DRAFT + xoá reason
- `publishBlockedWhenTicketsNotConfigured` — 409, giữ APPROVED
- `publishChecksStateBeforeCallingTicket` — DRAFT publish thì **không** gọi Ticket (verify `never()`)
- `invalidTimesAndDateRangeRejected` — 3 nhánh validation
- `adminListPassesStatusFilterAndDefaultSort` — EVT-11: lọc `status`, sort mặc định `updatedAt desc`, sort theo cột lạ bị chặn (`VALIDATION_ERROR`)

`SecurityConfigTest` (7 test, `@WebMvcTest` chạy SecurityFilterChain thật, chỉ giả JwtDecoder):
- Ma trận quyền của cả 17 endpoint
- `invalidTokensAreRejected` — token có 2 role business bị chặn
- `adminListIsAdminOnlyAndReadsStatusFilter` — EVT-11: 401 không token, 403 ORGANIZER, 403 token service, 200 cho ADMIN, `status` được truyền xuống service, phân trang đúng

### Chạy thật end-to-end (2026-11-21, bổ sung 2026-11-22)

Dựng MySQL 8.4 tạm + Keycloak thật + ticket-service thật, gọi bằng token thật:

| Case | Kết quả |
|---|---|
| Public search rỗng, categories rỗng | 200, JSON đúng `{content, page{...}}` |
| Tạo event không token | 401 `UNAUTHORIZED` |
| ADMIN tạo category | 201 |
| ORGANIZER tạo event | 201, status DRAFT |
| Submit | 200 → SUBMITTED |
| Public xem khi chưa publish | **404** (không lộ) |
| ADMIN approve | 200 → APPROVED |
| Publish khi ticket-service **tắt** | **503 `TICKET_SERVICE_UNAVAILABLE`**, event vẫn APPROVED |
| Publish khi ticket-service bật, chưa có ticket type | **409 `TICKET_CONFIGURATION_NOT_READY`** |
| Tạo ticket type `totalQuota=0` | 201, publish vẫn **409** (0 không đủ) |
| Nâng quota lên 100 | Publish **200 → PUBLISHED** |
| Public xem sau publish | 200, đủ field |
| Close | 200 → CLOSED |
| Public xem sau close | **404** |
| Internal (analytics token) vẫn đọc được event CLOSED | 200 |
| INT-03 ids by organizer | 200 `["c03e72de-..."]` |
| INT-02 batch | 200, đúng 1 phần tử |
| ADMIN đóng event của organizer khác | 403 `FORBIDDEN` (chặn ở tầng security, chưa tới `FORBIDDEN_NOT_OWNER`) |
| Submit lần 2 | 409 `INVALID_EVENT_STATE` |
| endTime < startTime | 400 `VALIDATION_ERROR` |
| Xoá category đang có event | 409 `CATEGORY_IN_USE` |
| Tạo category trùng tên | 409 `CATEGORY_NAME_EXISTS` |
| Internal không token | 401 |
| Internal bằng token ORGANIZER | 403 |
| Path không tồn tại, không token | 401 (không phải 404) |

**Không có case nào sai.** C08 fail-closed hoạt động đúng: ticket-service chết → 503 và **event không bị đổi trạng thái**.

### Schema bằng Flyway (2026-11-22)

| Kiểm tra | Kết quả |
|---|---|
| DB rỗng + app khởi động | Flyway log `Successfully applied 1 migration … now at version v1`; `GET /api/v1/events` → 200; `GET /api/v1/admin/events` không token → 401 |
| DB rỗng + nạp `docs/database/event-service/init.sql` | Cột/index/constraint **giống hệt** V1 (so bằng `information_schema`) |
| DB do Hibernate cũ tạo (status ENUM, `UKt8o6…`, `FKo6mla8…`) + helper | Giống hệt V1 về cột; log `skipped` khi chạy lại; DB rỗng thì từ chối chạy |
| DB do Hibernate cũ tạo + baseline | Log `Successfully baselined schema with version: 1`; khởi động lại với `validate` chạy bình thường |

Chi tiết quy trình nâng cấp ở [§7](#7-nâng-cấp-database-cũ-ddl-auto-update--flyway).

---

## 4. Những gì còn thiếu / cần làm

### 4.1. Chặn merge

| # | Vấn đề | Trạng thái |
|---|---|---|
| 1 | Flyway + `ddl-auto: validate` | **ĐÃ XONG** 2026-11-22 — V1 migration, `validate`, helper cho DB cũ, kiểm chứng end-to-end (§3, §7) |
| 2 | **Không có Testcontainers** | Plan §6 yêu cầu test lock/FK/unique trên MySQL thật. Pessimistic lock và CHECK/FK chỉ được chứng minh bằng tay, chưa có test tự động |
| 3 | **Không có test đồng thời** | Plan §6: "hai review/edit đồng thời chỉ một trạng thái hợp lệ". Code có `PESSIMISTIC_WRITE` nhưng **chưa có test** chứng minh |

Theo quy ước dự án (`SKILL.md` §7, `docs/plans/README.md`), không thêm file test mới/profile/test-only dependency khi chưa được yêu cầu; vì vậy #2 và #3 đang là **khoảng trống có ghi nhận**, không phải việc đang làm dở.

### 4.2. Sai lệch giữa docs và code

| # | Docs nói | Code thật |
|---|---|---|
| 1 | `docs/plans/event-service.md:87` gọi mã lỗi là `CATEGORY_ALREADY_EXISTS` | Code + OpenAPI dùng `CATEGORY_NAME_EXISTS` → **plan sai** |
| 2 | `docs/plans/event-service.md:54` đề xuất `compareAndSetStatus(...)` | Không tồn tại. Code chọn hướng **pessimistic lock** (`findByIdForUpdate`) — plan cho phép "chọn một cách nhất quán" nên không sai, nhưng dòng này gây hiểu nhầm là đã có |
| 3 | Plan §1 nói cần "mapper" | Không có MapStruct trong event-service; DTO tự viết `from(...)`. Nhất quán với 4 service còn lại (trừ auth-service) nhưng **lệch `SKILL.md`** |

### 4.3. Nợ kỹ thuật (không chặn nhưng nên dọn)

| # | Vấn đề |
|---|---|
| 1 | `CONCURRENT_UPDATE` chỉ dùng trong `GlobalExceptionHandler` cho `OptimisticLockingFailureException`; nhưng code dùng pessimistic lock nên nhánh này gần như không bao giờ chạy |
| 2 | `EventServiceImpl.publish` gọi `tx.executeWithoutResult` (bước 1) rồi `tx.execute` (bước 3) — **3 lần đọc DB** cho 1 thao tác. Đúng về logic nhưng tốn; có thể gộp bước 1 và 3 nếu chấp nhận gọi Ticket trước khi kiểm owner |
| 3 | Path không tồn tại trả **401** thay vì 404 (do `.anyRequest().denyAll()`) — giống auth-service |
| 4 | `admin` không đóng được event của organizer khác (403 ở security). Đúng ý đồ, nhưng `FORBIDDEN_NOT_OWNER` trong service chỉ chạm được nếu có role ORGANIZER — cần nói rõ trong docs |
| 5 | Không có audit log cho review (ai duyệt, khi nào, lý do). Plan §2 nói "nếu cần audit thì đề xuất schema riêng" — chưa có |
| 6 | Sửa `V1__init.sql` sau khi đã áp dụng là cấm: thay đổi schema phải thêm `V2__*.sql` (xem §7) |

### 4.4. Chưa tích hợp

| Việc | Trạng thái |
|---|---|
| Route API Gateway → event-service | **Chưa có**. `api-gateway/application.yml` không có `spring.cloud.gateway.routes` nào |
| Kafka publish `event.published` | Cố ý không làm (plan §2: chưa có consumer) |
| FE gọi event-service | Chưa nối |

---

## 5. Test có ổn không? — Kết luận

**Ổn, giữ lại, không cần làm lại.**

Lý do:
- Test **thực chất**, không phải test rỗng: `verify(tickets, never())` kiểm đúng thứ tự gọi; `SecurityConfigTest` chạy filter chain thật chứ không mock security; các case âm (owner khác, sai trạng thái, reason rỗng) đều có.
- 14/14 PASS, chạy lại sau mỗi thay đổi (Flyway, EVT-11), không phải test "sửa cho xanh".
- Chạy thật end-to-end 25 case, **không case nào sai**; thêm 4 case kiểm schema/Flyway ở §3.

**Nhưng chưa đủ.** Hai khoảng trống còn lại (mục 4.1 #2, #3): Testcontainers và test đồng thời. Test hiện tại chứng minh **logic đúng**, chưa chứng minh **hành vi dưới tải đồng thời** — mà đó chính là chỗ `PESSIMISTIC_WRITE` tồn tại để giải quyết.

---

## 6. Định nghĩa "xong" cho event-service

- [x] 17/17 endpoint khớp `docs/api/event-service-openapi.yaml`
- [x] Máy trạng thái đúng, không có chuyển trạng thái không hợp lệ
- [x] Phân quyền public/ORGANIZER/ADMIN/internal đúng
- [x] C08 fail-closed (ticket chết → 503, event không đổi)
- [x] Unit + WebMvcTest 14/14 PASS
- [x] Chạy thật end-to-end với ticket-service
- [x] Flyway migration V1 + `ddl-auto: validate` + đường nâng cấp DB cũ
- [x] `GET /api/v1/admin/events` cho ADMIN duyệt (EVT-11)
- [ ] Testcontainers cho lock/FK/unique
- [ ] Test 2 request đồng thời
- [ ] Route qua API Gateway
- [ ] Audit log review (nếu chủ dự án yêu cầu)

---

## 7. Nâng cấp database cũ (`ddl-auto: update` → Flyway)

Schema hiện hành là `event-service/src/main/resources/db/migration/V1__init.sql`; Hibernate chỉ **validate**, không tạo/sửa bảng. `docs/database/event-service/init.sql` là bản tham chiếu cùng nội dung (đã so bằng `information_schema`, giống hệt).

### 7.1. Ba tình huống khi triển khai

| DB hiện tại | Làm gì |
|---|---|
| **Rỗng** (máy mới, CI) | Chỉ cần chạy service: Flyway áp `V1` và ghi `flyway_schema_history` |
| **Hibernate tạo từ trước** (`status` là ENUM, FK tên `FKo6mla8…`, UNIQUE tên `UKt8o6…`) | Chạy **một lần** bằng build cũ với `--spring.jpa.hibernate.ddl-auto=update` cho Hibernate bổ sung cột/index/FK, rồi baseline (§7.2). Hoặc thay bước đó bằng helper (§7.3) |
| **Đã đúng V1** (đã baseline, hoặc tạo tay theo `init.sql`) | Không làm gì, Flyway thấy version 1 và bỏ qua |

### 7.2. Đường chuẩn (baseline)

```bash
# 1) Build CŨ (chưa có Flyway) chạy 1 lần để Hibernate đồng bộ cột/index/FK với entity:
./mvnw -pl event-service spring-boot:run -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=update
#    → dừng service sau khi khởi động xong.

# 2) Baseline: ghi "DB đã ở version 1" mà KHÔNG chạy V1 (V1 sẽ lỗi vì bảng đã tồn tại):
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true ./mvnw -pl event-service spring-boot:run \
  -Dspring-boot.run.arguments="--spring.flyway.baseline-version=1"
#    → log: Successfully baselined schema with version: 1

# 3) Từ nay chạy bình thường (validate): application.yml đã đặt ddl-auto: validate
./mvnw -pl event-service spring-boot:run
```

**Giới hạn đã kiểm chứng của bước 1** (`ddl-auto: update` không phải công cụ migration):
- Nếu **index trên cột FK đã tồn tại**, Hibernate **không thêm** foreign key (đã thử nghiệm trên MySQL 8.4) → DB vẫn thiếu FK dù đã chạy `update`.
- Hibernate **không đổi** `status` từ ENUM sang `VARCHAR(20)`, và không tạo CHECK `start_time < end_time`.

Vì vậy nếu sau bước 1 vẫn thấy `status` là ENUM hoặc thiếu `fk_events_category`, dùng helper §7.3 (an toàn để chạy trên DB vừa qua bước 1: bước nào xong thì bỏ qua).

### 7.3. Helper cho DB cũ (không phải migration)

`event-service/src/main/resources/db/migration/helper_legacy_datetime.sql` — không có tiền tố `V<n>__` nên **Flyway không tự chạy**; chạy tay:

```bash
mysql -h127.0.0.1 -P3306 -uroot -p eventhub_event \
  < event-service/src/main/resources/db/migration/helper_legacy_datetime.sql
```

Helper: kiểm tra đủ 2 bảng → `start_time < end_time` trước khi siết dữ liệu → đổi cột thời gian sang `DATETIME(6)`, bù `updated_at` → đổi `status` ENUM → `VARCHAR(20)` → thay FK/UNIQUE bằng tên chuẩn → thêm CHECK/index còn thiếu. Chạy lại: các bước đã xong ghi `skipped`. DB rỗng: dừng với thông báo "để Flyway chạy V1". Sau khi chạy helper vẫn cần bước baseline §7.2 (2).

### 7.4. Cảnh báo khi đổi `ddl-auto`

- **Đừng đổi `ddl-auto` sang `validate` trên DB chưa đồng bộ** → app crash-loop khi khởi động (`Schema-validation: missing table/column`).
- Kiểm tra biến môi trường/run-config có sẵn `SPRING_JPA_HIBERNATE_DDL_AUTO` hoặc `--spring.jpa.hibernate.ddl-auto` khác trong file cấu hình chạy: chúng **ghi đè** `application.yml` và âm thầm giữ `update`.
- Đã áp dụng `V1` thì **không sửa** `V1__init.sql` (checksum sẽ lệch, Flyway báo lỗi); thay đổi schema → thêm `V2__<mô tả>.sql`.
- Không chạy `docs/database/event-service/init.sql` vào DB đã có dữ liệu; file đó chỉ để tham chiếu/đọc.

---

## 8. Cách chạy để tự kiểm

```bash
# 1) DB — event-service dùng 3306 (KHÁC auth-service dùng 33101). DB rỗng: Flyway tự tạo bảng.
docker run -d --rm --name eventhub-event-db \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=eventhub_event \
  -p 3306:3306 mysql:8.4

# 2) Chạy service (Flyway áp V1 → log "now at version v1")
./mvnw -pl event-service spring-boot:run

# 3) Lấy token (Keycloak phải đang chạy ở 8180)
curl -s -X POST http://localhost:8180/realms/eventhub/protocol/openid-connect/token \
  -d 'grant_type=password' \
  -d 'client_id=eventhub-auth-service' \
  -d 'client_secret=eventhub-auth-service-dev-secret' \
  -d 'username=organizer@eventhub.local' -d 'password=Organizer@123'
```

Cần publish thành công thì **phải chạy ticket-service** ở 8083, nếu không sẽ nhận 503.

---

## 9. Liên quan

- Luồng chi tiết + OpenAPI: [`docs/api/event-service-openapi.yaml`](../docs/api/event-service-openapi.yaml)
- Plan gốc: [`docs/plans/event-service.md`](../docs/plans/event-service.md)
- Hợp đồng xuyên service (C03, C08, C10): [`docs/plans/00-cross-service-contracts.md`](../docs/plans/00-cross-service-contracts.md)
- Schema: [`V1__init.sql`](src/main/resources/db/migration/V1__init.sql) (chuẩn), [`init.sql`](../docs/database/event-service/init.sql) (tham chiếu), [`fields.md`](../docs/database/event-service/fields.md) (từ điển cột)
