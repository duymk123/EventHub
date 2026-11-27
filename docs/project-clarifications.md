# Quyết định nghiệp vụ và các phần còn cần hoàn thiện

Cập nhật 2026-11-05 theo xác nhận mới nhất của chủ dự án; bổ sung C10 (thống kê toàn hệ thống cho ADMIN) ngày 2026-11-13, C11 (đặt vé qua hàng đợi Kafka) ngày 2026-11-21, được thay bằng C12 (đặt vé theo ghế cụ thể) ngày 2026-11-22. Cùng ngày chốt thêm C03 (địa điểm), C05 rút gọn, C06 (VNPay sandbox), C07 (quét vé) và C13–C15 (token, Idempotency-Key, Flyway và phạm vi giai đoạn này). Ngày 2026-11-23 chốt C16–C22 (giữ ghế 10 phút, giờ ngừng bán, thanh toán lại, hủy đơn, một đơn chờ mỗi sự kiện, đối soát ADMIN, hủy sự kiện để sau). Các câu hỏi C02–C05/C08 trước đây được thay bằng quyết định bên dưới; C06 và C07 đã chốt ngày 2026-11-22. [Nguồn và thứ tự ưu tiên](architecture/source-decisions.md).

Đã chốt từ trước: UUID cho mọi PK/tham chiếu ứng dụng và gateId; VIP/PREMIUM/STANDARD; messageId khác eventId; Keycloak cấp token, Auth giữ UI/API riêng; lỗi REST có code/message/timestamp/path. C01 đã đóng về lựa chọn Auth facade. Realm/client, sub/role mapper, issuer/audience/JWK và S2S là cấu hình tích hợp còn cần hoàn thiện.

## Bảng quyết định hiện hành

| Mã | Trạng thái và nguồn | Quyết định áp dụng |
|---|---|---|
| C02 | Chủ dự án chốt | Snapshot email người mua đã xác minh lúc tạo đơn. Owner vé là người mua; holder là người sử dụng. Email vé gửi tới holderEmail nếu có; thiếu thì dùng customerEmail đã snapshot. Gom vé cùng địa chỉ thành một email; IN_APP cho người mua. |
| C03 | Chủ dự án chốt ngày 2026-11-22 | Một địa điểm chung cho mọi sự kiện: **141 Đường Chiến Thắng, Hà Nội**. Text này là cấu hình `EVENTHUB_VENUE_TEXT` của Notification, in trong email vé; frontend hiển thị cùng text. Chưa cần bảng địa điểm hoặc venue trên Event; ghế theo zone sinh riêng cho từng sự kiện ở Ticket (C12). |
| C04 | Chủ dự án chọn cả đơn và 70%; quyền/phí là phương án Codex chọn | Khách hủy cả đơn: hoàn 70% giá đã mua, giữ lại 30%, không cộng thêm phí. Chỉ ADMIN xem/duyệt/từ chối refund; CUSTOMER yêu cầu cho đơn mình, ORGANIZER chưa có quyền duyệt. Giữ điều kiện PAID, mọi vé chưa dùng, còn ít nhất 48 giờ lúc yêu cầu và duyệt. |
| C05 | Chủ dự án chốt **bản rút gọn** ngày 2026-11-22 | ADMIN duyệt → Ticket hủy vé và trả ghế → Order gọi VNPay hoàn 70% → phát `order.refunded`. Xác minh khi kết quả tiền chưa rõ, giữ quyền hủy (hold/fence) và hoàn bù 100% khi thu tiền mà không phát được vé là **hướng phát triển** ghi trong báo cáo; [thiết kế đầy đủ](architecture/payment-refund-recovery.md) giữ làm tham khảo. |
| C06 | Chủ dự án chọn ngày 2026-11-22 | **VNPay sandbox** (VND, HMAC-SHA512). Thanh toán bằng URL redirect, kết quả chính thức qua IPN; API `querydr` để đối soát khi chưa nhận IPN; API `refund` để hoàn 70%. Chi tiết ở mục C06 bên dưới. |
| C07 | Chủ dự án chốt ngày 2026-11-22 | Không làm bảng thiết bị hay quầy cổng. ORGANIZER chủ sự kiện và ADMIN quét QR bằng trang web dùng camera, đăng nhập bằng tài khoản của mình. Chỉ quét được trong khung giờ sự kiện: từ 60 phút trước `startTime` (mở cửa) đến `endTime`; ngoài khung trả 409 `SCAN_OUTSIDE_EVENT_TIME`. `gateId` vẫn là UUID chọn trên trang quét. |
| C08 | Chủ dự án chốt quota 0/cấu hình sau tạo Event; giao Codex chọn publish | Tạo Event trước rồi cấu hình Ticket, được để hạng quota 0. Submit không đòi đủ ba hạng. Publish cần owner, APPROVED, thời gian hợp lệ và ít nhất một hạng ACTIVE có total_quota > 0; không buộc cả ba hạng đều dương. |
| C09 | Giữ xác nhận trước đó ngày 2026-11-05 | Dashboard tổng gộp doanh thu/vé bán cả sự kiện; sales tách gộp, hoàn, thuần. Ngày Asia/Ho_Chi_Minh theo paidAt/refundedAt, giữ lịch sử qua các ngày. Không mở lại quyết định này. |
| C10 | Chủ dự án duyệt ý tưởng 2026-11-13; định nghĩa số liệu do Claude đề xuất; analytics-service triển khai 2026-11-14 | Thêm thống kê gộp cho ADMIN (mọi sự kiện) và ORGANIZER (sự kiện của mình, bổ sung 2026-11-14): doanh thu gộp/hoàn/thuần, vé bán, khán giả theo ngày/tháng và theo từng sự kiện. Khán giả = số vé đã check-in (mỗi vé một lần, theo ngày check-in). Giữ nguyên C09. Còn thiếu API nội bộ ở Event/Ticket; xem mục C10 bên dưới. |

| C11 | Chủ dự án chọn Kafka ngày 2026-11-21; **đã thay bằng C12 ngày 2026-11-22** | Giữ chỗ qua hàng đợi Kafka. Bỏ khi chuyển sang bán theo ghế; xem mục C11 bên dưới. |
| C12 | Chủ dự án chốt ngày 2026-11-22; chi tiết do Claude thiết kế | Bán vé theo ghế cụ thể: zone = hạng vé, organizer nhập số hàng × số ghế mỗi hàng cho từng sự kiện, khách chọn ghế trên sơ đồ, Ticket giữ ghế đồng bộ (409 SEAT_TAKEN khi trùng). Giao diện chọn zone dạng 3D, sân khấu một đầu. Xem mục C12 bên dưới. |
| C13 | Chủ dự án chốt ngày 2026-11-22 | Lưu token ở frontend: access token chỉ trong bộ nhớ; refresh token nằm trong cookie `HttpOnly` do Auth facade đặt (`SameSite=Strict`, `Path=/api/v1/auth`). Không dùng `localStorage`/`sessionStorage`. Thời hạn lấy theo realm Keycloak (access 5 phút, phiên refresh 7 ngày, xoay vòng refresh token). |
| C14 | Chủ dự án chốt ngày 2026-11-22 | `POST /orders` nhận header `Idempotency-Key`: gửi lại cùng key và cùng nội dung trả lại đúng đơn cũ; cùng key khác nội dung trả 409 `IDEMPOTENCY_KEY_REUSED`. |
| C15 | Chủ dự án chốt ngày 2026-11-22 | Phạm vi giai đoạn này: Ticket và Order chuyển sang Flyway **bật mặc định** + `ddl-auto: validate` (xóa DB dev vì chỉ có dữ liệu thử; Notification/Analytics đã có file Flyway nhưng local vẫn tắt, giữ nguyên); frontend giữ dữ liệu mock để demo UI; API Gateway cấu hình sau khi các service xong API; Redis không dùng, báo cáo ghi đúng thực tế là giữ ghế bằng MySQL. |
| C16 | Chủ dự án chốt ngày 2026-11-23 | Giữ ghế và hạn thanh toán **10 phút** (trước là 3 phút), đủ để khách nhập thẻ và OTP trên VNPay. `vnp_ExpireDate` bằng hạn của đơn. Khác BC_32_V2 ("giữ vé 3 phút"), báo cáo cần sửa theo. |
| C17 | Chủ dự án chốt ngày 2026-11-23 | Ngừng bán khi tới `startTime` của sự kiện, hoặc sớm hơn nếu organizer chuyển CLOSED. Ticket từ chối giữ ghế với 409 `TICKET_TYPE_NOT_ON_SALE`. |
| C18 | Chủ dự án chốt ngày 2026-11-23 | Khách hủy hoặc thanh toán lỗi trên VNPay: chỉ payment chuyển FAILED, đơn vẫn PENDING_PAYMENT và giữ ghế đến hết hạn. Khách bấm "Thanh toán lại" (Idempotency-Key mới → payment và `vnp_TxnRef` mới). Không còn phát `order.failed` lý do PAYMENT_FAILED. |
| C19 | Claude đề xuất kèm C20, áp dụng ngày 2026-11-23 | Khách tự hủy đơn chưa thanh toán: `POST /orders/{id}/cancel` → CANCELLED, trả ghế ngay. Không phát Kafka (khách tự làm nên không cần email). |
| C20 | Chủ dự án chốt ngày 2026-11-23 | Mỗi khách chỉ một đơn PENDING_PAYMENT cho mỗi sự kiện; tạo thêm trả 409 `PENDING_ORDER_EXISTS`. Chốt bằng UNIQUE trên cột sinh `pending_slot` của `orders`, không chỉ kiểm trong code. |
| C21 | Chủ dự án chốt ngày 2026-11-23 | ADMIN xem `GET /admin/payments/reconciliation`: payment VNPay SUCCEEDED nhưng không phải payment làm đơn PAID (`orders.payment_id`), tức tiền về khi đơn đã hết hạn/hủy hoặc khách trả hai lần. ADMIN hoàn tiền thủ công trên cổng VNPay. |
| C22 | Chủ dự án chốt ngày 2026-11-23 | Hủy cả sự kiện (trạng thái CANCELLED và hoàn 100% mọi đơn) **không làm giai đoạn này**, ghi vào báo cáo là hướng phát triển. |
| C23 | Chủ dự án chốt ngày 2026-11-23 | Hoàn tiền: ADMIN duyệt khi đã dưới 48 giờ trước sự kiện thì yêu cầu **tự chuyển REJECTED** (giữ C04); khách **không** tự rút yêu cầu hoàn; khoản đối soát C21 có thao tác "đã xử lý" (`POST /admin/payments/{id}/resolve` kèm ghi chú). Mốc 48 giờ dùng giờ bắt đầu Order lưu lúc giữ ghế, không cần API refund-eligibility. |

Các mục “Codex chọn” ghi rõ nguồn thiết kế, không phải lời xác nhận nguyên văn của chủ dự án. Đây là quyết định tài liệu; migration, adapter và API mới vẫn cần triển khai.

## C02 — Danh tính, email và người nhận vé

- customer_id lấy từ Principal đã xác minh. Dự kiến orders.customer_email lưu email đã xác minh lúc tạo đơn; cột này chưa có trong DDL Order, cần migration khi triển khai. Không lấy email người mua tùy ý từ body.
- Email xác minh lấy qua claim tin cậy hoặc adapter identity theo cấu hình Keycloak. Chốt yêu cầu xác minh không có nghĩa mọi claim/adapter đã có.
- tickets.owner_user_id = orders.customer_id. Holder có email không tự có quyền GET /tickets/me hoặc lấy QR bằng tài khoản khác.
- Order chuyển snapshot email người mua và holder qua confirm; Ticket giữ trong outbox ticket.issued để retry không tra email hiện tại.
- Mỗi vé gửi holderEmail nếu có và hợp lệ; thiếu thì dùng buyer snapshot. Gom cùng địa chỉ; mỗi địa chỉ chỉ nhận QR các vé gửi cho họ. IN_APP thuộc người mua, không tạo tài khoản holder suy từ email.
- Cập nhật này thay chính sách gom mọi QR về buyer. Notification đã có code nhiều người nhận ngày 2026-11-23 (một email cho mỗi địa chỉ, IN_APP cho người mua).

## C03 — Một địa điểm dùng chung

Phương án cấu hình dự kiến `EVENTHUB_VENUE_TEXT` tại Notification. Khi tạo batch, snapshot text vào model nội dung cùng QR; retry dùng nội dung đã lưu dù config đổi. `venue` nullable trong message cũ giữ để tương thích; nguồn hiển thị chuẩn giai đoạn một là text chung đã cấu hình. Thiếu text thật thì báo lỗi cấu hình, không gửi địa chỉ trống hoặc giả.

Chưa cần đổi EventRequest/Entity để mỗi sự kiện chọn địa điểm. Có nhiều địa điểm sau này sẽ mở yêu cầu schema/API riêng.

## C04 — Cách tính hoàn 70%

Giá gốc lấy từ tổng order_items.line_total = quantity × unit_price đã snapshot, không lấy giá Ticket hiện tại. Trong phạm vi không phí/voucher, tổng dòng phải khớp khoản thanh toán của đơn.

`refundAmount = round(sum(line_total) * 0.70, 2, HALF_UP)` bằng BigDecimal. Đơn 1.000.000 đồng hoàn 700.000 đồng, giữ lại 300.000 đồng. Hoàn cả đơn nghĩa hủy tất cả vé, không phải chỉ 70% số vé.

Phân bổ tiền theo 70% line_total từng hạng; phần lẻ đơn vị 0,01 xử lý theo phần dư lớn nhất, hòa thì ticketTypeId tăng dần. Tổng items[].refundedAmount phải bằng amount. Không chia đều giữa các hạng khác giá; lưu quote/phân bổ cố định để retry không đổi tiền.

Đơn miễn phí/quote bằng 0 không tạo lệnh chuyển tiền hoàn dương; hủy đơn miễn phí cần quy tắc riêng. Provider C06 phải kiểm tra đơn vị tiền hỗ trợ, không âm thầm đổi số tiền đã chốt.

## C05 — Hướng xử lý lỗi đã chọn (thiết kế đầy đủ, hướng phát triển)

> Từ 2026-11-22 phạm vi triển khai là [C05 rút gọn](#c05-rút-gọn--luồng-hoàn-tiền-giai-đoạn-này). Các điểm dưới đây giữ làm thiết kế đầy đủ cho phần "hướng phát triển" của báo cáo.

- Khách hủy sau phát vé: 70%, Ticket chuẩn bị giữ quyền hủy toàn bộ vé trước khi Order gọi provider.
- Provider xác nhận hoàn: Ticket commit hủy theo quyền đã giữ; đi qua mốc 48 giờ trong lúc chờ không làm mất quyền đó. Chỉ phát order.refunded khi tiền và hủy đều hoàn tất.
- Provider xác nhận chắc chắn không hoàn: release hold, vé vẫn hiệu lực, đơn vẫn PAID. Timeout/chưa rõ: giữ hold và đối soát, không mở vé hoặc gọi lần hai bằng key mới.
- Đã thu tiền nhưng reservation hết hạn/phát vé thất bại: xác minh và fence luồng phát vé trước, hoàn bù 100% khoản thực thu chưa hoàn. Không mở lại giữ chỗ hoặc bỏ kiểm tra quota.
- Lỗi không tự sửa được vào danh sách ADMIN đối soát, giữ trạng thái/bằng chứng; không tự thu tiền lại hoặc ghi thành công để bỏ lỗi.

[Thiết kế chi tiết, trạng thái, hợp đồng nội bộ và storage cần bổ sung](architecture/payment-refund-recovery.md).

## C06 — VNPay sandbox

Chủ dự án chọn ngày 2026-11-22. Thông số kỹ thuật dưới đây do Claude đề xuất theo tài liệu tích hợp VNPay; tài khoản sandbox (`vnp_TmnCode`, `vnp_HashSecret`) do nhóm tự đăng ký, không ghi vào repo.

| Bước | Cách làm |
|---|---|
| Khởi tạo thanh toán | `POST /orders/{id}/payment`: Order tạo payment PENDING, dựng URL `vpcpay.html` với `vnp_Amount` = tiền × 100, `vnp_TxnRef` = mã payment (32 ký tự hex, duy nhất), `vnp_ExpireDate` = hạn giữ ghế của đơn, ký HMAC-SHA512 trên tham số đã sắp xếp. Frontend chuyển khách sang trang VNPay. |
| Kết quả chính thức (IPN) | VNPay gọi `GET /api/v1/payments/vnpay/ipn` với query `vnp_*`. Order kiểm chữ ký, tìm payment theo `vnp_TxnRef`, so số tiền, chống xử lý lặp, rồi trả JSON `{"RspCode","Message"}` theo mã VNPay (00 thành công, 01 không thấy đơn, 02 đã xử lý, 04 sai số tiền, 97 sai chữ ký, 99 lỗi khác). Thành công khi `vnp_ResponseCode = 00` và `vnp_TransactionStatus = 00`. |
| Trang quay về | VNPay chuyển khách về `returnUrl` của frontend. Trang này chỉ để hiển thị, không quyết định kết quả; frontend gọi `POST /orders/{id}/payment/verify` để Order hỏi lại VNPay bằng `querydr` (cần khi chạy local không có IPN, hoặc IPN tới chậm). |
| Sau khi PAID | Như thiết kế cũ: Order gọi Ticket confirm (ghế HELD → SOLD, phát vé), ghi outbox `order.paid`. `orders.payment_id` lưu payment đã làm đơn PAID. |
| Thanh toán thất bại hoặc khách hủy trên VNPay | C18: payment FAILED, đơn vẫn chờ thanh toán đến hết hạn giữ ghế; khách thanh toán lại bằng payment mới. |
| Tiền về sau khi đơn hết hạn/hủy, hoặc trả hai lần | Ghi nhận payment SUCCEEDED, đơn giữ nguyên trạng thái và payment vào danh sách đối soát của ADMIN (C21) để hoàn tiền thủ công. Hoàn bù tự động là hướng phát triển (C05). |
| Hoàn tiền 70% | API `refund` của VNPay (`vnp_TransactionType = 03` hoàn một phần) với `vnp_TxnRef`, `vnp_TransactionNo`, `vnp_TransactionDate` của giao dịch gốc và `vnp_RequestId` mới mỗi lần gọi. |
| Chạy local | IPN cần URL public: dùng tunnel (ngrok, cloudflared) trỏ vào Order, hoặc bỏ qua IPN và dựa vào `verify` (`querydr`). Cấu hình qua biến môi trường `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_API_URL`, `VNPAY_RETURN_URL`. |

## C05 rút gọn — Luồng hoàn tiền giai đoạn này

1. CUSTOMER yêu cầu hoàn cả đơn: đơn PAID, mọi vé chưa dùng, còn ít nhất 48 giờ trước sự kiện. Refund REQUESTED, số tiền = 70% tổng `line_total` (C04).
2. ADMIN duyệt: Order kiểm lại 48 giờ, chuyển refund sang PROCESSING.
3. Order gọi Ticket `POST /internal/tickets/cancel-by-order/{orderId}`: hủy mọi vé (ISSUED → CANCELLED), trả ghế SOLD → AVAILABLE, outbox `ticket.cancelled`. Có vé đã dùng thì Ticket trả 409 và refund chuyển FAILED, không gọi VNPay.
4. Order gọi VNPay `refund` 70%. Thành công: refund REFUNDED, đơn REFUNDED, outbox `order.refunded` (Notification gửi email, Analytics ghi khoản hoàn).
5. VNPay trả lỗi hoặc timeout: refund FAILED kèm lý do; vé đã hủy không mở lại. ADMIN bấm thử lại sau khi kiểm tra giao dịch trên cổng merchant VNPay.

**Hướng phát triển (ghi vào báo cáo):** xác minh bằng `querydr` khi kết quả hoàn tiền chưa rõ trước khi cho thử lại; giữ quyền hủy (hold) trước khi gọi cổng thanh toán và chỉ hủy vé khi tiền đã hoàn; task bền vững thay cho gọi đồng bộ trong request ADMIN; hoàn bù 100% và message `payment.compensated` khi thu tiền nhưng không phát được vé.

## C08 — Điều kiện publish đã chọn

Tạo Event và submit không phụ thuộc đủ ba hạng. Trước publish, Event gọi API nội bộ Ticket đọc cấu hình; cần ít nhất một hạng ACTIVE có total_quota > 0, sau đó recheck owner/trạng thái và CAS APPROVED → PUBLISHED. Không giữ khóa DB Event lúc chờ HTTP. Ticket không phản hồi: 503 và chưa publish; chưa đủ cấu hình: 409 TICKET_CONFIGURATION_NOT_READY.

Đây là kiểm tra tại lúc chuẩn bị publish, không phải transaction hai DB hoặc cam kết luôn còn hàng. Sau publish có thể hết vé/tắt bán hạng; giữ ghế vẫn kiểm tra Event PUBLISHED, hạng ACTIVE và từng ghế còn AVAILABLE tại lúc đặt, cập nhật nguyên tử (C12). Thay đổi đồng thời không cho phép oversell.

## C10 — Thống kê toàn hệ thống cho ADMIN

Bối cảnh: trang Phân tích (admin) và báo cáo của Ban tổ chức cần số liệu gộp nhiều sự kiện cùng lúc; API Analytics hiện chỉ đọc từng sự kiện. Chủ dự án đồng ý hướng bổ sung API ngày 2026-11-13. Các định nghĩa dưới đây là đề xuất của Claude, áp dụng khi chưa có chỉ đạo khác.

| Câu hỏi | Quyết định áp dụng |
|---|---|
| Doanh thu, hoàn tiền ghi vào kỳ nào? | Giữ C09: doanh thu theo ngày VN của paidAt, hoàn tiền theo ngày VN của refundedAt. Doanh thu thuần của một kỳ = gộp − hoàn, có thể âm. |
| "Khán giả tham gia" là gì? | Số **vé** đã check-in hợp lệ, mỗi vé tính một lần, ghi vào ngày VN của lần check-in đầu tiên; check-out không trừ. Không phải số người/tài khoản khác nhau. |
| Tỉ lệ check-in tính thế nào? | Khán giả toàn thời gian / vé hợp lệ (bán − hoàn) toàn thời gian của sự kiện, chỉ hiển thị khi sự kiện đã diễn ra. Không chia theo khoảng lọc để tránh kết quả >100%. |
| "Theo tháng" là tháng nào? | Tháng dương lịch theo giờ Việt Nam; tháng đầu/cuối của khoảng lọc chỉ tính phần ngày nằm trong khoảng. |
| So sánh "kỳ trước"? | Khoảng liền trước có cùng số ngày. |
| Ai được xem? | Cập nhật theo yêu cầu chủ dự án 2026-11-14: cả ADMIN và ORGANIZER đều dùng trang Phân tích. ADMIN xem toàn hệ thống và mọi sự kiện; ORGANIZER xem tổng và bảng xếp hạng **chỉ gồm sự kiện mình sở hữu**, và từng sự kiện của mình; CUSTOMER không được xem. Analytics lấy danh sách sự kiện của ORGANIZER qua Event INT-03, không tự lưu organizer. |
| Giới hạn khoảng thời gian? | Theo ngày tối đa 366 ngày; theo tháng tối đa 36 tháng. |

Sơ đồ dữ liệu: [ERD C10](database/analytics-service/erd-c10.md). Thiết kế API, query và migration: [plan Analytics §8](plans/analytics-service.md#8-thống-kê-toàn-hệ-thống-cho-admin-c10). Hợp đồng: [Analytics](api/analytics-service-openapi.yaml), INT-02 ở [Event](api/event-service-openapi.yaml), capacity ở [Ticket](api/ticket-service-openapi.yaml).

## C11 — Đặt vé qua hàng đợi Kafka (đã thay bằng C12)

Ngày 2026-11-21 chủ dự án chọn đưa giữ chỗ qua hàng đợi Kafka: `POST /orders` trả 202 với đơn RESERVING, Ticket xử lý lần lượt theo từng sự kiện (key eventId), kết quả quay về Order qua Kafka. Lý do là khi bán theo số lượng, mọi request cùng hạng vé tranh khóa **một dòng** `ticket_types`.

Ngày 2026-11-22 chủ dự án chốt bán vé theo **ghế cụ thể** (C12). Mỗi ghế là một dòng riêng nên hai người chỉ tranh nhau khi chọn trùng ghế, lý do của C11 không còn; hàng đợi lại làm khách chờ vài giây mới biết ghế đã bị lấy. Vì vậy giữ ghế quay về REST đồng bộ. Ba topic `order.reservation-requested`, `ticket.reserved`, `ticket.reservation-rejected`, các trạng thái RESERVING/REJECTED và các cột `reserve_deadline`, `reject_code` bị bỏ khỏi thiết kế; chưa có dòng code nào của C11 nên không cần gỡ code.

## C12 — Đặt vé theo ghế cụ thể

Chủ dự án chốt ngày 2026-11-22: khách tự chọn từng ghế, ghế chia theo ba zone trùng với ba hạng vé, organizer tự nhập số hàng và số ghế mỗi hàng cho từng sự kiện, giữ ghế đồng bộ, giao diện chọn zone dạng 3D với sân khấu một đầu. Các điểm còn lại do Claude đề xuất, áp dụng khi chưa có chỉ đạo khác.

| Câu hỏi | Quyết định áp dụng |
|---|---|
| Zone là gì? | Zone = hạng vé: VIP sát sân khấu, PREMIUM ở giữa, STANDARD phía sau. Vẫn chỉ ba hạng như BC_32_V2 (`ticket_types` "đã gộp chung cả thông tin khu vực"); C12 thêm ghế bên trong mỗi zone. |
| Ghế sinh ra thế nào? | Organizer nhập `rowCount` và `seatsPerRow` cho từng hạng của từng sự kiện; Ticket sinh ghế hàng A, B, C… ghế 1..N, nhãn dạng `B-07`. Quota = rowCount × seatsPerRow. Một địa điểm chung (C03) nhưng mỗi sự kiện có số ghế riêng, không cố định sơ đồ vật lý. Giới hạn 30 hàng × 60 ghế mỗi zone. |
| Sửa sơ đồ sau khi đã bán? | Được thêm hàng. Chỉ được bớt hàng cuối khi mọi ghế của hàng đó còn AVAILABLE. Đổi `seatsPerRow` chỉ khi chưa có ghế nào HELD/SOLD. C08 giữ nguyên: rowCount 0 nghĩa là zone chưa mở bán. |
| Khách chọn ghế ra sao? | Màn 3D chọn zone (camera bay tới zone, chỉ xem cả zone). Sau đó sơ đồ ghế 2D của zone đó, bấm chọn tối đa 10 ghế mỗi đơn, mỗi ghế một người dùng vé. Sơ đồ làm mới mỗi 3–5 giây; WebSocket để sau. |
| Giữ ghế thế nào? | Order gọi Ticket REST đồng bộ. Ticket chạy một UPDATE có điều kiện `status = 'AVAILABLE'` trên đúng các ghế được chọn và kiểm số dòng đổi được. Thiếu dòng nào thì rollback, trả 409 `SEAT_TAKEN` kèm danh sách ghế bị lấy. Giữ 10 phút (C16). |
| Còn bộ đếm tồn kho? | Bỏ `available/reserved/sold_quantity` khỏi `ticket_types` để không còn dòng nóng. Số ghế trống tính bằng COUNT trên `seats` theo trạng thái. |
| Hết hạn, hủy, hoàn tiền | Reservation hết hạn hoặc release: ghế HELD → AVAILABLE. Confirm: HELD → SOLD, vé có `seat_id`/`seatLabel`. Hoàn tiền hủy vé: SOLD → AVAILABLE, ghế bán lại được. |
| Kafka | Không có topic mới. `ticket.issued` thêm `seatId`, `seatLabel` cho từng vé để email QR in số ghế. Các topic thông báo và thống kê giữ nguyên. |
| Redis | Vẫn không cần: giữ ghế và hạn 10 phút (C16) nằm trong MySQL cùng transaction với reservation. |

Thiết kế đầy đủ: [plan Ticket](plans/ticket-service.md), [plan Order](plans/order-service.md), [OpenAPI Ticket](api/ticket-service-openapi.yaml), [OpenAPI Order](api/order-service-openapi.yaml), [DDL Ticket](database/ticket-service/init.sql), [hợp đồng Kafka](kafka/events.md).

## Những việc còn cần trước khi tích hợp

Mọi câu hỏi nghiệp vụ C02–C23 đã có quyết định. Order và Ticket đã có code đủ (2026-11-23): Flyway (C15), bán theo ghế (C12), VNPay (C06), hoàn tiền rút gọn (C04/C05), Idempotency-Key (C14), khung giờ quét (C07), C16–C23. Cookie refresh token (C13) đã làm ở Auth (2026-11-24); API Gateway đã cấu hình và frontend đã nối API thật qua Gateway (2026-11-24, bỏ dữ liệu mock C15). Thứ tự và chi tiết ở các plan trong `docs/plans`.

Notification/Analytics đã có consumer/projection/batch/lease và attendance_state trong code hiện tại. Checklist kiểm tra dùng chạy thử thủ công theo quy ước repo; không tạo lại file/profile/dependency test tự động.
