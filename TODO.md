# CourtMate v2 roadmap tồn đọng

- [ ] Thống nhất nguồn lịch sân server-only, đồng bộ local SQLite và API (đổi lịch/cancel/event/court schedule) có conflict resolution, queue offline và idempotency keys.
- [ ] Đăng nhập cloud có token per-account, revoke/rotation, RBAC máy chủ; mã hóa dữ liệu offline và backup.
- [ ] Chốt nhà cung cấp PSP/ngân hàng, triển khai verify chữ ký và server-to-server reconciliation thực theo tài liệu chính thức, xử lý hoàn tiền.
- [ ] Chuyển 24+ màn hình còn lại sang ViewModel/Repository/DAO, tách `DatabaseHelper` theo miền; instrumentation migration test.
- [ ] Thiết kế đồng nhất UI/UX trên toàn bộ 26+ màn hình và thử accessibility/performance.
- [ ] Build và thử Android thiết bị thật, cập nhật target SDK an toàn với edge-to-edge trước khi phát hành.

---

# TODO - CourtMate

Ghi chú: project ban đầu không có file TODO. Danh sách dưới đây được lập từ yêu cầu triển khai và trạng thái thực tế sau khi hoàn thành.

## P0 - Bắt buộc

- [x] Sao lưu/khôi phục DB an toàn, lưu mốc sao lưu gần nhất.
- [x] Kiểm tra toàn vẹn DB trước và sau khôi phục.
- [x] Tách doanh thu thực thu, công nợ và cọc đang giữ.
- [x] Ghi nhận cọc thuê thiết bị vào bảng thanh toán.
- [x] Xóa khách an toàn bằng soft-delete, có khôi phục.
- [x] Xóa sân/dịch vụ an toàn, không mất lịch sử.
- [x] Ngừng bán/mở bán lại dịch vụ.

## P1 - Nghiệp vụ chính

- [x] Trả thiết bị từng phần.
- [x] Chống trả trùng phiếu thuê.
- [x] Đăng ký lại lớp/sự kiện đã hủy.
- [x] Chặn đặt lịch trùng phiên đang chơi.
- [x] Xử lý phiên qua 0h.
- [x] Dashboard là màn hình launcher.
- [x] Bỏ MoMo online, dùng QR nội bộ xác nhận tay.

## P2 - Thanh toán

- [x] Sổ thanh toán hiển thị tổng net sau hoàn cọc/điều chỉnh.
- [x] Cọc đang giữ hiển thị trên Dashboard.
- [x] Thực thu/công nợ/cọc hiển thị trên Thống kê.
- [x] Ghi nhận hoàn cọc và cấn trừ cọc.

## P3 - Migration và tương thích

- [x] Nâng schema v4 lên v5 additive.
- [x] Thêm cột `phien_choi.phuong_thuc_tt_moi`.
- [x] Giữ tương thích cột `phien_choi.phuong_thuc_tt`.
- [x] Thêm index cho các bảng chính.
- [x] Không xóa bảng/cột cũ.

## P4 - UI và chất lượng

- [x] Timeline sân theo ngày.
- [x] Hiển thị trạng thái Trống/Đang chơi/Có lịch/Bảo trì.
- [x] Hiển thị phiên qua 0h trên Timeline.
- [x] Rà soát encoding tiếng Việt.
- [x] Mở rộng unit test thời gian và tính toán thuê.

## P5 - Build và tài liệu

- [x] Chạy unit test.
- [x] Build APK debug.
- [x] Tạo README.
- [x] Tạo CHANGELOG.
- [x] Tạo kịch bản demo.
- [x] Tạo danh sách test case.
- [x] Tạo báo cáo triển khai.

## Việc cần kiểm tra thêm

- [ ] Chạy instrumentation test trên emulator/thiết bị thật.
- [ ] Kiểm tra migration bằng bản sao DB v4 có dữ liệu thật.
- [ ] Đổi tên ID layout `panelMomo`/`ivMomoQr` nếu muốn thuần ngữ nghĩa QR.
