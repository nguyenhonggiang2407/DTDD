import http from 'node:http';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { timingSafeEqual, randomUUID } from 'node:crypto';
import { Store } from './lib/store.mjs';
import { HttpError, check } from './lib/validation.mjs';
import { sign } from './lib/signature.mjs';
import { send, readRaw, readJSON } from './lib/http.mjs';
const ROOT = dirname(fileURLToPath(import.meta.url)),
  COOKIE = 'yanghooks_workspace';
const FILES = new Map([
  ['/', ['index.html', 'text/html']],
  ['/assets/app.js', ['app.js', 'text/javascript']],
  ['/assets/styles.css', ['styles.css', 'text/css']],
  ['/assets/favicon.svg', ['favicon.svg', 'image/svg+xml']],
]);
function equal(a, b) {
  const aa = Buffer.from(typeof a === 'string' ? a : ''),
    bb = Buffer.from(typeof b === 'string' ? b : '');
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}
export function createApp(options = {}) {
  const clock = options.clock || (() => Date.now()),
    store = new Store(
      options.dbPath || process.env.YANGHOOKS_DB || join(ROOT, 'data/yanghooks.sqlite'),
      clock,
    );
  const hosts =
    options.allowedHosts ||
    (process.env.YANGHOOKS_ALLOWED_HOSTS || 'localhost,127.0.0.1').split(',').map((s) => s.trim());
  const origin = options.publicOrigin || process.env.YANGHOOKS_PUBLIC_ORIGIN,
    secure = options.secureCookie ?? process.env.YANGHOOKS_SECURE_COOKIE === '1';
  const server = http.createServer({ maxHeaderSize: 16384 }, async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader(
      'Content-Security-Policy',
      "default-src 'self';script-src 'self';style-src 'self';img-src 'self' data:;object-src 'none';base-uri 'none';frame-ancestors 'none';form-action 'self'",
    );
    try {
      let url, path;
      try {
        const host = new URL('http://' + req.headers.host).hostname;
        check(hosts.includes(host), 'Host không được phép.');
        url = new URL(req.url, 'http://' + req.headers.host);
        path = decodeURIComponent(url.pathname);
      } catch (error) {
        throw error instanceof HttpError ? error : new HttpError(400, 'Địa chỉ không hợp lệ.');
      }
      if (path === '/health' && req.method === 'GET') {
        store.db.prepare('SELECT 1').get();
        return send(res, { status: 'ok' });
      }
      const inbound = path.match(/^\/hooks\/([a-f0-9-]{36})$/);
      if (inbound) {
        check(req.method === 'POST', 'Webhook chỉ nhận POST.', 405);
        const raw = await readRaw(req),
          result = store.accept(inbound[1], req.headers, raw);
        return send(res, result, result.replayed ? 200 : 201);
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
              ';Path=/;Max-Age=604800;HttpOnly;SameSite=Strict' +
              (secure ? ';Secure' : ''),
          );
        }
        return send(res, {
          csrf: session.csrf,
          expires: session.expires,
          limits: { endpoints: 5, events: 100, bodyBytes: 65536 },
          protocol: 'yanghooks-v1',
        });
      }
      check(session, 'Workspace hết hạn. Hãy tải lại.', 401);
      if (!['GET', 'HEAD'].includes(req.method)) {
        check(equal(req.headers['x-csrf-token'], session.csrf), 'Token CSRF không hợp lệ.', 403);
        check(
          !req.headers.origin || req.headers.origin === (origin || 'http://' + req.headers.host),
          'Origin không được phép.',
          403,
        );
      }
      const owner = session.id;
      if (path === '/api/endpoints' && req.method === 'GET')
        return send(res, { endpoints: store.list(owner) });
      if (path === '/api/endpoints' && req.method === 'POST')
        return send(res, store.create(owner, await readJSON(req)), 201);
      const match = path.match(
        /^\/api\/endpoints\/([a-f0-9-]{36})(?:\/(events|rotate|send|export))?$/,
      );
      if (match) {
        const [, id, action] = match;
        if (!action && req.method === 'GET') return send(res, { endpoint: store.get(owner, id) });
        if (!action && req.method === 'PATCH')
          return send(res, { endpoint: store.update(owner, id, await readJSON(req)) });
        if (!action && req.method === 'DELETE') {
          const payload = await readJSON(req);
          store.remove(owner, id, payload.version);
          res.writeHead(204);
          return res.end();
        }
        if (action === 'events' && req.method === 'GET')
          return send(res, { events: store.events(owner, id, url.searchParams.get('q') || '') });
        if (action === 'rotate' && req.method === 'POST') {
          const payload = await readJSON(req);
          return send(res, store.rotate(owner, id, payload.version));
        }
        if (action === 'export' && req.method === 'GET') {
          const events = store.export(owner, id);
          res.writeHead(200, {
            'Content-Type': 'application/json; charset=utf-8',
            'Content-Disposition': 'attachment; filename="yanghooks-events.json"',
          });
          return res.end(JSON.stringify({ protocol: 'yanghooks-v1', events }, null, 2));
        }
        if (action === 'send' && req.method === 'POST') {
          const payload = await readJSON(req),
            endpoint = store.rawEndpoint(owner, id);
          check(
            typeof payload.raw === 'string' && Buffer.byteLength(payload.raw) <= 65536,
            'Body gửi thử tối đa 64 KB.',
          );
          const mode = payload.mode || 'valid';
          check(
            ['valid', 'invalid-signature', 'expired'].includes(mode),
            'Chế độ thử không hợp lệ.',
          );
          const delivery = payload.deliveryId || randomUUID();
          check(
            typeof delivery === 'string' && /^[a-zA-Z0-9_-]{8,100}$/.test(delivery),
            'Delivery ID cần 8–100 chữ, số, _ hoặc -.',
          );
          const timestamp = String(Math.floor(clock() / 1000) - (mode === 'expired' ? 301 : 0)),
            raw = Buffer.from(payload.raw);
          let signature = sign(endpoint.secret, timestamp, delivery, raw);
          if (mode === 'invalid-signature')
            signature = signature.slice(0, -1) + (signature.endsWith('0') ? '1' : '0');
          const local = 'http://127.0.0.1:' + server.address().port + '/hooks/' + id;
          const response = await fetch(local, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'User-Agent': 'YangHooks-local-test',
              'X-Yang-Timestamp': timestamp,
              'X-Yang-Delivery': delivery,
              'X-Yang-Signature': signature,
            },
            body: raw,
            signal: AbortSignal.timeout(5000),
          });
          const receipt = await response.json();
          return send(res, {
            httpStatus: response.status,
            deliveryId: delivery,
            response: receipt,
            transport: 'loopback-http',
          });
        }
        throw new HttpError(405, 'Phương thức không được hỗ trợ.');
      }
      const event = path.match(/^\/api\/events\/([a-f0-9-]{36})$/);
      if (event && req.method === 'GET') return send(res, { event: store.event(owner, event[1]) });
      throw new HttpError(404, 'Không tìm thấy API.');
    } catch (error) {
      if (!res.headersSent)
        send(
          res,
          { error: error instanceof HttpError ? error.message : 'Không thể xử lý yêu cầu.' },
          error.status || 500,
        );
      else res.end();
    }
  });
  server.headersTimeout = 10000;
  server.requestTimeout = 10000;
  server.keepAliveTimeout = 5000;
  return {
    server,
    store,
    async close() {
      if (server.listening)
        await new Promise((done, reject) =>
          server.close((error) => (error ? reject(error) : done())),
        );
      store.close();
    },
  };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const app = createApp(),
    port = Number(process.env.PORT || 3140),
    host = process.env.YANGHOOKS_BIND || '127.0.0.1';
  app.server.listen(port, host, () => console.log('YangHooks: http://' + host + ':' + port));
  for (const signal of ['SIGINT', 'SIGTERM'])
    process.on(signal, async () => {
      await app.close();
      process.exit(0);
    });
}
