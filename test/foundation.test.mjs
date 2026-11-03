import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac, randomUUID } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { sign, verify } from '../lib/signature.mjs';
import { jsonBody } from '../lib/validation.mjs';
import { Store } from '../lib/store.mjs';
const NOW = 1800000000000,
  raw = Buffer.from('{"type":"order.created","id":"001"}');
function headers(secret, body = raw, id = 'delivery-001', seconds = 0) {
  const timestamp = String(NOW / 1000 + seconds);
  return {
    'content-type': 'application/json',
    'x-yang-timestamp': timestamp,
    'x-yang-delivery': id,
    'x-yang-signature': sign(secret, timestamp, id, body),
  };
}
function setup(t) {
  let now = NOW;
  const store = new Store(':memory:', () => now);
  t.after(() => store.close());
  const session = store.newSession(),
    owner = session.session.id,
    created = store.create(owner, { name: 'Orders' });
  return { store, owner, session, ...created, setClock: (value) => (now = value) };
}
test('HMAC framing agrees with independent Node crypto, including exact raw whitespace', () => {
  const signature = sign('local-test-key', '1800000000', 'delivery-001', raw);
  const expected =
    'sha256=' +
    createHmac('sha256', 'local-test-key')
      .update(Buffer.concat([Buffer.from('1800000000.delivery-001.'), raw]))
      .digest('hex');
  assert.equal(signature, expected);
  assert.notEqual(
    signature,
    sign(
      'local-test-key',
      '1800000000',
      'delivery-001',
      Buffer.from('{"type": "order.created","id":"001"}'),
    ),
  );
});
test('signature authenticates body, timestamp and delivery ID independently', () => {
  const h = headers('test-secret');
  assert.equal(verify('test-secret', h, raw, NOW).delivery, 'delivery-001');
  for (const changed of [
    { ...h, 'x-yang-delivery': 'delivery-002' },
    { ...h, 'x-yang-timestamp': '1800000001' },
    { ...h, 'x-yang-signature': 'sha256=' + 'a'.repeat(64) },
  ])
    assert.throws(
      () => verify('test-secret', changed, raw, NOW),
      (e) => e.status === 401,
    );
  assert.throws(
    () => verify('test-secret', h, Buffer.from('{}'), NOW),
    (e) => e.status === 401,
  );
});
test('timestamp window includes exactly +/-300s and rejects stale, future and malformed values', () => {
  for (const seconds of [-300, 300])
    assert.doesNotThrow(() =>
      verify('key', headers('key', raw, 'delivery-001', seconds), raw, NOW),
    );
  for (const seconds of [-301, 301])
    assert.throws(
      () => verify('key', headers('key', raw, 'delivery-001', seconds), raw, NOW),
      (e) => e.status === 401,
    );
  for (const patch of [
    { 'x-yang-signature': 'x' },
    { 'x-yang-signature': 'sha256=' + 'F'.repeat(64) },
    { 'x-yang-delivery': 'contains.dot' },
    { 'x-yang-timestamp': ' 1800000000' },
    { 'x-yang-timestamp': ['1800000000'] },
  ])
    assert.throws(() => verify('key', { ...headers('key'), ...patch }, raw, NOW));
});
test('JSON limits reject scalars, invalid UTF-8, depth, count, keys and oversized strings', () => {
  for (const body of [
    'null',
    '1',
    '"hello"',
    '{',
    '{"x":' + JSON.stringify('x'.repeat(10001)) + '}',
    JSON.stringify({ ['x'.repeat(201)]: 1 }),
    JSON.stringify(Array(1001).fill(1)),
  ])
    assert.throws(() => jsonBody(Buffer.from(body)));
  assert.throws(() => jsonBody(Buffer.from([0xc0, 0xaf])));
  let deep = {};
  for (let i = 0; i < 12; i++) deep = { x: deep };
  assert.throws(() => jsonBody(Buffer.from(JSON.stringify(deep))), /10 mức/);
  assert.throws(
    () =>
      jsonBody(Buffer.from(JSON.stringify(Array.from({ length: 6 }, () => Array(1000).fill(1))))),
    /5.000/,
  );
  assert.equal(jsonBody(Buffer.from('{"__proto__":{"polluted":true}}')).__proto__.polluted, true);
  assert.equal({}.polluted, undefined);
});
test('accepted receipts preserve raw bytes and redact credentials from stored headers', (t) => {
  const { store, owner, endpoint, secret } = setup(t),
    h = {
      ...headers(secret),
      authorization: 'Bearer private-test',
      cookie: 'secret-cookie',
      'x-api-key': 'private-key',
    };
  const result = store.accept(endpoint.id, h, raw);
  assert.equal(result.replayed, false);
  const event = store.event(owner, result.requestId);
  assert.equal(event.raw, raw.toString());
  assert.equal(event.eventType, 'order.created');
  assert.equal(event.headers.authorization, undefined);
  assert.equal(event.headers.cookie, undefined);
  assert.equal(event.headers['x-api-key'], undefined);
  assert.ok(!JSON.stringify(event).includes(secret));
  assert.equal(event.checksum.length, 64);
});
test('duplicate delivery is idempotent; a signed body conflict does not mutate the original', (t) => {
  const { store, owner, endpoint, secret } = setup(t);
  const first = store.accept(endpoint.id, headers(secret), raw);
  assert.equal(store.accept(endpoint.id, headers(secret), raw).requestId, first.requestId);
  const body = Buffer.from('{"type":"changed"}');
  assert.throws(
    () => store.accept(endpoint.id, headers(secret, body), body),
    (e) => e.status === 409,
  );
  assert.equal(store.event(owner, first.requestId).duplicates, 1);
  assert.equal(store.events(owner, endpoint.id).length, 1);
});
test('invalid signatures and JSON never create records or consume delivery IDs', (t) => {
  const { store, owner, endpoint, secret } = setup(t);
  assert.throws(
    () => store.accept(endpoint.id, headers('wrong-key'), raw),
    (e) => e.status === 401,
  );
  const invalid = Buffer.from('{');
  assert.throws(
    () => store.accept(endpoint.id, headers(secret, invalid), invalid),
    (e) => e.status === 400,
  );
  assert.equal(store.events(owner, endpoint.id).length, 0);
  assert.equal(store.accept(endpoint.id, headers(secret), raw).replayed, false);
});
test('read APIs never expose signing keys; key rotation preserves history and rejects old signatures', (t) => {
  const { store, owner, endpoint, secret } = setup(t),
    first = store.accept(endpoint.id, headers(secret), raw);
  for (const value of [
    store.list(owner),
    store.get(owner, endpoint.id),
    store.export(owner, endpoint.id),
  ])
    assert.ok(!JSON.stringify(value).includes(secret));
  const rotated = store.rotate(owner, endpoint.id, 1);
  assert.equal(rotated.endpoint.version, 2);
  assert.notEqual(rotated.secret, secret);
  assert.throws(
    () => store.accept(endpoint.id, headers(secret), raw),
    (e) => e.status === 401,
  );
  assert.equal(store.accept(endpoint.id, headers(rotated.secret), raw).requestId, first.requestId);
  assert.throws(
    () => store.rotate(owner, endpoint.id, 1),
    (e) => e.status === 409,
  );
});
test('paused endpoints reject signed requests; configuration version ignores new deliveries', (t) => {
  const { store, owner, endpoint, secret } = setup(t);
  store.accept(endpoint.id, headers(secret), raw);
  assert.equal(store.get(owner, endpoint.id).version, 1);
  const paused = store.update(owner, endpoint.id, { enabled: false, version: 1 });
  assert.equal(paused.version, 2);
  assert.throws(
    () => store.accept(endpoint.id, headers(secret, raw, 'delivery-002'), raw),
    (e) => e.status === 409,
  );
  assert.throws(
    () => store.update(owner, endpoint.id, { name: 'Stale', version: 1 }),
    (e) => e.status === 409,
  );
  assert.throws(
    () => store.update(owner, endpoint.id, { enabled: 'true', version: 2 }),
    (e) => e.status === 400,
  );
  store.update(owner, endpoint.id, { enabled: true, version: 2 });
  assert.equal(
    store.accept(endpoint.id, headers(secret, raw, 'delivery-002'), raw).replayed,
    false,
  );
});
test('workspace ownership protects endpoint settings, keys, events and exports', (t) => {
  const { store, owner, endpoint, secret } = setup(t),
    other = store.newSession().session.id,
    event = store.accept(endpoint.id, headers(secret), raw);
  for (const action of [
    () => store.get(other, endpoint.id),
    () => store.rawEndpoint(other, endpoint.id),
    () => store.events(other, endpoint.id),
    () => store.export(other, endpoint.id),
    () => store.event(other, event.requestId),
    () => store.update(other, endpoint.id, { version: 1, name: 'stolen' }),
    () => store.rotate(other, endpoint.id, 1),
    () => store.remove(other, endpoint.id, 1),
  ])
    assert.throws(action, (e) => e.status === 404);
  assert.equal(store.list(other).length, 0);
  assert.equal(store.list(owner).length, 1);
});
test('quotas bound endpoint and event storage while duplicate receipts remain usable', (t) => {
  const { store, owner, endpoint, secret } = setup(t);
  for (let i = 0; i < 4; i++) store.create(owner, { name: 'Endpoint ' + i });
  assert.throws(
    () => store.create(owner, { name: 'Six' }),
    (e) => e.status === 409,
  );
  for (let i = 0; i < 100; i++)
    store.accept(endpoint.id, headers(secret, raw, 'delivery-' + String(i).padStart(3, '0')), raw);
  assert.throws(
    () => store.accept(endpoint.id, headers(secret, raw, 'delivery-overflow'), raw),
    (e) => e.status === 409,
  );
  assert.equal(store.accept(endpoint.id, headers(secret, raw, 'delivery-000'), raw).replayed, true);
  assert.equal(store.events(owner, endpoint.id).length, 100);
  assert.throws(
    () => store.accept(endpoint.id, headers(secret), Buffer.alloc(65537)),
    (e) => e.status === 413,
  );
});
test('search, CAS deletion and expiry remove only the relevant endpoint and its events', (t) => {
  const { store, owner, endpoint, secret, setClock } = setup(t);
  store.accept(endpoint.id, headers(secret), raw);
  assert.equal(store.events(owner, endpoint.id, 'order.created').length, 1);
  assert.equal(store.events(owner, endpoint.id, 'missing').length, 0);
  assert.throws(
    () => store.remove(owner, endpoint.id, 2),
    (e) => e.status === 409,
  );
  store.remove(owner, endpoint.id, 1);
  assert.equal(store.db.prepare('SELECT COUNT(*) n FROM events').get().n, 0);
  const next = store.create(owner, { name: 'Expires' });
  store.accept(next.endpoint.id, headers(next.secret), raw);
  setClock(NOW + 7 * 86400000);
  store.cleanup();
  assert.equal(store.db.prepare('SELECT COUNT(*) n FROM endpoints').get().n, 0);
  assert.equal(store.db.prepare('SELECT COUNT(*) n FROM events').get().n, 0);
  assert.throws(
    () => store.accept(next.endpoint.id, headers(next.secret), raw),
    (e) => e.status === 404,
  );
});
test('restart preserves session, key and idempotency without exposing the stored key via reads', () => {
  const dir = mkdtempSync(join(tmpdir(), 'yanghooks-')),
    path = join(dir, 'app.sqlite');
  let store;
  try {
    store = new Store(path, () => NOW);
    const session = store.newSession(),
      created = store.create(session.session.id, { name: 'Persistent' }),
      receipt = store.accept(created.endpoint.id, headers(created.secret), raw);
    store.close();
    store = new Store(path, () => NOW);
    assert.equal(store.session(session.cookie).id, session.session.id);
    assert.equal(
      store.accept(created.endpoint.id, headers(created.secret), raw).requestId,
      receipt.requestId,
    );
    assert.ok(
      !JSON.stringify(store.get(session.session.id, created.endpoint.id)).includes(created.secret),
    );
  } finally {
    store?.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
