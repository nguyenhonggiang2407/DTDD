# CourtMate 2.2.0-demo — Bản bàn giao để đưa lên GitHub

Ngày: 17/11/2026.

## Phạm vi thay đổi thực tế

| Thành phần | Đã thay đổi | Cách kiểm tra |
|---|---|---|
| Cloud API | `PATCH /v1/admin/courts/{id}` và `POST /v1/admin/courts/{id}/update` để đổi giá, tên, bật/tắt | Test `test_court_maintenance_via_admin_and_android_alias` |
| Cloud API | `GET /v1/admin/summary?date=YYYY-MM-DD` | Test `test_summary_daily_counts_and_actual_paid_amount` |
| Cloud API | `POST /v1/public/bookings/cancel` (chỉ đơn PENDING/UNPAID; yêu cầu token) | 2 test hủy và bảo vệ lịch đã xác nhận |
| Cloud API | Chặn giờ quá khứ, validate ngày, chuẩn hóa tên sân, hạn chế tốc độ tra cứu | Test validation, rate-limit |
| Bảo mật cloud | Không trả `public_token` trong danh sách/tạo lịch của Admin | Test `test_staff_api_does_not_expose_customer_secret` |
| Website khách | Tra cứu bằng mã đơn + token, hủy yêu cầu chờ, thông báo lưu mã bí mật | Xem `backend/static/index.html` |
| Android cloud | Dashboard thống kê, danh sách sân cloud, đóng/mở nhận lịch | Xem `CloudBookingsActivity` và layout cloud |
| GitHub | README, .gitignore bổ sung, workflow Python + Android | `.github/workflows/ci.yml` |

**Tính tương thích:** không đổi `applicationId`, cấu trúc SQLite Android v7 hoặc dữ liệu seed 2.1.0. Android `versionCode` 5 / `versionName` 2.2.0-demo. Cloud FastAPI version 0.3.0. Backend DB hiện có được giữ nguyên và không cần migration cho thay đổi lần này.

## Kết quả xác nhận

- Backend: **16/16 bài kiểm thử pass** (`cd backend && python -m pytest tests -q`).
- Kiểm tra cú pháp Python và HTML/JavaScript/XML được chạy ở bước đóng gói.
- Android: **chưa chạy Gradle/assembleDebug/instrumentation test** vì môi trường đóng gói không có Android SDK, Gradle distribution. CI đã khai báo nhưng kết quả build Android chỉ biết được sau khi chạy trên GitHub Actions/Android Studio.
- Không có kết nối PSP/ngân hàng thật và không có test chuyển khoản thực.

## Kịch bản demo ngắn

1. Chạy backend trên máy chủ hoặc localhost (trình duyệt), cấu hình token mạnh; bật public booking trong môi trường demo.
2. Tạo một sân cloud, đặt lịch ngày tương lai trên website, thấy trạng thái PENDING và mã bí mật; tra cứu rồi hủy khi đang chờ.
3. Đặt lại khung giờ vừa hủy, xác nhận qua API Admin hoặc Android đã kết nối HTTPS; khách kiểm tra lại sẽ thấy CONFIRMED và không thể tự hủy.
4. Xem summary của ngày đó; tạm đóng sân cloud và thử đặt mới để nhận thông báo không thể đặt.
5. Mở Android để minh họa các module offline và dữ liệu seed; nhắc rõ lịch offline không tự đồng bộ lên cloud.

## Những hạng mục chưa hoàn tất

- Đồng bộ hai chiều Android SQLite ↔ cloud, offline queue/idempotency và merge xung đột.
- Cloud auth theo từng nhân viên; cơ chế refresh token và RBAC backend.
- Kết nối PSP thực, đối soát và hoàn tiền.
- Chuyển toàn bộ màn hình Android về kiến trúc Repository/ViewModel; test migration trên thiết bị.
- Bảo vệ file backup Android bằng mã hóa; hardening triển khai public, nhiều worker.

**Ghi chú:** Đóng ZIP là tạo bộ nguồn sẵn sàng để commit; chưa tự đăng hoặc cập nhật repository GitHub của người dùng.
