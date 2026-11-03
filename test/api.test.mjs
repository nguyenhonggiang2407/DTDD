import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../server.mjs';
import { sign } from '../lib/signature.mjs';
let app, base;
before(async () => {
  app = createApp({ dbPath: ':memory:' });
  await new Promise((done) => app.server.listen(0, '127.0.0.1', done));
  base = 'http://127.0.0.1:' + app.server.address().port;
});
after(async () => app.close());
async function session() {
  const r = await fetch(base + '/api/workspace'),
    b = await r.json();
  return { cookie: r.headers.get('set-cookie').split(';')[0], csrf: b.csrf };
}
const request = (s, path, method = 'GET', body, headers = {}) =>
  fetch(base + path, {
    method,
    headers: {
      ...(s ? { Cookie: s.cookie, 'X-CSRF-Token': s.csrf } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
async function endpoint(s) {
  const r = await request(s, '/api/endpoints', 'POST', { name: 'Orders' });
  assert.equal(r.status, 201);
  return r.json();
}
function signature(secret, raw, delivery = 'delivery-http-001') {
  const timestamp = String(Math.floor(Date.now() / 1000));
  return {
    'Content-Type': 'application/json',
    'X-Yang-Timestamp': timestamp,
    'X-Yang-Delivery': delivery,
    'X-Yang-Signature': sign(secret, timestamp, delivery, Buffer.from(raw)),
  };
}
test('workspace cookie and API middleware enforce private admin sessions, CSRF and Origin', async () => {
  const r = await fetch(base + '/api/workspace');
  assert.match(r.headers.get('set-cookie'), /HttpOnly;SameSite=Strict/);
  assert.match(r.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  const s = await session();
  assert.equal((await request(null, '/api/endpoints')).status, 401);
  assert.equal(
    (await request({ ...s, csrf: '' }, '/api/endpoints', 'POST', { name: 'bad' })).status,
    403,
  );
  assert.equal(
    (
      await request(
        s,
        '/api/endpoints',
        'POST',
        { name: 'bad' },
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
test('webhook route authenticates by HMAC without workspace cookies and returns a stable requestId', async () => {
  const s = await session(),
    created = await endpoint(s),
    raw = '{"type":"invoice.paid","id":"001"}',
    headers = signature(created.secret, raw),
    url = base + created.endpoint.path;
  const first = await fetch(url, { method: 'POST', headers, body: raw });
  assert.equal(first.status, 201);
  const receipt = await first.json();
  const duplicate = await fetch(url, { method: 'POST', headers, body: raw });
  assert.equal(duplicate.status, 200);
  assert.equal((await duplicate.json()).requestId, receipt.requestId);
  assert.equal(
    (await fetch(url, { method: 'POST', headers: signature('wrong', raw), body: raw })).status,
    401,
  );
  assert.equal((await fetch(url, { method: 'GET' })).status, 405);
  assert.equal(
    (await (await request(s, '/api/endpoints/' + created.endpoint.id + '/events')).json()).events[0]
      .duplicates,
    1,
  );
});
test('all endpoint/event operations are owner scoped, including local test sender and key rotation', async () => {
  const a = await session(),
    b = await session(),
    created = await endpoint(a),
    id = created.endpoint.id;
  const sent = await (
      await request(a, '/api/endpoints/' + id + '/send', 'POST', { raw: '{"type":"test"}' })
    ).json(),
    eventId = sent.response.requestId;
  for (const [path, method, body] of [
    ['/api/endpoints/' + id, 'GET'],
    ['/api/endpoints/' + id + '/events', 'GET'],
    ['/api/endpoints/' + id + '/export', 'GET'],
    ['/api/events/' + eventId, 'GET'],
    ['/api/endpoints/' + id, 'PATCH', { name: 'stolen', version: 1 }],
    ['/api/endpoints/' + id + '/rotate', 'POST', { version: 1 }],
    ['/api/endpoints/' + id + '/send', 'POST', { raw: '{}' }],
    ['/api/endpoints/' + id, 'DELETE', { version: 1 }],
  ])
    assert.equal((await request(b, path, method, body)).status, 404, path);
  assert.equal((await (await request(b, '/api/endpoints')).json()).endpoints.length, 0);
});
test('local sender uses real loopback HTTP and demonstrates valid, duplicate, invalid and expired requests', async () => {
  const s = await session(),
    created = await endpoint(s),
    path = '/api/endpoints/' + created.endpoint.id + '/send',
    data = { raw: '{"type":"demo"}', deliveryId: 'same-delivery-001' };
  const first = await (await request(s, path, 'POST', data)).json();
  assert.equal(first.httpStatus, 201);
  assert.equal(first.transport, 'loopback-http');
  assert.equal((await (await request(s, path, 'POST', data)).json()).httpStatus, 200);
  for (const mode of ['invalid-signature', 'expired'])
    assert.equal(
      (await (await request(s, path, 'POST', { ...data, mode })).json()).httpStatus,
      401,
    );
  assert.equal((await request(s, path, 'POST', { raw: '{}', mode: 'unknown' })).status, 400);
  assert.equal(
    (await (await request(s, path, 'POST', { raw: 'bad JSON' })).json()).httpStatus,
    400,
  );
});
test('rotation is one-time disclosure, stale mutations conflict, and history export never includes keys', async () => {
  const s = await session(),
    created = await endpoint(s),
    id = created.endpoint.id;
  await request(s, '/api/endpoints/' + id + '/send', 'POST', { raw: '{"type":"saved"}' });
  const rotated = await (
    await request(s, '/api/endpoints/' + id + '/rotate', 'POST', { version: 1 })
  ).json();
  assert.equal(rotated.endpoint.version, 2);
  assert.equal(
    (await request(s, '/api/endpoints/' + id + '/rotate', 'POST', { version: 1 })).status,
    409,
  );
  const view = await (await request(s, '/api/endpoints/' + id)).text();
  assert.ok(!view.includes(rotated.secret));
  assert.ok(!view.includes(created.secret));
  const exported = await request(s, '/api/endpoints/' + id + '/export');
  assert.match(exported.headers.get('content-disposition'), /attachment/);
  const json = await exported.text();
  assert.ok(!json.includes(created.secret));
  assert.ok(!json.includes(rotated.secret));
  assert.equal(JSON.parse(json).events.length, 1);
  assert.equal((await request(s, '/api/endpoints/' + id, 'DELETE', { version: 1 })).status, 409);
  assert.equal((await request(s, '/api/endpoints/' + id, 'DELETE', { version: 2 })).status, 204);
});
test('HTTP rejects bad hosts, private files, invalid JSON encodings and unsupported types', async () => {
  const status = await new Promise((resolve, reject) =>
    http
      .get(base + '/health', { headers: { Host: 'evil.invalid' } }, (r) => {
        r.resume();
        resolve(r.statusCode);
      })
      .on('error', reject),
  );
  assert.equal(status, 400);
  for (const path of ['/data/yanghooks.sqlite', '/upstream/api.step.py', '/%ZZ'])
    assert.ok([400, 404].includes((await fetch(base + path)).status));
  const s = await session();
  assert.equal(
    (await request(s, '/api/endpoints', 'POST', { name: 'test' }, { 'Content-Type': 'text/plain' }))
      .status,
    415,
  );
  assert.equal((await request(s, '/api/endpoints', 'POST', [])).status, 400);
  const bad = await fetch(base + '/api/endpoints', {
    method: 'POST',
    headers: { Cookie: s.cookie, 'X-CSRF-Token': s.csrf, 'Content-Type': 'application/json' },
    body: Buffer.from([0xc0, 0xaf]),
  });
  assert.equal(bad.status, 400);
});
test('public raw body is bounded with Content-Length and chunked transfer', async () => {
  const s = await session(),
    created = await endpoint(s),
    url = base + created.endpoint.path,
    body = 'x'.repeat(65537);
  assert.equal(
    (await fetch(url, { method: 'POST', headers: signature(created.secret, body), body })).status,
    413,
  );
  const status = await new Promise((resolve, reject) => {
    const req = http.request(
      url,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' },
      },
      (res) => {
        res.resume();
        resolve(res.statusCode);
      },
    );
    req.on('error', reject);
    req.end('a'.repeat(70000));
  });
  assert.equal(status, 413);
});
