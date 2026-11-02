# Thay đổi của bản YangCareer

- Giữ các luồng backend và giấy phép TalentBridge; ghi rõ nguồn và commit nền.
- Thêm phiên riêng, CSRF và kiểm tra quyền truy cập CV.
- Giới hạn PDF; thay đường dẫn dựa theo tên file bằng file tạm do server tạo.
- Trả PDF riêng tư trực tiếp, không để file preview trong thư mục public.
- Thêm hồ sơ kỹ năng thủ công; so khớp không phân biệt hoa thường.
- Thêm ràng buộc chống ứng tuyển trùng, xóa dữ liệu liên quan cùng CV.
- Sửa cấu hình khóa dịch vụ; phần cơ bản chạy không cần Google SDK.
- Tách CV khỏi chỉ mục vector chung.
- Thay frontend template bằng giao diện mới, dùng dữ liệu mẫu hư cấu.
- Thêm kiểm thử API, kiểm thử giao diện và workflow kiểm tra.

Chưa kiểm chứng lời gọi Gemini thật khi không có khóa dịch vụ.
