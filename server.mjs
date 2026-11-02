import http from 'node:http';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { timingSafeEqual } from 'node:crypto';
import { Store } from './lib/store.mjs';
import { Queue } from './lib/queue.mjs';
import { HttpError, check, object, bool, key, submission, MAX_INPUT } from './lib/validation.mjs';
const ROOT = dirname(fileURLToPath(import.meta.url));
const COOKIE = 'yangqueue_workspace',
  MAX_BODY = 768 * 1024;
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
  check(Number(req.headers['content-length'] || 0) <= MAX_BODY, 'Request tối đa 768 KB.', 413);
  return new Promise((resolveBody, reject) => {
    let bytes = 0,
      failed = false,
      chunks = [];
    req.on('data', (chunk) => {
      bytes += chunk.length;
      if (bytes > MAX_BODY && !failed) {
        failed = true;
        chunks = [];
        reject(new HttpError(413, 'Request tối đa 768 KB.'));
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
export function samples() {
  return [
    {
      title: '[Mẫu] Làm sạch danh sách đồ dùng',
      type: 'csv-clean',
      input:
        'Tên,Nhóm,Số lượng\nSổ tay,Văn phòng,2\n Bút xanh ,Văn phòng,5\nSổ tay,Văn phòng,2\n\nBình nước,Đồ dùng,1\n',
      options: { trim: true, dedupe: true },
    },
    {
      title: '[Mẫu] Chuyển ghi chú JSON sang CSV',
      type: 'json-csv',
      input:
        '[{"name":"API notes","topic":"backend","done":false},{"name":"Database notes","topic":"sql","done":true},{"name":"Testing notes","topic":"testing","done":false}]',
      options: {},
    },
    {
      title: '[Mẫu] Thống kê ghi chép tiếng Việt',
      type: 'text-stats',
      input:
        'Một công việc rõ ràng bắt đầu từ một ghi chép rõ ràng.\nGhi chép giúp nhóm biết ai phụ trách, việc nào cần làm và khi nào cần hoàn thành.\nĐây là dữ liệu giả lập của YangQueue.',
      options: {},
    },
  ];
}
export function createApp(options = {}) {
  const clock = options.clock || (() => Date.now());
  const store = new Store(
    options.dbPath || process.env.YANGQUEUE_DB || join(ROOT, 'data/yangqueue.sqlite'),
    clock,
  );
  const queue = new Queue(store, options.queueOptions);
  const hosts =
    options.allowedHosts ||
    (process.env.YANGQUEUE_ALLOWED_HOSTS || 'localhost,127.0.0.1').split(',');
  const secure = options.secureCookie ?? process.env.YANGQUEUE_SECURE_COOKIE === '1';
  const publicOrigin = options.publicOrigin || process.env.YANGQUEUE_PUBLIC_ORIGIN;
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
      let host;
      try {
        host = new URL('http://' + req.headers.host).hostname;
      } catch {
        throw new HttpError(400, 'Host không hợp lệ.');
      }
      check(hosts.includes(host), 'Host không được phép.', 400);
      const url = new URL(req.url, 'http://' + req.headers.host),
        path = decodeURIComponent(url.pathname);
      if (path === '/health' && req.method === 'GET') {
        store.db.prepare('SELECT 1').get();
        return send(res, { status: 'ok' });
      }
      if (FILES.has(path)) {
        check(['GET', 'HEAD'].includes(req.method), 'Phương thức không được hỗ trợ.', 405);
        const [name, type] = FILES.get(path);
        res.writeHead(200, { 'Content-Type': type + '; charset=utf-8' });
        return res.end(
          req.method === 'HEAD' ? undefined : readFileSync(join(ROOT, 'public', name)),
        );
      }
      check(path.startsWith('/api/'), 'Không tìm thấy trang.', 404);
      res.setHeader('Vary', 'Cookie');
      const cookie = (req.headers.cookie || '')
        .split(';')
        .map((x) => x.trim())
        .find((x) => x.startsWith(COOKIE + '='))
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
          paused: Boolean(session.paused),
          retentionDays: 7,
          maxInputBytes: MAX_INPUT,
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
      if (path === '/api/queue' && req.method === 'GET')
        return send(res, {
          jobs: store.list(owner, {
            q: url.searchParams.get('q'),
            state: url.searchParams.get('state'),
          }),
          stats: store.stats(owner),
          paused: Boolean(
            store.db.prepare('SELECT paused FROM sessions WHERE id=?').get(owner).paused,
          ),
          serverTime: clock(),
        });
      if (path === '/api/queue/pause' && req.method === 'POST') {
        const payload = await readJSON(req);
        store.pause(owner, bool(payload.paused));
        return send(res, { paused: payload.paused });
      }
      if (path === '/api/demo' && req.method === 'POST') {
        await readJSON(req);
        const oldStats = store.stats(owner);
        if (oldStats.total === 0) store.pause(owner, true);
        let added = 0;
        for (const data of samples()) {
          try {
            if (!store.create(owner, data, 'demo-v1-' + data.type).replayed) added++;
          } catch (err) {
            if (err.status !== 410) throw err;
          }
        }
        return send(res, {
          added,
          paused: Boolean(
            store.db.prepare('SELECT paused FROM sessions WHERE id=?').get(owner).paused,
          ),
        });
      }
      if (path === '/api/jobs' && req.method === 'POST') {
        const payload = await readJSON(req),
          result = store.create(owner, submission(payload), key(req.headers['idempotency-key']));
        return send(
          res,
          { ...result, requestId: result.job.id, status: result.job.status },
          result.replayed ? 200 : 201,
        );
      }
      const match = path.match(/^\/api\/jobs\/([a-zA-Z0-9-]{8,48})(?:\/(cancel|retry|download))?$/);
      check(match, 'Không tìm thấy API.', 404);
      const [, id, action] = match,
        job = store.get(owner, id);
      if (!action && req.method === 'GET')
        return send(res, { job, events: store.events(owner, id) });
      if (action === 'download' && req.method === 'GET') {
        check(job.status === 'succeeded' && job.result, 'Tệp kết quả chưa sẵn sàng.', 409);
        res.writeHead(200, {
          'Content-Type': job.result.mime + '; charset=utf-8',
          'Content-Disposition': 'attachment; filename="' + job.result.filename + '"',
        });
        return res.end(job.result.content);
      }
      check(
        req.method === 'POST' || (!action && req.method === 'DELETE'),
        'Phương thức không được hỗ trợ.',
        405,
      );
      const payload = await readJSON(req);
      if (action === 'cancel' && req.method === 'POST') {
        const result = store.cancel(owner, id, payload.version);
        queue.cancel(id);
        return send(res, { job: result });
      }
      if (action === 'retry' && req.method === 'POST') {
        const result = store.retry(owner, id, payload.version, key(req.headers['idempotency-key']));
        return send(res, result, result.replayed ? 200 : 201);
      }
      if (!action && req.method === 'DELETE') {
        store.remove(owner, id, payload.version);
        res.writeHead(204);
        return res.end();
      }
      throw new HttpError(405, 'Phương thức không được hỗ trợ.');
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
  if (options.startQueue !== false) queue.start();
  return {
    server,
    store,
    queue,
    async close() {
      if (server.listening)
        await new Promise((resolveClose, reject) =>
          server.close((err) => (err ? reject(err) : resolveClose())),
        );
      await queue.close();
      store.close();
    },
  };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const app = createApp(),
    port = Number(process.env.PORT || 3120),
    host = process.env.YANGQUEUE_BIND || '127.0.0.1';
  app.server.listen(port, host, () => console.log('YangQueue: http://' + host + ':' + port));
  for (const signal of ['SIGINT', 'SIGTERM'])
    process.on(signal, async () => {
      await app.close();
      process.exit(0);
    });
}
