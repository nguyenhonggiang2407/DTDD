# CourtMate — Quản lý sân Pickleball

**Phiên bản: 2.2.0-demo · Android (Java/SQLite) + Cloud Booking (Python/FastAPI)**

CourtMate là dự án quản lý vận hành sân Pickleball. Ứng dụng Android hỗ trợ lịch đặt sân, khách hàng, phiên chơi, dịch vụ, thuê thiết bị, hóa đơn và thống kê. Phần cloud là một máy chủ **độc lập** để nhận yêu cầu đặt sân từ website và chia sẻ lịch online giữa các máy quản trị.

> **Dự án demo/portfolio, chưa sẵn sàng triển khai kinh doanh thực tế.** Lịch Android SQLite **không đồng bộ hai chiều** với lịch cloud. Chưa tích hợp ngân hàng thật; QR tại chỗ phải có nhân viên xác nhận. Dữ liệu demo không phải giao dịch thật.

## Chức năng nổi bật

| Android (offline) | Cloud (online, thí điểm) |
|---|---|
| Đăng nhập Admin/Staff, mật khẩu băm và phân quyền | API FastAPI, cơ sở dữ liệu SQLite phía server |
| Sân, khách hàng, lịch đặt, lịch sân theo ngày | Website đặt sân, giữ chỗ 20 phút, chống trùng thời gian |
| Phiên chơi, thanh toán/cọc, hóa đơn | Nhân viên xác nhận/hủy yêu cầu, quản lý sân cloud |
| Dịch vụ, thuê thiết bị, HLV, lớp học, sự kiện | Khách tra cứu/hủy yêu cầu chờ bằng mã bí mật |
| Dashboard/thống kê, backup, dữ liệu demo | Thống kê đặt sân, số tiền đã nhận qua webhook đã xác minh |
| Điều khiển đèn LAN ở cấu hình hiện có | Webhook ký HMAC cho adapter PSP (chưa kết nối ngân hàng thật) |

## Cấu trúc dự án

```text
CourtMatePickleballCourtManager/
├── app/src/main/                 # Ứng dụng Android (Java, XML, SQLite)
│   ├── java/.../data/            # DatabaseHelper, DemoDataSeeder
│   ├── java/.../repository/      # Repository Android, cloud API
│   └── java/.../security/        # Đăng nhập và phân quyền
├── backend/
│   ├── app.py                    # FastAPI + luật nghiệp vụ cloud
│   ├── static/index.html         # Website khách đặt sân
│   ├── tests/                    # API/integration tests
│   └── compose.yaml              # Docker Compose để chạy nội bộ
├── .github/workflows/ci.yml     # GitHub Actions: Python và Android
├── docs/                        # Kịch bản demo, QA, hướng dẫn GitHub
└── README.md
```

## Xem demo nhanh (không cần cài Android Studio)

**Cách 1 — demo offline ngay:** tải file [`demo/index.html`](demo/index.html) về máy và mở bằng Chrome/Edge. Có 4 sân mẫu, lịch trống, gửi/hủy yêu cầu, bảng quản trị mô phỏng. **Đây là chế độ giả lập trong trình duyệt**, dữ liệu chỉ nằm trong bộ nhớ của trang và mất khi tải lại; không kết nối server hoặc thanh toán thật. [Ảnh giao diện](demo/web-preview.png).

**Cách 2 — backend FastAPI thật trên máy:** cần Python 3.11+ và internet để cài thư viện. Trên Windows nhấp đúp `scripts/run_demo_windows.bat`, hoặc trên Linux/macOS chạy `bash scripts/run_demo_linux.sh`; mở **http://127.0.0.1:8000/**. Script tự tạo mã token ngẫu nhiên, cơ sở dữ liệu demo và 4 sân mẫu. Không công khai server này trên internet; dừng bằng `Ctrl+C`. Để chạy trên máy khác, xem [backend/README.md](backend/README.md).

## 1. Chạy ứng dụng Android

1. Cài Android Studio và Android SDK phù hợp với `compileSdk 37`.
2. Mở **thư mục chứa `settings.gradle.kts`**, không mở riêng thư mục `app`.
3. Sync Gradle. Chọn máy ảo hoặc điện thoại và chạy cấu hình `app`.
4. **Lần chạy đầu, tạo tài khoản Admin riêng**; không có tài khoản/mật khẩu quản trị mặc định.
5. Dữ liệu minh họa được thêm khi tạo SQLite lần đầu. Với bản nâng cấp có DB sẵn, Admin có thể nạp dữ liệu demo trong Cài đặt sau khi sao lưu.

Lệnh kiểm tra (macOS/Linux: `bash ./gradlew`; Windows: `gradlew.bat`):

```bash
bash ./gradlew testDebugUnitTest
bash ./gradlew assembleDebug
```

Các lệnh trên yêu cầu tải được plugin Gradle/Android SDK. Máy đóng gói bản 2.2 chỉ kiểm thử backend, **chưa xác nhận build APK Android**.

## 2. Chạy Cloud Booking trên máy cá nhân

Yêu cầu Python 3.11+, không dùng token trong mã nguồn:

```bash
cd backend
python -m venv .venv
# Windows PowerShell: .venv\Scripts\Activate.ps1
# macOS/Linux: source .venv/bin/activate
python -m pip install -r requirements-dev.txt
# macOS/Linux: export COURTMATE_ADMIN_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
# Windows PowerShell: $env:COURTMATE_ADMIN_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(48))"
# macOS/Linux: export COURTMATE_PUBLIC_BOOKING=true
# Windows PowerShell: $env:COURTMATE_PUBLIC_BOOKING = "true"
uvicorn app:app --host 127.0.0.1 --port 8000
```

Mở `http://127.0.0.1:8000/docs` để xem API và `http://127.0.0.1:8000/` để xem website. Tạo sân trên `/docs` bằng `Authorization: Bearer <TOKEN>` hoặc qua Android sau khi triển khai server HTTPS. **Không nhập URL localhost vào Android cloud**; màn hình cloud yêu cầu HTTPS hợp lệ.

Chạy kiểm thử:

```bash
cd backend
python -m pytest tests -q
```

Để bật adapter thanh toán, bắt buộc có cấu hình PSP riêng và webhook đã xác minh. Không copy một URL demo rồi coi là đã tích hợp tiền thật. Xem [hướng dẫn backend](backend/README.md).

## 3. Phiên bản 2.2 bổ sung gì?

- Admin cloud có thể **tạm đóng/mở sân** để ngăn đặt mới mà không xóa lịch sử.
- Dashboard cloud hiển thị lịch đang chờ, đã xác nhận, đã hủy, doanh thu đã xác minh và số tiền phải thu.
- Khách có thể **tra cứu trạng thái, hủy lịch còn chờ** nếu giữ mã đơn và mã bí mật. Website không lưu mã bí mật vào trình duyệt.
- Chặn đặt khung giờ bắt đầu trong quá khứ (múi giờ Việt Nam); cải thiện bảo vệ token và chuẩn hóa dữ liệu.
- Android màn hình Cloud bổ sung quản lý trạng thái sân, thống kê; backend có **16 bài kiểm thử**.
- Có workflow GitHub Actions, file ignore và hướng dẫn đưa mã nguồn lên repository.

Xem [changelog](CHANGELOG.md), [bản bàn giao 2.2](docs/RELEASE_V2_2_GITHUB.md) và [hướng dẫn đưa lên GitHub](docs/GITHUB_PUBLISH.md).

## 4. Các giới hạn cần giải quyết trước khi dùng thật

- Lịch Android **offline và cloud tách rời**: không sử dụng song song để nhận đặt cho cùng sân trước khi có chiến lược đồng bộ/xử lý xung đột.
- Cloud dùng **một token quản trị chia sẻ**, chưa có đăng nhập per-user, thu hồi phiên hay RBAC trên máy chủ.
- Không có chống spam đủ mạnh ở hạ tầng công khai; cần HTTPS reverse proxy, WAF/CAPTCHA, giám sát và database đa tiến trình.
- Thanh toán ngân hàng chưa kết nối thực tế. Chưa có luồng refund và đối soát thực với PSP.
- File backup SQLite Android vẫn có thể chứa thông tin nhạy cảm dưới dạng chưa mã hóa.
- Chưa có Android instrumentation test trên thiết bị, chưa xác minh build Gradle trong bản đóng gói này.

## 5. Bảo mật khi đưa lên GitHub

**Không commit** `.env`, token Admin, số điện thoại thật, tệp `.db`, file keystore, backup, API key, mật khẩu cá nhân hoặc `local.properties`. Mẫu `.env.example` chỉ chứa placeholder. Bản demo yêu cầu tạo Admin khi chạy lần đầu.

Xem [SECURITY.md](SECURITY.md). Giấy phép sử dụng nguồn (LICENSE) chưa được chủ dự án chỉ định.
