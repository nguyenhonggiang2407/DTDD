# CourtMate 2.1.0-demo — Hướng dẫn bàn giao

## Điểm mới

**UI/UX:** Dashboard dark hero, bố cục thẻ thống kê, thanh điều hướng nhanh, đăng nhập mới, header thống nhất ở quản lý sân, khách hàng, danh sách đặt lịch, quản lý mở rộng và thống kê. Học bố cục từ app.zip (ứng dụng sạc EV), nhưng sử dụng hệ màu riêng của CourtMate và giữ nguyên nghiệp vụ.

**Dữ liệu mẫu:** Bộ `data/DemoDataSeeder.java` được nạp sau dữ liệu nền khi CSDL tạo mới. Các bộ sưu tập không có đủ dữ liệu trước đây được bổ sung để từng module có tối thiểu ba mẫu (danh sách dưới). Mẫu mới có tên/note/mã `DEMO` dễ nhận diện.

| Mục | Dữ liệu demo tự bổ sung |
|---|---|
| Khách hàng | 3 tài khoản khách minh họa; không phải tài khoản đăng nhập |
| Sân | Giữ 10 sân mặc định có sẵn |
| Lịch đặt | 3 lịch tương lai trên sân 1–3; lịch nền 4 mục nếu cài mới |
| Dịch vụ bán | Giữ 5 mặt hàng có sẵn |
| Thiết bị thuê | 2 có sẵn + 1 `Thuê giày Pickleball (Demo)` |
| Khung giờ vãng lai | 3 khung 14:00–16:00, sân 1–3 |
| Huấn luyện viên | 3 hồ sơ demo |
| Lớp học | 3 lớp + 3 buổi tuần + 3 đăng ký học viên |
| Giải đấu/sự kiện | 3 sự kiện + 3 phân sân + 3 lịch + 3 đăng ký |
| Khuyến mãi | 3 mã: `DEMO_GIOVANG`, `DEMO_DICHVU`, `DEMO_LOPHOC`; không tự áp nếu không gắn ngày đặc biệt |
| Ngày đặc biệt | Giữ các ngày lễ có sẵn (đã trên 3 mẫu) |
| Phiên chơi, hóa đơn | 3 phiên kết thúc đã thanh toán, hóa đơn mã `DEMO-*` |
| Sổ thanh toán | 3 thanh toán phiên + 3 giao dịch tiền cọc demo |
| Thuê thiết bị | 3 phiếu đang thuê + 3 chi tiết, tồn kho trừ đúng một lần |
| Tài khoản/quyền, đèn, cloud | Không tự sinh tài khoản hay token/URL đèn hoặc giao dịch ngân hàng; cần cấu hình thật |

## Cách chạy và kiểm tra

1. Giải nén ZIP, trong Android Studio chọn **File > Open > CourtMatePickleballCourtManager** (thư mục chứa `settings.gradle.kts`).
2. Sync Gradle; chọn máy ảo hoặc thiết bị Android; chọn `app` và Run.
3. Lần chạy đầu tạo tài khoản quản trị có mật khẩu riêng (không seed tài khoản quản trị/mật khẩu mặc định).
4. **Cài mới sạch:** tất cả mẫu được thêm tự động cùng SQLite v7. **Nâng cấp từ v2.0.0-alpha**: dữ liệu cũ được giữ nguyên; Admin vào **Cài đặt > Nạp dữ liệu demo (3 mẫu / phần)** để bổ sung nhóm còn thiếu.
5. Nút nạp demo xác nhận trước khi chạy; có thể bấm lại mà không thêm trùng **những bản ghi demo vẫn còn tồn tại**.
6. Trước khi dùng trên dữ liệu thật nên **sao lưu SQLite**. Dữ liệu có tên `DEMO` chỉ phục vụ học tập; báo cáo/sổ thanh toán có thể chứa giao dịch giả lập, không dùng số liệu này cho kinh doanh thật.

## Kiểm thử và giới hạn

- Kiểm tra cú pháp 61 tệp XML thành công.
- Chạy **chính mã nguồn `DemoDataSeeder`** với Android stub và SQLite v7 tạo từ schema project: `52` insert lần đầu; `0` insert lần hai; `PRAGMA foreign_key_check` trả tập rỗng.
- Chưa kiểm thử thao tác trên giao diện Android thật hay chạy Gradle/Android SDK ở môi trường đóng gói. Ứng dụng cần Sync/Run bằng Android Studio người dùng.
- Đây là dự án Android offline + backend cloud pilot giữ nguyên từ v2; **không có đồng bộ hai chiều SQLite ↔ cloud**. Không dùng đồng thời hai nguồn lịch cho cùng sân khi chưa đồng bộ xung đột.
- Không có kết nối thanh toán ngân hàng thật. Không cung cấp tài khoản/mật khẩu demo mặc định; người dùng tạo Admin lần đầu.
- Không tự động nạp lại toàn bộ demo khi đã có CSDL thật để không làm sai số liệu; thao tác nạp theo chủ đích chỉ có trong Settings.

## File chính thay đổi

- `app/src/main/java/.../data/DemoDataSeeder.java` (bổ sung)
- `app/src/main/java/.../data/DatabaseHelper.java` (gọi seed lúc tạo mới + API có transaction)
- `app/src/main/java/.../SettingsActivity.java` (nút nạp minh họa và xác nhận)
- `app/src/main/res/layout/activity_dashboard.xml`, `activity_login.xml` (thiết kế lại)
- `app/src/main/res/layout/activity_court.xml`, `activity_customer.xml`, `activity_main.xml`, `activity_management.xml`, `activity_stats.xml`, `activity_settings.xml`, `activity_booking_form.xml`, các item và toolbar (tối ưu style)
- `app/src/main/res/drawable/cm_*.xml`, `app/src/main/res/values/colors.xml`, `themes.xml`
- `app/build.gradle.kts` versionCode 4, versionName 2.1.0-demo, SQLite vẫn v7.
