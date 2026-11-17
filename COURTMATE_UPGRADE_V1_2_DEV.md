# CourtMate 1.2.0-dev — Đánh giá và bản vá thử nghiệm

Ngày rà soát: 10/11/2026. Phạm vi: mã nguồn Java/Android trong tệp CourtMatePickleballCourtManager.zip và tài liệu phân tích đính kèm. Đây là **bản cập nhật mã nguồn thử nghiệm**, không phải APK đã được build và kiểm thử trên Android.

## 1. Đánh giá tổng quan

CourtMate có nghiệp vụ tốt cho một quầy vận hành đơn lẻ: quản lý sân, lịch đặt, phiên chơi, dịch vụ/cho thuê, thanh toán thủ công, hóa đơn, lớp học, giải đấu, báo cáo, backup và đèn. Ưu điểm là các ràng buộc SQLite, transaction ở những nghiệp vụ quan trọng, hỗ trợ offline. Điểm yếu lớn là vận hành một thiết bị, thiếu phân quyền, dữ liệu và token đèn không mã hóa, `DatabaseHelper` ôm nhiều nhiệm vụ, kiểm thử ít, thanh toán QR chưa kết nối ngân hàng.

**Chấm tham khảo theo tiêu chí sản phẩm thương mại: 7,4/10**. Điểm này là đánh giá định tính, không phải benchmark hoặc điểm kiểm thử tự động.

## 2. Những chỉnh sửa đã thực hiện trong bản dev

| Khu vực | Thay đổi | Tệp |
|---|---|---|
| Bán hàng tại phiên | Chỉ hiển thị hàng loại `BAN`; kiểm tra lại `BAN` và `DANG_BAN` ở tầng dữ liệu | `SessionDetailActivity.java`, `DatabaseHelper.java` |
| Thuê thiết bị | Gợi ý phí trả trễ từ đơn giá phạt từng dòng thiết bị và số lượng còn lại, không lấy từ cột phí đã thu trên phiếu | `RentalActivity.java`, `RentalCalculator.java` |
| Trả từng phần | Tích lũy phí quá hạn/bồi thường qua nhiều lần trả, giữ nguyên trạng thái `QUA_HAN` nếu chưa trả đủ, chống yêu cầu số lượng âm/vượt tồn/không thay đổi, tính hoàn cọc trên tổng phí đã thu | `DatabaseHelper.java`, `RentalActivity.java` |
| Kiểm tra tiền | Chặn phép nhân số lượng × đơn giá dịch vụ vượt giới hạn `int`, hạn chế overflow khi tính phí thuê và hoàn cọc | `DatabaseHelper.java`, `RentalCalculator.java` |
| Hóa đơn dài | Ngắt sang trang mới khi có nhiều dòng hàng/dịch vụ, giữ phần tổng kết trên một trang | `InvoiceGenerator.java` |
| Hẹn giờ đèn | Chọn lịch sắp kết thúc gần nhất theo từng sân, lập lại alarm cho lịch tiếp theo sau khi xử lý; xác nhận lịch còn hiệu lực trước khi tắt đèn | `LightScheduler.java`, `LightAutoOffReceiver.java` |
| Đổi sân lịch đặt | Cập nhật cả lịch đèn sân cũ lẫn sân mới khi chuyển lịch đặt | `BookingFormActivity.java` |
| Tài liệu | Đồng bộ README với DB schema v6, bổ sung changelog, test mới và ghi chú kỹ thuật | `README.md`, `CHANGELOG.md`, `RentalCalculatorTest.java` |

**Không thay đổi cấu trúc SQLite hoặc migration**: `DB_VERSION` vẫn là 6. **Chưa thay đổi targetSdk 34** vì nâng target lên 36 phải kiểm tra compatibility, quyền alarm, edge-to-edge, chia sẻ tệp và tính ổn định trên thiết bị.

## 3. Những phần chưa xử lý — bắt buộc trước khi kinh doanh thật

1. **P0 - Toàn vẹn thanh toán:** Gộp thao tác kết thúc phiên và thanh toán vào transaction nhất quán hoặc có quy trình khôi phục công nợ; cho phép điều chỉnh/hoàn tiền có lịch sử kiểm soát; đối soát tất cả bút toán.
2. **P0 - Phí phát sinh vượt cọc:** Tạo khoản phải thu riêng cho phần chênh lệch, không chỉ giảm số hoàn cọc về 0.
3. **P0 - Phân quyền & bảo mật:** Chủ sân/Quản lý/Lễ tân, PIN hoặc tài khoản, nhật ký gắn người thực hiện, mã hóa token đèn, backup có bảo vệ, cấu hình network security hạn chế HTTP.
4. **P0 - Công bố ứng dụng:** Nâng `targetSdk` lên ít nhất 36 nếu phát hành bản cập nhật Android thông thường lên Google Play theo chính sách từ 31/08/2026; chạy regression trên các Android phù hợp trước khi publish.
5. **P1 - Nghiệp vụ nâng cao:** Tách giá đặt trước/giờ cao điểm/cuối tuần, phụ phí quá giờ và buffer giữa các lượt; bảo vệ dữ liệu học phí và lệ phí sự kiện trước xóa cứng.
6. **P1 - Codebase:** Di chuyển truy vấn nặng khỏi UI thread; tách `DatabaseHelper` thành repository/DAO/service theo module; thêm test DB và instrumentation.
7. **P1 - Đèn thực tế:** Thêm nhật ký gửi/nhận lệnh, retry và báo trạng thái thiết bị, kiểm thử khi app bị kill, reboot, Doze và không có quyền exact alarm.
8. **P2 - SaaS:** Backend, phân quyền đa thiết bị/đa chi nhánh, booking khách hàng online, thông báo, POS/VietQR qua đối tác thanh toán có khả năng xác nhận và đối soát giao dịch.

## 4. Kiểm thử và mức độ tin cậy

Đã biên dịch `javac` các lớp Java thuần (`DateTimeUtils`, `PhoneUtils`, `RentalCalculator`, `Rental`, `RentalItem`) và chạy **27/27 phương thức test Java thuần đạt** bằng một bộ thực thi dùng các assertion tương thích JUnit tại chỗ. Trong đó có 3 test được bổ sung cho logic phí quá hạn. Đây **không phải** kết quả từ Gradle/JUnit thật; chưa thể chạy `testDebugUnitTest` trong môi trường hiện tại. Các tính năng Android/SQLite, ngắt trang PDF trên Android, alarm, receiver và UI **chưa được build/chạy trên emulator hoặc máy thật ở môi trường này**; không nên tuyên bố bản patch đã qua QA toàn diện.

### Test thủ công bắt buộc

- Lưu dữ liệu thật bằng chức năng backup. Kiểm tra bản backup có thể khôi phục trên thiết bị thử nghiệm.
- Phiên chơi: mua một sản phẩm `BAN` phải trừ tồn; sản phẩm `THUE` không được bán như hàng hóa.
- Phiếu thuê: trả lần 1 một phần + phí quá hạn/bồi thường; lần 2 trả nốt + phí thêm, tổng phí phải cộng dồn và số hoàn cọc phải đúng. Trả số lượng vượt còn lại phải bị từ chối, tồn không đổi.
- Lịch đèn: hai lượt đặt cùng sân cùng ngày; đặt lượt thứ hai không được làm mất alarm của lượt thứ nhất. Sau lượt thứ nhất, alarm thứ hai phải có; đổi lịch từ sân A sang B phải cập nhật cả hai.
- Hủy/chuyển lịch sau khi đã lên alarm: alarm cũ không được tắt đèn cho lịch không còn hợp lệ.
- Hóa đơn với 30–60 dòng dịch vụ: không mất dòng, tổng tiền/trạng thái in đủ, PDF mở/được chia sẻ trên Android.
- Kiểm tra các thao tác hiện có: sao lưu/khôi phục v4-v6, phiên qua 0h, thanh toán, thu cọc, hóa đơn, lịch lớp/sự kiện và điều khiển đèn thật.

## 5. Lộ trình nâng cấp sau bản vá

**Giai đoạn A — Ổn định giao dịch và bảo mật:** hoàn thiện test DB, sửa luồng thanh toán nguyên tử, xử lý công nợ vượt cọc, cấp quyền, backup an toàn, targetSdk và QA thực tế.

**Giai đoạn B — Nâng UX vận hành:** dashboard trực quan theo trạng thái sân, timeline theo giờ với drag/drop có xác nhận, quy tắc giá giờ cao điểm, khách hàng thường xuyên, gói thành viên, thông báo đến hạn trả và báo cáo xuất Excel.

**Giai đoạn C — Mở rộng thương mại:** backend, đồng bộ nhiều thiết bị, portal đặt sân cho khách, API thanh toán/đối soát chính thức, quản lý nhiều cơ sở, audit và báo cáo đa chi nhánh.

**Giai đoạn D — Nâng cao:** phân tích doanh thu/sử dụng sân, gợi ý khung giờ, dự báo công suất. Không nên ưu tiên AI trước khi có dữ liệu sạch và hệ thống giao dịch ổn định.

## 6. Cách dùng bản dev

1. Giải nén tệp source patch, mở thư mục `CourtMatePickleballCourtManager` trong Android Studio.
2. Sao lưu dữ liệu trước khi cài bản được biên dịch từ mã đã sửa.
3. Chạy `./gradlew testDebugUnitTest` và `./gradlew assembleDebug` (Windows dùng `gradlew.bat`).
4. Kiểm tra thủ công theo danh sách mục 4 trước khi sử dụng bản APK trong môi trường thật.

Lưu ý: `versionName` của bản dev tăng lên `1.2.0-dev`, `versionCode=2`; đây chỉ là nhãn bản mã nguồn thử nghiệm, không phải chứng nhận phát hành.
