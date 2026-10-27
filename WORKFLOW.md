# Workflow phát triển EventHub

Cập nhật 2026-10-27. Chủ dự án yêu cầu **sửa trực tiếp trên main cho lượt đồng bộ tài liệu này**. Yêu cầu này thay thế hướng dẫn tạo nhánh ở kế hoạch cũ cho công việc hiện tại; không tự bao gồm commit/push/merge hay thay đổi branch protection.

Hai hướng dẫn cũ mâu thuẫn giữa nhánh theo thay đổi và nhánh cá nhân lâu dài. Báo cáo BC_32_V2 không quy định Git. Chính sách nhánh lâu dài của nhóm chưa được suy ra từ yêu cầu sửa main; trước công việc tiếp theo, theo yêu cầu của chủ dự án và quy tắc bảo vệ repository đang có. [Hướng dẫn thao tác](huong-dan-push-code.md) dùng cùng quy tắc với file này.

## Trước khi sửa

1. Kiểm tra git status và nhánh hiện tại; giữ nguyên công việc chưa commit của người khác.
2. Đọc [quyết định nguồn](docs/architecture/source-decisions.md), [câu hỏi còn mở](docs/project-clarifications.md), SKILL.md, services.md và hợp đồng API/Kafka/schema liên quan.
3. Phân biệt yêu cầu đã xác nhận, phần lấy từ báo cáo và đề xuất kỹ thuật. Không coi mô tả cũ là chức năng đã có trong mã nguồn.

## Thực hiện và kiểm tra

Code theo Controller → Service interface → ServiceImpl → Repository. Migration và thay đổi hợp đồng đi cùng thay đổi nghiệp vụ. Không FK/join xuyên service. Mỗi PR/thay đổi tập trung vào một mục có thể kiểm tra.

Tài liệu: parse OpenAPI, kiểm tra tham chiếu schema, ID, liên kết và diff. Code: build và chạy thử phù hợp; luồng tiền/vé cần kiểm tra idempotency, CAS, cạnh tranh, crash/retry và bù trừ. Theo yêu cầu chủ dự án ngày 2026-10-28, không tạo file/profile/dependency test tự động; các tình huống kiểm tra trong plan được dùng làm checklist chạy thử thủ công.

## Chia sẻ thay đổi

Chỉ commit/push khi được yêu cầu. Không push thẳng main nếu repository chặn; đưa thay đổi vào PR khi cần theo quyền và chính sách thực tế. Không khẳng định chỉ nhóm trưởng được merge hoặc mọi thành viên được merge khi chưa kiểm tra quyền. Không force-push nhánh dùng chung và không commit .env/token/mật khẩu.

Commit theo mẫu feat(scope): ..., fix(scope): ..., docs(scope): ..., chore(scope): .... Mô tả PR nêu vấn đề, kết quả, kiểm tra và phụ thuộc còn mở. Việc tạo tài liệu không đồng nghĩa đã triển khai tính năng.
