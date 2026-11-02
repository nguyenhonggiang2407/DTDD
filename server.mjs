import http from 'node:http';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { timingSafeEqual } from 'node:crypto';
import { Store } from './lib/store.mjs';
import { previewCSV } from './lib/datasets.mjs';
import { HttpError, check, object } from './lib/validation.mjs';

const ROOT = dirname(fileURLToPath(import.meta.url)),
  COOKIE = 'yangdata_workspace',
  MAX_BODY = 1024 * 1024;
const FILES = new Map([
  ['/', ['index.html', 'text/html']],
  ['/assets/styles.css', ['styles.css', 'text/css']],
  ['/assets/app.js', ['app.js', 'text/javascript']],
  ['/assets/favicon.svg', ['favicon.svg', 'image/svg+xml']],
]);
function send(res, body, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}
function equal(a, b) {
  const aa = Buffer.from(a || ''),
    bb = Buffer.from(b || '');
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}
function readJSON(req) {
  check(
    (req.headers['content-type'] || '').split(';')[0].trim() === 'application/json',
    'Cần gửi application/json.',
    415,
  );
  check(!req.headers['content-encoding'], 'Không nhận nội dung đã nén.', 415);
  check(Number(req.headers['content-length'] || 0) <= MAX_BODY, 'Request tối đa 1 MB.', 413);
  return new Promise((resolveBody, reject) => {
    let bytes = 0,
      failed = false,
      chunks = [];
    req.on('data', (chunk) => {
      bytes += chunk.length;
      if (bytes > MAX_BODY && !failed) {
        failed = true;
        chunks = [];
        reject(new HttpError(413, 'Request tối đa 1 MB.'));
      } else if (!failed) chunks.push(chunk);
    });
    req.once('end', () => {
      if (failed) return;
      try {
        resolveBody(
          object(
            JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks))),
          ),
        );
      } catch (err) {
        reject(err instanceof HttpError ? err : new HttpError(400, 'JSON không hợp lệ.'));
      }
    });
    req.once('error', reject);
    req.once('aborted', () => reject(new HttpError(400, 'Request đã bị ngắt.')));
  });
}

export const SAMPLE = {
  name: 'Đơn hàng cửa hàng mẫu',
  csv: 'Mã đơn,Khu vực,Nhóm hàng,Doanh thu,Số lượng,Trạng thái\n001,Hà Nội,Văn phòng,245000,5,Hoàn thành\n002,Đà Nẵng,Đồ dùng,180000,2,Hoàn thành\n003,Hồ Chí Minh,Văn phòng,420000,7,Đang xử lý\n004,Hà Nội,Đồ dùng,320000,4,Hoàn thành\n005,Đà Nẵng,Văn phòng,155000,3,Hoàn thành\n006,Hồ Chí Minh,Đồ dùng,280000,2,Đã hủy\n007,Hà Nội,Văn phòng,360000,6,Hoàn thành\n008,Đà Nẵng,Đồ dùng,210000,3,Đang xử lý\n009,Hồ Chí Minh,Văn phòng,540000,9,Hoàn thành\n010,Hà Nội,Đồ dùng,165000,1,Hoàn thành\n011,Đà Nẵng,Văn phòng,295000,5,Hoàn thành\n012,Hồ Chí Minh,Đồ dùng,450000,6,Hoàn thành\n',
};
function attachment(res, content, filename) {
  res.writeHead(200, {
    'Content-Type': 'text/csv; charset=utf-8',
    'Content-Disposition': 'attachment; filename="' + filename + '"',
  });
  res.end(content);
}
export function createApp(options = {}) {
  const clock = options.clock || (() => Date.now());
  const store = new Store(
    options.dbPath || process.env.YANGDATA_DB || join(ROOT, 'data/yangdata.sqlite'),
    clock,
  );
  const hosts =
    options.allowedHosts ||
    (process.env.YANGDATA_ALLOWED_HOSTS || 'localhost,127.0.0.1').split(',').map((s) => s.trim());
  const publicOrigin = options.publicOrigin || process.env.YANGDATA_PUBLIC_ORIGIN;
  const secure = options.secureCookie ?? process.env.YANGDATA_SECURE_COOKIE === '1';
  const server = http.createServer({ maxHeaderSize: 16384 }, async (req, res) => {
    res.setHeader(
      'Content-Security-Policy',
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
    );
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'same-origin');
    try {
      let url, path;
      try {
        url = new URL(req.url, 'http://' + req.headers.host);
        path = decodeURIComponent(url.pathname);
      } catch {
        throw new HttpError(400, 'Địa chỉ không hợp lệ.');
      }
      check(
        hosts.includes(new URL('http://' + req.headers.host).hostname),
        'Host không được phép.',
        400,
      );
      if (path === '/health' && req.method === 'GET') {
        store.db.prepare('SELECT 1').get();
        return send(res, { status: 'ok' });
      }
      if (FILES.has(path)) {
        check(['GET', 'HEAD'].includes(req.method), 'Phương thức không được hỗ trợ.', 405);
        const [file, type] = FILES.get(path);
        res.writeHead(200, { 'Content-Type': type + '; charset=utf-8' });
        return res.end(
          req.method === 'HEAD' ? undefined : readFileSync(join(ROOT, 'public', file)),
        );
      }
      check(path.startsWith('/api/'), 'Không tìm thấy trang.', 404);
      res.setHeader('Vary', 'Cookie');
      const cookie = (req.headers.cookie || '')
        .split(';')
        .map((s) => s.trim())
        .find((s) => s.startsWith(COOKIE + '='))
        ?.slice(COOKIE.length + 1);
      let session = store.session(cookie);
      if (path === '/api/workspace' && req.method === 'GET') {
        check(
          req.headers['sec-fetch-site'] !== 'cross-site',
          'Không mở workspace từ website khác.',
          403,
        );
        if (!session) {
          const fresh = store.newSession();
          session = fresh.session;
          res.setHeader(
            'Set-Cookie',
            COOKIE +
              '=' +
              fresh.cookie +
              '; Path=/; Max-Age=604800; HttpOnly; SameSite=Strict' +
              (secure ? '; Secure' : ''),
          );
        }
        return send(res, {
          csrf: session.csrf,
          expires: session.expires,
          retentionDays: 7,
          limits: {
            csvBytes: 524288,
            datasets: 5,
            reports: 20,
            rows: 5000,
            columns: 30,
            page: 500,
          },
        });
      }
      check(session, 'Workspace hết hạn. Hãy tải lại trang.', 401);
      if (!['GET', 'HEAD'].includes(req.method)) {
        check(equal(req.headers['x-csrf-token'], session.csrf), 'Token CSRF không hợp lệ.', 403);
        check(
          !req.headers.origin ||
            req.headers.origin === (publicOrigin || 'http://' + req.headers.host),
          'Origin không được phép.',
          403,
        );
      }
      const owner = session.id;
      if (path === '/api/datasets' && req.method === 'GET')
        return send(res, { datasets: store.listDatasets(owner, url.searchParams.get('q') || '') });
      if (path === '/api/datasets' && req.method === 'POST') {
        const result = store.importDataset(owner, await readJSON(req));
        return send(res, result, result.reused ? 200 : 201);
      }
      if (path === '/api/inspect' && req.method === 'POST')
        return send(res, previewCSV(await readJSON(req)));
      if (path === '/api/demo' && req.method === 'POST') {
        await readJSON(req);
        return send(res, store.importDataset(owner, SAMPLE));
      }
      if (path === '/api/query' && req.method === 'POST')
        return send(res, store.query(owner, await readJSON(req)));
      if (path === '/api/query/export' && req.method === 'POST') {
        const result = store.exportQuery(owner, await readJSON(req));
        return attachment(res, result.content, 'yangdata-result.csv');
      }
      if (path === '/api/reports' && req.method === 'GET')
        return send(res, { reports: store.listReports(owner) });
      if (path === '/api/reports' && req.method === 'POST')
        return send(res, { report: store.saveReport(owner, await readJSON(req)) }, 201);
      const datasetMatch = path.match(/^\/api\/datasets\/([a-f0-9-]{36})(?:\/(export))?$/);
      if (datasetMatch) {
        const [, id, action] = datasetMatch;
        if (action === 'export' && req.method === 'GET')
          return attachment(res, store.exportDataset(owner, id), 'yangdata-dataset.csv');
        check(!action, 'Phương thức không được hỗ trợ.', 405);
        if (req.method === 'GET') return send(res, { dataset: store.getDataset(owner, id) });
        if (req.method === 'PATCH')
          return send(res, { dataset: store.renameDataset(owner, id, await readJSON(req)) });
        if (req.method === 'DELETE') {
          const data = await readJSON(req);
          store.removeDataset(owner, id, data.version);
          res.writeHead(204);
          return res.end();
        }
        throw new HttpError(405, 'Phương thức không được hỗ trợ.');
      }
      const reportMatch = path.match(/^\/api\/reports\/([a-f0-9-]{36})(?:\/(run|export))?$/);
      if (reportMatch) {
        const [, id, action] = reportMatch;
        if (action === 'run' && req.method === 'POST') {
          await readJSON(req);
          return send(res, store.runReport(owner, id));
        }
        if (action === 'export' && req.method === 'POST') {
          await readJSON(req);
          return attachment(
            res,
            store.exportQuery(owner, store.getReport(owner, id).definition).content,
            'yangdata-report.csv',
          );
        }
        check(!action, 'Phương thức không được hỗ trợ.', 405);
        if (req.method === 'GET') return send(res, { report: store.getReport(owner, id) });
        if (req.method === 'PUT')
          return send(res, { report: store.saveReport(owner, await readJSON(req), id) });
        if (req.method === 'DELETE') {
          const data = await readJSON(req);
          store.removeReport(owner, id, data.version);
          res.writeHead(204);
          return res.end();
        }
        throw new HttpError(405, 'Phương thức không được hỗ trợ.');
      }
      throw new HttpError(404, 'Không tìm thấy API.');
    } catch (err) {
      if (!res.headersSent)
        send(
          res,
          {
            error:
              err instanceof HttpError ? err.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.',
          },
          err.status || 500,
        );
      else res.end();
    }
  });
  server.requestTimeout = 10000;
  server.headersTimeout = 10000;
  server.keepAliveTimeout = 5000;
  return {
    server,
    store,
    async close() {
      if (server.listening)
        await new Promise((done, reject) => server.close((err) => (err ? reject(err) : done())));
      store.close();
    },
  };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const app = createApp(),
    port = Number(process.env.PORT || 3130),
    host = process.env.YANGDATA_BIND || '127.0.0.1';
  app.server.listen(port, host, () => console.log('YangData: http://' + host + ':' + port));
  for (const signal of ['SIGINT', 'SIGTERM'])
    process.on(signal, async () => {
      await app.close();
      process.exit(0);
    });
}
