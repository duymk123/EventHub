# Event Service — Trạng thái hiện tại

Cập nhật: 2026-11-21. Đã chạy thật end-to-end (MySQL + Keycloak + ticket-service), 12/12 unit test PASS.

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

```
                    ┌──────────────────────────────────────┐
                    │                                      │
   [tạo]            │  organizer sửa (REJECTED→DRAFT,      │
     │              │  xoá rejectReason)                   │
     ▼              │                                      │
   DRAFT ──submit──▶ SUBMITTED ──APPROVE──▶ APPROVED ──publish──▶ PUBLISHED ──close──▶ CLOSED
     ▲                   │                     ▲                     │
     │                   │                     │                     │
     │              REJECT (bắt buộc reason)   │                     │
     │                   ▼                     │                     │
     └────────────── REJECTED ─────────────────┘                     │
        (sửa → DRAFT)                                                │
                                                                     ▼
                                                            PUBLIC không thấy
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

## 2. Chức năng — 16 endpoint

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

### Unit test: 12/12 PASS (`./mvnw -o test -pl event-service`)

`EventServiceImplTest` (6 test, Mockito, không cần DB):
- `fullLifecycle` — DRAFT→SUBMITTED→APPROVED→PUBLISHED→CLOSED
- `nonOwnerCannotChangeEvent` — organizer khác bị 403
- `rejectNeedsReasonAndEditingRejectedEventReturnsToDraft` — reason rỗng bị chặn; sửa REJECTED → DRAFT + xoá reason
- `publishBlockedWhenTicketsNotConfigured` — 409, giữ APPROVED
- `publishChecksStateBeforeCallingTicket` — DRAFT publish thì **không** gọi Ticket (verify `never()`)
- `invalidTimesAndDateRangeRejected` — 3 nhánh validation

`SecurityConfigTest` (6 test, `@WebMvcTest` chạy SecurityFilterChain thật, chỉ giả JwtDecoder):
- Ma trận quyền của cả 16 endpoint
- `invalidTokensAreRejected` — token có 2 role business bị chặn

### Chạy thật end-to-end (2026-11-21)

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

---

## 4. Những gì còn thiếu / cần làm

### 4.1. Chặn merge (phải làm trước khi coi là xong)

| # | Vấn đề | Ảnh hưởng |
|---|---|---|
| 1 | **Không có Flyway**, `ddl-auto: update`, không có `src/main/resources/db/migration/` | Plan §6 yêu cầu migration + `ddl-auto: validate`. Hiện Hibernate tự tạo bảng → production không kiểm soát được schema, không rollback được. **Đã chứng minh có hậu quả thật — xem 4.1.1** |
| 2 | **Không có Testcontainers** | Plan §6 yêu cầu test lock/FK/unique trên MySQL thật. Hiện pessimistic lock và FK chưa có test nào chứng minh — đây là chỗ dễ hỏng nhất khi có tải thật |
| 3 | **Không có test đồng thời** | Plan §6: "hai review/edit đồng thời chỉ một trạng thái hợp lệ". Code có `PESSIMISTIC_WRITE` nhưng **chưa ai chứng minh nó chạy đúng** |

#### 4.1.1. Bằng chứng: schema Hibernate tạo KHÁC DDL tham chiếu

Đã dựng MySQL 8.4, cho Hibernate tự tạo bảng, rồi so với `docs/database/event-service/init.sql`:

| Điểm | DDL tham chiếu | Hibernate `ddl-auto: update` tạo ra | Hậu quả |
|---|---|---|---|
| Cột `status` | `VARCHAR(20)` + `CHECK (status IN (...))` | `enum('APPROVED','CLOSED',...)` | Đổi thứ tự/giá trị enum phải `ALTER TABLE`, không giống DDL |
| `CHECK (start_time < end_time)` | **Có** | **KHÔNG CÓ** | Đã insert được `start_time = 2099-01-01 12:00` > `end_time = 10:00` → DB không bảo vệ |
| `CHECK (status IN (...))` | **Có** | **KHÔNG CÓ** (thay bằng enum) | Ít nguy hiểm hơn, nhưng khác DDL |
| Tên UNIQUE trên `categories.name` | `UNIQUE(name)` | `UKt8o6pivur7nn124jehx7cygw5` | Tên index khác — script/migration sau này dễ lỗi |
| Tên FK | không đặt tên | `FKo6mla8j1p5bokt4dxrlmgwc28` | Như trên |
| Kiểu thời gian | `TIMESTAMP` | `datetime(6)` | Khác biệt về timezone handling |

**Điều này có nghĩa:** hiện tại chỉ có tầng service bảo vệ `startTime < endTime`. Nếu ai insert trực tiếp vào DB, hoặc nếu sau này có service khác ghi vào bảng `events`, dữ liệu rác sẽ lọt vào. Đây chính là lý do plan §6 yêu cầu chuyển sang Flyway + `ddl-auto: validate`.

### 4.2. Sai lệch giữa docs và code

| # | Docs nói | Code thật |
|---|---|---|
| 1 | `docs/plans/event-service.md:87` gọi mã lỗi là `CATEGORY_ALREADY_EXISTS` | Code + OpenAPI dùng `CATEGORY_NAME_EXISTS` → **plan sai** |
| 2 | `docs/plans/event-service.md:54` đề xuất `compareAndSetStatus(...)` | Không tồn tại. Code chọn hướng **pessimistic lock** (`findByIdForUpdate`) — plan cho phép "chọn một cách nhất quán" nên không sai, nhưng dòng này gây hiểu nhầm là đã có |
| 3 | `docs/plans/event-service.md:85` "tạo migration đầu tiên… đổi ddl-auto validate" | Chưa làm gì cả. Đã ghi rõ "CHƯA LÀM" trong plan |
| 5 | `docs/database/event-service/init.sql` có 2 CHECK constraint | Schema thật do Hibernate tạo **không có** — xem 4.1.1 |
| 4 | Plan §1 nói cần "mapper" | Không có MapStruct trong event-service; DTO tự viết `from(...)`. Nhất quán với 4 service còn lại (trừ auth-service) nhưng **lệch `SKILL.md`** |

### 4.3. Nợ kỹ thuật (không chặn nhưng nên dọn)

| # | Vấn đề |
|---|---|
| 1 | `EventRepository.findWithCategoryById` — **không được gọi ở đâu cả**, code chết |
| 2 | `CONCURRENT_UPDATE` chỉ dùng trong `GlobalExceptionHandler` cho `OptimisticLockingFailureException`; nhưng code dùng pessimistic lock nên nhánh này gần như không bao giờ chạy |
| 3 | `EventServiceImpl.publish` gọi `tx.executeWithoutResult` (bước 1) rồi `tx.execute` (bước 3) — **3 lần đọc DB** cho 1 thao tác. Đúng về logic nhưng tốn; có thể gộp bước 1 và 3 nếu chấp nhận gọi Ticket trước khi kiểm owner |
| 4 | Path không tồn tại trả **401** thay vì 404 (do `.anyRequest().denyAll()`) — giống auth-service |
| 5 | `admin` không đóng được event của organizer khác (403 ở security). Đúng ý đồ, nhưng `FORBIDDEN_NOT_OWNER` trong service chỉ chạm được nếu có role ORGANIZER — cần nói rõ trong docs |
| 6 | Không có `GET /api/v1/admin/events` — ADMIN **không có cách nào liệt kê event đang chờ duyệt** |
| 7 | Không có audit log cho review (ai duyệt, khi nào, lý do). Plan §2 nói "nếu cần audit thì đề xuất schema riêng" — chưa có |

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
- 12/12 PASS ngay lần chạy đầu, không phải test "sửa cho xanh".
- Chạy thật end-to-end 25 case, **không case nào sai**.

**Nhưng chưa đủ.** Ba lỗ hổng phải bù (mục 4.1): Flyway, Testcontainers, test đồng thời. Test hiện tại chứng minh **logic đúng**, chưa chứng minh **hành vi dưới tải đồng thời** — mà đó chính là chỗ `PESSIMISTIC_WRITE` tồn tại để giải quyết.

---

## 6. Định nghĩa "xong" cho event-service

- [x] 16/16 endpoint khớp `docs/api/event-service-openapi.yaml`
- [x] Máy trạng thái đúng, không có chuyển trạng thái không hợp lệ
- [x] Phân quyền public/ORGANIZER/ADMIN/internal đúng
- [x] C08 fail-closed (ticket chết → 503, event không đổi)
- [x] Unit + WebMvcTest 12/12 PASS
- [x] Chạy thật end-to-end với ticket-service
- [ ] Flyway migration + `ddl-auto: validate`
- [ ] Testcontainers cho lock/FK/unique
- [ ] Test 2 request đồng thời
- [ ] `GET /api/v1/admin/events` cho ADMIN duyệt
- [ ] Route qua API Gateway
- [ ] Dọn `findWithCategoryById` nếu không dùng

---

## 7. Cách chạy để tự kiểm

```bash
# 1. DB — event-service dùng 3306 (KHÁC auth-service dùng 33101)
docker run -d --rm --name eventhub-event-db \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=eventhub_event \
  -p 3306:3306 mysql:8.4

# 2. Chạy service
./mvnw -pl event-service spring-boot:run

# 3. Lấy token (Keycloak phải đang chạy ở 8180)
curl -s -X POST http://localhost:8180/realms/eventhub/protocol/openid-connect/token \
  -d 'grant_type=password' \
  -d 'client_id=eventhub-auth-service' \
  -d 'client_secret=eventhub-auth-service-dev-secret' \
  -d 'username=organizer@eventhub.local' -d 'password=Organizer@123'
```

Cần publish thành công thì **phải chạy ticket-service** ở 8083, nếu không sẽ nhận 503.

---

## 8. Liên quan

- Luồng chi tiết + OpenAPI: [`docs/api/event-service-openapi.yaml`](../docs/api/event-service-openapi.yaml)
- Plan gốc: [`docs/plans/event-service.md`](../docs/plans/event-service.md)
- Hợp đồng xuyên service (C03, C08, C10): [`docs/plans/00-cross-service-contracts.md`](../docs/plans/00-cross-service-contracts.md)
