# Security notes / Lưu ý bảo mật CourtMate

CourtMate hiện là dự án học tập và demo, **không được coi là hệ thống thanh toán hoặc đặt sân production**.

- Không đưa Admin token cloud, webhook secret, API key, password, `.env`, file DB/backup hoặc khóa ký APK vào repository công khai.
- Android local admin và cloud server admin là **hai hệ thống xác thực riêng**; cloud hiện dùng một bearer token chung, chưa có per-user RBAC và revocation.
- Cloud cần chạy sau HTTPS reverse proxy, rate limit ở tầng hạ tầng, monitoring và xác thực khách đặt sân trước khi công khai.
- Website không lưu mã bí mật tra cứu vào localStorage; người dùng tự bảo vệ mã nhận lúc đặt.
- Android backup thủ công hiện chưa được mã hóa; không tải backup chứa dữ liệu khách lên GitHub.
- LAN light controller có thể dùng HTTP theo hệ thống cũ; không mở thiết bị điều khiển đèn ra Internet.
- `COURTMATE_PAYMENT_URL_TEMPLATE` + webhook HMAC là lớp mô phỏng tích hợp; **không xác nhận** đã kết nối bất kỳ ngân hàng/PSP thật nào.

Nếu phát hiện lỗi, ưu tiên liên hệ người duy trì repository bằng kênh riêng thay vì đăng token, DB hay chi tiết khai thác lên public issue.
