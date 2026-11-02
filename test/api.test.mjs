import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../server.mjs';
let app, base;
before(async () => {
  app = createApp({ dbPath: ':memory:', startQueue: false });
  await new Promise((r) => app.server.listen(0, '127.0.0.1', r));
  base = 'http://127.0.0.1:' + app.server.address().port;
});
after(async () => app.close());
async function session() {
  const r = await fetch(base + '/api/workspace'),
    d = await r.json();
  return { cookie: r.headers.get('set-cookie').split(';')[0], csrf: d.csrf };
}
async function request(s, path, method = 'GET', data, headers = {}) {
  return fetch(base + path, {
    method,
    headers: {
      ...(s ? { Cookie: s.cookie, 'X-CSRF-Token': s.csrf } : {}),
      ...(data === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    body: data === undefined ? undefined : JSON.stringify(data),
  });
}
const payload = (extra = {}) => ({
  title: 'CSV task',
  type: 'csv-clean',
  input: 'name\nA\nA\n',
  options: {},
  ...extra,
});
async function create(s, idem, data = payload()) {
  const r = await request(s, '/api/jobs', 'POST', data, { 'Idempotency-Key': idem });
  assert.equal(r.status, 201, await r.clone().text());
  return (await r.json()).job;
}
test('private cookies, CSRF, Origin and cross-site bootstrap', async () => {
  const r = await fetch(base + '/api/workspace');
  assert.match(r.headers.get('set-cookie'), /HttpOnly; SameSite=Strict/);
  assert.match(r.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal((await request(null, '/api/queue')).status, 401);
  const s = await session();
  assert.equal((await request({ ...s, csrf: '' }, '/api/jobs', 'POST', payload())).status, 403);
  assert.equal(
    (
      await request(
        s,
        '/api/queue/pause',
        'POST',
        { paused: true },
        { Origin: 'https://evil.invalid' },
      )
    ).status,
    403,
  );
  assert.equal(
    (await request(s, '/api/workspace', 'GET', undefined, { 'Sec-Fetch-Site': 'cross-site' }))
      .status,
    403,
  );
});
test('Host, file access, request limits and malformed UTF-8', async () => {
  const status = await new Promise((resolve, reject) =>
    http
      .get(base + '/health', { headers: { Host: 'evil.invalid' } }, (r) => {
        r.resume();
        resolve(r.statusCode);
      })
      .on('error', reject),
  );
  assert.equal(status, 400);
  for (const p of ['/data/yangqueue.sqlite', '/upstream/api.step.py'])
    assert.equal((await fetch(base + p)).status, 404);
  const s = await session();
  assert.equal(
    (
      await request(s, '/api/jobs', 'POST', payload({ input: 'a'.repeat(262145) }), {
        'Idempotency-Key': 'large-body-key',
      })
    ).status,
    413,
  );
  const bad = await fetch(base + '/api/jobs', {
    method: 'POST',
    headers: { Cookie: s.cookie, 'X-CSRF-Token': s.csrf, 'Content-Type': 'application/json' },
    body: Buffer.from([0xc0, 0xaf]),
  });
  assert.equal(bad.status, 400);
});
test('submission requires a key and returns the same accepted requestId on replay', async () => {
  const s = await session();
  assert.equal((await request(s, '/api/jobs', 'POST', payload())).status, 400);
  const first = await create(s, 'http-replay-key'),
    r = await request(s, '/api/jobs', 'POST', payload(), { 'Idempotency-Key': 'http-replay-key' });
  assert.equal(r.status, 200);
  const replay = await r.json();
  assert.equal(replay.job.id, first.id);
  assert.equal(replay.requestId, first.id);
  assert.equal(replay.replayed, true);
  assert.equal(
    (
      await request(s, '/api/jobs', 'POST', payload({ title: 'Changed' }), {
        'Idempotency-Key': 'http-replay-key',
      })
    ).status,
    409,
  );
});
test('all job operations and artifacts enforce workspace ownership', async () => {
  const a = await session(),
    b = await session(),
    job = await create(a, 'private-job-key'),
    p = '/api/jobs/' + job.id;
  assert.equal((await (await request(b, '/api/queue')).json()).stats.total, 0);
  for (const path of [p, p + '/download']) assert.equal((await request(b, path)).status, 404);
  for (const action of ['/cancel', '/retry'])
    assert.equal(
      (
        await request(
          b,
          p + action,
          'POST',
          { version: 1 },
          { 'Idempotency-Key': 'other-owner-key' },
        )
      ).status,
      404,
    );
  assert.equal((await request(b, p, 'DELETE', { version: 1 })).status, 404);
});
test('sample import is private, idempotent and initially paused', async () => {
  const a = await session(),
    b = await session();
  assert.equal((await (await request(a, '/api/demo', 'POST', {})).json()).added, 3);
  assert.equal((await (await request(a, '/api/demo', 'POST', {})).json()).added, 0);
  const q = await (await request(a, '/api/queue')).json();
  assert.equal(q.paused, true);
  assert.equal(q.stats.queued, 3);
  assert.equal((await (await request(b, '/api/queue')).json()).stats.total, 0);
});
test('pause is boolean and snapshot search supports Vietnamese without accents', async () => {
  const s = await session();
  await create(s, 'accent-filter-key', payload({ title: 'Làm sạch dữ liệu' }));
  assert.equal((await request(s, '/api/queue/pause', 'POST', { paused: 'true' })).status, 400);
  assert.equal((await request(s, '/api/queue/pause', 'POST', { paused: true })).status, 200);
  assert.equal(
    (await (await request(s, '/api/queue?q=lam%20sach&state=queued')).json()).jobs.length,
    1,
  );
  assert.equal((await request(s, '/api/queue?state=unknown')).status, 400);
});
test('cancellation, stale requests, download readiness and deleted-key tombstones', async () => {
  const s = await session(),
    job = await create(s, 'cancel-http-key'),
    p = '/api/jobs/' + job.id;
  assert.equal((await request(s, p + '/download')).status, 409);
  assert.equal((await request(s, p, 'DELETE', { version: 1 })).status, 409);
  const cancelled = (await (await request(s, p + '/cancel', 'POST', { version: 1 })).json()).job;
  assert.equal(cancelled.status, 'cancelled');
  assert.equal((await request(s, p + '/cancel', 'POST', { version: 1 })).status, 409);
  assert.equal((await request(s, p, 'DELETE', { version: cancelled.version })).status, 204);
  assert.equal(
    (await request(s, '/api/jobs', 'POST', payload(), { 'Idempotency-Key': 'cancel-http-key' }))
      .status,
    410,
  );
});
test('streamed bodies remain bounded without Content-Length', async () => {
  const s = await session();
  const status = await new Promise((resolve, reject) => {
    const req = http.request(
      base + '/api/jobs',
      {
        method: 'POST',
        headers: {
          Cookie: s.cookie,
          'X-CSRF-Token': s.csrf,
          'Content-Type': 'application/json',
          'Transfer-Encoding': 'chunked',
        },
      },
      (res) => {
        res.resume();
        resolve(res.statusCode);
      },
    );
    req.on('error', reject);
    req.end('a'.repeat(800 * 1024));
  });
  assert.equal(status, 413);
});
