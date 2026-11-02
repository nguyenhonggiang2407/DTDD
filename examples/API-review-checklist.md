# Checklist review API

Tài liệu giả lập dành cho bài thực hành YangDocs.

## Xác thực và phân quyền

Mọi thao tác đọc, sửa hoặc xóa phải kiểm tra workspace sở hữu tài nguyên. Cookie phiên dùng HttpOnly và SameSite. Các yêu cầu thay đổi dữ liệu cần token CSRF và kiểm tra Origin. Trả về 404 cho tài nguyên không thuộc phiên hiện tại.

## Tải tài liệu

Chỉ chấp nhận PDF có văn bản, Markdown và TXT UTF-8. Mỗi tài liệu tối đa 5 MB và 80 trang. Không giữ lại tệp gốc sau khi trích xuất. Từ chối PDF có mật khẩu và thông báo rõ khi PDF ảnh quét cần OCR.

## Kiểm thử tìm kiếm

Tìm kiếm tiếng Việt cần chấp nhận từ khóa có dấu và không dấu. Kiểm tra số trang của trích dẫn, nội dung xuất Markdown, tài liệu trùng và tính nhất quán của chỉ mục sau khi xóa. Không gán nhãn đoạn trích là câu trả lời do AI tạo.
