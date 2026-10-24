# Hướng dẫn push code và tạo Pull Request

Quy trình này dùng Git và GitHub CLI (`gh`) trong terminal. Mỗi thành viên giữ một nhánh cá nhân lâu dài; mọi thay đổi đi qua Pull Request (PR) từ nhánh cá nhân vào `main`.

## 1. Chuẩn bị một lần

Cài Git và GitHub CLI. Kiểm tra đã đăng nhập GitHub CLI:

```powershell
gh auth status
```

Nếu chưa đăng nhập, chạy:

```powershell
gh auth login
```

Các lệnh bên dưới chạy từ thư mục gốc của EventHub.

## 2. Tạo nhánh cá nhân lần đầu

Cập nhật `main`, rồi tạo nhánh cá nhân. Ví dụ bên dưới dùng `dev/duy`:

```powershell
git switch main
git pull origin main
git switch -c dev/duy
```

Thay `dev/duy` bằng nhánh riêng của bạn, ví dụ `dev/an` hoặc `dev/binh`. Tên nhánh phải duy nhất trên repo. Nhánh này theo bạn qua nhiều lần code và merge; tên chức năng như `auth-service` nên dùng làm tiêu đề PR, không dùng chung làm nhánh của nhiều người.

Kiểm tra đang đứng trên nhánh cá nhân:

```powershell
git branch --show-current
```

Không code trực tiếp trên `main`. Những lần làm việc sau, chuyển về nhánh cá nhân và lấy các thay đổi mới nhất từ `main` trước khi sửa:

```powershell
git switch dev/duy
git fetch origin
git merge origin/main
```

Thay `dev/duy` bằng tên nhánh cá nhân của bạn.

## 3. Stage và commit thay đổi

Sau khi code, xem những file đã sửa:

```powershell
git status
```

Thêm thay đổi vào commit. Có thể thêm tất cả file đã sửa:

```powershell
git add .
```

Hoặc chỉ thêm file muốn đưa lên:

```powershell
git add auth-service/src
```

Kiểm tra lại danh sách sẽ commit, rồi commit:

```powershell
git status
git commit -m "feat(auth): implement authentication endpoint"
```

Không đưa mật khẩu hoặc file `.env` vào Git. Repo đã cấu hình bỏ qua `.env`; vẫn kiểm tra `git status` trước khi commit.

## 4. Push nhánh lên GitHub

Push nhánh cá nhân lần đầu:

```powershell
git push -u origin dev/duy
```

Sau các commit tiếp theo trên cùng nhánh, chỉ cần:

```powershell
git add .
git commit -m "fix(auth): handle invalid credentials"
git push
```

## 5. Tạo Pull Request bằng terminal

Tạo PR từ nhánh đang làm vào `main`:

```powershell
gh pr create --base main --head dev/duy --title "feat(auth): implement authentication endpoint" --body "Mô tả thay đổi và cách kiểm tra."
```

Có thể bỏ `--title` và `--body`; `gh` sẽ hỏi nội dung PR ngay trong terminal. Thay `dev/duy` bằng nhánh của bạn. Nếu PR đang mở, push thêm commit sẽ cập nhật PR đó. Sau khi PR đã merge, lần thay đổi tiếp theo cần tạo một PR mới từ cùng nhánh.

`--base main` chỉ chọn `main` làm nhánh đích. Tạo PR hoặc approve PR chưa đưa code vào `main`; sau khi approve, người có quyền merge cần chạy lệnh ở mục 7.

Xem PR và các thay đổi bằng terminal:

```powershell
gh pr list --base main
gh pr view 12
gh pr diff 12
```

Thay `12` bằng số PR hiển thị trong `gh pr list`. Người tạo PR có thể tiếp tục sửa trên cùng nhánh; sau `git push`, PR đó tự cập nhật.

## 6. Người review: approve hoặc yêu cầu sửa

Xem danh sách PR, nội dung và diff:

```powershell
gh pr list --base main
gh pr view 12
gh pr diff 12
```

Đồng ý nội dung PR:

```powershell
gh pr review 12 --approve --body "Đã xem, đồng ý."
```

Yêu cầu người tạo sửa thêm:

```powershell
gh pr review 12 --request-changes --body "Vui lòng xử lý góp ý trong phần ..."
```

Nếu không nhận PR và muốn đóng hẳn:

```powershell
gh pr close 12
```

Yêu cầu sửa (`--request-changes`) giữ PR mở để tác giả cập nhật. Đóng PR kết thúc yêu cầu merge đó.

## 7. Merge PR vào `main`

**Approve không tự merge PR vào `main` theo mặc định.** Sau khi review đồng ý và các kiểm tra bắt buộc đã đạt, người có quyền merge chạy:

```powershell
gh pr merge 12 --merge
```

Không cần checkout sang `main` để chạy lệnh này: `gh` merge PR trực tiếp trên GitHub. Nhánh local của bạn không tự cập nhật; sau merge, làm bước 8 để đồng bộ nhánh cá nhân với `origin/main`.

Lệnh trên tạo merge commit và đưa thay đổi vào `main`, giữ lịch sử nhánh để tiếp tục dùng nhánh cá nhân. Nếu repo yêu cầu một approval hoặc CI checks, GitHub chỉ cho merge sau khi các điều kiện đó đạt. Người tạo PR không thể tự approve PR của chính mình.

Để giữ nhánh cá nhân sau khi merge, repo không được tự xóa head branch sau mỗi PR. Cách dùng nhánh lâu dài này phù hợp khi repo cho phép merge commit; nếu chỉ cho phép squash merge, nên tạo nhánh mới cho từng công việc.

Để bắt buộc cả nhóm gửi PR và không push thẳng vào `main`, chủ repo cần bật quy tắc bảo vệ nhánh `main` yêu cầu PR review. Nếu chưa bật quy tắc này, GitHub vẫn có thể cho phép push trực tiếp vào `main`.

## 8. Đồng bộ nhánh cá nhân sau khi merge

Sau khi PR đã merge, cập nhật nhánh cá nhân từ `main` để bắt đầu lượt code tiếp theo:

```powershell
git switch dev/duy
git fetch origin
git merge origin/main
git push
```

Thay `dev/duy` bằng tên nhánh cá nhân của bạn. Lặp lại bước này sau mỗi PR đã merge; nhánh cá nhân được giữ lại để dùng tiếp.
