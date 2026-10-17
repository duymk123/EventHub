

Tài liệu này hướng dẫn cách **tạo nhánh**, **chuyển nhánh** và **merge** khi tham gia dự án.


- ❌ **Không push trực tiếp vào `main`.** Nhánh `main` đã được bảo vệ, push thẳng sẽ bị từ chối.
- ✅ Mỗi chức năng / mỗi lỗi cần sửa làm trên **một nhánh riêng**.
- ✅ Code chỉ vào `main` thông qua **Pull Request (PR)** và phải được ** đọc code và approve**.

## Luồng làm việc tổng quát

```
main ──────────────────────────────────────────►
        \                                  /
         feature/ten-chuc-nang ──► Pull Request ──► Nhóm trưởng review ──► Approve ──► Merge
```

---

## 1. Chuẩn bị lần đầu

```bash
# Tải dự án về máy
git clone <link-repo-github>
```

---

## 2. Tạo nhánh mới

**Luôn cập nhật `main` mới nhất trước khi tạo nhánh:**

```bash
git switch main
git pull origin main
```

**Tạo nhánh mới và chuyển sang nhánh đó:**

```bash
git checkout -b feature/ten-chuc-nang
```

 ``

### Quy ước đặt tên nhánh

| Loại công việc | Mẫu tên | Ví dụ |
|---|---|---|
| Chức năng mới | `feature/<mô-tả>` | `feature/login-api` |
| Sửa lỗi | `fix/<mô-tả>` | `fix/null-pointer-order` |
| Tài liệu | `docs/<mô-tả>` | `docs/update-readme` |
| Refactor | `refactor/<mô-tả>` | `refactor/order-service` |

Quy tắc: viết **chữ thường**, **không dấu**, nối các từ bằng dấu `-`.

---

## 3. Chuyển nhánh

```bash
# Xem tất cả nhánh trên máy (nhánh có dấu * là nhánh đang đứng)
git branch

# Xem cả nhánh trên GitHub
git branch -a

# Chuyển sang nhánh khác
git checkout ten-nhanh

# Chuyển sang nhánh có trên GitHub nhưng chưa có trên máy
git fetch origin
git switch ten-nhanh
```

> **Lưu ý:** Nếu báo lỗi *"Your local changes would be overwritten"*, bạn cần commit hoặc cất tạm thay đổi trước khi chuyển nhánh:
>
> ```bash
> git stash          # cất tạm thay đổi
> git switch ten-nhanh
> git stash pop      # lấy lại thay đổi khi cần
> ```

---

## 4. Code, commit và push

```bash
# Xem file nào đã thay đổi
git status

# Thêm file vào commit
git add .                 # thêm tất cả
git add duong/dan/file    # hoặc thêm từng file

# Tạo commit
git commit -m "feat: them api dang nhap"

# Đẩy nhánh lên GitHub (lần đầu)
git push -u origin feature/ten-chuc-nang

# Các lần sau chỉ cần
git push
```

### Quy ước viết commit message

```
<loại>: <mô tả ngắn gọn>
```

| Loại | Ý nghĩa |
|---|---|
| `feat` | Thêm chức năng mới |
| `fix` | Sửa lỗi |
| `docs` | Sửa tài liệu |
| `refactor` | Cải thiện code, không đổi chức năng |
| `test` | Thêm / sửa test |
| `chore` | Việc vặt (cấu hình, thư viện...) |

Ví dụ: `feat: them api tao don hang`, `fix: sua loi tinh tong tien`.

---

## 5. Mở Pull Request (PR)

1. Push nhánh của bạn lên GitHub.
2. Vào trang repo trên GitHub, bấm nút **Compare & pull request**.
3. Kiểm tra: nhánh đích (**base**) là `main`, nhánh nguồn (**compare**) là nhánh của bạn.
4. Viết tiêu đề và mô tả: bạn đã làm gì, cách kiểm tra.
5. Ở mục **Reviewers**, chọn **nhóm trưởng**.
6. Bấm **Create pull request**.

### Khi nhóm trưởng yêu cầu sửa

Bạn **không cần tạo PR mới**. Chỉ cần sửa code trên cùng nhánh, commit và push tiếp:

```bash
git add .
git commit -m "fix: sua theo gop y review"
git push
```

PR sẽ tự động cập nhật. Sau khi sửa xong, báo lại cho nhóm trưởng review lại.

> Nhóm trưởng sẽ **merge** PR vào `main` sau khi approve. Thành viên không tự merge vào `main`.

---

## 6. Merge `main` vào nhánh của bạn

Khi bạn đang làm thì có người khác đã merge code mới vào `main`, bạn cần cập nhật để nhánh của mình không bị lỗi thời hoặc xung đột.

```bash
# Bước 1: Lấy code mới nhất của main
git switch main
git pull origin main

# Bước 2: Quay lại nhánh của bạn và merge main vào
git switch feature/ten-chuc-nang
git merge main

# Bước 3: Đẩy lên GitHub
git push
```

Cách ngắn hơn, không cần chuyển nhánh:

```bash
git fetch origin
git merge origin/main
```

---

## 7. Xử lý xung đột (conflict)

Khi merge, Git có thể báo `CONFLICT` nghĩa là hai người cùng sửa một chỗ. Cách xử lý:

1. Chạy `git status` để xem file nào bị xung đột.
2. Mở file đó, bạn sẽ thấy các dấu:

```
<<<<<<< HEAD
code cua ban
=======
code cua nguoi khac
>>>>>>> main
```

3. Giữ lại đoạn code đúng (có thể giữ cả hai), **xóa hết các dòng** `<<<<<<<`, `=======`, `>>>>>>>`.
4. Hoàn tất merge:

```bash
git add .
git commit -m "merge: giai quyet xung dot voi main"
git push
```

Nếu rối và muốn hủy merge để làm lại:

```bash
git merge --abort
```

> Không chắc chắn khi xử lý conflict? **Hỏi nhóm trưởng** trước khi xóa code của người khác.

---

## 8. Sau khi PR được merge

Dọn dẹp và chuẩn bị cho việc tiếp theo:

```bash
git switch main
git pull origin main

# Xóa nhánh đã xong trên máy
git branch -d feature/ten-chuc-nang
```

Sau đó tạo nhánh mới cho công việc tiếp theo (quay lại mục 2).

---

## Tóm tắt lệnh hay dùng

| Việc cần làm | Lệnh |
|---|---|
| Xem nhánh hiện tại | `git branch` |
| Tạo và chuyển sang nhánh mới | `git switch -c ten-nhanh` |
| Chuyển nhánh | `git switch ten-nhanh` |
| Cập nhật code mới từ GitHub | `git pull origin main` |
| Merge `main` vào nhánh hiện tại | `git merge main` |
| Xem trạng thái file | `git status` |
| Xem lịch sử commit | `git log --oneline` |
| Hủy merge đang bị conflict | `git merge --abort` |
| Xóa nhánh trên máy | `git branch -d ten-nhanh` |

## Những điều cần tránh

- ❌ Không `git push --force` (có thể ghi đè code của người khác).
- ❌ Không commit trực tiếp trên `main`.
- ❌ Không để một nhánh sống quá lâu. Chia nhỏ công việc, mở PR sớm.
- ❌ Không commit file nhạy cảm (mật khẩu, API key, file `.env`).
- ❌ Không nhét quá nhiều chức năng vào một PR, vì PR nhỏ sẽ được review nhanh hơn.

Nếu có thắc mắc, hãy hỏi nhóm trưởng trước khi thực hiện các lệnh bạn chưa hiểu rõ.