# AGENTS.md – EventHub Frontend

Hướng dẫn cố định cho Codex khi viết frontend của EventHub (hệ thống bán vé sự kiện, backend microservices Java Spring Boot). Đọc hết file này trước khi viết bất kỳ component nào.

## 0. Cách làm việc

- Chỉ sửa code trong thư mục frontend. Không đụng vào backend.
- Làm từng tính năng nhỏ, mỗi lần một trang hoặc một luồng.
- Trước khi báo hoàn thành: chạy `npm run lint`, `npm run build`, và tự đối chiếu với mục 8 (Checklist).
- Không tự thêm thư viện ngoài Tech Stack. Cần thêm thì nói rõ lý do.
- Từ 2026-11-24 frontend gọi API thật qua API Gateway (đã bỏ dữ liệu mock của giai đoạn C15). Kiểu DTO backend ở `src/types/api.ts` (khớp OpenAPI trong `docs/api`), chuyển sang kiểu giao diện ở `src/lib/api/adapters.ts`; hàm gọi API ở `src/lib/api/endpoints.ts`, hook TanStack Query ở `src/hooks/use-api.ts`. Chạy local: copy `.env.example` thành `.env.local`, mở `http://localhost:3000` (không dùng 127.0.0.1, xem mục 3).

## 1. Tech Stack

- Next.js (App Router) + TypeScript (strict, không dùng `any`)
- Tailwind CSS, shadcn/ui, Framer Motion, lucide-react
- Form: React Hook Form + Zod
- Gọi API và cache dữ liệu: TanStack Query
- Toast: sonner (có sẵn trong shadcn/ui)
- Không hardcode URL backend: đọc từ `NEXT_PUBLIC_API_BASE_URL` (trỏ tới API Gateway)

## 2. Cấu trúc thư mục

```
frontend/
├── src/
│   ├── app/
│   │   ├── (public)/            # trang khách: danh sách sự kiện, chi tiết sự kiện
│   │   ├── (auth)/              # đăng nhập, đăng ký
│   │   ├── (customer)/          # đặt vé, thanh toán, vé của tôi
│   │   ├── organizer/           # dashboard Ban tổ chức
│   │   └── admin/               # duyệt sự kiện, quản trị người dùng
│   ├── components/
│   │   ├── ui/                  # shadcn/ui (không sửa tay nếu không cần)
│   │   └── features/            # component theo nghiệp vụ: event/, ticket/, order/, dashboard/
│   ├── lib/
│   │   ├── api/                 # api client + hàm gọi theo từng service
│   │   ├── auth/                # lưu token, guard theo role
│   │   └── utils.ts
│   ├── hooks/
│   ├── types/                   # kiểu dữ liệu khớp response của backend
│   └── schemas/                 # Zod schema cho form
```

Quy tắc: component chỉ hiển thị. Gọi API đặt trong `lib/api` và hooks, không `fetch` trực tiếp trong component.

## 3. Hợp đồng với Backend (bắt buộc tuân thủ)

**Điểm vào duy nhất:** mọi request đi qua API Gateway, đường dẫn dạng `/api/v1/<resource>` (danh từ số nhiều, kebab-case). Không bao giờ gọi `/internal/**`.

**Vai trò:** `CUSTOMER`, `ORGANIZER`, `ADMIN`. Ẩn menu và chặn route theo role ở frontend để UX tốt, nhưng hiểu rằng backend mới là nơi quyết định quyền thật.

**Xác thực (JWT):**
- Thời hạn lấy theo realm Keycloak (`infrastructure/keycloak/eventhub-realm.json`): access token 300 giây (5 phút), phiên refresh 7 ngày, refresh token xoay vòng mỗi lần dùng. Không hardcode: dùng `expiresIn` trong response để hẹn refresh.
- Chỉ dùng MỘT api client chung, tự gắn `Authorization: Bearer <token>`.
- Khi gặp `401`: gọi refresh đúng một lần (gộp các request đang chờ), thành công thì gọi lại request cũ, thất bại thì đăng xuất và chuyển về trang đăng nhập.
- Lưu token (quyết định C13): access token **chỉ trong bộ nhớ** (biến/state của api client), không ghi `localStorage`/`sessionStorage`. Refresh token nằm trong cookie `eh_refresh` `HttpOnly` do Auth đặt; frontend không đọc được và không cần đọc. Gọi `/api/v1/auth/login|refresh|logout` với `credentials: "include"`. Tải lại trang thì gọi refresh để lấy access token mới.
- Khi chạy local, frontend và API phải cùng tên host (cùng `localhost`), nếu không cookie `SameSite=Strict` sẽ không được gửi.
- Không log token, không để token trong URL.

**Định dạng lỗi thống nhất từ backend:**
```json
{ "code": "SEAT_TAKEN", "message": "Seat already taken", "timestamp": "...", "path": "/api/v1/orders", "takenSeatIds": ["..."] }
```
- Hiển thị lỗi dựa trên `code`, không dựa trên `message`. Tạo một bảng map `code` → thông báo tiếng Việt thân thiện trong `lib/api/errors.ts`.
- Các code cần có thông báo: `EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `EMAIL_NOT_VERIFIED`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED`, `SEAT_TAKEN` (kèm `takenSeatIds`), `SEAT_NOT_FOUND`, `TOO_MANY_SEATS`, `TICKET_TYPE_NOT_ON_SALE`, `IDEMPOTENCY_KEY_REUSED`, `ORDER_EXPIRED`, `ORDER_NOT_PAYABLE`, `PAYMENT_FAILED`, `PAYMENT_PROVIDER_UNAVAILABLE`, `REFUND_WINDOW_EXPIRED`, `TICKET_ALREADY_CHECKED_IN`, `SCAN_OUTSIDE_EVENT_TIME`. Code lạ thì hiện thông báo chung, không crash.
- `SEAT_TAKEN`: tô xám các ghế trong `takenSeatIds`, bỏ chúng khỏi lựa chọn và đưa khách về sơ đồ ghế để chọn lại.

**Chuyển trạng thái dùng sub-resource:** ví dụ `POST /api/v1/events/{id}/submit`, `POST /api/v1/events/{id}/publish`. Không tự bịa endpoint, nếu chưa biết đường dẫn thì để TODO và hỏi.

## 4. Quy tắc nghiệp vụ ảnh hưởng đến UI

- **Không tin client:** giá, tổng tiền, kết quả thanh toán luôn lấy từ response của server. Frontend chỉ hiển thị, không tự tính tiền để gửi lên.
- **Giữ ghế 10 phút (C16):** ghế được giữ 10 phút, đơn hàng cũng hết hạn sau 10 phút. Thanh toán lỗi/hủy trên VNPay thì đơn vẫn chờ, cho phép "Thanh toán lại" đến hết hạn (C18). Đồng hồ countdown chỉ để hiển thị, hạn thật do server quyết định. Hết giờ hoặc server báo hết hạn thì thông báo rõ ràng và đưa người dùng về bước chọn vé.
- **Ba hạng vé VIP / PREMIUM / STANDARD, mỗi hạng là một khu ngồi (zone)** do Ticket Service quản lý (C12). Khách chọn khu trên màn 3D (sân khấu một đầu) rồi chọn **từng ghế** trên sơ đồ ghế của khu; mỗi sự kiện có số hàng × số ghế riêng do organizer nhập, không cố định sơ đồ. Tối đa 10 ghế mỗi đơn.
- **Idempotency-Key (C14):** sinh một UUID khi khách bấm "Giữ ghế" và gửi header `Idempotency-Key` với `POST /api/v1/orders`; retry do lỗi mạng giữ nguyên key, chọn lại ghế thì đổi key.
- **Thanh toán VNPay (C06):** `POST /api/v1/orders/{id}/payment` trả `paymentUrl`, chuyển khách sang trang VNPay. Trang kết quả (`returnUrl`) không tin tham số trên URL; gọi `POST /api/v1/orders/{id}/payment/verify` rồi hiển thị trạng thái đơn server trả về.
- **Địa điểm (C03):** mọi sự kiện dùng chung "141 Đường Chiến Thắng, Hà Nội".
- **Quét vé (C07):** trang quét QR bằng camera cho ORGANIZER chủ sự kiện và ADMIN, chỉ dùng được trong khung giờ sự kiện (60 phút trước giờ bắt đầu đến giờ kết thúc).
- **Chống bấm đúp:** nút tạo đơn, thanh toán, hoàn tiền phải bị vô hiệu hóa trong lúc đang gọi API.
- **Vòng đời sự kiện** (DRAFT → … → CLOSED): chỉ hiện nút hành động hợp lệ với trạng thái hiện tại.
- **Mã QR của vé** là token ngẫu nhiên do server cấp, chỉ hiển thị, không tự sinh hay giải mã.
- **Không xóa cứng:** các thao tác "hủy", "thu hồi", "khóa" là đổi trạng thái, giao diện dùng đúng từ này.
- Tiền tệ mặc định VND, định dạng bằng `Intl.NumberFormat("vi-VN")`. Thời gian từ server là UTC, hiển thị theo giờ địa phương.

## 5. Visual Identity & Theme (Khách xem sự kiện)

- **Concept:** giao diện điện ảnh, mang không khí concert/festival.
- **Typography:** tương phản mạnh. Tiêu đề sự kiện lớn, đậm (`tracking-tight font-black`).
- **Màu sắc:** nền đen thuần (`bg-black` hoặc `bg-neutral-950`) kết hợp một màu nhấn bão hòa cao: Electric Purple `#8B5CF6` làm màu chính, Amber `#F59E0B` làm màu phụ cho cảnh báo và điểm nhấn. Khai báo thành CSS variables/Tailwind tokens, không rải mã màu khắp component.
- **Card:** không viền, glow nhẹ, `backdrop-blur-md`, hover phóng to dần (`hover:scale-[1.02] transition-all duration-300`).
- **Banner chi tiết sự kiện:** dạng kính mờ (glassmorphism), có countdown, nút "Mua vé ngay" nổi bật nhất trên trang.
- Tôn trọng `prefers-reduced-motion`: tắt hoặc giảm animation khi người dùng yêu cầu.

## 6. Checkout & Đặt vé (Tương tác)

- **Nút bấm:** hiệu ứng nhún khi nhấn (`active:scale-95 transition-transform`).
- **Trạng thái loading khi thanh toán:** dùng chỉ báo từng bước (ví dụ: Giữ vé → Xác nhận → Thanh toán), không dùng spinner trần.
- **Chọn số lượng vé / sơ đồ ghế:** chuyển đổi mượt bằng Framer Motion layout animations. Hết vé thì hiện trạng thái rõ ràng, không để người dùng bấm rồi mới báo lỗi.
- **Form:** label nổi (floating label) hoặc gợi ý cố định. Chia thành nhiều bước, chuyển bước bằng micro-slide thay vì một trang cuộn dài.
- **Thông báo:** giữ vé thành công, hết hạn, lỗi thanh toán đều dùng toast, nội dung lấy từ bảng map `code`.

## 7. Dashboard Ban tổ chức & Admin

- **Bố cục:** lưới `grid-cols-12`, mật độ thông tin cao nhưng căn lề nghiêm ngặt theo hệ 4px/8px. Khu vực dashboard dùng giao diện gọn, ít hiệu ứng hơn trang khách (ưu tiên đọc dữ liệu).
- **Bảng dữ liệu** (danh sách người mua vé, đơn hàng): header cố định, cuộn ngang (`overflow-x-auto`), có vùng tìm kiếm và lọc, chữ dài thì cắt bớt kèm tooltip, phân trang gọi từ server.
- **Hành động nguy hiểm** (Hủy sự kiện, Hoàn tiền, Khóa tài khoản): bắt buộc có modal xác nhận thứ hai, nút dùng kiểu destructive (`bg-destructive text-destructive-foreground`), nêu rõ hậu quả trong nội dung modal.
- **Responsive:** phải dùng tốt trên cả Desktop và Mobile. Trên mobile, bảng chuyển sang dạng card hoặc cuộn ngang, không để vỡ layout.
- Mọi màn hình dữ liệu cần đủ 4 trạng thái: đang tải (skeleton), rỗng, lỗi (có nút thử lại), có dữ liệu.

## 8. Checklist trước khi báo hoàn thành

- [ ] Không hardcode URL, không `fetch` trực tiếp trong component.
- [ ] Mọi lỗi từ backend hiển thị qua map `code`, không lộ message kỹ thuật.
- [ ] Không tự tính tiền gửi lên server; không tin dữ liệu phía client.
- [ ] Nút hành động có trạng thái loading và không bấm đúp được.
- [ ] Hành động nguy hiểm có modal xác nhận.
- [ ] Có skeleton, trạng thái rỗng, trạng thái lỗi.
- [ ] Dùng đúng token màu và spacing 4px/8px; hỗ trợ `prefers-reduced-motion`.
- [ ] Kiểm tra hiển thị trên mobile.
- [ ] `npm run lint` và `npm run build` đều qua.

## 9. Mẫu prompt cho từng phần

- **Trang chi tiết sự kiện:** "Dựng `EventDetail` theo mục 5. Có countdown, banner glassmorphism, nút Mua vé ngay."
- **Chọn khu, ghế và checkout:** "Làm luồng chọn khu VIP/PREMIUM/STANDARD trên màn 3D, chọn ghế trên sơ đồ và checkout theo mục 4 và 6. Xử lý `SEAT_TAKEN` và toast khi giữ ghế thành công."
- **Dashboard Ban tổ chức:** "Tạo trang doanh thu và danh sách người mua vé theo mục 7. Hiển thị tốt trên cả Desktop và Mobile."
- **Tự kiểm tra:** "Audit lại file này theo mục 8 và sửa các chỗ spacing chưa chuẩn."
