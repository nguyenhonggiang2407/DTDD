import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../server.mjs';
let app, base;
before(async () => {
  app = createApp({ dbPath: ':memory:' });
  await new Promise((done) => app.server.listen(0, '127.0.0.1', done));
  base = 'http://127.0.0.1:' + app.server.address().port;
});
after(async () => app.close());
async function session() {
  const response = await fetch(base + '/api/workspace'),
    body = await response.json();
  return { cookie: response.headers.get('set-cookie').split(';')[0], csrf: body.csrf };
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
async function imported(s, data = { name: 'Orders', csv: 'id,city,amount\n001,A,10\n002,B,20\n' }) {
  const response = await request(s, '/api/datasets', 'POST', data);
  assert.equal(response.status, 201, await response.clone().text());
  return (await response.json()).dataset;
}
test('HTTP establishes private non-sliding cookies, rejects cross-site bootstrap and sets CSP', async () => {
  const response = await fetch(base + '/api/workspace');
  assert.match(response.headers.get('set-cookie'), /HttpOnly; SameSite=Strict/);
  assert.match(response.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  const s = await session();
  const again = await request(s, '/api/workspace');
  assert.equal(again.headers.get('set-cookie'), null);
  assert.equal(
    (await request(s, '/api/workspace', 'GET', undefined, { 'Sec-Fetch-Site': 'cross-site' }))
      .status,
    403,
  );
  assert.equal((await request(null, '/api/datasets')).status, 401);
});
test('mutations and private query POSTs require matching CSRF and Origin', async () => {
  const s = await session();
  for (const path of ['/api/demo', '/api/query', '/api/inspect', '/api/datasets', '/api/reports']) {
    assert.equal((await request({ ...s, csrf: '' }, path, 'POST', {})).status, 403);
    assert.equal(
      (await request(s, path, 'POST', {}, { Origin: 'https://evil.invalid' })).status,
      403,
    );
  }
});
test('Host and static allowlist keep SQLite files, original source and traversal private', async () => {
  const status = await new Promise((resolve, reject) =>
    http
      .get(base + '/health', { headers: { Host: 'evil.invalid' } }, (r) => {
        r.resume();
        resolve(r.statusCode);
      })
      .on('error', reject),
  );
  assert.equal(status, 400);
  for (const path of [
    '/data/yangdata.sqlite',
    '/upstream/rag_sql_tools.py',
    '/assets/../server.mjs',
    '/%ZZ',
  ])
    assert.ok([400, 404].includes((await fetch(base + path)).status));
  assert.deepEqual(await (await fetch(base + '/health')).json(), { status: 'ok' });
});
test('JSON parser rejects content types, compression, invalid UTF-8, arrays and oversized CSV', async () => {
  const s = await session();
  assert.equal(
    (await request(s, '/api/inspect', 'POST', { csv: 'a\n1\n' }, { 'Content-Type': 'text/plain' }))
      .status,
    415,
  );
  assert.equal(
    (await request(s, '/api/inspect', 'POST', { csv: 'a\n1\n' }, { 'Content-Encoding': 'gzip' }))
      .status,
    415,
  );
  assert.equal((await request(s, '/api/inspect', 'POST', [])).status, 400);
  const invalid = await fetch(base + '/api/inspect', {
    method: 'POST',
    headers: { Cookie: s.cookie, 'X-CSRF-Token': s.csrf, 'Content-Type': 'application/json' },
    body: Buffer.from([0xc0, 0xaf]),
  });
  assert.equal(invalid.status, 400);
  assert.equal(
    (await request(s, '/api/inspect', 'POST', { csv: 'a\n' + 'x'.repeat(524288) })).status,
    413,
  );
});
test('streamed JSON remains bounded even without Content-Length', async () => {
  const s = await session();
  const status = await new Promise((resolve, reject) => {
    const req = http.request(
      base + '/api/inspect',
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
    req.end('a'.repeat(1100 * 1024));
  });
  assert.equal(status, 413);
});
test('inspection never persists data; imports deduplicate and support accent-free name search', async () => {
  const s = await session(),
    data = { name: 'Doanh thu thử nghiệm', csv: 'id,total\n001,12\n' };
  const preview = await request(s, '/api/inspect', 'POST', data);
  assert.equal(preview.status, 200);
  assert.equal((await preview.json()).preview[0][0], '001');
  assert.equal((await (await request(s, '/api/datasets')).json()).datasets.length, 0);
  const dataset = await imported(s, data);
  const reused = await request(s, '/api/datasets', 'POST', data);
  assert.equal(reused.status, 200);
  assert.equal((await reused.json()).dataset.id, dataset.id);
  assert.equal((await (await request(s, '/api/datasets?q=doanh%20thu')).json()).datasets.length, 1);
});
test('dataset and report endpoints enforce workspace ownership for every operation', async () => {
  const a = await session(),
    b = await session(),
    dataset = await imported(a),
    id = dataset.id;
  const report = (
    await (
      await request(a, '/api/reports', 'POST', { name: 'Report', definition: { datasetId: id } })
    ).json()
  ).report;
  for (const [path, method, data] of [
    ['/api/datasets/' + id, 'GET'],
    ['/api/datasets/' + id + '/export', 'GET'],
    ['/api/datasets/' + id, 'PATCH', { name: 'Stolen', version: 1 }],
    ['/api/datasets/' + id, 'DELETE', { version: 1 }],
    ['/api/query', 'POST', { datasetId: id }],
    ['/api/query/export', 'POST', { datasetId: id }],
    ['/api/reports/' + report.id, 'GET'],
    ['/api/reports/' + report.id + '/run', 'POST', {}],
    ['/api/reports/' + report.id + '/export', 'POST', {}],
    [
      '/api/reports/' + report.id,
      'PUT',
      { name: 'Stolen', version: 1, definition: { datasetId: id } },
    ],
    ['/api/reports/' + report.id, 'DELETE', { version: 1 }],
  ])
    assert.equal((await request(b, path, method, data)).status, 404, path);
  assert.equal((await (await request(b, '/api/reports')).json()).reports.length, 0);
});
test('query API returns real parameterized SQL, result metadata and query plan; free SQL is rejected', async () => {
  const s = await session(),
    dataset = await imported(s);
  const response = await request(s, '/api/query', 'POST', {
    datasetId: dataset.id,
    filters: [{ column: 'c1', op: 'eq', value: 'A' }],
    aggregate: { fn: 'sum', column: 'c2' },
  });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.deepEqual(result.rows, [[10]]);
  assert.deepEqual(result.params, ['A', 50, 0]);
  assert.ok(result.plan.length);
  assert.match(result.sql, /SUM/);
  assert.equal(
    (await request(s, '/api/query', 'POST', { datasetId: dataset.id, sql: 'DROP TABLE sessions' }))
      .status,
    400,
  );
});
test('report create, run, CAS update, export and cascading deletion form a complete flow', async () => {
  const s = await session(),
    dataset = await imported(s),
    definition = { datasetId: dataset.id, columns: ['c0'] };
  const created = await request(s, '/api/reports', 'POST', { name: 'IDs', definition });
  assert.equal(created.status, 201);
  const report = (await created.json()).report;
  assert.equal((await request(s, '/api/reports/' + report.id + '/run', 'POST', {})).status, 200);
  const renamed = await request(s, '/api/reports/' + report.id, 'PUT', {
    name: 'Updated',
    definition,
    version: 1,
  });
  assert.equal(renamed.status, 200);
  assert.equal(
    (await request(s, '/api/reports/' + report.id, 'DELETE', { version: 1 })).status,
    409,
  );
  const csv = await request(s, '/api/reports/' + report.id + '/export', 'POST', {});
  assert.match(csv.headers.get('content-disposition'), /attachment/);
  assert.match(await csv.text(), /"001"/);
  const dataRename = await request(s, '/api/datasets/' + dataset.id, 'PATCH', {
    name: 'Renamed',
    version: 1,
  });
  assert.equal(dataRename.status, 200);
  assert.equal(
    (await request(s, '/api/datasets/' + dataset.id, 'DELETE', { version: 1 })).status,
    409,
  );
  assert.equal(
    (await request(s, '/api/datasets/' + dataset.id, 'DELETE', { version: 2 })).status,
    204,
  );
  assert.equal((await request(s, '/api/reports/' + report.id)).status, 404);
});
test('query exports include only the requested page while dataset exports retain all rows', async () => {
  const s = await session(),
    dataset = await imported(s);
  const page = await request(s, '/api/query/export', 'POST', {
    datasetId: dataset.id,
    columns: ['c0'],
    limit: 1,
  });
  assert.match(page.headers.get('content-type'), /text\/csv/);
  const text = await page.text();
  assert.match(text, /"001"/);
  assert.ok(!text.includes('002'));
  assert.match(await (await request(s, '/api/datasets/' + dataset.id + '/export')).text(), /"002"/);
});
test('sample import is idempotent within a workspace and independent across workspaces', async () => {
  const a = await session(),
    b = await session();
  const first = await (await request(a, '/api/demo', 'POST', {})).json();
  assert.equal(first.dataset.rowCount, 12);
  assert.equal(first.reused, false);
  assert.equal(
    (await (await request(a, '/api/demo', 'POST', {})).json()).dataset.id,
    first.dataset.id,
  );
  assert.equal((await (await request(b, '/api/datasets')).json()).datasets.length, 0);
});
