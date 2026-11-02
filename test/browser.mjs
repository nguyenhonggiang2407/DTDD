import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { chromium, expect } from '@playwright/test';
import { mkdirSync, mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { createApp } from '../server.mjs';
let app, browser, base;
const images = resolve('docs/screenshots'),
  downloads = mkdtempSync(join(tmpdir(), 'yangqueue-downloads-'));
before(async () => {
  mkdirSync(images, { recursive: true });
  app = createApp({ dbPath: ':memory:' });
  await new Promise((r) => app.server.listen(0, '127.0.0.1', r));
  base = 'http://127.0.0.1:' + app.server.address().port;
  browser = await chromium.launch(
    process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {},
  );
});
after(async () => {
  if (browser) await browser.close();
  if (app) await app.close();
});
async function setup(mobile = false) {
  const context = await browser.newContext({
    viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1050 },
    isMobile: mobile,
    hasTouch: mobile,
    acceptDownloads: true,
  });
  const page = await context.newPage(),
    errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(base);
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  return { context, page, errors };
}
async function sample(page) {
  await page.locator('#load-demo').click();
  await expect(page.locator('#stat-total')).toHaveText('3');
  await expect(page.locator('#queue-status')).toHaveText('Tạm dừng nhận tác vụ mới');
  await page.locator('#toggle-queue').click();
  await expect(page.locator('#stat-success')).toHaveText('3', { timeout: 15000 });
}
async function capture(page, name) {
  await expect(page.locator('#toast')).not.toBeVisible({ timeout: 6000 });
  await page.evaluate(() => {
    document.activeElement?.blur();
    window.scrollTo(0, 0);
  });
  await page.screenshot({ path: join(images, name + '.png'), fullPage: true });
}
test('sample queue executes in real workers and downloads verified CSV output', async () => {
  const { context, page, errors } = await setup();
  await sample(page);
  await capture(page, 'desktop');
  await page
    .getByRole('button', { name: 'Xem [Mẫu] Làm sạch danh sách đồ dùng', exact: true })
    .click();
  await expect(page.locator('#detail-status')).toHaveText('Thành công');
  await expect(page.locator('#timeline')).toContainText('Tạo tệp kết quả');
  await expect(page.locator('#result-preview table')).toBeVisible();
  await capture(page, 'result');
  const promise = page.waitForEvent('download');
  await page.locator('#download-result').click();
  const file = await promise,
    target = join(downloads, 'cleaned.csv');
  await file.saveAs(target);
  const data = readFileSync(target),
    digest = createHash('sha256').update(data).digest('hex');
  assert.equal(digest, await page.locator('#result-sha').textContent());
  assert.match(data.toString('utf8'), /Bút xanh/);
  assert.deepEqual(errors, []);
  await context.close();
});
test('queued cancellation and deletion keep an accurate task count', async () => {
  const { context, page, errors } = await setup();
  await page.locator('#load-demo').click();
  await expect(page.locator('#stat-active')).toHaveText('3');
  await page
    .getByRole('button', { name: 'Xem [Mẫu] Chuyển ghi chú JSON sang CSV', exact: true })
    .click();
  await page.getByRole('button', { name: 'Hủy tác vụ', exact: true }).click();
  await page.locator('#confirm-action').click();
  await expect(page.locator('#detail-status')).toHaveText('Đã hủy');
  await page.getByRole('button', { name: 'Xóa bản ghi', exact: true }).click();
  await page.getByRole('button', { name: 'Giữ lại', exact: true }).click();
  await expect(page.locator('#confirm-dialog')).not.toBeVisible();
  await page.getByRole('button', { name: 'Xóa bản ghi', exact: true }).click();
  await page.locator('#confirm-action').click();
  await expect(page.locator('#stat-total')).toHaveText('2');
  assert.deepEqual(errors, []);
  await context.close();
});
test('malformed data fails once; editing and resubmission creates successful new work', async () => {
  const { context, page, errors } = await setup();
  await page.locator('#new-job').click();
  await page.locator('#job-title').fill('CSV cần sửa');
  await page.locator('#job-input').fill('a,b\none');
  await page.locator('#submit-job').click();
  await expect(page.locator('#detail-status')).toHaveText('Lỗi', { timeout: 15000 });
  await expect(page.locator('#detail-failure')).toContainText('không khớp');
  await expect(page.locator('#detail-attempt')).toHaveText('Lượt 1 / 3');
  await page.getByRole('button', { name: 'Sửa và gửi mới', exact: true }).click();
  await page.locator('#job-input').fill('a,b\none,two');
  await page.locator('#submit-job').click();
  await expect(page.locator('#detail-status')).toHaveText('Thành công', { timeout: 15000 });
  await page.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
  await expect(page.locator('#stat-total')).toHaveText('2');
  assert.deepEqual(errors, []);
  await context.close();
});
test('mobile supports UTF-8 upload, safe rendering, navigation and result preview', async () => {
  const { context, page, errors } = await setup(true);
  await sample(page);
  await capture(page, 'mobile');
  assert.ok(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    'Mobile horizontal overflow',
  );
  await page.locator('#new-job').click();
  await page
    .locator('#file-input')
    .setInputFiles({
      name: 'notes.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('Đỏ xanh đỏ.\n<img src=x onerror=alert(1)>'),
    });
  await expect(page.locator('#job-type')).toHaveValue('text-stats');
  await page.locator('#job-title').fill('<img src=x onerror=alert(1)>');
  await page.locator('#submit-job').click();
  await expect(page.locator('#detail-status')).toHaveText('Thành công', { timeout: 15000 });
  await expect(page.locator('#detail-title')).toHaveText('<img src=x onerror=alert(1)>');
  assert.equal(await page.locator('#job-dialog img').count(), 0);
  await page.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
  await page.getByRole('button', { name: 'Đã hoàn thành', exact: false }).click();
  await expect(page.locator('#job-count')).toHaveText('4');
  assert.deepEqual(errors, []);
  await context.close();
});
