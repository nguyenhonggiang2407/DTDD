import { DatabaseSync } from 'node:sqlite';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { check, object, text, version, quote, sha, normalize } from './validation.mjs';
import { inspectCSV } from './datasets.mjs';
import { compile } from './query.mjs';
import { csvFile } from './csv.mjs';

const TTL = 7 * 24 * 60 * 60 * 1000;
export class Store {
  constructor(path = ':memory:', clock = () => Date.now()) {
    this.clock = clock;
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;');
    this.db.exec(
      'CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, token_hash TEXT UNIQUE NOT NULL, csrf TEXT NOT NULL, expires INTEGER NOT NULL);' +
      'CREATE TABLE IF NOT EXISTS datasets (id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, name TEXT NOT NULL, table_name TEXT UNIQUE NOT NULL, columns_json TEXT NOT NULL, row_count INTEGER NOT NULL, source_bytes INTEGER NOT NULL, digest TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created INTEGER NOT NULL, updated INTEGER NOT NULL, UNIQUE(owner,digest));' +
      'CREATE INDEX IF NOT EXISTS dataset_owner ON datasets(owner,created);' +
      'CREATE TABLE IF NOT EXISTS reports (id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, dataset_id TEXT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE, name TEXT NOT NULL, definition_json TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created INTEGER NOT NULL, updated INTEGER NOT NULL);' +
      'CREATE INDEX IF NOT EXISTS report_owner ON reports(owner,updated);'
    );
    this.cleanup();
  }
  transaction(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try { const result = fn(); this.db.exec('COMMIT'); return result; }
    catch (err) { this.db.exec('ROLLBACK'); throw err; }
  }
  cleanup() {
    return this.transaction(() => {
      const tables = this.db.prepare('SELECT table_name FROM datasets WHERE owner IN (SELECT id FROM sessions WHERE expires<=?)').all(this.clock());
      for (const row of tables) this.db.exec('DROP TABLE IF EXISTS ' + quote(row.table_name));
      this.db.prepare('DELETE FROM sessions WHERE expires<=?').run(this.clock());
      return tables.length;
    });
  }
  newSession() {
    this.cleanup();
    const id = randomUUID(), cookie = randomBytes(32).toString('hex'), csrf = randomBytes(24).toString('hex');
    const expires = this.clock() + TTL;
    this.db.prepare('INSERT INTO sessions(id,token_hash,csrf,expires) VALUES(?,?,?,?)').run(id, sha(cookie), csrf, expires);
    return { cookie, session: { id, csrf, expires } };
  }
  session(cookie) {
    if (typeof cookie !== 'string' || !/^[a-f0-9]{64}$/.test(cookie)) return null;
    const session = this.db.prepare('SELECT id,csrf,expires FROM sessions WHERE token_hash=?').get(sha(cookie));
    if (!session) return null;
    if (session.expires <= this.clock()) { this.cleanup(); return null; }
    return session;
  }
  assertOwner(owner) {
    check(Boolean(this.db.prepare('SELECT 1 FROM sessions WHERE id=? AND expires>?').get(owner, this.clock())), 'Workspace hết hạn.', 401);
  }
  mapDataset(row) {
    return { id: row.id, name: row.name, columns: JSON.parse(row.columns_json), rowCount: row.row_count, sourceBytes: row.source_bytes, checksum: row.digest, version: row.version, created: row.created, updated: row.updated };
  }
  getRaw(owner, id) {
    this.assertOwner(owner);
    const row = this.db.prepare('SELECT * FROM datasets WHERE owner=? AND id=?').get(owner, id);
    check(row, 'Không tìm thấy tập dữ liệu.', 404);
    return row;
  }
  getDataset(owner, id) { return this.mapDataset(this.getRaw(owner, id)); }
  internalDataset(row) { return { id: row.id, table: row.table_name, columns: JSON.parse(row.columns_json) }; }
  listDatasets(owner, query = '') {
    this.assertOwner(owner);
    check(typeof query === 'string' && query.length <= 120, 'Tìm kiếm tối đa 120 ký tự.');
    const q = normalize(query.trim());
    return this.db.prepare('SELECT * FROM datasets WHERE owner=? ORDER BY created DESC,id').all(owner).map(row => this.mapDataset(row)).filter(row => normalize(row.name).includes(q));
  }
  importDataset(owner, payload) {
    this.assertOwner(owner);
    object(payload);
    const name = text(payload.name, 'Tên tập dữ liệu');
    const data = inspectCSV(payload);
    return this.transaction(() => {
      const old = this.db.prepare('SELECT * FROM datasets WHERE owner=? AND digest=?').get(owner, data.digest);
      if (old) return { dataset: this.mapDataset(old), reused: true };
      const total = this.db.prepare('SELECT COUNT(*) AS n FROM datasets WHERE owner=?').get(owner).n;
      check(total < 5, 'Mỗi workspace tối đa 5 tập dữ liệu. Xóa một tập cũ để nhập thêm.', 409);
      const id = randomUUID(), table = 'd_' + id.replaceAll('-', ''), now = this.clock();
      const declarations = data.columns.map(column => quote(column.key) + (column.type === 'number' ? ' REAL' : ' TEXT'));
      this.db.exec('CREATE TABLE ' + quote(table) + '(row_no INTEGER PRIMARY KEY,' + declarations.join(',') + ') STRICT');
      const insert = this.db.prepare('INSERT INTO ' + quote(table) + '(' + data.columns.map(column => quote(column.key)).join(',') + ') VALUES(' + data.columns.map(() => '?').join(',') + ')');
      for (const row of data.rows) insert.run(...row);
      this.db.prepare('INSERT INTO datasets(id,owner,name,table_name,columns_json,row_count,source_bytes,digest,created,updated) VALUES(?,?,?,?,?,?,?,?,?,?)')
        .run(id, owner, name, table, JSON.stringify(data.columns), data.rowCount, data.bytes, data.digest, now, now);
      return { dataset: this.getDataset(owner, id), reused: false };
    });
  }
  renameDataset(owner, id, payload) {
    const old = this.getRaw(owner, id);
    object(payload);
    const expected = version(payload.version), name = text(payload.name, 'Tên tập dữ liệu');
    check(old.version === expected, 'Dữ liệu đã thay đổi. Hãy tải lại.', 409);
    const changed = this.db.prepare('UPDATE datasets SET name=?,version=version+1,updated=? WHERE owner=? AND id=? AND version=?').run(name, this.clock(), owner, id, expected);
    check(changed.changes === 1, 'Dữ liệu đã thay đổi. Hãy tải lại.', 409);
    return this.getDataset(owner, id);
  }
  removeDataset(owner, id, expected) {
    const old = this.getRaw(owner, id);
    version(expected);
    check(old.version === expected, 'Dữ liệu đã thay đổi. Hãy tải lại.', 409);
    this.transaction(() => {
      this.db.exec('DROP TABLE ' + quote(old.table_name));
      const removed = this.db.prepare('DELETE FROM datasets WHERE owner=? AND id=? AND version=?').run(owner, id, expected);
      check(removed.changes === 1, 'Dữ liệu đã thay đổi. Hãy tải lại.', 409);
    });
  }
  query(owner, payload) {
    object(payload);
    check(typeof payload.datasetId === 'string', 'Cần chọn tập dữ liệu.');
    const row = this.getRaw(owner, payload.datasetId), compiled = compile(this.internalDataset(row), payload);
    const total = Number(this.db.prepare(compiled.countSQL).get(...compiled.countParams).n);
    const result = this.db.prepare(compiled.sql).all(...compiled.params);
    const rows = result.map(record => compiled.output.map((_, i) => record['out_' + i]));
    const plan = this.db.prepare('EXPLAIN QUERY PLAN ' + compiled.sql).all(...compiled.params).map(step => ({ id: step.id, parent: step.parent, detail: step.detail }));
    return { datasetId: row.id, columns: compiled.output, rows, total, limit: compiled.limit, offset: compiled.offset, sql: compiled.sql, params: compiled.params, plan, definition: compiled.definition };
  }
  exportQuery(owner, payload) {
    const result = this.query(owner, payload);
    return { content: csvFile([result.columns.map(column => column.name), ...result.rows]), rows: result.rows.length };
  }
  exportDataset(owner, id) {
    const row = this.getRaw(owner, id), dataset = this.internalDataset(row);
    const records = this.db.prepare('SELECT * FROM ' + quote(dataset.table) + ' ORDER BY row_no').all();
    return csvFile([dataset.columns.map(column => column.name), ...records.map(record => dataset.columns.map(column => record[column.key]))]);
  }
  mapReport(row) {
    return { id: row.id, datasetId: row.dataset_id, name: row.name, definition: JSON.parse(row.definition_json), version: row.version, created: row.created, updated: row.updated };
  }
  listReports(owner) {
    this.assertOwner(owner);
    return this.db.prepare('SELECT * FROM reports WHERE owner=? ORDER BY updated DESC,id').all(owner).map(row => this.mapReport(row));
  }
  getReport(owner, id) {
    this.assertOwner(owner);
    const row = this.db.prepare('SELECT * FROM reports WHERE owner=? AND id=?').get(owner, id);
    check(row, 'Không tìm thấy báo cáo.', 404);
    return this.mapReport(row);
  }
  saveReport(owner, payload, id) {
    this.assertOwner(owner); object(payload);
    const old = id ? this.getReport(owner, id) : null;
    if (old) check(old.version === version(payload.version), 'Báo cáo đã thay đổi. Hãy tải lại.', 409);
    const name = text(payload.name, 'Tên báo cáo'), definition = object(payload.definition);
    check(typeof definition.datasetId === 'string', 'Cần chọn tập dữ liệu.');
    const dataset = this.getRaw(owner, definition.datasetId);
    const compiled = compile(this.internalDataset(dataset), definition);
    compiled.definition.offset = 0;
    const now = this.clock();
    if (!old) {
      const count = this.db.prepare('SELECT COUNT(*) AS n FROM reports WHERE owner=?').get(owner).n;
      check(count < 20, 'Mỗi workspace tối đa 20 báo cáo.', 409);
      id = randomUUID();
      this.db.prepare('INSERT INTO reports(id,owner,dataset_id,name,definition_json,created,updated) VALUES(?,?,?,?,?,?,?)')
        .run(id, owner, dataset.id, name, JSON.stringify(compiled.definition), now, now);
    } else {
      const changed = this.db.prepare('UPDATE reports SET dataset_id=?,name=?,definition_json=?,version=version+1,updated=? WHERE owner=? AND id=? AND version=?')
        .run(dataset.id, name, JSON.stringify(compiled.definition), now, owner, id, payload.version);
      check(changed.changes === 1, 'Báo cáo đã thay đổi. Hãy tải lại.', 409);
    }
    return this.getReport(owner, id);
  }
  runReport(owner, id) { return this.query(owner, this.getReport(owner, id).definition); }
  removeReport(owner, id, expected) {
    this.getReport(owner, id); version(expected);
    const removed = this.db.prepare('DELETE FROM reports WHERE owner=? AND id=? AND version=?').run(owner, id, expected);
    check(removed.changes === 1, 'Báo cáo đã thay đổi. Hãy tải lại.', 409);
  }
  close() { this.db.close(); }
}
