# Hướng dẫn làm việc bằng Git và Pull Request

Mỗi người dùng **một nhánh cá nhân lâu dài**, ví dụ `dev/duy`, `dev/an`. Mọi thay đổi được gửi vào `main` qua Pull Request (PR).

Cần cài Git và GitHub CLI (`gh`). Đăng nhập một lần bằng `gh auth login`.

## 1. Tạo nhánh cá nhân lần đầu

Thay `dev/duy` bằng tên nhánh riêng của bạn. Chạy một lần:

```powershell
git switch main
git pull origin main
git switch -c dev/duy
git push -u origin dev/duy
```

Những lần sau dùng lại nhánh đó, không tạo nhánh mới:

```powershell
git switch dev/duy
```

## 2. Trước khi code

Lấy thay đổi mới nhất từ `main` vào nhánh cá nhân:

```powershell
git switch dev/duy
git status
git fetch origin
git merge origin/main
```

Nếu Git mở Vim để xác nhận merge message: nhấn `Esc`, gõ `:wq`, rồi nhấn `Enter`.

**Lệnh trên chỉ cập nhật nhánh đang đứng (`dev/duy`); nó không đưa code vào `main`.**

## 3. Commit và push code

Sau khi sửa code:

```powershell
git status
git add .
git commit -m "feat(auth): implement authentication endpoint"
git push
```

Không commit file `.env`, mật khẩu hoặc thông tin bí mật.

## 4. Tạo Pull Request

Tạo PR từ nhánh cá nhân vào `main`:

```powershell
gh pr create --base main --head dev/duy --title "feat(auth): implement authentication endpoint" --body "Mô tả thay đổi và cách kiểm tra."
```

Nếu PR đang mở, các lần `git push` tiếp theo tự cập nhật PR đó; không tạo PR mới. Xem số PR bằng:

```powershell
gh pr list --base main
```

## 5. Review và merge

Một thành viên khác có quyền ghi vào repo review PR. Tác giả không thể tự approve PR của mình.

Reviewer thay `3` bằng số PR:

```powershell
gh pr review 3 --approve --body "Đã xem, đồng ý."
```

Nếu cần sửa, reviewer có thể yêu cầu thay đổi:

```powershell
gh pr review 3 --request-changes --body "Vui lòng sửa ..."
```

Sau khi được approve và các kiểm tra đạt, người có quyền merge chạy:

```powershell
gh pr merge 3 --merge
```

Lệnh này merge PR trên GitHub vào `main`; không cần checkout sang `main`. **Đây là bước đưa code vào `main`.**

## 6. Đồng bộ nhánh cá nhân sau khi PR được merge

Trước lượt code tiếp theo:

```powershell
git switch dev/duy
git fetch origin
git merge origin/main
git push
```

Việc merge `origin/main` khi đang ở `dev/duy` chỉ đồng bộ nhánh cá nhân. Sau đó, tạo PR mới khi có thay đổi mới.

Nhánh cá nhân được giữ lại nếu repo tắt **Settings → General → Pull Requests → Automatically delete head branches**. Trong repo EventHub, tùy chọn này hiện đang bỏ chọn.

