# Hướng dẫn đưa CourtMate lên GitHub

## Bước 1 — Xem lại mã nguồn trước khi công khai

Giải nén ZIP vào thư mục sạch, đọc `README.md` và `.gitignore`. Kiểm tra không có số điện thoại khách thật, token truy cập, `.env`, `.db`, backup SQLite, APK, keystore, `local.properties` hoặc tài liệu chứa mật khẩu. Mẫu demo này chỉ đi kèm placeholder và dữ liệu mẫu.

## Bước 2 — Tạo repository

Trên GitHub tạo **repository rỗng** (ví dụ `CourtMate`), không chọn Initialize with README nếu đang upload cả source. Trong thư mục `CourtMatePickleballCourtManager` chứa `settings.gradle.kts`, mở Terminal/Git Bash:

```bash
git init
git branch -M main
git add .
git status
git commit -m "feat: CourtMate 2.2 demo with cloud booking improvements"
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

Thay `USERNAME/REPOSITORY` theo repository thực tế. **Không chạy `git push --force` khi repository có lịch sử từ trước.** Nếu repo đã tồn tại, clone repo gốc rồi tích hợp thay đổi thành nhánh và tạo Pull Request (xem bên dưới).

## Nếu repository CourtMate đã tồn tại

```bash
git clone https://github.com/USERNAME/REPOSITORY.git
cd REPOSITORY
git checkout -b feature/courtmate-v2.2
# Sao chép nội dung dự án ZIP vào thư mục clone, KHÔNG ghi đè .git
git add .
git diff --cached --stat
git commit -m "feat: improve cloud booking and GitHub readiness"
git push -u origin feature/courtmate-v2.2
```

Sau đó tạo Pull Request và kiểm tra lại nội dung diff, đặc biệt dữ liệu mẫu và phiên bản Android. Workflow Actions sẽ chạy kiểm thử tự động; nếu job Android fail, xem logs trước khi merge.

## Kiểm tra nhanh sau push

- Mở tab **Actions**, xác minh job `FastAPI tests` và `Android unit tests & debug APK`.
- Tab **Code** cần có `app/`, `backend/`, `.github/workflows/ci.yml`, `README.md`.
- Nếu Actions build APK thành công, artifact `courtmate-debug-apk` được đính kèm workflow run. APK debug không phải bản phát hành có ký số.
- Tạo Release `v2.2.0-demo` chỉ sau khi kiểm tra nội dung source, quyền truy cập và kết quả build.

## Ghi chú bảo mật

`.gitignore` chỉ ngăn đưa file mới vào stage; **không xóa bí mật đã commit trong lịch sử**. Nếu từng lộ token, phải thu hồi/đổi ngay và xử lý lịch sử git riêng.
