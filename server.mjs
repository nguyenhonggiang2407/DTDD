import http from 'node:http';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname, join } from 'node:path';
import { timingSafeEqual } from 'node:crypto';
import { Store } from './lib/store.mjs';
import {
  HttpError,
  requireThat,
  object,
  bool,
  version,
  today,
  normalize,
  download,
} from './lib/validation.mjs';
import * as minutes from './apps/yang-minutes/domain.mjs';
import * as launch from './apps/yang-launch/domain.mjs';
import * as spec from './apps/yang-spec/domain.mjs';

const ROOT = dirname(fileURLToPath(import.meta.url));
const MODULES = { minutes, launch, spec };
const COOKIE = 'yanglab_workspace';
const MAX_BODY = 256 * 1024;
const FILES = new Map([
  ['/', ['index.html', 'text/html']],
  ['/minutes', ['minutes.html', 'text/html']],
  ['/launch', ['launch.html', 'text/html']],
  ['/spec', ['spec.html', 'text/html']],
  ['/assets/styles.css', ['styles.css', 'text/css']],
  ['/assets/shared.js', ['shared.js', 'text/javascript']],
  ['/assets/minutes.js', ['minutes.js', 'text/javascript']],
  ['/assets/launch.js', ['launch.js', 'text/javascript']],
  ['/assets/spec.js', ['spec.js', 'text/javascript']],
  ['/assets/favicon.svg', ['favicon.svg', 'image/svg+xml']],
  ['/assets/home.js', ['home.js', 'text/javascript']],
]);
function json(res, data, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(data));
}
function same(a, b) {
  const aa = Buffer.from(a || ''),
    bb = Buffer.from(b || '');
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}
function readJSON(req) {
  requireThat(
    (req.headers['content-type'] || '').split(';')[0].trim() === 'application/json',
    'Cần gửi application/json.',
    415,
  );
  requireThat(!req.headers['content-encoding'], 'Không nhận request đã nén.', 415);
  requireThat(
    Number(req.headers['content-length'] || 0) <= MAX_BODY,
    'Nội dung gửi lên tối đa 256 KB.',
    413,
  );
  return new Promise((resolveBody, reject) => {
    let length = 0,
      failed = false,
      chunks = [];
    req.on('data', (chunk) => {
      length += chunk.length;
      if (length > MAX_BODY && !failed) {
        failed = true;
        chunks = [];
        reject(new HttpError(413, 'Nội dung gửi lên tối đa 256 KB.'));
      } else if (!failed) chunks.push(chunk);
    });
    req.once('end', () => {
      if (failed) return;
      try {
        const value = JSON.parse(
          new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks)),
        );
        resolveBody(object(value));
      } catch (err) {
        reject(err instanceof HttpError ? err : new HttpError(400, 'JSON không hợp lệ.'));
      }
    });
    req.once('error', reject);
    req.once('aborted', () => reject(new HttpError(400, 'Request đã bị ngắt.')));
  });
}
function security(res) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'same-origin');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader(
    'Content-Security-Policy',
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
  );
  res.setHeader('Cache-Control', 'no-store');
}
function filterItems(items, search) {
  const q = (search.get('q') || '').trim();
  requireThat(q.length <= 120, 'Từ khóa tối đa 120 ký tự.');
  const stage = search.get('stage') || '',
    from = search.get('from') || '',
    to = search.get('to') || '';
  const channel = search.get('channel') || '';
  if (channel)
    requireThat(
      ['facebook', 'linkedin', 'blog', 'x'].includes(channel),
      'Bộ lọc kênh không hợp lệ.',
    );
  if (stage) requireThat(launch.STAGES.includes(stage), 'Bộ lọc giai đoạn không hợp lệ.');
  for (const day of [from, to])
    if (day) requireThat(/^\d{4}-\d{2}-\d{2}$/.test(day), 'Bộ lọc ngày không hợp lệ.');
  return items.filter(
    (item) =>
      (!q || normalize(JSON.stringify(item.data)).includes(normalize(q))) &&
      (!channel || item.data.channel === channel) &&
      (!stage || item.data.stage === stage) &&
      (!from || (item.data.due || item.data.date || '') >= from) &&
      (!to || ((item.data.due || item.data.date || '') && (item.data.due || item.data.date) <= to)),
  );
}
export function createApp(options = {}) {
  const clock = options.clock || (() => Date.now());
  const store = new Store(
    options.dbPath || process.env.YANGLAB_DB || join(ROOT, 'data/yanglab.sqlite'),
    clock,
  );
  const allowedHosts =
    options.allowedHosts || (process.env.YANGLAB_ALLOWED_HOSTS || '127.0.0.1,localhost').split(',');
  const secureCookie = options.secureCookie ?? process.env.YANGLAB_SECURE_COOKIE === '1';
  const publicOrigin = options.publicOrigin || process.env.YANGLAB_PUBLIC_ORIGIN;
  const server = http.createServer({ maxHeaderSize: 16384 }, async (req, res) => {
    security(res);
    try {
      let host;
      try {
        host = new URL('http://' + req.headers.host).hostname;
      } catch {
        throw new HttpError(400, 'Host không hợp lệ.');
      }
      requireThat(allowedHosts.includes(host), 'Host không được phép.', 400);
      const url = new URL(req.url, 'http://' + req.headers.host);
      const pathname = decodeURIComponent(url.pathname);
      if (pathname === '/health' && req.method === 'GET') return json(res, { status: 'ok' });
      if (FILES.has(pathname)) {
        requireThat(['GET', 'HEAD'].includes(req.method), 'Phương thức không được hỗ trợ.', 405);
        const [file, type] = FILES.get(pathname);
        res.writeHead(200, { 'Content-Type': type + '; charset=utf-8' });
        return res.end(
          req.method === 'HEAD' ? undefined : readFileSync(join(ROOT, 'public', file)),
        );
      }
      requireThat(pathname.startsWith('/api/'), 'Không tìm thấy trang.', 404);
      res.setHeader('Vary', 'Cookie');
      const cookie = (req.headers.cookie || '')
        .split(';')
        .map((p) => p.trim())
        .find((p) => p.startsWith(COOKIE + '='))
        ?.slice(COOKIE.length + 1);
      let session = store.session(cookie);
      if (pathname === '/api/workspace' && req.method === 'GET') {
        requireThat(
          req.headers['sec-fetch-site'] !== 'cross-site',
          'Không mở workspace từ website khác.',
          403,
        );
        if (!session) {
          const fresh = store.createSession();
          session = fresh.session;
          res.setHeader(
            'Set-Cookie',
            COOKIE +
              '=' +
              fresh.cookie +
              '; Path=/; Max-Age=604800; HttpOnly; SameSite=Strict' +
              (secureCookie ? '; Secure' : ''),
          );
        }
        return json(res, {
          csrf: session.csrf,
          today: today(clock),
          retentionDays: 7,
          maxItems: 50,
        });
      }
      requireThat(session, 'Phiên đã hết hạn. Hãy tải lại trang để mở workspace mới.', 401);
      if (!['GET', 'HEAD'].includes(req.method)) {
        requireThat(
          same(req.headers['x-csrf-token'], session.csrf),
          'Token CSRF không hợp lệ.',
          403,
        );
        const expectedOrigin = publicOrigin || 'http://' + req.headers.host;
        requireThat(
          !req.headers.origin || req.headers.origin === expectedOrigin,
          'Origin không được phép.',
          403,
        );
      }
      const owner = session.id;
      if (pathname === '/api/activity' && req.method === 'GET')
        return json(res, { events: store.activity(owner) });
      const segments = pathname.split('/').filter(Boolean);
      const module = segments[1],
        domain = MODULES[module];
      requireThat(Object.hasOwn(MODULES, module), 'Không tìm thấy ứng dụng.', 404);
      if (segments[2] === 'extract' && module === 'minutes' && req.method === 'POST') {
        const input = await readJSON(req);
        return json(res, minutes.extractActions(input.notes));
      }
      if (segments[2] === 'demo' && req.method === 'POST') {
        await readJSON(req);
        const existing = store.list(owner, module);
        let added = 0;
        for (const data of domain.demo(today(clock))) {
          if (existing.some((item) => item.data.title === data.title)) continue;
          store.create(owner, module, domain.validate(data));
          added++;
        }
        return json(res, { added });
      }
      if (segments[2] === 'export.csv' && module === 'launch' && req.method === 'GET') {
        return download(
          res,
          launch.csv(filterItems(store.list(owner, module), url.searchParams)),
          'yanglaunch-plan.csv',
          'text/csv',
        );
      }
      requireThat(segments[2] === 'items', 'Không tìm thấy API.', 404);
      const recordId = segments[3],
        sub = segments[4];
      if (!recordId) {
        if (req.method === 'GET')
          return json(res, {
            items: filterItems(store.list(owner, module), url.searchParams),
            today: today(clock),
          });
        requireThat(req.method === 'POST', 'Phương thức không được hỗ trợ.', 405);
        const input = await readJSON(req);
        return json(res, store.create(owner, module, domain.validate(input.data)), 201);
      }
      const record = store.get(owner, module, recordId);
      if (sub === 'history' && req.method === 'GET')
        return json(res, { revisions: store.history(owner, module, recordId) });
      if (sub === 'export' && req.method === 'GET') {
        const format = url.searchParams.get('format') || 'md';
        if (format === 'md')
          return download(
            res,
            domain.markdown(record),
            'yang' + module + '-notes.md',
            'text/markdown',
          );
        if (format === 'ics' && module === 'minutes')
          return download(
            res,
            minutes.calendar(record, clock()),
            'yangminutes-tasks.ics',
            'text/calendar',
          );
        if (format === 'json' && module === 'spec')
          return download(
            res,
            JSON.stringify(spec.openapi(record), null, 2),
            'yangspec-openapi.json',
            'application/json',
          );
        throw new HttpError(400, 'Định dạng xuất không hỗ trợ.');
      }
      if (!sub && req.method === 'GET') return json(res, record);
      const input = await readJSON(req);
      const expected = version(input.version);
      requireThat(
        expected === record.version,
        'Dữ liệu đã thay đổi ở tab khác. Hãy tải lại trước khi lưu.',
        409,
      );
      if (sub === 'actions' && module === 'minutes' && req.method === 'PATCH') {
        const action = record.data.actions.find((item) => item.id === segments[5]);
        requireThat(action, 'Không tìm thấy công việc.', 404);
        action.done = bool(input.done);
        return json(
          res,
          store.update(
            owner,
            module,
            recordId,
            expected,
            minutes.validate(record.data),
            'action-changed',
          ),
        );
      }
      if (sub === 'stage' && module === 'launch' && req.method === 'PATCH') {
        const data = launch.validate({ ...record.data, stage: input.stage }, record.data);
        return json(res, store.update(owner, module, recordId, expected, data, 'stage-changed'));
      }
      requireThat(!sub, 'Không tìm thấy API.', 404);
      if (req.method === 'PUT')
        return json(
          res,
          store.update(owner, module, recordId, expected, domain.validate(input.data, record.data)),
        );
      if (req.method === 'DELETE') {
        store.remove(owner, module, recordId, expected);
        res.writeHead(204);
        return res.end();
      }
      throw new HttpError(405, 'Phương thức không được hỗ trợ.');
    } catch (err) {
      if (res.headersSent) {
        res.end();
        return;
      }
      json(
        res,
        {
          error:
            err instanceof HttpError ? err.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.',
        },
        err.status || 500,
      );
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
        await new Promise((resolveClose, reject) =>
          server.close((err) => (err ? reject(err) : resolveClose())),
        );
      store.close();
    },
  };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const app = createApp();
  const port = Number(process.env.PORT || 3100),
    host = process.env.YANGLAB_BIND || '127.0.0.1';
  app.server.listen(port, host, () => console.log('YangLab: http://' + host + ':' + port));
  for (const signal of ['SIGINT', 'SIGTERM'])
    process.on(signal, async () => {
      await app.close();
      process.exit(0);
    });
}
