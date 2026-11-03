# Danh mục ứng dụng trong DTDD

Audit source và nhánh ngày 03–04/11/2026. `main` tiếp tục là YangCareer, dự án
FastAPI/SQLite kế thừa TalentBridge của Trịnh Văn Hào theo MIT. Các nhánh bên dưới
là các cây ứng dụng độc lập; không merge toàn bộ chúng vào `main` của YangCareer.

## Snapshot và phân loại

| Ứng dụng / nhánh | Commit đã đọc và kiểm tra | Stack và điểm kỹ thuật | Phân loại |
| --- | --- | --- | --- |
| [YangSlots / yangslots](https://github.com/nguyenhonggiang2407/DTDD/tree/yangslots) | `d9419ac787bf5bdec3686c342a8e2c4f353f7b40` | Java 17, Spring Boot, Spring JDBC, H2; giữ chỗ hết hạn, kiểm tra sức chứa và idempotency trong transaction | Supporting; ứng viên tách đầu tiên cho định hướng Java |
| [YangDocs / yangdocs-preview](https://github.com/nguyenhonggiang2407/DTDD/tree/yangdocs-preview) | `29c218b69767592dc405594774f931c23c420c1e` | Python 3.12, FastAPI, SQLite FTS5; tài liệu riêng theo workspace và đoạn trích có trang nguồn | Supporting; ứng viên tách thứ hai |
| [YangQueue / yangqueue](https://github.com/nguyenhonggiang2407/DTDD/tree/yangqueue) | `47f73b10fb9d6607ed21987bbc3980276773fa66` | Node.js 24, SQLite WAL, Worker Threads; retry, lease/fencing, idempotency và artifact riêng | Supporting; ứng viên tách thứ ba |
| [YangData / yangdata](https://github.com/nguyenhonggiang2407/DTDD/tree/yangdata) | `587b812b5f74e5501e5acbcf45d17c10811d2389` | Node.js 24, SQLite; CSV có kiểu dữ liệu, query builder tham số hóa, query plan và báo cáo | Supporting |
| [YangHooks / yanghooks](https://github.com/nguyenhonggiang2407/DTDD/tree/yanghooks) | `6831b9635107ff848e11a53eb20c3c5d9b7c7f20` | Node.js 24, SQLite; HMAC theo byte thô, cửa sổ timestamp, replay và xoay khóa | Supporting; giao thức lab riêng |
| [YangLab / yanglab](https://github.com/nguyenhonggiang2407/DTDD/tree/yanglab) | `4ff287fbfdf97ff6c8015888e4fcc83056558f74` | Node.js 24, SQLite; YangMinutes, YangLaunch, YangSpec dùng chung phiên/revision/audit | Supporting; giữ thành monorepo khi tách |

Mỗi nhánh có README, LICENSE, NOTICE, source, CI, hướng dẫn Docker và screenshot
có sẵn. Các nhận định ở bảng dựa trên cấu trúc/source, không tự chứng nhận các
ảnh cũ hoặc toàn bộ tính năng đã được thử lại. Không phát hiện repository độc lập
mang các tên trên trong danh sách tài khoản được audit ở đầu đợt này.

## Kiểm tra thực sự đã chạy

Các nhánh được checkout thành worktree tách biệt tại đúng SHA trong bảng, không
đổi default branch hoặc push lên các nhánh đó. Database kiểm thử là dữ liệu local.

| Nhánh | Lệnh | Kết quả |
| --- | --- | --- |
| yangslots | `mvnw.cmd clean verify` với JDK 17.0.21 | BUILD SUCCESS; 24 bài JUnit, 0 fail/error/skip; có `target/yangslots.jar` (03/11) |
| yangdocs-preview | `python -m pip check`, `python -m pytest tests/test_documents.py -q`, `python -m ruff check app tests`, `python -m ruff format --check app tests` | 28 bài pass, Ruff pass và 8 file đã đúng định dạng; một cảnh báo deprecation Starlette dùng httpx (04/11) |
| yangqueue | `npm ci`, `npm test` | 33 bài pass; npm audit khi cài báo 0 advisories (03/11) |
| yangdata | `npm ci`, `npm test` | 35 bài pass; npm audit khi cài báo 0 advisories (03/11) |
| yanghooks | `npm ci`, `npm test` | 22 bài pass; npm audit khi cài báo 0 advisories (03/11) |
| yanglab | `npm ci`, `npm test` | 24 bài pass; npm audit khi cài báo 0 advisories (03/11) |

Node dùng bản 24.19.0 trên Windows; YangDocs dùng Python 3.12.14 trong virtualenv
riêng theo `requirements-dev.txt`. Chưa chạy lại bộ browser/Docker của sáu nhánh,
chưa triển khai chúng lên public URL và chưa xác minh screenshot cũ khớp runtime mới.
Kết quả npm audit chỉ phản ánh advisory database lúc cài, không chứng nhận an toàn tuyệt đối.

Ví dụ checkout đúng ứng dụng để thử riêng:

```powershell
git clone --branch yangslots --single-branch https://github.com/nguyenhonggiang2407/DTDD.git YangSlots
Set-Location YangSlots
.\mvnw.cmd clean verify
```

Node branches dùng `npm ci`, `npm test`, `npm start`. YangDocs dùng hướng dẫn
Python trong README của nhánh, không dùng `npm start`. Không dùng lệnh chạy của
YangCareer trên các nhánh ứng dụng này.

## Nguồn và attribution

- YangCareer giữ MIT và NOTICE của TalentBridge / Trịnh Văn Hào.
- Sáu nhánh giữ MIT, NOTICE và nguồn tham chiếu từ `patchy631/ai-engineering-hub`
  được tiếp cận qua fork Trinhvhao, snapshot `86dc1699fe036e5cd21ed7d3c57419a7fc3c844b`.
- Các bản nguồn tham chiếu trong `upstream/` là một phần provenance cần giữ.
  NOTICE từng nhánh phân biệt mã tham chiếu với phần triển khai mới.
- YangSlots còn có thông báo tooling trong `LICENSES/`. Không bỏ chúng khi chuyển repo.

Không đổi tác giả lịch sử hoặc thêm mốc phát triển giả để tạo cảm giác repo mới
được phát triển lâu hơn. Chủ sở hữu, license và nguồn của các phần kế thừa cần
được giữ theo từng nhánh, không áp một tuyên bố tác giả chung lên mọi file.

## Quyết định tổ chức trong đợt này

Giữ nguyên cả sáu nhánh và ghi rõ đường vào. Chưa tạo repository mới vì chưa
nghiệm thu chuyển repo: công cụ hiện dùng chưa xác minh khả năng tạo repo, các link
CI/README còn gắn DTDD, và browser/Docker chưa được kiểm tra lại sau chuyển.
Các unit/API tests pass là điều kiện tốt để tách, chưa đủ để gọi việc chuyển repo hoàn tất.

Thứ tự đề xuất: **YangSlots → YangDocs → YangQueue → YangData → YangHooks → YangLab**.
YangLab dùng chung runtime/store; tách cả monorepo trước, không cắt ba app mất phần chung.

Khi thực hiện đợt chuyển:

1. Tạo repo trống đúng tên; đối chiếu tên chưa được dùng và quyền push.
2. Push nguyên commit tip và toàn bộ lịch sử reachable của nhánh vào `main` repo mới,
   dùng push fast-forward thông thường; không squash/copy-file để giả lịch sử mới.
3. Kiểm tra source tree, LICENSE/NOTICE/upstream và các commit gốc còn truy cập được.
4. Cập nhật URL clone, badge, workflow branch filters, screenshot links và metadata
   chỉ ở repo mới; kiểm tra build/API/browser/Docker phù hợp.
5. Thêm link hai chiều vào catalog/Profile sau khi repo mới thực sự tồn tại.
   Giữ nhánh DTDD gốc trong giai đoạn đầu, không xóa hoặc force push.

Không có billing, domain, database thật hoặc dịch vụ hosting nào được thay đổi
để thực hiện audit tổ chức này.
