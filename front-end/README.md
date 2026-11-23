# EventHub · Frontend trải nghiệm UI/UX

Frontend tương tác bằng dữ liệu giả lập, chạy độc lập với backend. Các thao tác cập nhật cùng một bộ dữ liệu và được lưu qua lần tải trang bằng `localStorage`.

## Chạy tại máy

```powershell
cd D:\Code\EventHub\front-end
npm ci
npm run dev
```

Mở http://127.0.0.1:3000. Cần Node.js 20.9+ và npm. Lần build đầu cần kết nối mạng để Next tải font. Ảnh mẫu nằm sẵn trong `public/images`.

```powershell
npm run lint
npm run build
npm start
```

`npm start` phục vụ bản production đã build. Dừng dev server trước nếu cùng dùng cổng 3000.

## Cách thử nhanh

Mặc định dùng tài khoản khách hàng Nguyễn Minh Anh. Bấm **Trải nghiệm · DEMO** ở góc dưới để đổi giữa Khách hàng, Ban tổ chức và Quản trị viên. Bảng này cũng cho thử skeleton, trạng thái rỗng, lỗi và khôi phục toàn bộ dữ liệu mẫu.

1. Vào một sự kiện, chọn VIP / PREMIUM / STANDARD, điền người sử dụng từng vé và giữ vé. Đồng hồ đếm 10 phút bắt đầu khi tạo đơn.
2. Thử thanh toán thất bại, sau đó thanh toán lại. Thành công sẽ tạo vé, cập nhật doanh thu và thông báo. Có thể tải lại trang hoặc quay lại từ Đơn hàng khi đơn còn hạn.
3. Trong Đơn hàng, gửi yêu cầu hoàn tiền. Đổi sang Admin → Yêu cầu hoàn tiền → duyệt. Toàn bộ vé của đơn bị hủy, hoàn 70% giá mua, trả lại tồn kho và tạo thông báo cho khách.
4. Đổi sang Ban tổ chức → Kiểm soát vé. Dùng `EH-T001` hoặc `EH-T002` để check-in rồi check-out. Vé không được vào lại; số người đang có mặt cập nhật trên dashboard.
5. Ban tổ chức tạo bản nháp → gửi duyệt. Admin xem hồ sơ → duyệt hoặc từ chối kèm lý do. Ban tổ chức mở bán sự kiện đã duyệt; sự kiện xuất hiện ở Khám phá. Sự kiện bị từ chối cần sửa và lưu lại thành nháp.
6. Xem báo cáo theo ngày/hạng vé, lọc sự kiện, khoảng thời gian và tải CSV. Doanh thu gộp ghi theo `paidAt`; khoản hoàn ghi theo `reviewedAt` của bản mô phỏng, tương ứng `refundedAt` khi tích hợp thật. Hoàn tiền không xóa lịch sử bán vé.

Mọi thao tác thử nghiệm trong trình duyệt kiểm thử riêng không làm thay đổi dữ liệu mẫu khi bạn mở trình duyệt của mình.

## Các màn hình

| Khu vực     | Đường dẫn                                                          | Thao tác                                                              |
| ----------- | ------------------------------------------------------------------ | --------------------------------------------------------------------- |
| Khách       | `/`, `/explore`, `/events/:id`, `/favorites`                       | Tìm, lọc, sắp xếp, lưu sự kiện, chọn vé                               |
| Tài khoản   | `/login`, `/register`, `/forgot-password`, `/profile`              | Đăng nhập/đăng ký giả lập, đổi tên, kiểm tra form đổi mật khẩu        |
| Mua vé      | `/checkout`, `/orders`, `/tickets`                                 | Giữ vé, thanh toán, hủy giữ chỗ, xem/lưu thông tin vé, yêu cầu hoàn   |
| Thông báo   | `/notifications`                                                   | Xem, lọc chưa đọc, đánh dấu đã đọc                                    |
| Ban tổ chức | `/organizer`, `/organizer/events`, `/organizer/events/new`         | Dashboard, tạo/sửa nháp, gửi duyệt, mở/đóng bán                       |
| Vận hành    | `/organizer/attendees`, `/organizer/checkin`, `/organizer/reports` | Người tham dự, xuất CSV, kiểm soát vé, báo cáo                        |
| Admin       | `/admin`, `/admin/events`, `/admin/refunds`                        | Tổng quan, xét duyệt sự kiện và hoàn tiền                             |
| Quản trị    | `/admin/users`, `/admin/categories`, `/admin/templates`            | Khóa/mở khóa, chuyển vai trò, tạo/đổi tên danh mục, sửa/xem trước mẫu |
| Trợ giúp    | `/help`                                                            | Hướng dẫn và chính sách áp dụng cho prototype                         |

Tài khoản mẫu: `minhanh@example.com` (Customer), `nam@example.com` (Organizer), `linh@example.com` (Admin). Form đăng nhập chấp nhận chuỗi thử nghiệm từ 8 ký tự; không xác thực, lưu hoặc gửi mật khẩu. Tài khoản bị khóa không đăng nhập được. Form đăng ký chỉ lưu tên/email/role của người dùng giả lập.

## Phạm vi và nguồn yêu cầu

- Đã đọc `AGENTS.md` trong thư mục này. Hạng vé **VIP / PREMIUM / STANDARD** dùng theo xác nhận trực tiếp của chủ dự án, thay cho A–D trong hướng dẫn.
- Nghiệp vụ đối chiếu từ [source-decisions](../docs/architecture/source-decisions.md), [project-clarifications](../docs/project-clarifications.md) và [hợp đồng API](../docs/api/README.md). Đây là các tài liệu đã tổng hợp SRS BC_32_V2 trong repo; file SRS gốc không có trong workspace ở lượt dựng prototype này.
- Guard frontend phục vụ trải nghiệm vai trò. Không triển khai bảo mật thật, Keycloak/JWT, refresh token, API Gateway, WebSocket, thanh toán, SMTP hoặc quét camera.
- QR là biểu tượng **DEMO**, mã vé là mã thử nội bộ. Không sinh token QR thật; nút lưu vé tải tệp thông tin `.txt` ghi rõ mô phỏng.
- Form quên/đổi mật khẩu chỉ mô phỏng kết quả. Mẫu thông báo được chỉnh sửa/lưu/xem trước, chưa dùng làm bộ dựng thông báo tự động. Không email nào được gửi.
- Vé thuộc đơn đang chờ xét hoàn tạm không được quét trong mô phỏng; khi tích hợp cần dùng trạng thái giữ quyền hủy của backend theo thiết kế phục hồi hoàn tiền.
- Địa điểm, ảnh, lịch trình và sự kiện là dữ liệu minh họa. Ngày mẫu tháng 11/2026; tạo sự kiện mới bằng ngày trong tương lai nếu thử sau thời điểm đó. Đóng bán không làm mất hiệu lực vé đã phát hành.
- Không xóa cứng tài khoản/sự kiện/danh mục. Pagination, filter và CSV chạy tại máy, sẽ chuyển sang dữ liệu server khi tích hợp.
- Dữ liệu giả lập dùng chung trong một tab, khóa `eventhub-ui-demo-v1`. Dùng bảng Trải nghiệm để reset. Không bảo đảm đồng bộ nhiều tab hoặc nhiều thiết bị.

## Cấu trúc

```text
src/app/                    App Router, layout, giao diện responsive
src/components/ui/          shadcn/ui primitives
src/components/features/    Trang và component nghiệp vụ
src/lib/mock/seed.ts         Bộ dữ liệu ban đầu
src/lib/mock/store.ts        Lưu và khôi phục dữ liệu trải nghiệm
src/lib/mock/engine.ts       Quy tắc trạng thái, tồn kho và thao tác giả lập
src/lib/mock/analytics.ts    Tổng hợp theo ngày/hạng vé
src/lib/api/errors.ts        Map mã lỗi sang tiếng Việt
src/hooks/                  Store subscription, chống bấm đúp, thời gian
src/schemas/                Zod + React Hook Form
src/types/                  Kiểu dữ liệu của prototype
```

Các kiểu trong `types` là view model của prototype, không phải DTO đã map backend. TanStack Query đã có provider; chưa tạo query hoặc API client. Không hardcode địa chỉ backend. Khi tích hợp mới thêm một client chung đọc `NEXT_PUBLIC_API_BASE_URL` theo hợp đồng được xác nhận.

Thiết kế: cinematic tối, Electric Purple + Amber; Be Vietnam Pro và Space Grotesk; ảnh sân khấu thật. Trang khám phá ưu tiên hình ảnh (variance 7, motion 5, density 4); dashboard ưu tiên dữ liệu (density 7). Tôn trọng reduced motion, có focus bàn phím và modal xác nhận cho thao tác ảnh hưởng dữ liệu.

## Kiểm chứng

`npm run lint` và `npm run build` là hai kiểm tra bắt buộc. Các hành trình đã được kiểm tra bằng Playwright trên Edge headless, gồm mua/hoàn vé, duyệt/mở bán, check-in/out, hết hạn/hủy giữ vé, quản trị, guard vai trò, reload và layout mobile. Công cụ kiểm tra/screenshot đặt trong `.qa/` được gitignore; không thêm Playwright vào dependency ứng dụng.

Ảnh minh họa tải từ Unsplash, lưu tại `public/images`: `photo-1470229722913-7c0e2dbbafd3`, `photo-1516280440614-37939bbacd81`, `photo-1579783902614-a3fb3927b6a5`, `photo-1459749411175-04bf5292ceea`, `photo-1515187029135-18ee286d815b`, `photo-1510915361894-db8b60106cb1`.
