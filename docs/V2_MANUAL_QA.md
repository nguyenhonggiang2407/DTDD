# CourtMate 2.0-alpha — Checklist kiểm thử trước khi sử dụng

Đây là checklist chưa được chạy trên emulator/thiết bị trong môi trường xây dựng. Không gộp các ca này vào số **39 Java** hoặc **8 backend pytest** đã chạy.

## Chuẩn bị

- [ ] Sao lưu DB CourtMate v6 từ bản 1.2 vào một tệp riêng, giữ an toàn.
- [ ] Tạo thiết bị Android/emulator API 24, 29, 34, 35/36 và kiểm tra UI dark/light, portrait/landscape.
- [ ] Build `:app:testDebugUnitTest`, `:app:assembleDebug` và `:app:connectedDebugAndroidTest` khi có Android SDK.
- [ ] Đảm bảo thiết bị không có tài khoản demo mặc định; bắt buộc tạo Admin.

## Đăng nhập & phân quyền

- [ ] Fresh install: mở `LoginActivity`, tạo Admin từ giao diện và vào Dashboard.
- [ ] Không cho phép mật khẩu ít hơn 10 ký tự, tên không hợp lệ, tên trùng.
- [ ] Sau 5 mật khẩu sai, khóa đăng nhập 5 phút; đóng mở Activity vẫn bị khóa.
- [ ] Tạo một Staff bằng Admin tại `Cài đặt → Tài khoản & phân quyền`.
- [ ] Staff được xem lịch/khách/phiên và thu tiền; không được mở Settings, Ledger, Statistics, Users, Cloud, giá dịch vụ, đèn, nhật ký.
- [ ] Staff không nhìn thấy doanh thu, công nợ, tiền cọc trên Dashboard.
- [ ] Admin khóa Staff; tài khoản Staff không thể đăng nhập lại.
- [ ] Đổi mật khẩu phải yêu cầu mật khẩu cũ chính xác, sau khi đổi logout và không cho dùng mật khẩu cũ.
- [ ] Đăng xuất, force-stop và khởi động lại đều yêu cầu đăng nhập (không có session persist).
- [ ] Thử launch mọi Activity qua `adb am start` để xác nhận non-exported và role guard.
- [ ] Upgrade DB v6→v7 từ bản cũ **không xóa** lịch đặt, cọc, hóa đơn; vẫn tạo được Admin.
- [ ] Khôi phục DB v6 qua Settings, logout và yêu cầu khởi tạo Admin trong DB mới.

## Phiên chơi & thanh toán nguyên tử

- [ ] Mở phiên chơi, thêm dịch vụ và phí thuê, xác nhận tiền mặt: chỉ 1 bản ghi sổ, 1 trạng thái DA_THANH_TOAN, 1 mã hóa đơn.
- [ ] Nhấn nút thanh toán nhiều lần nhanh; DB chặn trùng; số tiền không bị cộng hai lần.
- [ ] Thử QR nội bộ và xác nhận tay: phương thức hiển thị đúng QR_NOI_BO.
- [ ] Dừng app giữa lúc transaction chưa commit (fault injection test): rollback cả trạng thái phiên và sổ thanh toán.
- [ ] Phiên từ bản v1.2 đã DA_KET_THUC nhưng CHUA_THANH_TOAN thanh toán được một lần.
- [ ] Kiểm tra số tiền quá `Integer.MAX_VALUE`: không bị âm/thu 0 do tràn số.
- [ ] Kiểm tra hóa đơn PDF nhiều trang, trả thiết bị từng phần và lịch đèn sau thanh toán.

## Cloud & website khách

- [ ] Chạy backend với token mạnh, `COURTMATE_PUBLIC_BOOKING=false` mặc định; public endpoints phải trả 503.
- [ ] Bật public portal trên môi trường test, tạo sân bằng Admin, website `/` liệt kê và đặt sân được.
- [ ] Hai khách đặt cùng giờ cùng sân đồng thời: đúng một yêu cầu thành công, yêu cầu khác trả 409.
- [ ] Khung giờ chạm mép giờ kết thúc được đặt; khung chồng nhau bị từ chối.
- [ ] Yêu cầu PENDING hết 20 phút được giải phóng; đơn đã CONFIRMED không hết hạn.
- [ ] Admin duyệt/hủy đặt; lịch BANK_PENDING hoặc PAID không thể hủy trực tiếp.
- [ ] Customer lookup chỉ thành công với random access token tương ứng; không trả thông tin khách khác.
- [ ] Android Cloud chỉ kết nối qua URL HTTPS; token không được lưu dưới dạng SharedPreferences plaintext.
- [ ] Hai máy Android cùng cloud URL/token thấy danh sách online giống nhau (qua Refresh).
- [ ] Không bật đồng thời 2 nguồn lịch offline/cloud cho cùng sân trước khi có chiến lược sync dữ liệu chính thống.

## Ngân hàng & webhook adapter

- [ ] Không có partner env: `bank-intent` trả 503, không ghi PAID.
- [ ] Không có chữ ký, sai chữ ký, timestamp quá hạn và sai số tiền: webhook bị từ chối.
- [ ] Gửi cùng `event_id` 2 lần: không tăng tiền/doanh thu 2 lần.
- [ ] Đối tác thật: kiểm tra chữ ký theo spec của đối tác **trước khi** đưa vào adapter contract chung.
- [ ] Test chuyển tiền thiếu/thừa, webhook đến trễ, hoàn/hủy tiền, double settlement và thất bại callback.

## Bảo mật và vận hành

- [ ] Giấu token đèn, token cloud khỏi log và crash reports.
- [ ] Kiểm tra phân quyền file backup; sao lưu thủ công hiện plaintext, chưa an toàn cho đưa lên cloud.
- [ ] Test mất internet, lỗi DNS, timeout TLS/certificate, server restart & database backup.
- [ ] Rà accessibility, font lớn, TalkBack, cỡ màn hình nhỏ, biểu đồ, bảng danh sách dài và hiệu năng.
