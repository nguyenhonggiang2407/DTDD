import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, readFileSync } from 'node:fs';
import { chromium, expect } from '@playwright/test';
import { createApp } from '../server.mjs';
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
  page.on('pageerror', (error) => errors.push(error.message));
  t.after(async () => {
    await context.close();
    assert.deepEqual(errors, [], 'No uncaught client errors');
  });
  await page.goto(base);
  await expect(page.locator('#demoBtn')).toBeEnabled();
  return { context, page };
}
async function demo(page) {
  await page.locator('#demoBtn').click();
  await expect(page.locator('#resultMeta')).toContainText('12 dòng');
}
async function importCSV(page, csv, name = 'Orders') {
  await page.getByRole('button', { name: '＋ Nhập CSV', exact: true }).click();
  await page
    .locator('#csvFile')
    .setInputFiles({ name: name + '.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await expect(page.locator('#importSubmit')).toBeEnabled();
  await page.locator('#importSubmit').click();
  await expect(page.locator('#importDialog')).not.toBeVisible();
  await expect(page.locator('#datasetTitle')).toHaveText(name);
  await expect(page.locator('#runBtn')).toBeEnabled();
}
async function save(page, name) {
  await page.locator('#saveReportBtn').click();
  await page.locator('#reportName').fill(name);
  await page.locator('#reportSubmit').click();
  await expect(page.locator('#reportDialog')).not.toBeVisible();
}
async function capture(page, name) {
  await expect(page.locator('#toast')).toBeHidden({ timeout: 8000 });
  await page.locator('#main').focus();
  await page.locator('#main').evaluate((element) => element.blur());
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: 'docs/screenshots/' + name + '.png', fullPage: true });
}
test('filter → grouped SQL → saved report → reopen → edit → CSV export', async (t) => {
  const { page } = await workspace(t);
  await demo(page);
  await expect(page.locator('#resultTable tbody tr').first()).toContainText('001');
  await page.locator('#addFilterBtn').click();
  await page.getByLabel('Cột điều kiện 1', { exact: true }).selectOption('c5');
  await page.getByLabel('Giá trị điều kiện 1', { exact: true }).fill('Hoàn thành');
  await page.locator('#aggregateFn').selectOption('sum');
  await page.locator('#aggregateColumn').selectOption('c3');
  await page.locator('#groupBy').selectOption('c1');
  await page.locator('#direction').selectOption('desc');
  await expect(page.locator('#saveReportBtn')).toBeDisabled();
  await page.locator('#runBtn').click();
  await expect(page.locator('#resultMeta')).toContainText('3 nhóm');
  await expect(page.locator('#resultTable tbody tr').first()).toContainText('Hà Nội');
  await expect(page.locator('#resultTable tbody tr').first()).toContainText('1.090.000');
  await expect(page.locator('#insight meter')).toHaveCount(3);
  await save(page, 'Doanh thu hoàn thành theo vùng');
  await expect(page.locator('#statReports')).toHaveText('1');
  await capture(page, 'desktop');
  await page.locator('#sqlDetails summary').click();
  await expect(page.locator('#sqlText')).toContainText('SUM("c3")');
  await expect(page.locator('#paramsText')).toContainText('Hoàn thành');
  await expect(page.locator('#queryPlan')).toContainText('SCAN');
  await capture(page, 'query-plan');
  await page.locator('#sqlDetails summary').click();
  const downloadPromise = page.waitForEvent('download');
  await page.locator('#exportQueryBtn').click();
  const download = await downloadPromise;
  assert.equal(download.suggestedFilename(), 'yangdata-result.csv');
  const csv = readFileSync(await download.path(), 'utf8');
  assert.match(csv, /"Hà Nội","1090000"/);
  assert.ok(!csv.includes('001'));
  await page.locator('[data-view="reports"]').click();
  await expect(page.locator('.report-card')).toHaveCount(1);
  await page.getByRole('button', { name: 'Mở báo cáo ↗', exact: true }).click();
  await expect(page.locator('#resultMeta')).toContainText('3 nhóm');
  await expect(page.getByLabel('Giá trị điều kiện 1', { exact: true })).toHaveValue('Hoàn thành');
  await page.getByLabel('Giá trị điều kiện 1', { exact: true }).fill('Đang xử lý');
  await page.locator('#runBtn').click();
  await expect(page.locator('#resultMeta')).toContainText('2 nhóm');
  await save(page, 'Đơn đang xử lý theo vùng');
  await expect(page.locator('#reportContextText')).toContainText('v2');
  await page.locator('[data-view="reports"]').click();
  await expect(page.locator('.report-card h2')).toHaveText('Đơn đang xử lý theo vùng');
  await capture(page, 'reports');
});
test('CSV type correction, literal HTML, formula-safe export and duplicate reuse', async (t) => {
  const { page } = await workspace(t);
  const csv =
    'id,region,sales,note\n001,Hà Nội,12,"<img src=x onerror=""window.hacked=1"">"\n002,Đà Nẵng,3,=1+1\n';
  await page.getByRole('button', { name: '＋ Nhập CSV', exact: true }).click();
  await page.locator('#importName').fill('Safety dataset');
  await page.locator('#csvInput').fill(csv);
  await page.locator('#inspectBtn').click();
  await expect(page.locator('#importSubmit')).toBeEnabled();
  await expect(page.getByLabel('Kiểu cột id', { exact: true })).toHaveValue('text');
  await page.getByLabel('Kiểu cột id', { exact: true }).selectOption('number');
  await expect(page.locator('#importError')).toContainText('không phải số');
  await expect(page.locator('#importSubmit')).toBeDisabled();
  await page.getByLabel('Kiểu cột id', { exact: true }).selectOption('text');
  await expect(page.locator('#importSubmit')).toBeEnabled();
  await page.locator('#importSubmit').click();
  await expect(page.locator('#importDialog')).not.toBeVisible();
  await expect(page.locator('#resultTable')).toContainText('<img src=x');
  await expect(page.locator('#resultTable img')).toHaveCount(0);
  assert.equal(await page.evaluate(() => window.hacked), undefined);
  const downloadPromise = page.waitForEvent('download');
  await page.locator('#exportDatasetBtn').click();
  const download = await downloadPromise;
  assert.match(readFileSync(await download.path(), 'utf8'), /"'=1\+1"/);
  await page.getByRole('button', { name: '＋ Nhập CSV', exact: true }).click();
  await page
    .locator('#csvFile')
    .setInputFiles({ name: 'same.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await expect(page.locator('#importSubmit')).toBeEnabled();
  await page.locator('#importSubmit').click();
  await expect(page.locator('#importDialog')).not.toBeVisible();
  await expect(page.locator('#statDatasets')).toHaveText('1');
  await expect(page.locator('#datasetTitle')).toHaveText('Safety dataset');
});
test('malformed CSV and invalid UTF-8 give actionable errors without persisting datasets', async (t) => {
  const { page } = await workspace(t);
  await page.getByRole('button', { name: '＋ Nhập CSV', exact: true }).click();
  await page.locator('#csvInput').fill('a,b\n1\n');
  await page.locator('#inspectBtn').click();
  await expect(page.locator('#importError')).toContainText('Dòng 2');
  await expect(page.locator('#importSubmit')).toBeDisabled();
  await page.locator('#csvInput').fill('id,total\n001,10\n');
  await page.locator('#inspectBtn').click();
  await expect(page.locator('#importSubmit')).toBeEnabled();
  await page
    .locator('#csvFile')
    .setInputFiles({ name: 'bad.csv', mimeType: 'text/csv', buffer: Buffer.from([0xc0, 0xaf]) });
  await expect(page.locator('#importError')).toContainText('UTF-8');
  await expect(page.locator('#importSubmit')).toBeDisabled();
  await expect(page.locator('#csvInput')).toHaveValue('');
  await page.keyboard.press('Escape');
  await expect(page.locator('#importDialog')).not.toBeVisible();
  await expect(page.locator('#statDatasets')).toHaveText('0');
});
test('late inspection failures cannot replace the preview of a newer file', async (t) => {
  const { page } = await workspace(t);
  let release;
  let pending = false;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  await page.route('**/api/inspect', async (route) => {
    if (route.request().postDataJSON().csv === 'a,b\n1\n') {
      const response = await route.fetch();
      pending = true;
      await gate;
      await route.fulfill({ response });
    } else {
      await route.continue();
    }
  });
  try {
    await page.getByRole('button', { name: '＋ Nhập CSV', exact: true }).click();
    await page.locator('#csvInput').fill('a,b\n1\n');
    await page.locator('#inspectBtn').click();
    await expect.poll(() => pending).toBe(true);
    await page.locator('#csvFile').setInputFiles({
      name: 'New orders.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('id,total\n001,10\n'),
    });
    await expect(page.locator('#importSubmit')).toBeEnabled();
    await expect(page.locator('#previewTable')).toContainText('001');
    release();
    await expect(page.locator('#inspectBtn')).toBeEnabled();
    await expect(page.locator('#importError')).not.toBeVisible();
    await expect(page.locator('#importSubmit')).toBeEnabled();
    await page.locator('#importSubmit').click();
    await expect(page.locator('#importDialog')).not.toBeVisible();
    await expect(page.locator('#datasetTitle')).toHaveText('New orders');
    await expect(page.locator('#resultTable')).toContainText('001');
  } finally {
    release();
  }
});
test('pagination, saved snapshots, confirmation cancellation and cascade delete', async (t) => {
  const { page } = await workspace(t);
  const csv =
    'id,amount\n' +
    Array.from({ length: 37 }, (_, i) => String(i + 1).padStart(3, '0') + ',' + (i + 1)).join(
      '\n',
    ) +
    '\n';
  await importCSV(page, csv, 'Pagination');
  await page.locator('#pageSize').selectOption('25');
  await page.locator('#runBtn').click();
  await expect(page.locator('#pageLabel')).toHaveText('1–25 / 37');
  await page.locator('#nextPage').click();
  await expect(page.locator('#pageLabel')).toHaveText('26–37 / 37');
  await save(page, 'Page snapshot');
  await page.locator('[data-view="reports"]').click();
  await page.getByRole('button', { name: 'Mở báo cáo ↗', exact: true }).click();
  await expect(page.locator('#pageLabel')).toHaveText('1–25 / 37');
  await page.locator('#renameDatasetBtn').click();
  await page.locator('#renameName').fill('37 đơn mẫu');
  await page.locator('#renameSubmit').click();
  await expect(page.locator('#datasetTitle')).toHaveText('37 đơn mẫu');
  await page.locator('#deleteDatasetBtn').click();
  await expect(page.locator('#confirmText')).toContainText('1 báo cáo');
  await page.getByRole('button', { name: 'Giữ lại', exact: true }).click();
  await expect(page.locator('#workbench')).toBeVisible();
  await page.locator('#deleteDatasetBtn').click();
  await page.locator('#confirmSubmit').click();
  await expect(page.locator('#emptyState')).toBeVisible();
  await expect(page.locator('#statReports')).toHaveText('0');
});
test('mobile retains dataset switching, query controls, saved reports and a bounded layout', async (t) => {
  const { page } = await workspace(t, { width: 390, height: 844 });
  await demo(page);
  await page.locator('#aggregateFn').selectOption('count');
  await page.locator('#aggregateColumn').selectOption('*');
  await page.locator('#groupBy').selectOption('c1');
  await page.locator('#runBtn').click();
  await expect(page.locator('#resultMeta')).toContainText('3 nhóm');
  await save(page, 'Đơn hàng theo vùng');
  await page.locator('#datasetSelect').selectOption({ label: 'Đơn hàng cửa hàng mẫu' });
  await expect(page.locator('#datasetSelect')).toBeVisible();
  await expect(page.locator('#resultTable')).toBeVisible();
  const dimensions = await page.evaluate(() => ({
    screen: innerWidth,
    document: document.documentElement.scrollWidth,
  }));
  assert.ok(dimensions.document <= dimensions.screen + 1, JSON.stringify(dimensions));
  await capture(page, 'mobile');
  await page.locator('[data-view="reports"]').click();
  await expect(page.locator('.report-card')).toHaveCount(1);
  await page.getByRole('button', { name: 'Mở báo cáo ↗', exact: true }).click();
  await expect(page.locator('#resultMeta')).toContainText('3 nhóm');
});
