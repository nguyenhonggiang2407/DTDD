# Báo cáo triển khai CourtMate

Ngày thực hiện: 2026-11-03

## Kết quả kiểm tra

- `.\gradlew.bat testDebugUnitTest`: thành công, 19 test pass.
- `.\gradlew.bat assembleDebug`: thành công.
- `.\gradlew.bat lintDebug`: thành công, không có lỗi lint.
- APK: `app\build\outputs\apk\debug\app-debug.apk`.

## Thay đổi chính

### Dữ liệu và migration

- Nâng `DB_VERSION` từ 4 lên 5.
- Thêm cột `phien_choi.phuong_thuc_tt_moi` bằng `ALTER TABLE` an toàn.
- Giữ nguyên cột `phien_choi.phuong_thuc_tt` cho dữ liệu cũ.
- Thêm index cho phiên, phiếu thuê, thanh toán và lịch đặt.
- Ghi QR nội bộ vào cột mới; cột cũ chỉ ghi `TIEN_MAT` hoặc `MOMO`.

### Thanh toán và cọc

- Ghi giao dịch `COC` khi tạo phiếu thuê thiết bị.
- Ghi `HOAN_COC` và `DIEU_CHINH` khi trả thiết bị.
- Tính cọc đang giữ theo tổng COC trừ HOAN_COC và DIEU_CHINH.
- Sổ thanh toán hiển thị tổng net, khoản hoàn có dấu trừ.
- Dashboard và Thống kê hiển thị cọc đang giữ, thực thu và công nợ.

### Xóa an toàn

- Khách hàng có lịch sử được chuyển sang trạng thái `NGUNG`.
- Thêm màn hình danh sách khách đã ngừng và khôi phục.
- Dịch vụ đã phát sinh giao dịch được hướng dẫn ngừng bán.
- Thêm thao tác ngừng bán/mở bán lại dịch vụ.

### Lịch và phiên

- Chặn đặt lịch trùng phiên đang chơi.
- Xử lý phiên qua 0h khi kiểm tra trùng lịch.
- Bổ sung thông báo lỗi `ERROR_SESSION_OVERLAP`.
- Thêm Timeline sân theo ngày, hiển thị trạng thái và phiên qua 0h.

### Sao lưu

- Lưu mốc sao lưu gần nhất vào SharedPreferences.
- Hiển thị lại mốc sao lưu khi mở Cài đặt.
- Kiểm tra `PRAGMA quick_check` khi xác thực tệp khôi phục.
- Giữ bản tạm để hoàn tác nếu khôi phục thất bại.

## Tệp đã sửa

- `app/src/main/java/com/example/courtmatepickleballcourtmanager/data/DatabaseHelper.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/util/BackupManager.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/SettingsActivity.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/DashboardActivity.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/StatisticsActivity.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/CustomerActivity.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/ServiceManagementActivity.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/BookingFormActivity.java`
- `app/src/main/java/com/example/courtmatepickleballcourtmanager/PaymentLedgerActivity.java`
- `app/src/main/res/layout/activity_dashboard.xml`
- `app/src/main/res/layout/activity_stats.xml`
- `app/src/main/res/layout/activity_customer.xml`
- `app/src/main/res/menu/menu_dashboard.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/example/courtmatepickleballcourtmanager/DateTimeUtilsTest.java`

## Tệp đã tạo

- `app/src/main/java/com/example/courtmatepickleballcourtmanager/CourtTimelineActivity.java`
- `app/src/main/res/layout/activity_timeline.xml`
- `app/src/test/java/com/example/courtmatepickleballcourtmanager/RentalCalculatorTest.java`
- `README.md`
- `CHANGELOG.md`
- `TODO.md`
- `docs/DEMO_SCRIPT.md`
- `docs/TEST_CASES.md`
- `docs/REPORT.md`

## Lưu ý còn lại

- Chưa chạy kiểm thử instrumentation trên emulator/thiết bị thật.
- Chưa kiểm tra trực tiếp migration từ DB v4 có dữ liệu thật; logic đã dùng `ALTER TABLE` và `CREATE INDEX IF NOT EXISTS`.
- Tệp backup trước khi sửa nằm tại `C:\Users\asus\AppData\Local\Temp\opencode\CourtMate_backup_20261103.zip`.
