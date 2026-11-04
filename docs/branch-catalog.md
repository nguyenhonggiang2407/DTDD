# Ứng dụng độc lập và hồ sơ tách từ DTDD

Cập nhật **04/11/2026**: sáu ứng dụng đã được xuất bản thành repository công khai
riêng, default branch `main`. DTDD `main` tiếp tục là YangCareer, dự án FastAPI/SQLite
kế thừa TalentBridge của Trịnh Văn Hào theo MIT. Các nhánh DTDD gốc vẫn còn nguyên.

## Repository hiện tại

| Repository | Stack và điểm kỹ thuật | Source branch được giữ trong DTDD | Source commits giữ nguyên |
| --- | --- | --- | ---: |
| [YangSlots](https://github.com/nguyenhonggiang2407/YangSlots) | Java 17, Spring Boot, Spring JDBC, H2; giữ chỗ hết hạn, capacity và idempotency trong transaction | [yangslots](https://github.com/nguyenhonggiang2407/DTDD/tree/yangslots) | 7 |
| [YangDocs](https://github.com/nguyenhonggiang2407/YangDocs) | Python 3.12, FastAPI, SQLite FTS5; tìm kiếm tài liệu riêng và đoạn trích theo trang | [yangdocs-preview](https://github.com/nguyenhonggiang2407/DTDD/tree/yangdocs-preview) | 9 |
| [YangQueue](https://github.com/nguyenhonggiang2407/YangQueue) | Node.js 24, SQLite WAL, Worker Threads; retry, lease/fencing và idempotency | [yangqueue](https://github.com/nguyenhonggiang2407/DTDD/tree/yangqueue) | 7 |
| [YangData](https://github.com/nguyenhonggiang2407/YangData) | Node.js 24, SQLite; CSV có kiểu dữ liệu, query builder tham số hóa và báo cáo | [yangdata](https://github.com/nguyenhonggiang2407/DTDD/tree/yangdata) | 9 |
| [YangHooks](https://github.com/nguyenhonggiang2407/YangHooks) | Node.js 24, SQLite; HMAC theo byte thô, timestamp, replay và xoay khóa | [yanghooks](https://github.com/nguyenhonggiang2407/DTDD/tree/yanghooks) | 7 |
| [YangLab](https://github.com/nguyenhonggiang2407/YangLab) | Node.js 24, SQLite; YangMinutes, YangLaunch, YangSpec dùng chung runtime/store | [yanglab](https://github.com/nguyenhonggiang2407/DTDD/tree/yanglab) | 5 |

Các số trên là số commit reachable của từng source branch được giữ nguyên,
không phải số commit mới tự tạo hoặc số người dùng. Các commit migration/CI mới
được nối tiếp lịch sử đó. YangLab giữ cả monorepo để ba app không mất runtime chung.

## Source snapshot và provenance

| Repository mới | Source tip đầy đủ | Hồ sơ migration trong repo mới |
| --- | --- | --- |
| YangSlots | `d9419ac787bf5bdec3686c342a8e2c4f353f7b40` | [Migration](https://github.com/nguyenhonggiang2407/YangSlots/blob/main/docs/repository-migration.md) |
| YangDocs | `29c218b69767592dc405594774f931c23c420c1e` | [Migration](https://github.com/nguyenhonggiang2407/YangDocs/blob/main/docs/repository-migration.md) |
| YangQueue | `47f73b10fb9d6607ed21987bbc3980276773fa66` | [Migration](https://github.com/nguyenhonggiang2407/YangQueue/blob/main/docs/repository-migration.md) |
| YangData | `587b812b5f74e5501e5acbcf45d17c10811d2389` | [Migration](https://github.com/nguyenhonggiang2407/YangData/blob/main/docs/repository-migration.md) |
| YangHooks | `6831b9635107ff848e11a53eb20c3c5d9b7c7f20` | [Migration](https://github.com/nguyenhonggiang2407/YangHooks/blob/main/docs/repository-migration.md) |
| YangLab | `4ff287fbfdf97ff6c8015888e4fcc83056558f74` | [Migration](https://github.com/nguyenhonggiang2407/YangLab/blob/main/docs/repository-migration.md) |

Việc tách dùng clone đầy đủ lịch sử reachable của nhánh, đổi nhánh local thành
`main` và push bình thường vào repo trống. Source SHA vẫn là ancestor; không squash,
rewrite tác giả/ngày hoặc force push. Không xóa source branch DTDD.

- YangCareer giữ MIT và NOTICE của TalentBridge / Trịnh Văn Hào.
- Sáu repo mới giữ LICENSE, NOTICE và tài liệu tham chiếu `upstream/` từ
  `patchy631/ai-engineering-hub`, tiếp cận qua fork Trinhvhao, snapshot
  `86dc1699fe036e5cd21ed7d3c57419a7fc3c844b`.
- NOTICE từng repo phân biệt reference với phần triển khai mới; nguồn tham chiếu
  không được đổi thành tác phẩm của tác giả mới. YangSlots giữ cả `LICENSES/` tooling.
- Clone/setup/CI links hiện dùng repo mới. Historical source và CI links vẫn trỏ
  tới đúng snapshot hoặc lần chạy gốc.

## Kiểm chứng khi chuyển và CI mới

Các lượt CI sau đã được kiểm tra API GitHub ngày 04/11/2026: `completed / success`.
Mỗi link là bằng chứng cho đúng commit được chạy, không bảo đảm mọi commit về sau.

| Repository | Kiểm tra local đã thực hiện | CI mới đã xác minh |
| --- | --- | --- |
| YangSlots | Maven/JDK 17: 24 tests, JAR; 6 browser tests trên packaged JAR | [Run 41748585896](https://github.com/nguyenhonggiang2407/YangSlots/actions/runs/41748585896) |
| YangDocs | 28 document/API tests, 3 browser tests, pip/Ruff/syntax; sửa test đọc export UTF-8 trên Windows | [Main CI](https://github.com/nguyenhonggiang2407/YangDocs/actions/runs/41748290675) · [Container smoke](https://github.com/nguyenhonggiang2407/YangDocs/actions/runs/41749380523) |
| YangQueue | Baseline đúng source SHA: 33 backend tests; sau tách 4 browser tests, worker/CSV hash thật | [Run 41748608982](https://github.com/nguyenhonggiang2407/YangQueue/actions/runs/41748608982) |
| YangData | Baseline đúng source SHA: 35 backend tests; sau tách 6 browser tests về query/report/CSV/mobile | [Run 41748737709](https://github.com/nguyenhonggiang2407/YangData/actions/runs/41748737709) |
| YangHooks | 22 backend tests, 4 browser tests, 11 JS modules qua syntax | [Run 41748757517](https://github.com/nguyenhonggiang2407/YangHooks/actions/runs/41748757517) |
| YangLab | 24 backend tests, 7 browser tests, 13 JS modules qua syntax | [Main CI](https://github.com/nguyenhonggiang2407/YangLab/actions/runs/41748986596) · [Container smoke](https://github.com/nguyenhonggiang2407/YangLab/actions/runs/41749383242) |

Screenshot là ảnh từ app đang chạy với dữ liệu hư cấu. Không dùng mockup để giả
thành screenshot hoặc số liệu hoạt động thật. Các file/cây license-reference đã
được đối chiếu khi chuyển. Local Windows không có Docker CLI khả dụng. Docker
build/health smoke của YangSlots, YangQueue, YangData và YangHooks chạy trong main CI;
hai lượt container smoke riêng của YangDocs và YangLab ở bảng trên cũng đã
`completed / success` trên GitHub Actions. Đây là kết quả Linux CI, không phải
tuyên bố Docker đã chạy trên máy Windows local.

Chưa có public application URL được xác minh cho sáu repo mới. Public source và
CI thành công không phải bằng chứng deployment. Không chuyển database cá nhân,
tăng billing hoặc tạo dịch vụ hosting khi tách repository.

## Chạy source hiện tại

```powershell
git clone https://github.com/nguyenhonggiang2407/YangSlots.git
Set-Location YangSlots
.\mvnw.cmd clean verify
```

Node apps dùng `npm ci`, `npm test`, `npm start`. YangDocs dùng Python và hướng
dẫn trong README riêng. Mở repo mới để lấy cấu hình, ảnh, giới hạn và NOTICE hiện
tại; không dùng lệnh YangCareer để chạy các ứng dụng này.

## Historical — audit nhánh ngày 03–04/11/2026 trước khi tách

Phần này lưu bằng chứng audit ban đầu. Khi đó các app còn ở nhánh DTDD; các quyết
định trì hoãn tách tại thời điểm ấy đã được thay bằng việc xuất bản sáu repo ở trên.

| Nhánh nguồn tại SHA trong bảng snapshot | Lệnh đã chạy | Kết quả audit ban đầu |
| --- | --- | --- |
| yangslots | `mvnw.cmd clean verify`, JDK 17.0.21 | BUILD SUCCESS; 24 JUnit, 0 fail/error/skip; có JAR (03/11) |
| yangdocs-preview | pip check, `pytest tests/test_documents.py -q`, Ruff check/format | 28 pass, 8 file formatted; một Starlette/httpx deprecation warning (04/11) |
| yangqueue | `npm ci`, `npm test` | 33 pass; install audit báo 0 advisories (03/11) |
| yangdata | `npm ci`, `npm test` | 35 pass; install audit báo 0 advisories (03/11) |
| yanghooks | `npm ci`, `npm test` | 22 pass; install audit báo 0 advisories (03/11) |
| yanglab | `npm ci`, `npm test` | 24 pass; install audit báo 0 advisories (03/11) |

Audit ban đầu dùng worktree riêng, Node 24.19.0/Python 3.12.14 trên Windows và
database kiểm thử local. Khi đó chưa kiểm tra lại browser/Docker của sáu nhánh;
bằng chứng browser/migration mới nằm ở phần hiện tại. npm audit phản ánh advisory
database khi cài, không phải chứng nhận ứng dụng không có lỗ hổng.
