# Nguồn gốc và thay đổi

YangLab được phát triển và cá nhân hóa cho **Nguyễn Hồng Giang**
([nguyenhonggiang2407](https://github.com/nguyenhonggiang2407)).
Ba ứng dụng dùng chung một runtime Node.js/SQLite, nằm trong cùng một monorepo.

Các điểm xuất phát dưới đây thuộc
[patchy631/ai-engineering-hub](https://github.com/patchy631/ai-engineering-hub),
được tiếp cận qua fork [Trinhvhao/ai-engineering-hub](https://github.com/Trinhvhao/ai-engineering-hub).
Snapshot đã đọc: **86dc1699fe036e5cd21ed7d3c57419a7fc3c844b**.
Giữ nguyên MIT License và **Copyright (c) 2024 patchy631** trong [LICENSE](LICENSE).

| Ứng dụng    | Nguồn MIT đã đọc                                                                                                                                                                  | Phần được sử dụng và phát triển                                                                                                                |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| YangMinutes | [Meeting data models](https://github.com/Trinhvhao/ai-engineering-hub/blob/86dc1699fe036e5cd21ed7d3c57419a7fc3c844b/multilingual-meeting-notes-generator/src/data/data_models.py) | Chuyển mô hình ActionItem thành dữ liệu JavaScript có validation; bổ sung biên bản, checklist có cấu trúc, phân công, hạn, phiên bản và export |
| YangLaunch  | [Content scheduler](https://github.com/Trinhvhao/ai-engineering-hub/blob/86dc1699fe036e5cd21ed7d3c57419a7fc3c844b/content_planner_flow/scheduler.py)                              | Port hàm json_to_typefully_content thành formatThread; thay lệnh gọi Typefully bằng bảng kế hoạch, luồng duyệt, lịch và export thủ công        |
| YangSpec    | [Documentation models](https://github.com/Trinhvhao/ai-engineering-hub/blob/86dc1699fe036e5cd21ed7d3c57419a7fc3c844b/documentation-writer-flow/schemas/models.py)                 | Tham khảo cấu trúc title/description/examples của DocPlan; viết mới trình thiết kế endpoint, suy schema từ JSON mẫu và bộ xuất OpenAPI         |

Các bản nguồn nguyên vẹn tương ứng nằm trong thư mục [upstream](upstream).
Chúng chỉ là bản tham chiếu, không được import vào runtime hoặc chạy khi kiểm thử.
Không sao chép tệp nghiên cứu, dữ liệu cá nhân, model, logo hoặc ảnh của tác giả.

Runtime, UI, lưu trữ, phân quyền workspace, optimistic locking và kiểm thử của
YangLab được viết mới. YangSpec là hướng phát triển mới lấy cảm hứng từ mô hình
tài liệu, không phải bản port của bộ sinh tài liệu upstream. Không nhận công
trình hoặc khả năng của upstream là thành tích tự thực hiện.

Các tên người trong dữ liệu mẫu là giả lập. Tên **Nguyễn Hồng Giang** trên giao
diện và README là tên người duy trì bản cá nhân hóa; tên tác giả gốc được giữ
trong ghi nguồn và giấy phép.

Phần mã mới của YangLab: Copyright (c) 2026 Nguyễn Hồng Giang, phát hành theo
các điều khoản MIT trong LICENSE, cùng với thông báo bản quyền upstream.
