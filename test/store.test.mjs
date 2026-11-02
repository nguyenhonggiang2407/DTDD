import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Store } from '../lib/store.mjs';

const fixture =
  'id,region,sales,note\n001,Bắc,10,100%_done\n002,Bắc,20,\n003,Nam,30,<script>alert(1)</script>\n004,Nam,,\n005,Bắc,-5,"quote\' OR 1=1--"\n';
function setup(t, clock) {
  const store = new Store(':memory:', clock);
  t.after(() => store.close());
  const owner = store.newSession().session.id;
  const dataset = store.importDataset(owner, { name: 'Bán hàng', csv: fixture }).dataset;
  return {
    store,
    owner,
    dataset,
    query: (extra) => store.query(owner, { datasetId: dataset.id, ...extra }),
  };
}
test('import creates a strict typed table and canonical duplicate reuse is workspace scoped', (t) => {
  const { store, owner, dataset } = setup(t);
  assert.equal(dataset.rowCount, 5);
  assert.equal(dataset.columns[0].type, 'text');
  assert.equal(dataset.columns[2].type, 'number');
  assert.equal(
    store.importDataset(owner, { name: 'Copy', csv: fixture.replaceAll('\n', '\r\n') }).dataset.id,
    dataset.id,
  );
  const other = store.newSession().session.id;
  assert.notEqual(
    store.importDataset(other, { name: 'Other', csv: fixture }).dataset.id,
    dataset.id,
  );
  assert.equal(store.listDatasets(owner, 'ban hang').length, 1);
  assert.equal(
    store.db
      .prepare("SELECT COUNT(*) AS n FROM sqlite_master WHERE name GLOB 'd_*' AND type='table'")
      .get().n,
    2,
  );
});
test('ordered pages, projections, count and real SQLite EXPLAIN agree', (t) => {
  const { query } = setup(t);
  const first = query({ columns: ['c0', 'c2'], orderBy: 'c2', direction: 'desc', limit: 2 });
  assert.deepEqual(first.rows, [
    ['003', 30],
    ['002', 20],
  ]);
  assert.equal(first.total, 5);
  const next = query({
    columns: ['c0', 'c2'],
    orderBy: 'c2',
    direction: 'desc',
    limit: 2,
    offset: 2,
  });
  assert.deepEqual(next.rows, [
    ['001', 10],
    ['005', -5],
  ]);
  assert.ok(first.plan.some((x) => /SCAN/.test(x.detail)));
  assert.match(first.sql, /LIMIT \? OFFSET \?/);
});
test('SQLite NULL semantics and all numeric filter operators are explicit', (t) => {
  const { query } = setup(t);
  assert.deepEqual(query({ columns: ['c0'], filters: [{ column: 'c2', op: 'isnull' }] }).rows, [
    ['004'],
  ]);
  assert.equal(query({ filters: [{ column: 'c2', op: 'notnull' }] }).total, 4);
  for (const [op, value, count] of [
    ['gt', 10, 2],
    ['gte', 10, 3],
    ['lt', 10, 1],
    ['lte', 10, 2],
    ['eq', 20, 1],
    ['ne', 20, 3],
  ]) {
    assert.equal(query({ filters: [{ column: 'c2', op, value }] }).total, count);
  }
});
test('LIKE escapes percent, underscore and backslash and hostile values stay data', (t) => {
  const { query, store, owner, dataset } = setup(t);
  assert.deepEqual(
    query({ columns: ['c0'], filters: [{ column: 'c3', op: 'contains', value: '%_' }] }).rows,
    [['001']],
  );
  assert.equal(query({ filters: [{ column: 'c3', op: 'eq', value: "' OR 1=1--" }] }).total, 0);
  assert.equal(query({ filters: [{ column: 'c3', op: 'starts', value: "quote' OR" }] }).total, 1);
  assert.equal(store.getDataset(owner, dataset.id).rowCount, 5);
  const other = store.importDataset(owner, { name: 'slashes', csv: 's\n"a\\%_b"\nabc\n' }).dataset;
  assert.equal(
    store.query(owner, {
      datasetId: other.id,
      filters: [{ column: 'c0', op: 'contains', value: '\\%_' }],
    }).total,
    1,
  );
});
test('grouped SUM, AVG, MIN, MAX, COUNT(*) and COUNT(column) run over stored rows', (t) => {
  const { query } = setup(t);
  const grouped = query({
    groupBy: 'c1',
    aggregate: { fn: 'sum', column: 'c2' },
    orderBy: 'value',
    direction: 'desc',
  });
  assert.deepEqual(grouped.rows, [
    ['Nam', 30],
    ['Bắc', 25],
  ]);
  assert.equal(grouped.total, 2);
  assert.deepEqual(query({ aggregate: { fn: 'count', column: '*' } }).rows, [[5]]);
  assert.deepEqual(query({ aggregate: { fn: 'count', column: 'c2' } }).rows, [[4]]);
  assert.deepEqual(query({ aggregate: { fn: 'avg', column: 'c2' } }).rows, [[13.75]]);
  assert.deepEqual(query({ aggregate: { fn: 'min', column: 'c2' } }).rows, [[-5]]);
  assert.deepEqual(query({ aggregate: { fn: 'max', column: 'c2' } }).rows, [[30]]);
});
test('no-match aggregations return SQL COUNT=0 and SUM=NULL', (t) => {
  const { query } = setup(t),
    filters = [{ column: 'c1', op: 'eq', value: 'Missing' }];
  assert.deepEqual(query({ filters, aggregate: { fn: 'count', column: '*' } }).rows, [[0]]);
  assert.deepEqual(query({ filters, aggregate: { fn: 'sum', column: 'c2' } }).rows, [[null]]);
  assert.equal(query({ filters, groupBy: 'c1', aggregate: { fn: 'sum', column: 'c2' } }).total, 0);
});
test('dataset quota is atomic, duplicate reuse remains possible at quota and no orphan table is created', (t) => {
  const { store, owner } = setup(t);
  for (let i = 0; i < 4; i++)
    store.importDataset(owner, { name: 'Set ' + i, csv: 'n\n' + i + '\n' });
  assert.equal(store.listDatasets(owner).length, 5);
  assert.equal(store.importDataset(owner, { name: 'Same', csv: fixture }).reused, true);
  assert.throws(
    () => store.importDataset(owner, { name: 'Six', csv: 'n\n99\n' }),
    (err) => err.status === 409,
  );
  assert.throws(() => store.importDataset(owner, { name: 'Bad', csv: 'a,b\n1\n' }));
  assert.equal(
    store.db
      .prepare("SELECT COUNT(*) AS n FROM sqlite_master WHERE name GLOB 'd_*' AND type='table'")
      .get().n,
    5,
  );
});
test('ownership covers dataset query, exports, rename, deletion, reports and saved-run paths', (t) => {
  const { store, owner, dataset } = setup(t),
    other = store.newSession().session.id;
  const report = store.saveReport(owner, { name: 'Report', definition: { datasetId: dataset.id } });
  for (const operation of [
    () => store.getDataset(other, dataset.id),
    () => store.query(other, { datasetId: dataset.id }),
    () => store.exportDataset(other, dataset.id),
    () => store.exportQuery(other, { datasetId: dataset.id }),
    () => store.renameDataset(other, dataset.id, { name: 'Stolen', version: 1 }),
    () => store.removeDataset(other, dataset.id, 1),
    () => store.getReport(other, report.id),
    () => store.runReport(other, report.id),
    () => store.saveReport(other, { name: 'Invalid', definition: { datasetId: dataset.id } }),
    () =>
      store.saveReport(
        other,
        { name: 'Invalid', version: 1, definition: { datasetId: dataset.id } },
        report.id,
      ),
    () => store.removeReport(other, report.id, 1),
  ])
    assert.throws(operation, (err) => err.status === 404);
  assert.equal(store.listReports(other).length, 0);
});
test('report snapshots reset pagination, preserve query and reject stale updates and deletes', (t) => {
  const { store, owner, dataset } = setup(t);
  const first = store.saveReport(owner, {
    name: 'North',
    definition: {
      datasetId: dataset.id,
      filters: [{ column: 'c1', op: 'eq', value: 'Bắc' }],
      limit: 2,
      offset: 2,
    },
  });
  assert.equal(first.definition.offset, 0);
  assert.equal(store.runReport(owner, first.id).total, 3);
  const next = store.saveReport(
    owner,
    { name: 'North updated', definition: first.definition, version: 1 },
    first.id,
  );
  assert.equal(next.version, 2);
  assert.throws(
    () =>
      store.saveReport(
        owner,
        { name: 'Stale', definition: first.definition, version: 1 },
        first.id,
      ),
    (err) => err.status === 409,
  );
  assert.throws(
    () => store.removeReport(owner, first.id, 1),
    (err) => err.status === 409,
  );
  store.removeReport(owner, first.id, 2);
  assert.equal(store.listReports(owner).length, 0);
});
test('saved report quotas do not affect existing reports', (t) => {
  const { store, owner, dataset } = setup(t);
  for (let i = 0; i < 20; i++)
    store.saveReport(owner, { name: 'Report ' + i, definition: { datasetId: dataset.id } });
  assert.throws(
    () => store.saveReport(owner, { name: 'Too many', definition: { datasetId: dataset.id } }),
    (err) => err.status === 409,
  );
  assert.equal(store.listReports(owner).length, 20);
});
test('rename CAS and dataset removal cascade reports and physical table in the same transaction', (t) => {
  const { store, owner, dataset } = setup(t);
  store.saveReport(owner, { name: 'Linked', definition: { datasetId: dataset.id } });
  const renamed = store.renameDataset(owner, dataset.id, { name: 'New title', version: 1 });
  assert.equal(renamed.version, 2);
  assert.throws(
    () => store.renameDataset(owner, dataset.id, { name: 'Old', version: 1 }),
    (err) => err.status === 409,
  );
  assert.throws(
    () => store.removeDataset(owner, dataset.id, 1),
    (err) => err.status === 409,
  );
  store.removeDataset(owner, dataset.id, 2);
  assert.equal(store.listReports(owner).length, 0);
  assert.equal(store.listDatasets(owner).length, 0);
  assert.equal(
    store.db
      .prepare("SELECT COUNT(*) AS n FROM sqlite_master WHERE name GLOB 'd_*' AND type='table'")
      .get().n,
    0,
  );
});
test('exports use current selection or full dataset, with stable ordering and formula protection', (t) => {
  const { store, owner, dataset } = setup(t);
  const exported = store.exportQuery(owner, { datasetId: dataset.id, columns: ['c0'], limit: 2 });
  assert.equal(exported.rows, 2);
  assert.match(exported.content, /"001"\r\n"002"/);
  assert.ok(!exported.content.includes('003'));
  const all = store.exportDataset(owner, dataset.id);
  assert.match(all, /"005"/);
  const malicious = store.importDataset(owner, {
    name: 'Formulas',
    csv: 'n,amount\n=1+1,-5\n',
  }).dataset;
  const safe = store.exportDataset(owner, malicious.id);
  assert.match(safe, /"'=1\+1","-5"/);
});
test('expired workspace drops physical data and cascades reports without touching active owners', (t) => {
  let now = 1000;
  const { store, owner, dataset } = setup(t, () => now);
  store.saveReport(owner, { name: 'Expires', definition: { datasetId: dataset.id } });
  now += 1000;
  const fresh = store.newSession();
  const alive = store.importDataset(fresh.session.id, { name: 'Alive', csv: 'n\n1\n' }).dataset;
  now = 1000 + 7 * 86400000;
  assert.equal(store.cleanup(), 1);
  assert.equal(store.session(fresh.cookie).id, fresh.session.id);
  assert.throws(
    () => store.getDataset(owner, dataset.id),
    (err) => err.status === 401,
  );
  assert.equal(store.getDataset(fresh.session.id, alive.id).rowCount, 1);
  assert.equal(store.db.prepare('SELECT COUNT(*) AS n FROM reports').get().n, 0);
  assert.equal(
    store.db
      .prepare("SELECT COUNT(*) AS n FROM sqlite_master WHERE name GLOB 'd_*' AND type='table'")
      .get().n,
    1,
  );
});
test('SQLite restart retains sessions, data, schema and report definitions', () => {
  const dir = mkdtempSync(join(tmpdir(), 'yangdata-')),
    path = join(dir, 'data.sqlite');
  let store;
  try {
    store = new Store(path);
    const fresh = store.newSession(),
      owner = fresh.session.id;
    const dataset = store.importDataset(owner, { name: 'Persistent', csv: fixture }).dataset;
    const report = store.saveReport(owner, {
      name: 'SUM',
      definition: { datasetId: dataset.id, aggregate: { fn: 'sum', column: 'c2' } },
    });
    store.close();
    store = new Store(path);
    assert.equal(store.session(fresh.cookie).id, owner);
    assert.deepEqual(store.runReport(owner, report.id).rows, [[55]]);
    assert.equal(store.getDataset(owner, dataset.id).columns[0].type, 'text');
  } finally {
    store?.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
