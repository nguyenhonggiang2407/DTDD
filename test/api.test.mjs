import { test, after, before } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../server.mjs';
import { today, date } from '../lib/validation.mjs';
import { formatThread } from '../apps/yang-launch/domain.mjs';
import { infer } from '../apps/yang-spec/domain.mjs';

let app, base;
const fixedNow = Date.parse('2026-11-02T03:00:00Z');
before(async () => {
  app = createApp({ dbPath: ':memory:', clock: () => fixedNow });
  await new Promise((resolve) => app.server.listen(0, '127.0.0.1', resolve));
  base = 'http://127.0.0.1:' + app.server.address().port;
});
after(async () => app.close());
async function session() {
  const res = await fetch(base + '/api/workspace');
  const cookie = res.headers.get('set-cookie').split(';')[0];
  const data = await res.json();
  return { cookie, csrf: data.csrf };
}
async function call(s, path, method = 'GET', payload, headers = {}) {
  return fetch(base + path, {
    method,
    headers: {
      ...(s ? { Cookie: s.cookie, 'X-CSRF-Token': s.csrf } : {}),
      ...(payload === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
}
const meeting = (extra = {}) => ({
  title: 'Sprint review',
  date: '2026-11-02',
  participants: ['Giang'],
  notes: 'Meeting notes',
  summary: 'Keep the MVP small.',
  actions: [
    {
      description: 'Review access control',
      assignee: 'Giang',
      due: '2026-11-05',
      priority: 'high',
      done: false,
    },
  ],
  ...extra,
});
const post = (extra = {}) => ({
  title: 'Backend diary',
  campaign: 'Build in public',
  channel: 'blog',
  due: '2026-11-04',
  owner: 'Giang',
  caption: 'Write down meaningful test cases.',
  notes: '',
  stage: 'draft',
  ...extra,
});
const endpoint = (extra = {}) => ({
  method: 'GET',
  path: '/items/{id}',
  summary: 'Read one item',
  status: 200,
  request: null,
  response: '{"id":1,"active":true}',
  ...extra,
});
const contract = (extra = {}) => ({
  title: 'Document API',
  release: '1.0.0',
  description: 'Sample contract',
  endpoints: [endpoint()],
  ...extra,
});
async function create(s, module, data) {
  const res = await call(s, '/api/' + module + '/items', 'POST', { data });
  assert.equal(res.status, 201, await res.clone().text());
  return res.json();
}

test('bootstrap uses private cookies and CSP and does not serve data files', async () => {
  const res = await fetch(base + '/api/workspace');
  assert.match(res.headers.get('set-cookie'), /HttpOnly; SameSite=Strict/);
  assert.match(res.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal(res.headers.get('cache-control'), 'no-store');
  assert.equal((await fetch(base + '/data/yanglab.sqlite')).status, 404);
  assert.equal((await fetch(base + '/upstream/scheduler.py')).status, 404);
});
test('missing session, wrong CSRF and foreign origin fail', async () => {
  assert.equal((await call(null, '/api/minutes/items')).status, 401);
  const s = await session();
  assert.equal((await call({ ...s, csrf: '' }, '/api/minutes/demo', 'POST', {})).status, 403);
  assert.equal(
    (await call(s, '/api/minutes/demo', 'POST', {}, { Origin: 'https://evil.invalid' })).status,
    403,
  );
});
test('bootstrap rejects cross-site requests', async () => {
  assert.equal(
    (await fetch(base + '/api/workspace', { headers: { 'Sec-Fetch-Site': 'cross-site' } })).status,
    403,
  );
});
test('Host allowlist blocks DNS-rebinding style requests', async () => {
  const status = await new Promise((resolve, reject) => {
    const request = http.get(
      base + '/health',
      { headers: { Host: 'untrusted.invalid' } },
      (res) => {
        res.resume();
        resolve(res.statusCode);
      },
    );
    request.on('error', reject);
  });
  assert.equal(status, 400);
});
test('server limits body size, rejects non-JSON and malformed JSON', async () => {
  const s = await session();
  assert.equal(
    (await call(s, '/api/minutes/items', 'POST', { data: 'a'.repeat(270000) })).status,
    413,
  );
  assert.equal(
    (
      await fetch(base + '/api/minutes/items', {
        method: 'POST',
        headers: { Cookie: s.cookie, 'X-CSRF-Token': s.csrf, 'Content-Type': 'text/plain' },
        body: '{}',
      })
    ).status,
    415,
  );
  assert.equal(
    (
      await fetch(base + '/api/minutes/items', {
        method: 'POST',
        headers: { Cookie: s.cookie, 'X-CSRF-Token': s.csrf, 'Content-Type': 'application/json' },
        body: '{',
      })
    ).status,
    400,
  );
});
test('records are private across every operation and export', async () => {
  const a = await session(),
    b = await session();
  for (const [module, data] of [
    ['minutes', meeting()],
    ['launch', post()],
    ['spec', contract()],
  ]) {
    const record = await create(a, module, data);
    const path = '/api/' + module + '/items/' + record.id;
    assert.equal((await call(b, '/api/' + module + '/items')).status, 200);
    assert.equal((await (await call(b, '/api/' + module + '/items')).json()).items.length, 0);
    for (const suffix of ['', '/history', '/export'])
      assert.equal((await call(b, path + suffix)).status, 404);
    assert.equal((await call(b, path, 'PUT', { version: 1, data })).status, 404);
    assert.equal((await call(b, path, 'DELETE', { version: 1 })).status, 404);
  }
});
test('date validation includes leap years and Vietnamese day boundary', () => {
  assert.equal(date('2024-02-29'), '2024-02-29');
  assert.throws(() => date('2026-02-29'));
  assert.throws(() => date('2026-11-31'));
  assert.equal(
    today(() => Date.parse('2026-11-01T18:00:00Z')),
    '2026-11-02',
  );
});
test('meeting checklist extraction keeps assignee, due and completed state', async () => {
  const s = await session();
  const res = await call(s, '/api/minutes/extract', 'POST', {
    notes:
      '- [ ] @Giang | 2026-11-04 | Kiểm tra API\n- [x] @Linh | 2026-11-05 | Chuẩn bị ảnh\n- [ ] @A | 2026-02-30 | Invalid date\n- [ ] Wrong structure',
  });
  const data = await res.json();
  assert.equal(data.actions.length, 2);
  assert.equal(data.actions[0].assignee, 'Giang');
  assert.equal(data.actions[1].done, true);
  assert.equal(data.warnings.length, 2);
});
test('meeting action completion is versioned and stale toggles fail', async () => {
  const s = await session(),
    record = await create(s, 'minutes', meeting());
  const path = '/api/minutes/items/' + record.id + '/actions/' + record.data.actions[0].id;
  const res = await call(s, path, 'PATCH', { version: 1, done: true });
  assert.equal(res.status, 200);
  const updated = await res.json();
  assert.equal(updated.version, 2);
  assert.equal(updated.data.actions[0].done, true);
  assert.equal((await call(s, path, 'PATCH', { version: 1, done: false })).status, 409);
  assert.equal((await call(s, path, 'PATCH', { version: 2, done: 'false' })).status, 400);
});
test('meeting Markdown and ICS carry exact deadlines and safe folded lines', async () => {
  const s = await session();
  const record = await create(
    s,
    'minutes',
    meeting({
      actions: [
        {
          description: 'Đọc, ghi chú; ' + 'tài liệu '.repeat(20),
          due: '2026-11-05',
          assignee: 'Giang',
          done: false,
        },
      ],
    }),
  );
  const path = '/api/minutes/items/' + record.id + '/export';
  const md = await (await call(s, path)).text();
  assert.match(md, /2026-11-05/);
  const ics = await (await call(s, path + '?format=ics')).text();
  assert.match(ics, /DUE;VALUE=DATE:20261105/);
  assert.match(ics, /Đọc\\, ghi chú\\;/);
  assert.match(ics, /STATUS:NEEDS-ACTION/);
  for (const line of ics.split('\r\n')) assert.ok(Buffer.byteLength(line) <= 75);
});
test('meeting rejects invalid dates, oversized participants and duplicate action IDs', async () => {
  const s = await session();
  const sameId = '12345678';
  for (const data of [
    meeting({ date: '2026-02-30' }),
    meeting({ participants: Array(17).fill('A') }),
    meeting({
      actions: [
        { id: sameId, description: 'A' },
        { id: sameId, description: 'B' },
      ],
    }),
  ]) {
    assert.equal((await call(s, '/api/minutes/items', 'POST', { data })).status, 400);
  }
});
test('launch enforces approval transitions on PATCH and PUT', async () => {
  const s = await session(),
    record = await create(s, 'launch', post());
  const path = '/api/launch/items/' + record.id;
  assert.equal(
    (await call(s, path + '/stage', 'PATCH', { version: 1, stage: 'published' })).status,
    409,
  );
  assert.equal(
    (await call(s, path, 'PUT', { version: 1, data: post({ stage: 'published' }) })).status,
    409,
  );
  let current = record;
  for (const stage of ['review', 'ready', 'published']) {
    const res = await call(s, path + '/stage', 'PATCH', { version: current.version, stage });
    assert.equal(res.status, 200);
    current = await res.json();
  }
  assert.equal(current.version, 4);
  assert.equal(current.data.stage, 'published');
});
test('launch rejects empty drafts and unknown channels', async () => {
  const s = await session();
  for (const data of [
    post({ caption: '' }),
    post({ channel: 'unknown' }),
    post({ stage: 'published' }),
  ]) {
    assert.equal((await call(s, '/api/launch/items', 'POST', { data })).status, 400);
  }
});
test('launch filters dates, stages and accent-insensitive search', async () => {
  const s = await session();
  await create(s, 'launch', post({ title: 'Lập trình backend', due: '2026-11-06' }));
  await create(s, 'launch', post({ title: 'Khác', due: '2026-11-09' }));
  const res = await call(
    s,
    '/api/launch/items?q=lap%20trinh&stage=draft&from=2026-11-05&to=2026-11-07',
  );
  assert.equal((await res.json()).items.length, 1);
});
test('launch exports safe CSV and ports the original thread formatter', async () => {
  const s = await session();
  await create(
    s,
    'launch',
    post({ title: '=HYPERLINK("bad")', caption: 'Quoted "caption", with comma' }),
  );
  const res = await call(s, '/api/launch/export.csv');
  const output = await res.text();
  assert.match(output, /'=HYPERLINK/);
  assert.match(output, /""caption""/);
  assert.match(res.headers.get('content-disposition'), /yanglaunch-plan.csv/);
  assert.equal(
    formatThread({
      tweets: [{ content: 'one', media_urls: ['https://example.com/a.png'] }, { content: 'two' }],
    }),
    'one\nhttps://example.com/a.png\n\n\n\ntwo',
  );
});
test('API schema inference supports nullable and heterogeneous arrays', () => {
  assert.deepEqual(infer(null), { type: 'null' });
  const schema = infer({ count: 2, ratio: 1.5, mixed: [1, 'a', null], empty: [] });
  assert.equal(schema.properties.count.type, 'integer');
  assert.equal(schema.properties.ratio.type, 'number');
  assert.equal(schema.properties.mixed.items.anyOf.length, 3);
  assert.deepEqual(schema.properties.empty.items, {});
});
test('spec generates OpenAPI path parameters and JSON request/response schemas', async () => {
  const s = await session(),
    record = await create(
      s,
      'spec',
      contract({
        endpoints: [
          endpoint(),
          endpoint({ method: 'POST', path: '/items', request: '{"title":"Guide"}', status: 201 }),
        ],
      }),
    );
  const res = await call(s, '/api/spec/items/' + record.id + '/export?format=json');
  const doc = await res.json();
  assert.equal(doc.openapi, '3.1.0');
  assert.equal(doc.paths['/items/{id}'].get.parameters[0].required, true);
  assert.equal(
    doc.paths['/items'].post.requestBody.content['application/json'].schema.type,
    'object',
  );
  assert.equal(doc.paths['/items'].post.responses['201'].content['application/json'].example.id, 1);
});
test('spec rejects invalid JSON, invalid paths, duplicate and ambiguous operations', async () => {
  const s = await session();
  const cases = [
    [endpoint({ response: '{' })],
    [endpoint({ path: '/items?q=x' })],
    [endpoint({ path: '/items/{id}/{id}' })],
    [endpoint({ method: 'CONNECT' })],
    [endpoint({ request: '{"q":1}' })],
    [endpoint({ status: 204 })],
    [endpoint(), endpoint()],
    [endpoint(), endpoint({ path: '/items/{slug}', method: 'POST' })],
  ];
  for (const endpoints of cases) {
    const res = await call(s, '/api/spec/items', 'POST', { data: contract({ endpoints }) });
    assert.equal(res.status, 400, await res.clone().text());
  }
});
test('spec allows multiple methods on one path and no-content responses', async () => {
  const s = await session();
  const record = await create(
    s,
    'spec',
    contract({
      endpoints: [endpoint(), endpoint({ method: 'DELETE', status: 204, response: null })],
    }),
  );
  const doc = await (await call(s, '/api/spec/items/' + record.id + '/export?format=json')).json();
  assert.equal(doc.paths['/items/{id}'].delete.responses['204'].content, undefined);
});
test('spec infers prototype-like JSON keys without changing object prototypes', async () => {
  const s = await session(),
    record = await create(
      s,
      'spec',
      contract({ endpoints: [endpoint({ response: '{"__proto__":{"polluted":true}}' })] }),
    );
  const doc = await (await call(s, '/api/spec/items/' + record.id + '/export?format=json')).json();
  assert.equal(
    doc.paths['/items/{id}'].get.responses['200'].content['application/json'].schema.properties
      .__proto__.type,
    'object',
  );
  assert.equal({}.polluted, undefined);
});
test('stale edit and stale delete preserve current data; revision history stays private', async () => {
  const s = await session(),
    record = await create(s, 'spec', contract());
  const path = '/api/spec/items/' + record.id;
  assert.equal(
    (await call(s, path, 'PUT', { version: 1, data: contract({ title: 'Updated' }) })).status,
    200,
  );
  assert.equal((await call(s, path, 'PUT', { version: 1, data: contract() })).status, 409);
  assert.equal((await call(s, path, 'DELETE', { version: 1 })).status, 409);
  const history = await (await call(s, path + '/history')).json();
  assert.equal(history.revisions.length, 2);
  assert.equal(history.revisions[1].data.title, 'Document API');
  assert.equal((await call(s, path, 'DELETE', { version: 2 })).status, 204);
  assert.equal((await call(s, path + '/history')).status, 404);
  assert.equal(
    app.store.db.prepare('SELECT count(*) n FROM revisions WHERE record=?').get(record.id).n,
    0,
  );
});
test('demo import is idempotent and activity only belongs to its workspace', async () => {
  const a = await session(),
    b = await session();
  for (const module of ['minutes', 'launch', 'spec']) {
    assert.ok((await (await call(a, '/api/' + module + '/demo', 'POST', {})).json()).added > 0);
    assert.equal((await (await call(a, '/api/' + module + '/demo', 'POST', {})).json()).added, 0);
  }
  assert.ok((await (await call(a, '/api/activity')).json()).events.length > 0);
  assert.equal((await (await call(b, '/api/activity')).json()).events.length, 0);
});
test('expired workspace cannot read its previous items and gets cleaned', async () => {
  const s = await session();
  await create(s, 'minutes', meeting());
  const hashed = app.store.session(s.cookie.split('=')[1]).id;
  app.store.db.prepare('UPDATE sessions SET expires=? WHERE id=?').run(fixedNow - 1, hashed);
  assert.equal((await call(s, '/api/minutes/items')).status, 401);
  await session();
  assert.equal(
    app.store.db.prepare('SELECT count(*) n FROM records WHERE owner=?').get(hashed).n,
    0,
  );
});
test('per-module quota is checked transactionally', async () => {
  const s = await session();
  for (let i = 0; i < 50; i++)
    await create(s, 'spec', contract({ title: 'API ' + i, endpoints: [] }));
  assert.equal((await call(s, '/api/spec/items', 'POST', { data: contract() })).status, 409);
  assert.equal((await (await call(s, '/api/spec/items')).json()).items.length, 50);
  await create(s, 'minutes', meeting());
});
