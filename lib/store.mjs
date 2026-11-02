import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { randomBytes, randomUUID } from 'node:crypto';
import {
  ACTIVE,
  STATES,
  check,
  digest,
  key,
  sha,
  submission,
  version,
  normalize,
} from './validation.mjs';
export const SESSION_TTL = 7 * 86400000,
  LEASE_MS = 10000;
export const backoff = (attempt) => Math.min(60000, 1000 * 2 ** Math.max(0, attempt - 1));
export class Store {
  constructor(path, clock = () => Date.now()) {
    this.clock = clock;
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=3000;');
    this.db.exec(
      [
        'CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY,csrf TEXT NOT NULL,expires INTEGER NOT NULL,paused INTEGER NOT NULL DEFAULT 0);',
        "CREATE TABLE IF NOT EXISTS jobs(seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,title TEXT NOT NULL,type TEXT NOT NULL,input TEXT NOT NULL,options TEXT NOT NULL CHECK(json_valid(options)),state TEXT NOT NULL CHECK(state IN ('queued','running','retry_wait','succeeded','failed','cancelled')),attempts INTEGER NOT NULL DEFAULT 0,max_attempts INTEGER NOT NULL DEFAULT 3,next_run INTEGER NOT NULL,lease_until INTEGER,token TEXT,version INTEGER NOT NULL DEFAULT 1,created INTEGER NOT NULL,updated INTEGER NOT NULL,started INTEGER,finished INTEGER,result TEXT CHECK(result IS NULL OR json_valid(result)),error_code TEXT,error_message TEXT,parent_id TEXT REFERENCES jobs(id) ON DELETE SET NULL);",
        'CREATE INDEX IF NOT EXISTS jobs_queue ON jobs(state,next_run,created);',
        'CREATE INDEX IF NOT EXISTS jobs_owner ON jobs(owner,created);',
        'CREATE TABLE IF NOT EXISTS idempotency(owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,idem_key TEXT NOT NULL,digest TEXT NOT NULL,job_id TEXT REFERENCES jobs(id) ON DELETE SET NULL,PRIMARY KEY(owner,idem_key));',
        'CREATE TABLE IF NOT EXISTS events(seq INTEGER PRIMARY KEY AUTOINCREMENT,job_id TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,at INTEGER NOT NULL,kind TEXT NOT NULL,message TEXT NOT NULL);',
      ].join('\n'),
    );
  }
  transaction(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const value = fn();
      this.db.exec('COMMIT');
      return value;
    } catch (err) {
      this.db.exec('ROLLBACK');
      throw err;
    }
  }
  session(cookie) {
    if (!cookie || !/^[\w-]{32,100}$/.test(cookie)) return null;
    return (
      this.db
        .prepare('SELECT * FROM sessions WHERE id=? AND expires>?')
        .get(sha(cookie), this.clock()) || null
    );
  }
  newSession() {
    this.db.prepare('DELETE FROM sessions WHERE expires<=?').run(this.clock());
    const cookie = randomBytes(32).toString('base64url'),
      csrf = randomBytes(32).toString('base64url'),
      id = sha(cookie);
    this.db
      .prepare('INSERT INTO sessions VALUES (?,?,?,0)')
      .run(id, csrf, this.clock() + SESSION_TTL);
    return { cookie, session: { id, csrf, paused: 0 } };
  }
  pause(owner, value) {
    this.db.prepare('UPDATE sessions SET paused=? WHERE id=?').run(Number(value), owner);
  }
  serialize(row, full = true) {
    const result = row.result ? JSON.parse(row.result) : null;
    if (result && !full) {
      delete result.content;
      if (result.report) delete result.report.preview;
    }
    return {
      id: row.id,
      title: row.title,
      type: row.type,
      status: row.state,
      attempts: row.attempts,
      maxAttempts: row.max_attempts,
      version: row.version,
      nextRun: row.next_run,
      created: row.created,
      updated: row.updated,
      started: row.started,
      finished: row.finished,
      durationMs: row.finished !== null && row.started !== null ? row.finished - row.started : null,
      inputBytes: Buffer.byteLength(row.input),
      parentId: row.parent_id,
      result,
      error: row.error_code ? { code: row.error_code, message: row.error_message } : null,
      ...(full ? { input: row.input, options: JSON.parse(row.options) } : {}),
    };
  }
  get(owner, id, full = true) {
    const row = this.db.prepare('SELECT * FROM jobs WHERE owner=? AND id=?').get(owner, id);
    check(row, 'Không tìm thấy tác vụ trong workspace này.', 404);
    return this.serialize(row, full);
  }
  list(owner, filters = {}) {
    const state = filters.state || '',
      q = (filters.q || '').trim();
    check(!state || STATES.includes(state), 'Trạng thái lọc không hợp lệ.');
    check(q.length <= 120, 'Từ khóa tối đa 120 ký tự.');
    return this.db
      .prepare('SELECT * FROM jobs WHERE owner=? ORDER BY created DESC,seq DESC')
      .all(owner)
      .filter(
        (row) =>
          (!state || row.state === state) &&
          (!q || normalize(row.title + ' ' + row.type + ' ' + row.id).includes(normalize(q))),
      )
      .map((row) => this.serialize(row, false));
  }
  stats(owner) {
    const counts = Object.fromEntries(STATES.map((state) => [state, 0]));
    for (const row of this.db
      .prepare('SELECT state,count(*) n FROM jobs WHERE owner=? GROUP BY state')
      .all(owner))
      counts[row.state] = row.n;
    return {
      ...counts,
      total: Object.values(counts).reduce((a, b) => a + b, 0),
      active: ACTIVE.reduce((n, state) => n + counts[state], 0),
    };
  }
  event(id, kind, message) {
    this.db
      .prepare('INSERT INTO events(job_id,at,kind,message) VALUES (?,?,?,?)')
      .run(id, this.clock(), kind, message.slice(0, 220));
  }
  events(owner, id) {
    this.get(owner, id, false);
    return this.db
      .prepare('SELECT at,kind,message FROM events WHERE job_id=? ORDER BY seq')
      .all(id);
  }
  create(owner, raw, idemKey, { parentId = null, maxAttempts = 3 } = {}) {
    const data = submission(raw),
      idem = key(idemKey),
      fingerprint = digest(data, parentId);
    check(
      Number.isInteger(maxAttempts) && maxAttempts >= 1 && maxAttempts <= 3,
      'Số lần thử không hợp lệ.',
    );
    return this.transaction(() => {
      const previous = this.db
        .prepare('SELECT * FROM idempotency WHERE owner=? AND idem_key=?')
        .get(owner, idem);
      if (previous) {
        check(
          previous.digest === fingerprint,
          'Idempotency-Key đã được dùng cho dữ liệu khác.',
          409,
        );
        check(previous.job_id, 'Tác vụ trước đã bị xóa. Hãy dùng mã chống trùng mới.', 410);
        return { job: this.get(owner, previous.job_id), replayed: true };
      }
      check(
        this.db
          .prepare('SELECT id FROM sessions WHERE id=? AND expires>?')
          .get(owner, this.clock()),
        'Workspace đã hết hạn.',
        401,
      );
      const stats = this.stats(owner);
      check(
        stats.total < 50,
        'Workspace tối đa 50 tác vụ. Xóa tác vụ đã kết thúc để thêm mới.',
        409,
      );
      check(stats.active < 10, 'Mỗi workspace tối đa 10 tác vụ chưa kết thúc.', 409);
      if (parentId) this.get(owner, parentId);
      const id = randomUUID(),
        now = this.clock();
      this.db
        .prepare(
          "INSERT INTO jobs(id,owner,title,type,input,options,state,max_attempts,next_run,created,updated,parent_id) VALUES (?,?,?,?,?,?,'queued',?,?,?,?,?)",
        )
        .run(
          id,
          owner,
          data.title,
          data.type,
          data.input,
          JSON.stringify(data.options),
          maxAttempts,
          now,
          now,
          now,
          parentId,
        );
      this.db.prepare('INSERT INTO idempotency VALUES (?,?,?,?)').run(owner, idem, fingerprint, id);
      this.event(
        id,
        'queued',
        parentId ? 'Tạo lượt chạy mới từ tác vụ trước.' : 'Tác vụ đã vào hàng đợi.',
      );
      return { job: this.get(owner, id), replayed: false };
    });
  }
  claim() {
    return this.transaction(() => {
      const now = this.clock();
      const row = this.db
        .prepare(
          "SELECT j.* FROM jobs j JOIN sessions s ON s.id=j.owner WHERE j.state IN ('queued','retry_wait') AND j.next_run<=? AND s.expires>? AND s.paused=0 AND NOT EXISTS(SELECT 1 FROM jobs busy WHERE busy.owner=j.owner AND busy.state='running') ORDER BY j.created,j.seq LIMIT 1",
        )
        .get(now, now);
      if (!row) return null;
      const token = randomUUID();
      this.db
        .prepare(
          "UPDATE jobs SET state='running',attempts=attempts+1,token=?,lease_until=?,version=version+1,updated=?,started=COALESCE(started,?),error_code=NULL,error_message=NULL WHERE id=?",
        )
        .run(token, now + LEASE_MS, now, now, row.id);
      this.event(
        row.id,
        'started',
        'Worker nhận lượt thử ' + (row.attempts + 1) + '/' + row.max_attempts + '.',
      );
      return {
        id: row.id,
        owner: row.owner,
        token,
        type: row.type,
        input: row.input,
        options: JSON.parse(row.options),
      };
    });
  }
  ownedClaim(id, token, expired = false) {
    const row = this.db
      .prepare("SELECT * FROM jobs WHERE id=? AND token=? AND state='running'")
      .get(id, token);
    return row && (expired ? row.lease_until <= this.clock() : row.lease_until > this.clock())
      ? row
      : null;
  }
  heartbeat(id, token) {
    if (!this.ownedClaim(id, token)) return false;
    return (
      this.db
        .prepare("UPDATE jobs SET lease_until=? WHERE id=? AND token=? AND state='running'")
        .run(this.clock() + LEASE_MS, id, token).changes === 1
    );
  }
  phase(id, token, label) {
    if (!this.ownedClaim(id, token)) return false;
    this.event(id, 'stage', String(label));
    return true;
  }
  complete(id, token, result) {
    return this.transaction(() => {
      if (!this.ownedClaim(id, token)) return false;
      check(
        result &&
          typeof result.content === 'string' &&
          Buffer.byteLength(result.content) <= 1024 * 1024,
        'Đầu ra không hợp lệ hoặc vượt 1 MB.',
      );
      check(
        /^[a-z0-9.-]{1,80}$/.test(result.filename) &&
          ['text/csv', 'text/markdown'].includes(result.mime),
        'Định dạng đầu ra không hợp lệ.',
      );
      check(
        result.sha256 === sha(result.content) && result.bytes === Buffer.byteLength(result.content),
        'Checksum đầu ra không khớp.',
      );
      this.db
        .prepare(
          "UPDATE jobs SET state='succeeded',result=?,finished=?,updated=?,token=NULL,lease_until=NULL,version=version+1 WHERE id=?",
        )
        .run(JSON.stringify(result), this.clock(), this.clock(), id);
      this.event(id, 'succeeded', 'Đã tạo tệp kết quả và mã SHA-256.');
      return true;
    });
  }
  fail(id, token, error, expired = false) {
    return this.transaction(() => {
      const row = this.ownedClaim(id, token, expired);
      if (!row) return false;
      const retry = error.retryable === true && row.attempts < row.max_attempts;
      this.db
        .prepare(
          'UPDATE jobs SET state=?,next_run=?,finished=?,updated=?,token=NULL,lease_until=NULL,error_code=?,error_message=?,version=version+1 WHERE id=?',
        )
        .run(
          retry ? 'retry_wait' : 'failed',
          retry ? this.clock() + backoff(row.attempts) : this.clock(),
          retry ? null : this.clock(),
          this.clock(),
          String(error.code).slice(0, 50),
          String(error.message).slice(0, 220),
          id,
        );
      this.event(
        id,
        retry ? 'retry_wait' : 'failed',
        retry
          ? 'Lỗi tạm thời. Chờ ' + backoff(row.attempts) + ' ms trước lượt tiếp theo.'
          : 'Tác vụ dừng: ' + String(error.message),
      );
      return true;
    });
  }
  recoverExpired() {
    let recovered = 0;
    for (const row of this.db
      .prepare("SELECT id,token FROM jobs WHERE state='running' AND lease_until<=?")
      .all(this.clock()))
      if (
        this.fail(
          row.id,
          row.token,
          {
            code: 'WORKER_LOST',
            message: 'Worker bị gián đoạn trước khi hoàn tất.',
            retryable: true,
          },
          true,
        )
      )
        recovered++;
    return recovered;
  }
  cancel(owner, id, expectedVersion) {
    return this.transaction(() => {
      const job = this.get(owner, id);
      check(
        job.version === version(expectedVersion),
        'Tác vụ đã đổi trạng thái. Hãy tải lại trước khi hủy.',
        409,
      );
      check(ACTIVE.includes(job.status), 'Chỉ hủy được tác vụ chưa kết thúc.', 409);
      this.db
        .prepare(
          "UPDATE jobs SET state='cancelled',token=NULL,lease_until=NULL,finished=?,updated=?,version=version+1 WHERE owner=? AND id=?",
        )
        .run(this.clock(), this.clock(), owner, id);
      this.event(id, 'cancelled', 'Người dùng đã hủy tác vụ.');
      return this.get(owner, id);
    });
  }
  retry(owner, id, expectedVersion, idemKey) {
    const previous = this.get(owner, id);
    check(previous.version === version(expectedVersion), 'Tác vụ đã thay đổi. Hãy tải lại.', 409);
    check(
      ['failed', 'cancelled'].includes(previous.status),
      'Chỉ tạo lượt chạy lại từ tác vụ lỗi hoặc đã hủy.',
      409,
    );
    return this.create(owner, previous, idemKey, { parentId: id });
  }
  remove(owner, id, expectedVersion) {
    this.transaction(() => {
      const job = this.get(owner, id);
      check(job.version === version(expectedVersion), 'Tác vụ đã thay đổi. Hãy tải lại.', 409);
      check(!ACTIVE.includes(job.status), 'Hủy hoặc chờ tác vụ kết thúc trước khi xóa.', 409);
      this.db.prepare('DELETE FROM jobs WHERE owner=? AND id=?').run(owner, id);
    });
  }
  close() {
    this.db.close();
  }
}
