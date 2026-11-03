import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync } from 'node:fs';
import { chromium, expect } from '@playwright/test';
import { createApp } from '../server.mjs';
import { sign } from '../lib/signature.mjs';
let app, browser, base;
before(async () => {
  mkdirSync('docs/screenshots', { recursive: true });
  app = createApp({ dbPath: ':memory:' });
  await new Promise((done) => app.server.listen(0, '127.0.0.1', done));
  base = 'http://127.0.0.1:' + app.server.address().port;
  browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
});
after(async () => {
  await browser?.close();
  await app?.close();
});
async function workspace(t, viewport = { width: 1440, height: 1050 }) {
  const context = await browser.newContext({ viewport, locale: 'vi-VN' }),
    page = await context.newPage(),
    errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  t.after(async () => {
    await context.close();
    assert.deepEqual(errors, []);
  });
  await page.goto(base);
  await expect(page.locator('#demoBtn')).toBeEnabled();
  return { page, context };
}
async function create(page, name) {
  await page.getByRole('button', { name: '＋ Tạo endpoint', exact: true }).click();
  await page.locator('#createName').fill(name);
  await page.locator('#createSubmit').click();
  await expect(page.locator('#keyDialog')).toBeVisible();
  const secret = await page.locator('#newSecret').inputValue(),
    url = await page.locator('#endpointUrl').inputValue();
  assert.match(secret, /^[a-f0-9]{64}$/);
  await page.getByRole('button', { name: 'Đã lưu khóa', exact: true }).click();
  await expect(page.locator('#newSecret')).toHaveValue('');
  return { secret, url };
}
async function demo(page) {
  await page.locator('#demoBtn').click();
  await expect(page.locator('#keyDialog')).toBeVisible();
  await page.getByRole('button', { name: 'Đã lưu khóa', exact: true }).click();
  await expect(page.locator('.event-row')).toHaveCount(2);
}
async function send(page, status) {
  await page.locator('#sendBtn').click();
  await expect(page.locator('#httpCode')).toHaveText('HTTP ' + status);
  await expect(page.locator('#sendBtn')).toBeEnabled();
}
async function capture(page, name) {
  await expect(page.locator('#toast')).toBeHidden({ timeout: 9000 });
  await page.locator('#main').focus();
  await page.locator('#main').evaluate((el) => el.blur());
  await page.evaluate(() => scrollTo(0, 0));
  await page.screenshot({ path: 'docs/screenshots/' + name + '.png', fullPage: true });
}
async function external(url, secret, raw, id) {
  const timestamp = String(Math.floor(Date.now() / 1000));
  return fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Yang-Timestamp': timestamp,
      'X-Yang-Delivery': id,
      'X-Yang-Signature': sign(secret, timestamp, id, Buffer.from(raw)),
    },
    body: raw,
  });
}
test('real sample inbox, HTTP test sender, receipt detail and exports', async (t) => {
  const { page } = await workspace(t);
  await demo(page);
  await expect(page.locator('#statReceived')).toHaveText('2');
  await page
    .locator('#sendBody')
    .fill(
      JSON.stringify({ type: 'inventory.updated', sku: 'NOTEBOOK-01', available: 24 }, null, 2),
    );
  await send(page, 201);
  await expect(page.locator('.event-row')).toHaveCount(3);
  await capture(page, 'desktop');
  await page.getByRole('button', { name: /inventory.updated/ }).click();
  await expect(page.locator('#eventDialog')).toBeVisible();
  await expect(page.locator('#eventRaw')).toContainText('NOTEBOOK-01');
  await expect(page.locator('#eventHeaders')).not.toContainText('signature');
  await capture(page, 'receipt');
  const downloading = page.waitForEvent('download');
  await page.locator('#downloadEventBtn').click();
  const d = await downloading,
    data = JSON.parse(readFileSync(await d.path(), 'utf8'));
  assert.equal(JSON.parse(data.event.raw).available, 24);
  await page.getByRole('button', { name: 'Đóng lượt nhận', exact: true }).click();
  const exported = page.waitForEvent('download');
  await page.locator('#exportBtn').click();
  const all = JSON.parse(readFileSync(await (await exported).path(), 'utf8'));
  assert.equal(all.events.length, 3);
  assert.equal(all.protocol, 'yanghooks-v1');
});
test('one-time key, external signature, literal HTML and rotation with old-key rejection', async (t) => {
  const { page } = await workspace(t),
    created = await create(page, 'External test');
  const body = JSON.stringify({
    type: 'external.event',
    note: '<img src=x onerror="window.hacked=1">',
  });
  assert.equal(
    (await external(created.url, created.secret, body, 'external-delivery-001')).status,
    201,
  );
  await page.locator('#refreshBtn').click();
  await expect(page.locator('.event-row')).toHaveCount(1);
  await page.getByRole('button', { name: /external.event/ }).click();
  await expect(page.locator('#eventRaw')).toContainText('<img src=x');
  await expect(page.locator('#eventRaw img')).toHaveCount(0);
  assert.equal(await page.evaluate(() => window.hacked), undefined);
  await page.getByRole('button', { name: 'Đóng lượt nhận', exact: true }).click();
  await page.locator('#rotateBtn').click();
  await page.locator('#confirmSubmit').click();
  await expect(page.locator('#keyDialog')).toBeVisible();
  const next = await page.locator('#newSecret').inputValue();
  assert.notEqual(next, created.secret);
  await page.getByRole('button', { name: 'Đã lưu khóa', exact: true }).click();
  assert.equal(
    (await external(created.url, created.secret, body, 'external-delivery-002')).status,
    401,
  );
  assert.equal((await external(created.url, next, body, 'external-delivery-002')).status, 201);
  await page.locator('#refreshBtn').click();
  await expect(page.locator('#endpointMeta')).toContainText('v2');
  await expect(page.locator('.event-row')).toHaveCount(2);
});
test('duplicate, bad/stale signature, conflict, pause, resume and confirmed removal', async (t) => {
  const { page } = await workspace(t);
  await create(page, 'Replay lab');
  await send(page, 201);
  const receipt = JSON.parse(await page.locator('#receiptJson').textContent());
  await send(page, 200);
  assert.equal(
    JSON.parse(await page.locator('#receiptJson').textContent()).requestId,
    receipt.requestId,
  );
  await expect(page.locator('#statDuplicates')).toHaveText('1');
  await page.locator('#sendMode').selectOption('invalid-signature');
  await send(page, 401);
  await page.locator('#sendMode').selectOption('expired');
  await send(page, 401);
  await expect(page.locator('#statReceived')).toHaveText('1');
  await page.locator('#sendMode').selectOption('valid');
  await page.locator('#sendBody').fill('{"type":"changed"}');
  await send(page, 409);
  await expect(page.locator('#receiptJson')).toContainText('body khác');
  await page.locator('#pauseBtn').click();
  await expect(page.locator('#endpointState')).toHaveText('TẠM DỪNG');
  await page.locator('#newDeliveryBtn').click();
  await send(page, 409);
  await expect(page.locator('#receiptJson')).toContainText('tạm dừng');
  await page.locator('#pauseBtn').click();
  await expect(page.locator('#endpointState')).toHaveText('ĐANG NHẬN');
  await send(page, 201);
  await expect(page.locator('#statReceived')).toHaveText('2');
  await page.locator('#renameBtn').click();
  await page.locator('#renameName').fill('Replay verified');
  await page.locator('#renameSubmit').click();
  await expect(page.locator('#endpointName')).toHaveText('Replay verified');
  await page.locator('#deleteBtn').click();
  await page.getByRole('button', { name: 'Giữ lại', exact: true }).click();
  await expect(page.locator('#workbench')).toBeVisible();
  await page.locator('#deleteBtn').click();
  await page.locator('#confirmSubmit').click();
  await expect(page.locator('#empty')).toBeVisible();
  await expect(page.locator('#statReceived')).toHaveText('0');
});
test('mobile keeps endpoint switching, sending and receipt inspection in a bounded page', async (t) => {
  const { page } = await workspace(t, { width: 390, height: 844 });
  await demo(page);
  await expect(page.locator('#endpointSelect')).toBeVisible();
  await send(page, 201);
  const size = await page.evaluate(() => ({
    viewport: innerWidth,
    width: document.documentElement.scrollWidth,
  }));
  assert.ok(size.width <= size.viewport + 1, JSON.stringify(size));
  await capture(page, 'mobile');
  await page
    .getByRole('button', { name: /order.created/ })
    .first()
    .click();
  await expect(page.locator('#eventDialog')).toBeVisible();
  await expect(page.locator('#eventRaw')).toContainText('ORDER-001');
});
