# YangCareer trên Render Free

## Bản đang chạy

- Live demo: [https://giang-yangcareer.onrender.com/app/](https://giang-yangcareer.onrender.com/app/).
- Render service: `srv-dbkr15ugekts73eukbmg`, tên `giang-yangcareer`, workspace YangRent, vùng Singapore, gói **Free**.
- Dashboard: [quản lý dịch vụ](https://dashboard.render.com/web/srv-dbkr15ugekts73eukbmg).
- Mã được triển khai: `e32472382b4b1aa7e798958146ed61eec665f7d8` trên nhánh `main`; CI của mã này đã đạt trước khi triển khai.
- Ngày kiểm tra: 03/11/2026. Kiểm tra HTTPS, health API, frontend assets, cookie Secure/HttpOnly, tạo rồi đọc lại dữ liệu hư cấu, từ chối Origin/CSRF không hợp lệ.

Dịch vụ được tạo qua Render MCP, hiện tự deploy theo **On Commit**. `render.yaml` là cấu hình tái tạo; dịch vụ hiện tại chưa được nối với Blueprint nên sửa YAML không tự đổi settings trên Render. Bản YAML vẫn định nghĩa HTTP health path khi dùng Blueprint; dịch vụ MCP hiện dùng TCP readiness, còn health API được kiểm tra riêng qua HTTPS. Khi chuyển sang Blueprint, đối chiếu service hiện có và nguồn cấu hình trước khi sync.


Cấu hình [render.yaml](../render.yaml) dành riêng cho YangCareer trong nhánh `main` của `nguyenhonggiang2407/DTDD`. Không gộp hoặc triển khai các dự án khác trong các nhánh cũ của repository này. Dịch vụ và URL phía trên đã được triển khai và kiểm tra.

## Chạy trên Render

Khi tái tạo trong một workspace khác, tạo Python Web Service từ Blueprint `render.yaml`, chọn **Free**, không thêm disk hoặc database trả phí. Python 3.12 lấy bản vá mới nhất theo `.python-version`. Build `python -m pip install -r requirements.txt`; start `ALLOWED_ORIGINS="${ALLOWED_ORIGINS:-$RENDER_EXTERNAL_URL}" python main.py`.

Start command đọc đúng URL HTTPS do Render cấp để kiểm tra Origin/CSRF; không dùng wildcard. `HOST=0.0.0.0`, cổng lấy từ `PORT` của Render. `COOKIE_SECURE=1` giữ cookie phiên Secure/HttpOnly. Với domain riêng, đặt `ALLOWED_ORIGINS` thành đúng origin HTTPS, hoặc danh sách origin chính xác phân cách bằng dấu phẩy, rồi redeploy.

Mở trang chính để chuyển đến `/app/`. Health check `/health` kiểm tra truy vấn SQLite. Frontend HTML/CSS/JS được Python phục vụ cùng origin, không cần Node.js trong build của ứng dụng.

## Dữ liệu và phần tùy chọn

Database ở `/tmp/yangcareer.sqlite3` chỉ là dữ liệu demo tạm. **Hồ sơ, cookie phiên phía server và lịch sử có thể mất khi Render ngủ, restart hoặc redeploy.** Dữ liệu việc làm mẫu hư cấu được tạo lại khi database mới. Không tải CV thật hoặc dữ liệu cá nhân lên bản portfolio này.

Hồ sơ thủ công, lọc việc, so khớp kỹ năng và lịch sử hoạt động không cần API key. Blueprint không cài `requirements-ai.txt`, không bật index nền và không tạo dịch vụ AI trả phí. Phân tích PDF/Gemini vẫn cần cài dependency và cấu hình riêng; không xem đây là phần đã được triển khai hay đã được kiểm chứng.

Bản Free có giờ chạy dùng chung với các service khác và có thể ngủ khi không có truy cập. Không dùng ping giữ service thức. Muốn lưu hồ sơ lâu dài cần database/host bền vững; cấu hình này không thực hiện chuyển đổi đó.

## Kiểm tra sau khi có URL

1. `GET /health` trả 200 và `/app/` tải HTML, CSS và JavaScript.
2. Mở workspace; cookie có Secure/HttpOnly.
3. Tạo một hồ sơ hư cấu, tìm việc và lưu một ứng tuyển; tải lại để kiểm tra trong cùng lần chạy.
4. Phiên trình duyệt khác không đọc được hồ sơ đó; thao tác ghi sai Origin/CSRF trả 403.
5. Sau restart làm mới workspace nếu database tạm đã mất. Không coi bản Free là lưu trữ bền vững.
6. Chỉ thêm URL demo vào GitHub sau khi kiểm tra trên host thật.

Tham khảo: [Render Free](https://render.com/docs/free), [biến môi trường](https://render.com/docs/environment-variables), [Python version](https://render.com/docs/python-version).
