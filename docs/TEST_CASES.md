# Danh sách test case

## Unit test tự động

Chạy:

```powershell
.\gradlew.bat testDebugUnitTest
```

| Nhóm | Test case | Kỳ vọng |
|---|---|---|
| DateTimeUtils | durationMinutes | Trả đúng số phút, trả 0 với khoảng không hợp lệ |
| DateTimeUtils | estimateTotal | Làm tròn nghìn đúng |
| DateTimeUtils | utcDateMillis | Chuyển đổi ngày không lệch múi giờ |
| DateTimeUtils | minutesBetweenDbDateTime | Qua 0h và qua năm đúng |
| DateTimeUtils | estimateSessionFee | Tính phí phiên qua 0h đúng |
| DateTimeUtils | addHoursToDbDateTime | Cộng giờ qua ngày đúng |
| RentalCalculator | calculateDeposit | Tổng cọc theo số lượng đúng |
| RentalCalculator | calculateRentFee | Tổng phí thuê đúng |
| RentalCalculator | lateMinutes | Không âm, tính đúng khi quá hạn |
| RentalCalculator | lateFee | Làm tròn nghìn đúng |
| RentalCalculator | refund | Không hoàn âm, xử lý phí âm như 0 |

## Kiểm thử thủ công trọng yếu

| ID | Bước | Kỳ vọng |
|---|---|---|
| TC-01 | Thanh toán phiên bằng QR nội bộ | Lưu thành công, không lỗi CHECK constraint |
| TC-02 | Xem lại phiên đã thanh toán QR | Hiển thị phương thức QR nội bộ |
| TC-03 | Tạo phiếu thuê có cọc | Sinh giao dịch COC trong sổ thanh toán |
| TC-04 | Dashboard sau khi tạo phiếu thuê | Cọc đang giữ tăng đúng |
| TC-05 | Trả toàn bộ thiết bị không phát sinh phí | Sinh giao dịch HOAN_COC đúng số tiền |
| TC-06 | Trả toàn bộ thiết bị có phí | Sinh HOAN_COC và DIEU_CHINH, cọc giữ về 0 |
| TC-07 | Trả lại phiếu đã trả | Bị chặn, không sinh giao dịch trùng |
| TC-08 | Xóa khách có lịch đặt | Khách chuyển sang ngừng hoạt động |
| TC-09 | Khôi phục khách đã ngừng | Khách hoạt động lại, dữ liệu lịch sử giữ nguyên |
| TC-10 | Xóa dịch vụ đã có giao dịch | Báo không thể xóa, gợi ý ngừng bán |
| TC-11 | Ngừng bán/mở bán dịch vụ | Trạng thái đổi đúng, không mất dữ liệu |
| TC-12 | Đặt lịch trùng phiên đang chơi | Báo lỗi trùng phiên |
| TC-13 | Phiên bắt đầu hôm trước còn đang chơi | Timeline hôm nay hiển thị Đang chơi và nhãn qua 0h |
| TC-14 | Sổ thanh toán có hoàn cọc | Tổng net trừ đúng khoản hoàn |
| TC-15 | Sao lưu rồi khôi phục | Dữ liệu trở về đúng bản sao lưu |
| TC-16 | Khôi phục tệp sai | Báo tệp không hợp lệ, dữ liệu hiện tại giữ nguyên |
| TC-17 | Nâng cấp từ DB v4 lên v5 | Giữ dữ liệu cũ, thêm cột mới, ứng dụng mở bình thường |
