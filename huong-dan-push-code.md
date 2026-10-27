# Hướng dẫn Git và gửi thay đổi

Nguồn quy trình chung là [WORKFLOW.md](WORKFLOW.md). Lượt đồng bộ tài liệu theo BC_32_V2 sửa trực tiếp trên main theo yêu cầu chủ dự án. Không còn hướng dẫn bắt buộc dùng dev/duy hoặc bắt buộc tạo nhánh riêng cho lượt này.

## Kiểm tra thay đổi local

~~~powershell
git branch --show-current
git status --short
git diff --stat
git diff --check
git diff
~~~

Chưa commit/push khi chỉ được yêu cầu sửa file. Khi có yêu cầu commit, chọn đúng file thay vì thêm toàn bộ workspace; không thêm .env, target hay secret.

~~~powershell
git add duong-dan-file
git commit -m "docs: align contracts with BC_32_V2"
~~~

## Nếu cần Pull Request

Nếu main được bảo vệ hoặc nhóm yêu cầu review, tạo nhánh chứa thay đổi đã kiểm tra rồi push và mở PR theo quyền được cấp. Đừng thay branch protection để vượt kiểm tra. Chính sách nhánh lâu dài cần nhóm xác nhận riêng.

~~~powershell
git switch -c docs/ten-thay-doi
git push -u origin docs/ten-thay-doi
gh pr create --base main --head docs/ten-thay-doi --title "docs: ten thay doi" --body-file pr-body.md
~~~

Tác giả sửa tiếp trên cùng nhánh khi có góp ý; người có quyền review/merge thực hiện theo quy tắc repository. Không chạy lệnh commit/push/merge từ hướng dẫn này nếu chưa có yêu cầu thực hiện.
