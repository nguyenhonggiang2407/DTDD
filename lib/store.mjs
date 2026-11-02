import { DatabaseSync } from 'node:sqlite';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { HttpError, requireThat } from './validation.mjs';

const hash = (value) => createHash('sha256').update(value).digest('hex');
export const RETENTION = 7 * 86400000;
export class Store {
  constructor(path, clock = () => Date.now()) {
    this.clock = clock;
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;');
    this.db.exec(
      [
        'CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY,csrf TEXT NOT NULL,expires INTEGER NOT NULL);',
        "CREATE TABLE IF NOT EXISTS records(id TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,module TEXT NOT NULL CHECK(module IN ('minutes','launch','spec')),body TEXT NOT NULL CHECK(json_valid(body)),version INTEGER NOT NULL DEFAULT 1,created INTEGER NOT NULL,updated INTEGER NOT NULL);",
        'CREATE INDEX IF NOT EXISTS records_owner ON records(owner,module);',
        'CREATE TABLE IF NOT EXISTS revisions(record TEXT NOT NULL REFERENCES records(id) ON DELETE CASCADE,version INTEGER NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(record,version));',
        'CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,module TEXT NOT NULL,entity TEXT NOT NULL,action TEXT NOT NULL,at INTEGER NOT NULL);',
      ].join('\n'),
    );
  }
  session(cookie) {
    if (!cookie || !/^[\w-]{32,100}$/.test(cookie)) return null;
    return (
      this.db
        .prepare('SELECT * FROM sessions WHERE id=? AND expires>?')
        .get(hash(cookie), this.clock()) || null
    );
  }
  createSession() {
    this.db.prepare('DELETE FROM sessions WHERE expires<=?').run(this.clock());
    const cookie = randomBytes(32).toString('base64url');
    const session = { id: hash(cookie), csrf: randomBytes(32).toString('base64url') };
    this.db
      .prepare('INSERT INTO sessions VALUES (?,?,?)')
      .run(session.id, session.csrf, this.clock() + RETENTION);
    return { cookie, session };
  }
  serialize(row) {
    return row
      ? {
          id: row.id,
          version: row.version,
          created: row.created,
          updated: row.updated,
          data: JSON.parse(row.body),
        }
      : null;
  }
  get(owner, module, recordId) {
    const row = this.db
      .prepare('SELECT * FROM records WHERE owner=? AND module=? AND id=?')
      .get(owner, module, recordId);
    if (!row) throw new HttpError(404, 'Không tìm thấy dữ liệu trong workspace này.');
    return this.serialize(row);
  }
  list(owner, module) {
    return this.db
      .prepare('SELECT * FROM records WHERE owner=? AND module=? ORDER BY updated DESC,id')
      .all(owner, module)
      .map((row) => this.serialize(row));
  }
  transaction(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const result = fn();
      this.db.exec('COMMIT');
      return result;
    } catch (err) {
      this.db.exec('ROLLBACK');
      throw err;
    }
  }
  audit(owner, module, entity, action) {
    this.db
      .prepare('INSERT INTO audit(owner,module,entity,action,at) VALUES (?,?,?,?,?)')
      .run(owner, module, entity, action, this.clock());
  }
  create(owner, module, data) {
    return this.transaction(() => {
      const count = this.db
        .prepare('SELECT count(*) n FROM records WHERE owner=? AND module=?')
        .get(owner, module).n;
      requireThat(count < 50, 'Mỗi ứng dụng tối đa 50 bản ghi/workspace.', 409);
      const recordId = randomUUID();
      const json = JSON.stringify(data);
      const now = this.clock();
      this.db
        .prepare('INSERT INTO records VALUES (?,?,?,?,1,?,?)')
        .run(recordId, owner, module, json, now, now);
      this.db.prepare('INSERT INTO revisions VALUES (?,1,?,?)').run(recordId, json, now);
      this.audit(owner, module, recordId, 'created');
      return this.get(owner, module, recordId);
    });
  }
  update(owner, module, recordId, expected, data, action = 'updated') {
    return this.transaction(() => {
      const existing = this.get(owner, module, recordId);
      requireThat(
        existing.version === expected,
        'Dữ liệu đã thay đổi ở tab khác. Hãy tải lại bản mới trước khi lưu.',
        409,
      );
      const json = JSON.stringify(data);
      this.db
        .prepare(
          'UPDATE records SET body=?,version=version+1,updated=? WHERE id=? AND owner=? AND version=?',
        )
        .run(json, this.clock(), recordId, owner, expected);
      this.db
        .prepare('INSERT INTO revisions VALUES (?,?,?,?)')
        .run(recordId, expected + 1, json, this.clock());
      // Keep the last 20 revisions per item.
      this.db
        .prepare('DELETE FROM revisions WHERE record=? AND version<=?')
        .run(recordId, expected + 1 - 20);
      this.audit(owner, module, recordId, action);
      return this.get(owner, module, recordId);
    });
  }
  remove(owner, module, recordId, expected) {
    this.transaction(() => {
      const existing = this.get(owner, module, recordId);
      requireThat(
        existing.version === expected,
        'Bản ghi đã thay đổi. Hãy tải lại trước khi xóa.',
        409,
      );
      this.db
        .prepare('DELETE FROM records WHERE owner=? AND module=? AND id=?')
        .run(owner, module, recordId);
      this.audit(owner, module, recordId, 'deleted');
    });
  }
  history(owner, module, recordId) {
    this.get(owner, module, recordId);
    return this.db
      .prepare('SELECT version,body,created FROM revisions WHERE record=? ORDER BY version DESC')
      .all(recordId)
      .map((r) => ({ version: r.version, data: JSON.parse(r.body), at: r.created }));
  }
  activity(owner) {
    return this.db
      .prepare('SELECT module,entity,action,at FROM audit WHERE owner=? ORDER BY id DESC LIMIT 30')
      .all(owner);
  }
  close() {
    this.db.close();
  }
}
