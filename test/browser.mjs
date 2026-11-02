import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { chromium, expect } from '@playwright/test';
import SwaggerParser from '@apidevtools/swagger-parser';
import { mkdtempSync, mkdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { createApp } from '../server.mjs';

let app, browser, base;
const screenshots = resolve('docs/screenshots'),
  downloads = mkdtempSync(join(tmpdir(), 'yanglab-downloads-'));
before(async () => {
  mkdirSync(screenshots, { recursive: true });
  app = createApp({ dbPath: ':memory:', clock: () => Date.parse('2026-11-02T03:00:00Z') });
  await new Promise((resolveListen) => app.server.listen(0, '127.0.0.1', resolveListen));
  base = 'http://127.0.0.1:' + app.server.address().port;
  browser = await chromium.launch(
    process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {},
  );
});
after(async () => {
  if (browser) await browser.close();
  if (app) await app.close();
});
async function pageFor(module, mobile = false) {
  const context = await browser.newContext({
    viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1050 },
    isMobile: mobile,
    hasTouch: mobile,
    acceptDownloads: true,
  });
  const page = await context.newPage(),
    errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(base + '/' + module);
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  return { context, page, errors };
}
async function demo(page, module) {
  await page.locator('#load-demo').click();
  const stat = { minutes: '#stat-meetings', launch: '#stat-posts', spec: '#stat-contracts' }[
    module
  ];
  await expect(page.locator(stat)).toHaveText(module === 'launch' ? '3' : '1');
}
async function capture(page, name) {
  await expect(page.locator('#toast')).not.toBeVisible({ timeout: 6000 });
  await page.evaluate(() => {
    document.activeElement?.blur();
    window.scrollTo(0, 0);
  });
  await page.screenshot({ path: join(screenshots, name + '.png'), fullPage: true });
}

test('YangMinutes: sample, checklist extraction, task completion and Markdown/ICS exports', async () => {
  const { context, page, errors } = await pageFor('minutes');
  await demo(page, 'minutes');
  await capture(page, 'yangminutes-desktop');
  await page
    .getByRole('checkbox', { name: 'Hoàn thành Hoàn thiện tài liệu API', exact: true })
    .check();
  await expect(page.locator('#stat-open')).toHaveText('1');
  const mdDownload = page.waitForEvent('download');
  await page.getByRole('link', { name: '↓ Markdown', exact: true }).click();
  const md = await mdDownload;
  const mdPath = join(downloads, 'meeting.md');
  await md.saveAs(mdPath);
  assert.match(readFileSync(mdPath, 'utf8'), /Hoàn thiện tài liệu API/);
  const icsDownload = page.waitForEvent('download');
  await page.getByRole('link', { name: '↓ Lịch ICS', exact: true }).click();
  const ics = await icsDownload;
  const icsPath = join(downloads, 'meeting.ics');
  await ics.saveAs(icsPath);
  assert.match(readFileSync(icsPath, 'utf8'), /BEGIN:VTODO/);

  await page.locator('#new-meeting').click();
  const form = page.locator('#meeting-form');
  await form.getByLabel('Tên cuộc họp', { exact: true }).fill('Review tuần');
  await form
    .getByLabel('Ghi chép', { exact: true })
    .fill('- [ ] @Giang | 2026-11-08 | Kiểm thử export');
  await page.getByRole('button', { name: 'Lấy công việc từ ghi chép', exact: true }).click();
  await expect(page.locator('#extract-preview')).toContainText('Kiểm thử export');
  await page.getByRole('button', { name: 'Dùng danh sách này khi lưu', exact: true }).click();
  await page.locator('#save-meeting').click();
  await expect(page.locator('#stat-meetings')).toHaveText('2');
  await expect(page.getByRole('checkbox', { name: 'Hoàn thành Kiểm thử export' })).toBeVisible();
  assert.deepEqual(errors, []);
  await context.close();
});
test('YangLaunch: gated review, calendar, channel-aware CSV and safe title rendering', async () => {
  const { context, page, errors } = await pageFor('launch');
  await demo(page, 'launch');
  await capture(page, 'yanglaunch-desktop');
  const stage = page.getByLabel('Chuyển giai đoạn Một buổi sáng đi xe buýt', { exact: true });
  await stage.selectOption('review');
  await expect(page.getByRole('region', { name: 'Chờ duyệt', exact: true })).toContainText(
    'Một buổi sáng đi xe buýt',
  );
  await page
    .getByLabel('Chuyển giai đoạn Một buổi sáng đi xe buýt', { exact: true })
    .selectOption('ready');
  await expect(page.locator('#stat-ready')).toHaveText('1');
  await page.locator('#calendar-view').click();
  await expect(page.locator('.calendar-day')).toHaveCount(3);
  await page.locator('#channel-filter').selectOption('facebook');
  await expect(page.locator('.calendar-day')).toHaveCount(1);
  const downloadPromise = page.waitForEvent('download');
  await page.locator('#export-csv').click();
  const download = await downloadPromise,
    target = join(downloads, 'plan.csv');
  await download.saveAs(target);
  const csv = readFileSync(target, 'utf8');
  assert.match(csv, /facebook/);
  assert.doesNotMatch(csv, /linkedin/);
  await page.locator('#new-post').click();
  const form = page.locator('#post-form');
  await form.getByLabel('Tiêu đề', { exact: true }).fill('<img src=x onerror=alert(1)>');
  await page.locator('#save-post').click();
  await expect(page.locator('#stat-posts')).toHaveText('4');
  await page.locator('#clear-filters').click();
  await page.locator('#board-view').click();
  await expect(
    page.getByRole('button', { name: '<img src=x onerror=alert(1)>', exact: true }),
  ).toBeVisible();
  assert.equal(await page.locator('.post-card img').count(), 0);
  assert.deepEqual(errors, []);
  await context.close();
});
test('YangSpec: invalid JSON stays in editor, endpoint save and real OpenAPI parser validation', async () => {
  const { context, page, errors } = await pageFor('spec');
  await demo(page, 'spec');
  await capture(page, 'yangspec-desktop');
  await page.getByRole('button', { name: '＋ Endpoint', exact: true }).click();
  const form = page.locator('#endpoint-form');
  await form.getByLabel('HTTP method', { exact: true }).selectOption('POST');
  await form.getByLabel('Đường dẫn', { exact: true }).fill('/notes');
  await form.getByLabel('Tóm tắt endpoint', { exact: true }).fill('Tạo ghi chú');
  await form.getByLabel('Mã phản hồi', { exact: true }).fill('201');
  await form.getByLabel('JSON request mẫu', { exact: true }).fill('{');
  await page.locator('#save-endpoint').click();
  await expect(page.locator('#endpoint-dialog .dialog-error')).toContainText('JSON hợp lệ');
  await expect(page.locator('#stat-endpoints')).toHaveText('2');
  await form.getByLabel('JSON request mẫu', { exact: true }).fill('{"title":"Note"}');
  await form
    .getByLabel('JSON response mẫu', { exact: true })
    .fill('{"id":3,"title":"Note","tags":["api",null]}');
  await page.locator('#save-endpoint').click();
  await expect(page.locator('#stat-endpoints')).toHaveText('3');
  await page.getByRole('button', { name: 'Xem OpenAPI', exact: true }).click();
  await expect(page.locator('#openapi-preview')).toContainText('"/notes"');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('link', { name: '↓ Tải OpenAPI JSON', exact: true }).click();
  const download = await downloadPromise,
    target = join(downloads, 'openapi.json');
  await download.saveAs(target);
  const doc = JSON.parse(readFileSync(target, 'utf8'));
  await SwaggerParser.validate(structuredClone(doc));
  assert.equal(
    doc.paths['/notes'].post.responses['201'].content['application/json'].schema.properties.id.type,
    'integer',
  );
  assert.deepEqual(errors, []);
  await context.close();
});
test('two tabs preserve unsaved edits and reject stale saves', async () => {
  const { context, page: first, errors } = await pageFor('minutes');
  await demo(first, 'minutes');
  const second = await context.newPage();
  await second.goto(base + '/minutes');
  await expect(second.locator('#stat-meetings')).toHaveText('1');
  await first.getByRole('button', { name: 'Chỉnh sửa', exact: true }).click();
  await second.getByRole('button', { name: 'Chỉnh sửa', exact: true }).click();
  await second
    .locator('#meeting-form')
    .getByLabel('Tên cuộc họp', { exact: true })
    .fill('Bản cập nhật từ tab hai');
  await second.locator('#save-meeting').click();
  await expect(second.locator('#meeting-dialog')).not.toBeVisible();
  await first
    .locator('#meeting-form')
    .getByLabel('Tên cuộc họp', { exact: true })
    .fill('Nội dung chưa lưu');
  await first.locator('#save-meeting').click();
  await expect(first.locator('#meeting-dialog .dialog-error')).toContainText('tab khác');
  await expect(
    first.locator('#meeting-form').getByLabel('Tên cuộc họp', { exact: true }),
  ).toHaveValue('Nội dung chưa lưu');
  await first.getByRole('button', { name: 'Đóng cuộc họp', exact: true }).click();
  await first.locator('#refresh').click();
  await expect(first.locator('#meeting-detail h2')).toHaveText('Bản cập nhật từ tab hai');
  assert.deepEqual(errors, []);
  await context.close();
});
for (const module of ['minutes', 'launch', 'spec'])
  test(module + ': mobile navigation, responsive layout and delete cancellation', async () => {
    const { context, page, errors } = await pageFor(module, true);
    await demo(page, module);
    await expect(page.locator('#refresh')).toBeVisible();
    if (module === 'launch') {
      await expect(page.locator('#export-csv')).toBeVisible();
      await expect(page.locator('#calendar-view')).toBeVisible();
      await expect(page.locator('.calendar-day')).toHaveCount(3);
      await page.locator('#board-view').click();
      await expect(page.locator('.lane')).toHaveCount(5);
      await page.locator('#calendar-view').click();
    }
    await capture(page, 'yang' + module + '-mobile');
    assert.ok(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      'Horizontal overflow at 390 px',
    );
    await expect(page.locator('.nav-link.active')).toBeVisible();
    if (module !== 'launch') {
      await page.getByRole('button', { name: 'Xóa', exact: true }).click();
      await page.getByRole('button', { name: 'Giữ lại', exact: true }).click();
      await expect(page.locator('#confirm-dialog')).not.toBeVisible();
      await page.getByRole('button', { name: 'Xóa', exact: true }).click();
      await page.getByRole('button', { name: 'Xóa dữ liệu', exact: true }).click();
      await expect(
        page.locator(module === 'minutes' ? '#stat-meetings' : '#stat-contracts'),
      ).toHaveText('0');
    }
    assert.deepEqual(errors, []);
    await context.close();
  });
