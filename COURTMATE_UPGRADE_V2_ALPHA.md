# CourtMate 2.0.0-alpha — Báo cáo nâng cấp thực tế

**Ngày đóng gói:** 10/11/2026. **Nguồn:** bản CourtMate v1.2.0-dev do người dùng cung cấp. **Trạng thái:** mã nguồn thử nghiệm, chưa build/chạy trên Android hoặc chưa kết nối ngân hàng thật.

## Tóm tắt 6 yêu cầu và trạng thái thực hiện

| Yêu cầu | Đã triển khai trong gói | Còn thiếu trước khi dùng thương mại |
| --- | --- | --- |
| 1. UI/UX | Làm mới màu sắc Material 3 toàn app, header Dashboard, hiển thị vai trò, màn hình đăng nhập, người dùng, đổi mật khẩu, cloud, website đặt sân responsive | Thiết kế lại toàn diện 26 màn hình, accessibility, thử nghiệm thiết bị thật và thiết kế đồng nhất từng quy trình |
| 2. Đăng nhập / Admin–Nhân viên | Setup Admin lần đầu **không tài khoản mặc định**, PBKDF2 210k vòng và salt ngẫu nhiên, lưu hash vào SQLite schema v7, AuthSession RAM 6 giờ, rate limit 5 lần/5 phút, tạo/khóa Staff, thay mật khẩu, truy cập kiểm tra ở mọi Activity cũ, phân quyền mặc định từ chối, ẩn tài chính với Staff, log tên người dùng | Mã hóa toàn bộ SQLite (SQLCipher hoặc giải pháp tương đương), bảo mật thiết bị thất lạc, 2FA, quản trị vai trò trên cloud, audit security đầy đủ |
| 3. Đặt sân online / đa thiết bị | Backend FastAPI SQLite/WAL duy nhất, API quản lý sân, booking, xác nhận/hủy, POST đặt sân công khai, 20 phút giữ chỗ, chống trùng có `BEGIN IMMEDIATE`, website khách và màn hình Android CloudBookingsActivity (HTTPS) có tạo/xem/xác nhận/hủy lịch. Nhiều thiết bị cùng truy cập **lịch cloud** khi có server | **CHƯA có đồng bộ hai chiều** giữa local SQLite và cloud; **CHƯA** gộp nguồn dữ liệu. Dùng song song hai sổ với cùng sân có thể đặt trùng. Cần kế hoạch migration, server canonical, offline sync queue & conflict resolution |
| 4. Ngân hàng tự động | Phác thảo thanh toán cloud qua PSP URL cấu hình ở server, webhook HMAC-SHA256 có timestamp chống replay, match amount/reference/currency, ghi PAID idempotent, không tin trạng thái tự báo từ khách. Website có theo dõi trạng thái riêng theo token. **Đã có kiểm thử webhook** | **CHƯA tích hợp ngân hàng/PSP cụ thể hoặc chuyển khoản thật**. Chưa có merchant credentials, ngân hàng/provider webhook contract, xác thực đối soát server-to-server, xử lý refund. QR nội bộ ở Android vẫn được nhân viên xác nhận thủ công |
| 5. MVVM / Repository | `DashboardRepository` + `DashboardViewModel` + `LiveData`, `CheckoutRepository` + `CheckoutViewModel` + `LiveData`, truy vấn/checkout chạy ExecutorService, chính sách phân quyền tách lớp thuần | **CHƯA chuyển toàn bộ 26 màn/DatabaseHelper 3.5k+ dòng sang MVVM/DAO/Room**; cần kế hoạch chuyển theo miền để không phá schema |
| 6. Transaction kết thúc/thu tiền | `DatabaseHelper.completeSessionPayment(...)` ghi trạng thái, tiền, sổ thanh toán và audit trong **một transaction**; chặn thu trùng bằng UPDATE có điều kiện; rollback toàn bộ khi chèn sổ/audit lỗi. `PaymentActivity` gọi qua ViewModel worker; vẫn xử lý phiên đã kết thúc nhưng chưa trả từ bản cũ | Chưa kiểm tra instrumentation DB trên máy/emulator, chưa có luồng ngân hàng tự động cho session SQLite, cần thử kịch bản mất điện và rollback thực tế |

## Thay đổi mã nguồn có thể kiểm tra

- `app/src/main/java/.../security/`: `AuthSession`, `AuthRepository`, `RoleAccessPolicy`.
- `LoginActivity`, `ProtectedActivity`, `UsersActivity`, `ChangePasswordActivity`.
- `CloudBookingsActivity`, `repository/CloudBookingRepository`.
- `repository/DashboardRepository`, `viewmodel/DashboardViewModel`.
- `repository/CheckoutRepository`, `viewmodel/CheckoutViewModel`.
- `data/DatabaseHelper.java`: upgrade `DB_VERSION` 6→7, tạo bảng `nguoi_dung`, thêm `completeSessionPayment` nguyên tử; hoạt động cũ giữ tương thích.
- `PaymentActivity.java`: không còn kết thúc trước rồi thu tiền ở 2 transactions tách rời; tính tổng tiền qua `PaymentAmountMath` có kiểm tra overflow.
- Manifest: LoginActivity trở thành LAUNCHER; `DashboardActivity` không export; Android OS Backup **tắt** để giảm rủi ro lộ tài khoản (sao lưu chủ động trong app vẫn tồn tại và đang **không mã hóa**).
- `backend/`: API, trang đặt sân online, bộ test.

## Quá trình kiểm thử

| Nhóm | Kết quả | Phạm vi |
| --- | --- | --- |
| Python/FastAPI `pytest` | **8/8 đạt** | Phân quyền token, tạo sân/lịch, tránh trùng, đồng thời, giữ chỗ hết hạn, token trạng thái, tính nguyên vẹn webhook, payload sai, duplicate, cấu hình cổng thanh toán thiếu |
| Java thuần | **39/39 phương thức đạt** bằng JUnit-compatible local harness | 27 test từ v1.2 + 8 test `RoleAccessPolicy` + 4 test `PaymentAmountMath` (không phải Gradle JUnit đầy đủ) |
| XML | **58/58 hợp lệ** theo XML parser | Kiểm tra cú pháp các file `app/src/main/*.xml`, `res/**/*.xml` |
| Web JS | `node --check` đạt | Kiểm tra cú pháp script trong `backend/static/index.html` |
| `./gradlew --offline :app:assembleDebug :app:testDebugUnitTest` | **KHÔNG CHẠY ĐƯỢC** | Gradle 9.6.0 không có trong container; DNS `services.gradle.org` không truy cập được. Môi trường không có Android SDK/Emulator. **Không có APK được kiểm chứng** |

> Các kiểm thử backend **không chứng minh** mã Android compile hoặc ứng dụng hoạt động trên thiết bị; cần hoàn thành checklist thủ công bên dưới trước khi sử dụng dữ liệu/tiền thật.

## Chạy Android trong Android Studio

1. Giải nén dự án, mở thư mục `CourtMatePickleballCourtManager` trong Android Studio có Android SDK phù hợp.
2. Đồng bộ Gradle với kết nối mạng (Gradle 9.6.0, AGP hiện tại theo dự án).
3. Chạy `./gradlew testDebugUnitTest` rồi `./gradlew assembleDebug` hoặc trên Windows `gradlew.bat`.
4. Cài build lên Android/emulator. Lần đầu **tạo tài khoản Admin** (3–32 ký tự tên user; mật khẩu 10–128 ký tự); không có mật khẩu mặc định.
5. Admin vào **Cài đặt → Tài khoản & phân quyền** để tạo Staff; kiểm tra Staff không mở được quản lý tài chính, cấu hình và cloud.
6. Test một session hoàn tất + sổ thanh toán có đúng 1 dòng; thử xác nhận thanh toán lần thứ hai và kiểm tra không có giao dịch trùng.

## Chạy backend và xem website đặt sân

```bash
cd backend
python -m pip install -r requirements.txt
export COURTMATE_ADMIN_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
export COURTMATE_PUBLIC_BOOKING=true
uvicorn app:app --host 127.0.0.1 --port 8000
```

Trên Windows PowerShell đặt `$env:COURTMATE_ADMIN_TOKEN="..."` và `$env:COURTMATE_PUBLIC_BOOKING="true"`.

- Tạo sân qua `POST /v1/admin/courts` (Swagger `/docs`, dùng Authorization Bearer).
- Website khách ở `/`. Admin Android mở **Đặt sân online / Đồng bộ máy chủ** ở Dashboard, nhập HTTPS hostname và API token. **Lưu ý** Android CloudBookingsActivity chặn `http://`, cần deploy HTTPS thực sự; backend khởi động local chỉ phục vụ test trên browser/curl.
- Admin xác nhận yêu cầu khách từ `PENDING` → `CONFIRMED` trước khi giữ chỗ 20 phút hết hạn.
- Chỉ khi `COURTMATE_PAYMENT_URL_TEMPLATE` và `COURTMATE_GATEWAY_WEBHOOK_SECRET` là cấu hình PSP thực, server mới tạo link và nhận webhook hợp lệ. Không sử dụng URL placeholder trong sản xuất.

## Phải làm trước khi go-live (bắt buộc)

1. **Quyết định source of truth lịch sân.** Không bật đồng thời đặt sân local và cloud cho cùng sân, vì hai nơi chưa chia sẻ quy tắc chống trùng. Thiết kế migration số sân/giá, replay lịch hiện có và đồng bộ offline theo phiên bản/bản ghi.
2. **Gắn provider thực** (merchant onboarding, API create payment, kiểm tra ký PSP đúng spec, đối soát, refund và webhook retry). `COURTMATE_GATEWAY_WEBHOOK_SECRET` hiện chỉ định nghĩa *contract của adapter*, không đại diện chữ ký thật của bất kỳ ngân hàng nào.
3. **Bảo vệ PII, backup và token**: mã hóa backup export, mã hóa DB, Android Keystore cho token thiết bị, HTTPS+certificate, logging không chứa SĐT/token, retention. Backend admin một shared API key chưa đủ production; thêm RBAC server, refresh/revoke tokens.
4. **Test instrumented SQLite migration 6→7 và rollback** có dữ liệu thật; test nhiều nhân viên/vài nghìn lượt đặt, điều khiển đèn và time zone trên emulator/thiết bị thật.
5. **Giảm technical debt**: tách tiếp `DatabaseHelper` theo repositories/DAO từng miền; tránh tái viết toàn bộ không có regression suite.
6. **Chuẩn bị phát hành**: rà lại `targetSdk 34` & yêu cầu store hiện hành, test edge-to-edge nếu nâng targetSdk, branding, tài khoản riêng, chính sách riêng tư và điều khoản dùng dịch vụ.

## Chuyển dữ liệu và rollback

- Bản v2 giữ database file `courtmate.db`, nâng schema từ v6 lên v7 bằng cách thêm bảng người dùng; không xóa dữ liệu đặt sân cũ. Tạo backup **an toàn** bằng tính năng backup của v1.2 *trước khi nâng cấp* và bảo quản offline.
- Android không tự downgrade schema v7→v6; nếu quay về v1.2 phải **khôi phục backup v6 trên thiết bị thử nghiệm**, không đơn giản cài đè APK cũ.
- Dữ liệu online ở `backend/courtmate_cloud.db` (hoặc `COURTMATE_DB`) tách biệt và phải được backup riêng.
- Một Admin đã thiết lập nên chuẩn bị quy trình quản lý mật khẩu khẩn cấp; hiện không có phục hồi mật khẩu từ xa.
