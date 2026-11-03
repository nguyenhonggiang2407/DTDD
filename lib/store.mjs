import { DatabaseSync } from 'node:sqlite';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { check, object, text, version, sha, jsonBody } from './validation.mjs';
import { verify, safeHeaders } from './signature.mjs';
export class Store {
  constructor(path = ':memory:', clock = () => Date.now()) {
    this.clock = clock;
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA foreign_keys=ON;PRAGMA journal_mode=WAL;PRAGMA busy_timeout=5000;');
    this.db.exec(
      'CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY,token_hash TEXT UNIQUE NOT NULL,csrf TEXT NOT NULL,expires INTEGER NOT NULL);' +
        'CREATE TABLE IF NOT EXISTS endpoints(id TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,name TEXT NOT NULL,secret TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1,version INTEGER NOT NULL DEFAULT 1,created INTEGER NOT NULL,updated INTEGER NOT NULL);' +
        'CREATE INDEX IF NOT EXISTS endpoint_owner ON endpoints(owner);' +
        'CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY,endpoint_id TEXT NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,delivery TEXT NOT NULL,digest TEXT NOT NULL,raw TEXT NOT NULL,headers_json TEXT NOT NULL,event_type TEXT NOT NULL,received INTEGER NOT NULL,last_seen INTEGER NOT NULL,duplicates INTEGER NOT NULL DEFAULT 0,UNIQUE(endpoint_id,delivery));' +
        'CREATE INDEX IF NOT EXISTS event_endpoint ON events(endpoint_id,received);',
    );
    this.cleanup();
  }
  tx(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const result = fn();
      this.db.exec('COMMIT');
      return result;
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }
  cleanup() {
    return this.db.prepare('DELETE FROM sessions WHERE expires<=?').run(this.clock()).changes;
  }
  newSession() {
    this.cleanup();
    const id = randomUUID(),
      cookie = randomBytes(32).toString('hex'),
      csrf = randomBytes(24).toString('hex'),
      expires = this.clock() + 7 * 86400000;
    this.db.prepare('INSERT INTO sessions VALUES(?,?,?,?)').run(id, sha(cookie), csrf, expires);
    return { cookie, session: { id, csrf, expires } };
  }
  session(cookie) {
    if (typeof cookie !== 'string' || !/^[a-f0-9]{64}$/.test(cookie)) return null;
    const s = this.db
      .prepare('SELECT id,csrf,expires FROM sessions WHERE token_hash=?')
      .get(sha(cookie));
    if (!s) return null;
    if (s.expires <= this.clock()) {
      this.cleanup();
      return null;
    }
    return s;
  }
  owner(id) {
    check(
      Boolean(
        this.db.prepare('SELECT 1 FROM sessions WHERE id=? AND expires>?').get(id, this.clock()),
      ),
      'Workspace hết hạn.',
      401,
    );
  }
  rawEndpoint(owner, id) {
    this.owner(owner);
    const row = this.db.prepare('SELECT * FROM endpoints WHERE owner=? AND id=?').get(owner, id);
    check(row, 'Không tìm thấy endpoint.', 404);
    return row;
  }
  endpointView(row) {
    const stats = this.db
      .prepare(
        'SELECT COUNT(*) AS total,COALESCE(SUM(duplicates),0) AS duplicates,MAX(received) AS latest FROM events WHERE endpoint_id=?',
      )
      .get(row.id);
    return {
      id: row.id,
      name: row.name,
      enabled: Boolean(row.enabled),
      version: row.version,
      created: row.created,
      updated: row.updated,
      path: '/hooks/' + row.id,
      ...stats,
    };
  }
  list(owner) {
    this.owner(owner);
    return this.db
      .prepare('SELECT * FROM endpoints WHERE owner=? ORDER BY created DESC,id')
      .all(owner)
      .map((row) => this.endpointView(row));
  }
  get(owner, id) {
    return this.endpointView(this.rawEndpoint(owner, id));
  }
  create(owner, payload) {
    this.owner(owner);
    object(payload);
    const name = text(payload.name, 'Tên endpoint');
    return this.tx(() => {
      check(
        this.db.prepare('SELECT COUNT(*) AS n FROM endpoints WHERE owner=?').get(owner).n < 5,
        'Mỗi workspace tối đa 5 endpoint.',
        409,
      );
      const id = randomUUID(),
        secret = randomBytes(32).toString('hex'),
        now = this.clock();
      this.db
        .prepare('INSERT INTO endpoints(id,owner,name,secret,created,updated) VALUES(?,?,?,?,?,?)')
        .run(id, owner, name, secret, now, now);
      return { endpoint: this.get(owner, id), secret };
    });
  }
  update(owner, id, payload) {
    object(payload);
    const row = this.rawEndpoint(owner, id),
      expected = version(payload.version);
    check(row.version === expected, 'Cấu hình đã thay đổi. Hãy tải lại.', 409);
    const name = payload.name === undefined ? row.name : text(payload.name, 'Tên endpoint'),
      enabled = payload.enabled === undefined ? Boolean(row.enabled) : payload.enabled;
    check(typeof enabled === 'boolean', 'Trạng thái nhận cần boolean.');
    const result = this.db
      .prepare(
        'UPDATE endpoints SET name=?,enabled=?,version=version+1,updated=? WHERE owner=? AND id=? AND version=?',
      )
      .run(name, Number(enabled), this.clock(), owner, id, expected);
    check(result.changes === 1, 'Cấu hình đã thay đổi. Hãy tải lại.', 409);
    return this.get(owner, id);
  }
  rotate(owner, id, expected) {
    this.rawEndpoint(owner, id);
    version(expected);
    const secret = randomBytes(32).toString('hex');
    const result = this.db
      .prepare(
        'UPDATE endpoints SET secret=?,version=version+1,updated=? WHERE owner=? AND id=? AND version=?',
      )
      .run(secret, this.clock(), owner, id, expected);
    check(result.changes === 1, 'Cấu hình đã thay đổi. Hãy tải lại.', 409);
    return { endpoint: this.get(owner, id), secret };
  }
  remove(owner, id, expected) {
    this.rawEndpoint(owner, id);
    version(expected);
    const result = this.db
      .prepare('DELETE FROM endpoints WHERE owner=? AND id=? AND version=?')
      .run(owner, id, expected);
    check(result.changes === 1, 'Cấu hình đã thay đổi. Hãy tải lại.', 409);
  }
  eventView(row, full = false) {
    const result = {
      id: row.id,
      endpointId: row.endpoint_id,
      deliveryId: row.delivery,
      eventType: row.event_type,
      checksum: row.digest,
      bytes: Buffer.byteLength(row.raw),
      received: row.received,
      lastSeen: row.last_seen,
      duplicates: row.duplicates,
      preview: row.raw.slice(0, 140),
    };
    if (full) {
      result.raw = row.raw;
      result.headers = JSON.parse(row.headers_json);
    }
    return result;
  }
  events(owner, id, query = '') {
    this.rawEndpoint(owner, id);
    check(typeof query === 'string' && query.length <= 120, 'Tìm kiếm tối đa 120 ký tự.');
    const needle = query.toLowerCase().normalize('NFC');
    return this.db
      .prepare('SELECT * FROM events WHERE endpoint_id=? ORDER BY received DESC,id')
      .all(id)
      .filter((row) =>
        [row.delivery, row.event_type, row.raw].some((value) =>
          value.toLowerCase().normalize('NFC').includes(needle),
        ),
      )
      .map((row) => this.eventView(row));
  }
  event(owner, id) {
    this.owner(owner);
    const row = this.db
      .prepare(
        'SELECT e.* FROM events e JOIN endpoints p ON p.id=e.endpoint_id WHERE p.owner=? AND e.id=?',
      )
      .get(owner, id);
    check(row, 'Không tìm thấy lượt nhận.', 404);
    return this.eventView(row, true);
  }
  export(owner, id) {
    this.rawEndpoint(owner, id);
    return this.db
      .prepare('SELECT * FROM events WHERE endpoint_id=? ORDER BY received,id')
      .all(id)
      .map((row) => this.eventView(row, true));
  }
  accept(id, headers, raw) {
    check(Buffer.isBuffer(raw) && raw.length <= 65536, 'Webhook tối đa 64 KB.', 413);
    return this.tx(() => {
      const endpoint = this.db
        .prepare(
          'SELECT p.* FROM endpoints p JOIN sessions s ON s.id=p.owner WHERE p.id=? AND s.expires>?',
        )
        .get(id, this.clock());
      check(endpoint, 'Không tìm thấy endpoint.', 404);
      const proof = verify(endpoint.secret, headers, raw, this.clock());
      check(endpoint.enabled === 1, 'Endpoint đang tạm dừng.', 409);
      const body = jsonBody(raw),
        digest = sha(raw),
        existing = this.db
          .prepare('SELECT * FROM events WHERE endpoint_id=? AND delivery=?')
          .get(id, proof.delivery);
      if (existing) {
        check(existing.digest === digest, 'Delivery ID đã dùng với body khác.', 409);
        this.db
          .prepare('UPDATE events SET duplicates=duplicates+1,last_seen=? WHERE id=?')
          .run(this.clock(), existing.id);
        return { requestId: existing.id, status: 'accepted', replayed: true };
      }
      check(
        this.db.prepare('SELECT COUNT(*) AS n FROM events WHERE endpoint_id=?').get(id).n < 100,
        'Endpoint đã đạt 100 lượt nhận. Hãy xuất dữ liệu và tạo endpoint mới.',
        409,
      );
      const requestId = randomUUID(),
        now = this.clock(),
        type =
          typeof body.type === 'string'
            ? body.type.slice(0, 100)
            : Array.isArray(body)
              ? 'json.array'
              : 'json.object';
      this.db
        .prepare(
          'INSERT INTO events(id,endpoint_id,delivery,digest,raw,headers_json,event_type,received,last_seen) VALUES(?,?,?,?,?,?,?,?,?)',
        )
        .run(
          requestId,
          id,
          proof.delivery,
          digest,
          raw.toString('utf8'),
          JSON.stringify(safeHeaders(headers)),
          type,
          now,
          now,
        );
      return { requestId, status: 'accepted', replayed: false };
    });
  }
  close() {
    this.db.close();
  }
}
