# YangCareer

**Hosting demo:** đã có cấu hình Render Free. Xem [cách triển khai và giới hạn dữ liệu tạm](docs/render-free.md). Dữ liệu SQLite có thể mất sau khi host ngủ/restart; chưa có URL demo được xác minh.

[![Checks](https://github.com/nguyenhonggiang2407/DTDD/actions/workflows/checks.yml/badge.svg)](https://github.com/nguyenhonggiang2407/DTDD/actions/workflows/checks.yml)

Không gian tạo hồ sơ kỹ năng, xem công việc mẫu và theo dõi ứng tuyển.
Bản phát triển của **Nguyễn Hồng Giang**, dựa trên **TalentBridge của Trịnh Văn Hào**
theo MIT License. Xem [nguồn và phạm vi thay đổi](NOTICE.md).

## Chức năng

- Tạo hồ sơ kỹ năng không cần khóa dịch vụ bên ngoài.
- Tìm kiếm và lọc công việc; sắp xếp theo số kỹ năng trùng khớp.
- Ghi nhận ứng tuyển và xem lịch sử theo từng hồ sơ.
- Tách CV theo phiên trình duyệt, kiểm tra quyền ở API.
- Upload PDF tối đa 10 MB, tạo file tạm bằng tên do server sinh.
- Xem PDF bằng response riêng tư, không xuất file ra thư mục public.
- Phân tích CV và gợi ý qua Gemini/LangChain khi được cấu hình.
- Giao diện tiếng Việt, có bố cục máy tính và điện thoại.

Dữ liệu công việc trong bản này **hoàn toàn hư cấu**. Nút lưu ứng tuyển chỉ lưu vào
ứng dụng, không gửi hồ sơ đến nhà tuyển dụng. So khớp kỹ năng là phép so sánh tập
kỹ năng, không phải dự đoán khả năng trúng tuyển.

## Chạy cục bộ

Cần Python 3.12.

```bash
git clone https://github.com/nguyenhonggiang2407/DTDD.git
cd DTDD
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
python -m pip install -r requirements.txt
python main.py
```

Mở **http://127.0.0.1:9990/app/**. API docs: **http://127.0.0.1:9990/docs**.

Lần chạy đầu tạo SQLite và sáu công việc mẫu. Chưa cấu hình Gemini vẫn dùng được
hồ sơ thủ công, lọc việc làm, so khớp kỹ năng và lịch sử ứng tuyển.

## Phần phân tích CV tùy chọn

```bash
python -m pip install -r requirements-ai.txt
```

Sao chép `.env.example` thành `.env`, điền `GOOGLE_API_KEY` của bạn.
Chọn `GOOGLE_MODEL` và `EMBEDDING_MODEL` đang được tài khoản hỗ trợ.
Đặt `INDEX_ON_STARTUP=1` nếu muốn tạo/cập nhật chỉ mục việc làm.

Không commit `.env`. Bản nâng cấp dùng một khóa cấu hình, không luân phiên khóa
để né giới hạn dịch vụ. PDF chỉ được gửi đến Gemini sau thao tác gửi và chấp thuận
của người dùng. Bản này chưa hỗ trợ PDF scan cần OCR.

Các luồng gọi mô hình thật cần khóa riêng để kiểm chứng. Kiểm thử tự động sử dụng
dữ liệu hư cấu và thay phần trích xuất bên ngoài bằng mock cho bài kiểm tra upload;
không dùng mock để trình bày kết quả dịch vụ thật.

## Phạm vi quyền truy cập

CV và lịch sử gắn với cookie phiên HttpOnly. Thao tác ghi cần CSRF token.
Dữ liệu CV không được thêm vào chỉ mục vector chung của công việc.

Đây là phiên riêng theo trình duyệt, **chưa phải hệ thống tài khoản có khôi phục**.
Cookie hết hạn sau 14 ngày; xóa cookie có thể làm mất quyền truy cập dữ liệu cũ.
CV cũ chưa có owner không tự chuyển cho người truy cập đầu tiên.
Nếu phát triển thành dịch vụ nhiều người dùng, cần bổ sung tài khoản, quy trình
xóa/lưu trữ dữ liệu, giới hạn tài nguyên và HTTPS.

## Kiểm tra

```bash
python -m unittest discover -s tests -v
python -m compileall -q api main.py tests
node --check frontend/app.js
```

Bộ kiểm tra gồm tách phiên, đọc CV chéo, CSRF, Origin, upload không hợp lệ/quá lớn,
dọn file tạm, xóa dữ liệu liên quan, ứng tuyển trùng và phân trang.

Kiểm thử trình duyệt:

```bash
npm install
npx playwright install chromium
npm run test:ui
```

CI chạy kiểm thử API và luồng giao diện, kiểm tra tràn ngang trên điện thoại,
lưu ảnh chụp thực tế sau khi kiểm tra thành công.

## Hình ảnh

![Giao diện máy tính](docs/screenshots/desktop.png)

<details><summary>Giao diện điện thoại</summary>

![Giao diện điện thoại](docs/screenshots/mobile.png)

</details>

## Cấu trúc

- `api/main.py`: các route kế thừa và mở rộng.
- `api/db_utils.py`: schema, migration và truy vấn dữ liệu.
- `api/workspace_security.py`: phiên, CSRF và kiểm tra chủ sở hữu.
- `api/upload_safety.py`: đọc PDF có giới hạn và tạo file tạm.
- `api/providers.py`: nạp phần phân tích bên ngoài khi cần.
- `frontend/`: giao diện mới bằng HTML/CSS/JavaScript, không dùng template gốc.
- `data/demo_jobs.jsonl`: dữ liệu mẫu hư cấu.
- `tests/`: kiểm thử API và trình duyệt.

## Giấy phép và ghi nhận

Giữ nguyên [MIT License](LICENSE), gồm bản quyền của Trịnh Văn Hào.
Mã backend kế thừa từ [TalentBridge](https://github.com/Trinhvhao/TalentBridge),
commit `acb5ad12bb8108c28c0058f997b0595633aad216`.

Phần nâng cấp và thương hiệu YangCareer do Nguyễn Hồng Giang phát triển trên nền đó;
repository này không được trình bày là toàn bộ mã gốc do Nguyễn Hồng Giang viết.
