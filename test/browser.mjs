import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { mkdirSync, readFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium, expect } from '@playwright/test';
let processApp,
  browser,
  base,
  logs = '';
before(async () => {
  mkdirSync('docs/screenshots', { recursive: true });
  const allocator = createServer();
  await new Promise((done) => allocator.listen(0, '127.0.0.1', done));
  const port = allocator.address().port;
  await new Promise((done) => allocator.close(done));
  base = 'http://127.0.0.1:' + port;
  processApp = spawn(
    'java',
    [
      '-jar',
      'target/yangslots.jar',
      '--server.port=' + port,
      '--spring.datasource.url=jdbc:h2:mem:browser-test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE',
      '--spring.main.banner-mode=off',
    ],
    { stdio: ['ignore', 'pipe', 'pipe'] },
  );
  for (const stream of [processApp.stdout, processApp.stderr])
    stream.on('data', (chunk) => (logs = (logs + chunk).slice(-18000)));
  let ready = false;
  for (let i = 0; i < 600; i++) {
    if (processApp.exitCode !== null) throw new Error('Java exited: ' + logs);
    try {
      const r = await fetch(base + '/health', { signal: AbortSignal.timeout(1000) });
      if (r.ok) {
        ready = true;
        break;
      }
    } catch {}
    await delay(100);
  }
  if (!ready) throw new Error('Java startup timed out: ' + logs);
  browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
});
after(async () => {
  await browser?.close();
  if (processApp && processApp.exitCode === null) {
    processApp.kill('SIGTERM');
    await Promise.race([new Promise((done) => processApp.once('exit', done)), delay(6000)]);
    if (processApp.exitCode === null) processApp.kill('SIGKILL');
  }
});
async function workspace(t, viewport = { width: 1440, height: 1050 }) {
  const context = await browser.newContext({
      viewport,
      locale: 'vi-VN',
      timezoneId: 'Asia/Ho_Chi_Minh',
    }),
    page = await context.newPage(),
    errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  t.after(async () => {
    await context.close();
    assert.deepEqual(errors, [], 'No uncaught client errors');
  });
  await page.goto(base);
  await expect(page.locator('#demoBtn')).toBeEnabled();
  return { page, context };
}
async function demo(page) {
  await page.locator('#demoBtn').click();
  await expect(page.locator('#resourceName')).toHaveText('Phòng họp Lotus');
  await expect(page.locator('#statConfirmed')).toHaveText('1');
  await expect(page.locator('#slotGrid .slot[data-time="10:00"]')).toBeEnabled();
}
async function choose(page, time = '10:00') {
  await page.locator('#slotGrid .slot[data-time="' + time + '"]').click();
  await expect(page.locator('#holdBtn')).toBeEnabled();
}
async function hold(page, title = 'Trao đổi kế hoạch') {
  await choose(page);
  await page.locator('#holdTitle').fill(title);
  await page.locator('#holdBtn').click();
  await expect(page.locator('#reservationDialog')).toBeVisible();
  await expect(page.locator('#dialogStatus')).toHaveText('Đang giữ chỗ');
  await expect(page.locator('#countdown')).toHaveText(/\d{2}:\d{2}/);
}
async function confirm(page) {
  await page.locator('#confirmBookingBtn').click();
  await expect(page.locator('#dialogStatus')).toHaveText('Đã xác nhận');
  await expect(page.locator('#confirmBookingBtn')).not.toBeVisible();
}
async function close(page) {
  await page.getByRole('button', { name: 'Đóng lượt đặt', exact: true }).click();
  await expect(page.locator('#reservationDialog')).not.toBeVisible();
}
async function capture(page, name, fullPage = true) {
  await expect(page.locator('#toast')).toBeHidden({ timeout: 9000 });
  if (fullPage) {
    await page.locator('#main').focus();
    await page.locator('#main').evaluate((el) => el.blur());
    await page.evaluate(() => scrollTo(0, 0));
  }
  await page.screenshot({ path: 'docs/screenshots/' + name + '.png', fullPage });
}
async function create(page, name = 'Thiết bị dùng thử', capacity = 1) {
  await page.getByRole('button', { name: '＋ Thêm tài nguyên', exact: true }).click();
  await page.locator('#createName').fill(name);
  await page.locator('#createCapacity').fill(String(capacity));
  await page.locator('#createSubmit').click();
  await expect(page.locator('#createDialog')).not.toBeVisible();
  await expect(page.locator('#resourceName')).toHaveText(name);
  await expect(page.locator('#slotGrid .slot[data-time="10:00"]')).toBeEnabled();
}
test('sample calendar → five-minute hold → confirmation → capacity and CSV/ICS export', async (t) => {
  const { page } = await workspace(t);
  await demo(page);
  await hold(page, 'Buổi họp tiếng Việt');
  await capture(page, 'hold', false);
  await confirm(page);
  await close(page);
  await expect(page.locator('#statConfirmed')).toHaveText('2');
  await expect(page.locator('#slotGrid .slot[data-time="10:00"]')).toBeDisabled();
  await expect(page.locator('.booking-row')).toHaveCount(2);
  await capture(page, 'desktop');
  const event = page.waitForEvent('download');
  await page.locator('#icsBtn').click();
  const download = await event;
  assert.equal(download.suggestedFilename(), 'yangslots.ics');
  const calendar = readFileSync(await download.path(), 'utf8');
  assert.equal(calendar.split('BEGIN:VEVENT').length - 1, 2);
  assert.match(calendar.replace(/\r\n /g, ''), /SUMMARY:Buổi họp tiếng Việt/);
  const csvEvent = page.waitForEvent('download');
  await page.locator('#csvBtn').click();
  const csv = readFileSync(await (await csvEvent).path(), 'utf8');
  assert.match(csv, /"CONFIRMED"/);
  assert.match(csv, /"Buổi họp tiếng Việt"/);
});
test('cancellation returns capacity, rename preserves history and deletion is explicit', async (t) => {
  const { page } = await workspace(t);
  await create(page);
  await hold(page);
  await page.locator('#cancelBookingBtn').click();
  await expect(page.locator('#dialogStatus')).toHaveText('Đã hủy');
  await close(page);
  await expect(page.locator('#slotGrid .slot[data-time="10:00"]')).toBeEnabled();
  await page.locator('#renameBtn').click();
  await page.locator('#renameName').fill('Thiết bị đã đổi tên');
  await page.locator('#renameSubmit').click();
  await expect(page.locator('#resourceName')).toHaveText('Thiết bị đã đổi tên');
  await expect(page.locator('.booking-row')).toContainText('Thiết bị đã đổi tên');
  await page.locator('#deleteBtn').click();
  await expect(page.locator('#deleteMessage')).toContainText('cả lịch đã xác nhận');
  await page.getByRole('button', { name: 'Giữ lại', exact: true }).click();
  await expect(page.locator('#workbench')).toBeVisible();
  await page.locator('#deleteBtn').click();
  await page.locator('#deleteSubmit').click();
  await expect(page.locator('#empty')).toBeVisible();
  await expect(page.locator('#statResources')).toHaveText('0');
});
test('lost response can be recovered with the same intent without creating a second hold', async (t) => {
  const { page, context } = await workspace(t);
  await create(page, 'Network-safe room');
  await choose(page);
  let first = true;
  await page.route('**/api/holds', async (route) => {
    if (first) {
      first = false;
      await route.fetch();
      await route.abort('failed');
    } else await route.continue();
  });
  await page.locator('#holdBtn').click();
  await expect(page.locator('#unknownNote')).toBeVisible();
  await expect(page.locator('#holdBtn')).toHaveText('↻ Nhận lại kết quả');
  await page.locator('#holdBtn').click();
  await expect(page.locator('#reservationDialog')).toBeVisible();
  await expect(page.locator('#replayedNotice')).toBeVisible();
  const id = await page.locator('#resourceSelect').inputValue();
  const response = await context.request.get(base + '/api/reservations?resourceId=' + id);
  const data = await response.json();
  assert.equal(data.reservations.length, 1);
  assert.equal(data.reservations[0].status, 'HELD');
  await confirm(page);
  await close(page);
  await expect(page.locator('#statConfirmed')).toHaveText('1');
});
test('literal user text, formula-safe CSV and invalid date controls remain safe', async (t) => {
  const { page } = await workspace(t);
  const name = 'Phòng <img src=x onerror=window.hacked=1>';
  await create(page, name);
  await hold(page, '=1+1');
  await confirm(page);
  await close(page);
  await expect(page.locator('#resourceName img')).toHaveCount(0);
  assert.equal(await page.evaluate(() => window.hacked), undefined);
  const downloadEvent = page.waitForEvent('download');
  await page.locator('#csvBtn').click();
  assert.match(readFileSync(await (await downloadEvent).path(), 'utf8'), /"'=1\+1"/);
  await page.locator('#dayInput').fill('');
  await expect(page.locator('#prevDay')).toBeDisabled();
  await expect(page.locator('#nextDay')).toBeDisabled();
  await expect(page.locator('#holdBtn')).toBeDisabled();
});
test('a concurrent confirmation refreshes stale modal state instead of repeating a mutation', async (t) => {
  const { page, context } = await workspace(t);
  await create(page, 'Two tabs');
  await hold(page);
  const workspace = await (await context.request.get(base + '/api/workspace')).json();
  const list = await (await context.request.get(base + '/api/reservations')).json();
  const booking = list.reservations[0];
  const changed = await context.request.post(
    base + '/api/reservations/' + booking.id + '/confirm',
    { headers: { 'X-CSRF-Token': workspace.csrf }, data: { version: booking.version } },
  );
  assert.equal(changed.status(), 200);
  await page.locator('#confirmBookingBtn').click();
  await expect(page.locator('#reservationError')).toBeVisible();
  await expect(page.locator('#dialogStatus')).toHaveText('Đã xác nhận');
  await expect(page.locator('#confirmBookingBtn')).not.toBeVisible();
});
test('mobile retains resource switching, slot selection and completion without horizontal overflow', async (t) => {
  const { page } = await workspace(t, { width: 390, height: 844 });
  await demo(page);
  await expect(page.locator('#resourceSelect')).toBeVisible();
  await hold(page, 'Lịch trên điện thoại');
  await confirm(page);
  await close(page);
  const size = await page.evaluate(() => ({
    width: document.documentElement.scrollWidth,
    viewport: innerWidth,
  }));
  assert.ok(size.width <= size.viewport + 1, JSON.stringify(size));
  await capture(page, 'mobile');
  await page.locator('#resourceSelect').selectOption({ label: 'Bàn làm việc tầng 2' });
  await expect(page.locator('#resourceName')).toHaveText('Bàn làm việc tầng 2');
  await expect(page.locator('#statConfirmed')).toHaveText('2');
});
