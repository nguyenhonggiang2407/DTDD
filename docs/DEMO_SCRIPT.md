# Kịch bản demo CourtMate

## Chuẩn bị

1. Build và cài APK debug:
   ```powershell
   .\gradlew.bat assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
2. Mở ứng dụng, vào Cài đặt để kiểm tra phiên bản và nút sao lưu.
3. Chuẩn bị ít nhất 2 sân, 1 khách hàng, 1 dịch vụ bán và 1 dịch vụ cho thuê.

## Kịch bản 1: Đặt lịch và thanh toán

1. Từ Dashboard chọn Lịch đặt sân.
2. Tạo lịch cho khách, chọn sân và khung giờ.
3. Thử đặt trùng giờ để thấy thông báo lỗi.
4. Vào Phiên chơi, bắt đầu phiên từ lịch đã đặt.
5. Thêm dịch vụ, kết thúc phiên.
6. Chọn thanh toán QR nội bộ, xác nhận đã nhận tiền.
7. Mở Sổ thanh toán để kiểm tra giao dịch.

## Kịch bản 2: Timeline và phiên qua 0h

1. Từ Dashboard chọn Timeline sân.
2. Kiểm tra trạng thái Trống/Đang chơi/Có lịch/Bảo trì.
3. Chuyển sang ngày mai để xem lịch đã đặt.
4. Nếu có phiên bắt đầu trước 0h và còn đang chơi, Timeline hiển thị nhãn “qua 0h”.
5. Thử đặt lịch sát giờ của phiên đang chơi để thấy thông báo trùng phiên.

## Kịch bản 3: Thuê và trả thiết bị từng phần

1. Mở Thuê thiết bị, tạo phiếu thuê có cọc.
2. Kiểm tra Dashboard hiển thị cọc đang giữ.
3. Trả 1 phần số lượng, xác nhận phiếu vẫn ở trạng thái đang thuê.
4. Trả phần còn lại, nhập phí quá hạn/bồi thường nếu có.
5. Kiểm tra cọc được hoàn hoặc cấn trừ trong Sổ thanh toán.
6. Thử trả lại lần nữa để thấy hệ thống chặn trả trùng.

## Kịch bản 4: Xóa an toàn và khôi phục

1. Mở Khách hàng, chọn một khách đã có lịch đặt.
2. Chọn xóa, xác nhận khách được ngừng hoạt động thay vì xóa vĩnh viễn.
3. Bấm “Khách đã ngừng”, chọn khách và khôi phục.
4. Mở Dịch vụ, thử xóa dịch vụ đã phát sinh đơn hàng.
5. Chọn ngừng bán, sau đó mở bán lại.

## Kịch bản 5: Sao lưu và khôi phục

1. Vào Cài đặt, bấm Sao lưu dữ liệu.
2. Chọn vị trí lưu tệp, xác nhận thông báo thành công và mốc thời gian.
3. Thay đổi dữ liệu demo.
4. Bấm Khôi phục, chọn tệp vừa sao lưu.
5. Xác nhận dữ liệu quay về trạng thái trước khi thay đổi.
6. Thử chọn một tệp không phải DB để thấy thông báo không hợp lệ.
