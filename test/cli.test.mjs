import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { createApp } from '../server.mjs';
const execute = promisify(execFile);
test('CLI signs exact file bytes over local HTTP and preserves idempotency', async () => {
  const app = createApp({ dbPath: ':memory:' });
  const dir = mkdtempSync(join(tmpdir(), 'yanghooks-cli-'));
  try {
    await new Promise((done) => app.server.listen(0, '127.0.0.1', done));
    const owner = app.store.newSession().session.id;
    const created = app.store.create(owner, { name: 'CLI test' });
    const url = 'http://127.0.0.1:' + app.server.address().port + created.endpoint.path;
    const file = join(dir, 'body.json');
    writeFileSync(file, '{\n  "type": "cli.created", "id": "001"\n}\n');
    const args = [resolve('scripts/send.mjs'), url, file, 'cli-delivery-001'];
    const options = { env: { ...process.env, YANGHOOKS_SECRET: created.secret }, timeout: 10000 };
    const first = await execute(process.execPath, args, options);
    assert.match(first.stdout, /^201 /);
    assert.ok(!first.stdout.includes(created.secret));
    assert.match((await execute(process.execPath, args, options)).stdout, /^200 /);
    writeFileSync(file, '{"type":"changed"}');
    await assert.rejects(
      execute(process.execPath, args, options),
      (e) => e.code === 1 && /^409 /.test(e.stdout),
    );
    assert.equal(app.store.events(owner, created.endpoint.id).length, 1);
  } finally {
    await app.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
test('CLI rejects remote destinations before reading a body or sending a signing key', async () => {
  await assert.rejects(
    execute(
      process.execPath,
      [resolve('scripts/send.mjs'), 'https://example.invalid/hooks/123', 'missing.json'],
      { env: { ...process.env, YANGHOOKS_SECRET: 'local-test-key' }, timeout: 3000 },
    ),
    (e) =>
      e.code === 1 &&
      /only sends to localhost/.test(e.stderr) &&
      !e.stderr.includes('local-test-key'),
  );
});
