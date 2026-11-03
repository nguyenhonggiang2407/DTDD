const $ = (id) => document.getElementById(id);
const state = {
  csrf: '',
  datasets: [],
  reports: [],
  currentId: null,
  filters: [],
  result: null,
  report: null,
  dirty: false,
  running: false,
  querySeq: 0,
  revision: 0,
  preview: null,
  previewValid: false,
  importRevision: 0,
  inspectSeq: 0,
  importTypes: {},
  busy: new Set(),
};
const format = (value) =>
  new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value);
const when = (value) =>
  new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined && text !== null) element.textContent = String(text);
  return element;
}
function current() {
  return state.datasets.find((dataset) => dataset.id === state.currentId);
}
let toastTimer;
function toast(message, error = false) {
  clearTimeout(toastTimer);
  $('toast').textContent = message;
  $('toast').classList.toggle('error', error);
  $('toast').hidden = false;
  toastTimer = setTimeout(() => ($('toast').hidden = true), error ? 8000 : 4500);
}
class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}
async function api(path, method = 'GET', body, download = false) {
  const response = await fetch(path, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(method === 'GET' ? {} : { 'X-CSRF-Token': state.csrf }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    if (response.status === 401) $('sessionExpired').hidden = false;
    throw new ApiError(problem.error || 'Không thể kết nối. Vui lòng thử lại.', response.status);
  }
  if (download) return response.blob();
  return response.status === 204 ? null : response.json();
}
function setError(id, error) {
  if (!id) return;
  $(id).textContent = error ? error.message : '';
  $(id).hidden = !error;
}
async function task(button, action, errorId) {
  if (button && state.busy.has(button)) return;
  if (button) {
    state.busy.add(button);
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
  }
  setError(errorId, null);
  try {
    return await action();
  } catch (error) {
    if (errorId) setError(errorId, error);
    else toast(error.message || 'Có lỗi kết nối. Hãy thử lại.', true);
    if (error.status === 409) await refreshLists().catch(() => {});
  } finally {
    if (button) {
      state.busy.delete(button);
      button.disabled = false;
      button.removeAttribute('aria-busy');
    }
    updateControls();
  }
}
function options(select, items, value) {
  select.replaceChildren(...items.map((item) => new Option(item.name, item.key)));
  if (items.some((item) => item.key === value)) select.value = value;
}
function view(name) {
  $('exploreView').hidden = name !== 'explore';
  $('reportsView').hidden = name !== 'reports';
  $('breadcrumb').textContent = name === 'reports' ? 'Báo cáo đã lưu' : 'Khám phá dữ liệu';
  document
    .querySelectorAll('[data-view]')
    .forEach((button) => button.classList.toggle('active', button.dataset.view === name));
  if (name === 'reports') renderReports();
}
function renderDatasetList() {
  const list = $('datasetList');
  list.replaceChildren();
  for (const dataset of state.datasets) {
    const button = node(
      'button',
      'dataset-link' + (dataset.id === state.currentId ? ' active' : ''),
    );
    button.type = 'button';
    button.append(
      node('span', '', '▦'),
      node('span', 'dataset-name', dataset.name),
      node('small', '', format(dataset.rowCount)),
    );
    button.title = dataset.name;
    button.addEventListener('click', () => task(button, () => selectDataset(dataset.id)));
    list.append(button);
  }
  if (!state.datasets.length) list.append(node('p', 'side-muted', 'Chưa có tập dữ liệu.'));
  options(
    $('datasetSelect'),
    state.datasets.map((data) => ({ key: data.id, name: data.name })),
    state.currentId,
  );
}
function renderStats() {
  $('datasetCount').textContent = state.datasets.length;
  $('reportCount').textContent = state.reports.length;
  $('statDatasets').textContent = state.datasets.length;
  $('statRows').textContent = format(state.datasets.reduce((sum, d) => sum + d.rowCount, 0));
  $('statReports').textContent = state.reports.length;
  $('statColumns').textContent = current()?.columns.length || 0;
  $('emptyState').hidden = state.datasets.length > 0;
  $('workbench').hidden = !current();
}
function renderDatasetInfo() {
  const dataset = current();
  if (!dataset) return;
  $('datasetTitle').textContent = dataset.name;
  $('datasetMeta').textContent =
    format(dataset.rowCount) +
    ' dòng · ' +
    dataset.columns.length +
    ' cột · ' +
    format(dataset.sourceBytes) +
    ' B · SHA256 ' +
    dataset.checksum.slice(0, 10) +
    '… · v' +
    dataset.version;
}
async function refreshLists() {
  const [datasets, reports] = await Promise.all([api('/api/datasets'), api('/api/reports')]);
  state.datasets = datasets.datasets;
  state.reports = reports.reports;
  if (state.report) {
    const fresh = state.reports.find((report) => report.id === state.report.id);
    if (!fresh) state.report = null;
  }
  renderDatasetList();
  renderStats();
  renderDatasetInfo();
  renderReports();
}
function markDirty() {
  state.dirty = true;
  state.revision++;
  $('dirtyNote').hidden = !state.result;
  updateControls();
}
function renderColumns(selected) {
  const dataset = current(),
    checked = selected?.length ? selected : dataset.columns.map((column) => column.key);
  $('columnChecks').replaceChildren();
  for (const column of dataset.columns) {
    const label = node('label'),
      box = node('input');
    box.type = 'checkbox';
    box.value = column.key;
    box.checked = checked.includes(column.key);
    box.addEventListener('change', () => {
      updateSelectedCount();
      updateSort();
      markDirty();
    });
    label.append(
      box,
      node('span', '', column.name),
      node('span', 'type-dot', column.type === 'number' ? '#' : 'Aa'),
    );
    $('columnChecks').append(label);
  }
  updateSelectedCount();
}
function selectedColumns() {
  return [...$('columnChecks').querySelectorAll('input:checked')].map((input) => input.value);
}
function updateSelectedCount() {
  $('selectedColumnsCount').textContent =
    selectedColumns().length + '/' + (current()?.columns.length || 0);
}
function filterOps(column) {
  return [
    { key: 'eq', name: 'Bằng' },
    { key: 'ne', name: 'Khác' },
    ...(column.type === 'number'
      ? [
          { key: 'gt', name: 'Lớn hơn' },
          { key: 'gte', name: 'Lớn / bằng' },
          { key: 'lt', name: 'Nhỏ hơn' },
          { key: 'lte', name: 'Nhỏ / bằng' },
        ]
      : [
          { key: 'contains', name: 'Chứa chuỗi' },
          { key: 'starts', name: 'Bắt đầu' },
        ]),
    { key: 'isnull', name: 'Là NULL' },
    { key: 'notnull', name: 'Khác NULL' },
  ];
}
function renderFilters() {
  const dataset = current();
  $('filterRows').replaceChildren();
  $('filterEmpty').hidden = state.filters.length > 0;
  state.filters.forEach((filter, index) => {
    const row = node('div', 'filter-row'),
      top = node('div', 'filter-top'),
      bottom = node('div', 'filter-bottom');
    const column = node('select'),
      op = node('select'),
      value = node('input'),
      remove = node('button', 'icon-btn danger-text', '×');
    column.setAttribute('aria-label', 'Cột điều kiện ' + (index + 1));
    op.setAttribute('aria-label', 'Toán tử điều kiện ' + (index + 1));
    value.setAttribute('aria-label', 'Giá trị điều kiện ' + (index + 1));
    remove.type = 'button';
    remove.setAttribute('aria-label', 'Xóa điều kiện ' + (index + 1));
    options(
      column,
      dataset.columns.map((c) => ({ key: c.key, name: c.name })),
      filter.column,
    );
    const info = dataset.columns.find((c) => c.key === filter.column) || dataset.columns[0];
    const ops = filterOps(info);
    if (!ops.some((item) => item.key === filter.op)) filter.op = 'eq';
    options(op, ops, filter.op);
    value.value = filter.value ?? '';
    value.maxLength = 200;
    value.placeholder = info.type === 'number' ? 'Ví dụ: 100000' : 'Giá trị cần tìm';
    value.disabled = ['isnull', 'notnull'].includes(filter.op);
    column.addEventListener('change', () => {
      filter.column = column.value;
      filter.op = 'eq';
      filter.value = '';
      renderFilters();
      markDirty();
    });
    op.addEventListener('change', () => {
      filter.op = op.value;
      value.disabled = ['isnull', 'notnull'].includes(op.value);
      markDirty();
    });
    value.addEventListener('input', () => {
      filter.value = value.value;
      markDirty();
    });
    remove.addEventListener('click', () => {
      state.filters.splice(index, 1);
      renderFilters();
      markDirty();
    });
    top.append(column, remove);
    bottom.append(op, value);
    row.append(top, bottom);
    $('filterRows').append(row);
  });
  $('addFilterBtn').disabled = state.filters.length >= 8;
}
function updateAggregate(preferred) {
  const dataset = current();
  if (!dataset) return;
  const fn = $('aggregateFn').value,
    old = preferred ?? $('aggregateColumn').value;
  const candidates =
    fn === 'count'
      ? [{ key: '*', name: '* — mọi dòng' }, ...dataset.columns]
      : dataset.columns.filter((column) => column.type === 'number');
  options($('aggregateColumn'), candidates, old);
  $('aggregateColumn').disabled = !fn;
  $('groupBy').disabled = !fn;
  $('columnsSection').hidden = Boolean(fn);
  updateSort();
}
function updateSort(preferred) {
  const dataset = current();
  if (!dataset) return;
  const fn = $('aggregateFn').value,
    group = $('groupBy').value,
    old = preferred ?? $('orderBy').value;
  let fields;
  if (fn)
    fields = [
      ...(group ? dataset.columns.filter((c) => c.key === group) : []),
      { key: 'value', name: fn.toUpperCase() + ' — kết quả' },
    ];
  else fields = dataset.columns.filter((column) => selectedColumns().includes(column.key));
  options($('orderBy'), [{ key: '', name: fn ? 'Không chọn' : 'Thứ tự dòng gốc' }, ...fields], old);
  if (fn && (!old || !fields.some((item) => item.key === old))) $('orderBy').value = 'value';
}
function definition(offset = 0) {
  const fn = $('aggregateFn').value;
  return {
    datasetId: state.currentId,
    columns: selectedColumns(),
    filters: state.filters.map((filter) =>
      ['isnull', 'notnull'].includes(filter.op)
        ? { column: filter.column, op: filter.op }
        : { ...filter },
    ),
    groupBy: fn ? $('groupBy').value : '',
    aggregate: fn ? { fn, column: $('aggregateColumn').value } : null,
    orderBy: $('orderBy').value,
    direction: $('direction').value,
    limit: Number($('pageSize').value),
    offset,
  };
}
function renderReportContext() {
  $('reportContext').hidden = !state.report;
  $('reportContextText').textContent = state.report
    ? 'Đang sửa: ' + state.report.name + ' · v' + state.report.version
    : '';
  $('saveReportBtn').textContent = state.report ? '▤ Cập nhật báo cáo' : '▤ Lưu báo cáo';
}
async function selectDataset(id, snapshot = null, report = null) {
  state.currentId = id;
  state.report = report;
  state.result = null;
  state.filters = (snapshot?.filters || []).map((filter) => ({ ...filter }));
  state.dirty = false;
  state.revision++;
  const dataset = current();
  if (!dataset) throw new Error('Tập dữ liệu không còn tồn tại. Hãy tải lại.');
  view('explore');
  renderDatasetList();
  renderStats();
  renderDatasetInfo();
  renderColumns(snapshot?.columns);
  renderFilters();
  options(
    $('groupBy'),
    [{ key: '', name: 'Không nhóm' }, ...dataset.columns],
    snapshot?.groupBy || '',
  );
  $('aggregateFn').value = snapshot?.aggregate?.fn || '';
  updateAggregate(snapshot?.aggregate?.column);
  $('direction').value = snapshot?.direction || 'asc';
  $('pageSize').value = String(snapshot?.limit || 50);
  updateSort(snapshot?.orderBy || '');
  renderReportContext();
  $('resultTable').querySelector('thead').replaceChildren();
  $('resultTable').querySelector('tbody').replaceChildren();
  $('resultMeta').textContent = 'Đang chạy truy vấn…';
  $('insight').hidden = true;
  $('noRows').hidden = true;
  $('dirtyNote').hidden = true;
  await runQuery(definition(0));
}
function writeTable(table, columns, rows, { offset = 0, numbers = true } = {}) {
  const header = node('tr');
  if (numbers) header.append(node('th', 'row-number', '#'));
  for (const column of columns) {
    const th = node('th', column.type === 'number' ? 'numeric' : '');
    th.scope = 'col';
    th.append(
      document.createTextNode(column.name),
      node('span', 'col-type', column.type === 'number' ? '#' : 'Aa'),
    );
    header.append(th);
  }
  table.querySelector('thead').replaceChildren(header);
  const fragment = document.createDocumentFragment();
  rows.forEach((row, index) => {
    const tr = node('tr');
    if (numbers) tr.append(node('td', 'row-number', offset + index + 1));
    row.forEach((value, i) => {
      const td = node('td', columns[i].type === 'number' ? 'numeric' : '');
      if (value === null) td.append(node('span', 'null-value', 'NULL'));
      else {
        td.textContent = typeof value === 'number' ? format(value) : value;
        td.title = String(value);
      }
      tr.append(td);
    });
    fragment.append(tr);
  });
  table.querySelector('tbody').replaceChildren(fragment);
}
function renderInsight(result) {
  const enabled =
    result.definition.groupBy &&
    result.columns.length === 2 &&
    result.rows.length > 0 &&
    result.rows.every((row) => row[1] === null || (typeof row[1] === 'number' && row[1] >= 0));
  $('insight').hidden = !enabled;
  if (!enabled) return;
  $('insightTitle').textContent = result.columns[1].name + ' theo ' + result.columns[0].name;
  const maximum = Math.max(1, ...result.rows.map((row) => row[1] || 0));
  $('insightBars').replaceChildren();
  for (const row of result.rows.slice(0, 8)) {
    const item = node('div', 'bar-row'),
      meter = node('meter');
    meter.min = 0;
    meter.max = maximum;
    meter.value = row[1] || 0;
    meter.setAttribute('aria-label', String(row[0] ?? 'NULL') + ': ' + String(row[1] ?? 'NULL'));
    item.append(
      node('span', '', row[0] ?? 'NULL'),
      meter,
      node('strong', '', row[1] === null ? 'NULL' : format(row[1])),
    );
    $('insightBars').append(item);
  }
}
function renderResult() {
  const result = state.result;
  if (!result) return;
  writeTable($('resultTable'), result.columns, result.rows, { offset: result.offset });
  $('resultTitle').textContent = result.definition.aggregate ? 'Kết quả tổng hợp' : 'Bảng dữ liệu';
  $('resultMeta').textContent =
    format(result.total) +
    ' ' +
    (result.definition.groupBy ? 'nhóm' : 'dòng') +
    ' khớp · ' +
    result.columns.length +
    ' cột kết quả';
  $('noRows').hidden = result.rows.length > 0;
  $('pageLabel').textContent = result.rows.length
    ? format(result.offset + 1) +
      '–' +
      format(result.offset + result.rows.length) +
      ' / ' +
      format(result.total)
    : '0 / ' + format(result.total);
  $('sqlText').textContent = result.sql;
  $('paramsText').textContent = JSON.stringify(result.params, null, 2);
  $('queryPlan').replaceChildren(...result.plan.map((step) => node('li', '', step.detail)));
  $('dirtyNote').hidden = !state.dirty;
  renderInsight(result);
  renderReportContext();
  updateControls();
}
async function runQuery(query) {
  const sequence = ++state.querySeq,
    revision = state.revision;
  state.running = true;
  updateControls();
  try {
    const result = await api('/api/query', 'POST', query);
    if (sequence !== state.querySeq || result.datasetId !== state.currentId) return;
    state.result = result;
    state.dirty = state.revision !== revision;
    renderResult();
  } finally {
    if (sequence === state.querySeq) {
      state.running = false;
      updateControls();
    }
  }
}
function updateControls() {
  const dataset = current(),
    ready = Boolean(dataset && state.result && !state.dirty && !state.running);
  $('runBtn').disabled = !dataset || state.running;
  $('runBtn').textContent = state.running ? 'Đang truy vấn…' : '▷ Chạy truy vấn';
  $('exportQueryBtn').disabled = !ready || state.busy.has($('exportQueryBtn'));
  $('saveReportBtn').disabled = !ready;
  $('prevPage').disabled = !ready || state.result.offset === 0;
  $('nextPage').disabled =
    !ready || state.result.offset + state.result.rows.length >= state.result.total;
  $('importSubmit').disabled = !state.previewValid || state.busy.has($('importSubmit'));
  $('inspectBtn').disabled = state.busy.has($('inspectBtn'));
  $('dirtyNote').hidden = !state.result || !state.dirty;
}
function renderReports() {
  const list = $('reportList');
  list.replaceChildren();
  if (!state.reports.length) {
    const empty = node('div', 'report-empty');
    empty.append(
      node('h2', '', 'Chưa có báo cáo nào'),
      node(
        'p',
        '',
        'Chạy một truy vấn, sau đó chọn “Lưu báo cáo”.\nCấu hình được giữ lại để bạn mở và chạy lại.',
      ),
    );
    list.append(empty);
    return;
  }
  for (const report of state.reports) {
    const dataset = state.datasets.find((item) => item.id === report.datasetId),
      card = node('article', 'report-card'),
      buttons = node('div', 'report-buttons');
    card.append(
      node('div', 'report-icon', '▤'),
      node('h2', '', report.name),
      node(
        'p',
        '',
        (dataset?.name || 'Tập dữ liệu') +
          '\nCập nhật ' +
          when(report.updated) +
          ' · v' +
          report.version,
      ),
    );
    const d = report.definition;
    card.append(
      node(
        'span',
        'report-capsule',
        (d.aggregate ? d.aggregate.fn.toUpperCase() : 'BẢNG DÒNG') +
          ' · ' +
          d.filters.length +
          ' điều kiện' +
          (d.groupBy ? ' · có nhóm' : ''),
      ),
    );
    const open = node('button', 'button small primary', 'Mở báo cáo ↗'),
      download = node('button', 'button small outline', '↓ CSV'),
      remove = node('button', 'button small ghost danger-text', 'Xóa');
    open.addEventListener('click', () =>
      task(open, async () => {
        const latest = (await api('/api/reports/' + report.id)).report;
        await selectDataset(latest.datasetId, latest.definition, latest);
      }),
    );
    download.addEventListener('click', () =>
      task(download, () =>
        downloadFile('/api/reports/' + report.id + '/export', 'POST', {}, 'yangdata-report.csv'),
      ),
    );
    remove.addEventListener('click', () =>
      askDelete(
        'Xóa báo cáo?',
        'Báo cáo “' + report.name + '” sẽ bị xóa. Tập dữ liệu vẫn được giữ.',
        async () => {
          await api('/api/reports/' + report.id, 'DELETE', { version: report.version });
          if (state.report?.id === report.id) state.report = null;
          await refreshLists();
          renderReportContext();
          toast('Đã xóa báo cáo.');
        },
      ),
    );
    buttons.append(open, download, remove);
    card.append(buttons);
    list.append(card);
  }
}
async function downloadFile(path, method, body, name) {
  const blob = await api(path, method, body, true),
    url = URL.createObjectURL(blob),
    a = node('a');
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
let confirmation;
function askDelete(title, message, action) {
  confirmation = action;
  $('confirmTitle').textContent = title;
  $('confirmText').textContent = message;
  setError('confirmError', null);
  $('confirmDialog').showModal();
}
function invalidateImport() {
  state.importRevision++;
  state.inspectSeq++;
  state.preview = null;
  state.previewValid = false;
  state.importTypes = {};
  $('importPreview').hidden = true;
  setError('importError', null);
  const bytes = new TextEncoder().encode($('csvInput').value).length;
  $('csvSize').textContent = format(bytes) + ' B / 524.288 B · UTF-8';
  updateControls();
}
function openImport() {
  $('importForm').reset();
  invalidateImport();
  $('importDialog').showModal();
  $('importName').focus();
}
async function inspect() {
  const sequence = ++state.inspectSeq,
    revision = state.importRevision;
  state.previewValid = false;
  updateControls();
  let result;
  try {
    result = await api('/api/inspect', 'POST', {
      csv: $('csvInput').value,
      types: { ...state.importTypes },
    });
  } catch (error) {
    if (sequence !== state.inspectSeq || revision !== state.importRevision) return;
    throw error;
  }
  if (sequence !== state.inspectSeq || revision !== state.importRevision) return;
  state.preview = result;
  state.previewValid = true;
  $('previewMeta').textContent =
    format(result.rowCount) + ' dòng · ' + result.columns.length + ' cột';
  $('importPreview').hidden = false;
  $('importTypes').replaceChildren();
  for (const column of result.columns) {
    const label = node('label', '', column.name),
      select = node('select');
    select.id = 'type_' + column.key;
    select.setAttribute('aria-label', 'Kiểu cột ' + column.name);
    options(
      select,
      [
        { key: 'text', name: 'Văn bản (TEXT)' },
        { key: 'number', name: 'Số (REAL)' },
      ],
      column.type,
    );
    state.importTypes[column.key] = column.type;
    select.addEventListener('change', () => {
      state.importRevision++;
      state.importTypes[column.key] = select.value;
      state.previewValid = false;
      task(null, inspect, 'importError');
      updateControls();
    });
    label.append(select);
    $('importTypes').append(label);
  }
  writeTable($('previewTable'), result.columns, result.preview, { numbers: false });
  updateControls();
}
document
  .querySelectorAll('[data-view]')
  .forEach((button) => button.addEventListener('click', () => view(button.dataset.view)));
document
  .querySelectorAll('[data-import]')
  .forEach((button) => button.addEventListener('click', openImport));
document
  .querySelectorAll('[data-close]')
  .forEach((button) => button.addEventListener('click', () => $(button.dataset.close).close()));
$('reloadSession').addEventListener('click', () => location.reload());
$('backToExplore').addEventListener('click', () => view('explore'));
$('datasetSelect').addEventListener('change', () =>
  task(null, () => selectDataset($('datasetSelect').value)),
);
$('demoBtn').addEventListener('click', () =>
  task($('demoBtn'), async () => {
    const result = await api('/api/demo', 'POST', {});
    await refreshLists();
    await selectDataset(result.dataset.id);
    toast(result.reused ? 'Đã mở lại bộ đơn hàng mẫu.' : 'Đã nhập 12 đơn hàng giả lập.');
  }),
);
$('runBtn').addEventListener('click', () => task(null, () => runQuery(definition(0))));
$('resetBtn').addEventListener('click', () =>
  task($('resetBtn'), () => selectDataset(state.currentId)),
);
$('addFilterBtn').addEventListener('click', () => {
  if (state.filters.length >= 8) return;
  state.filters.push({ column: current().columns[0].key, op: 'eq', value: '' });
  renderFilters();
  markDirty();
});
$('aggregateFn').addEventListener('change', () => {
  updateAggregate();
  markDirty();
});
$('aggregateColumn').addEventListener('change', markDirty);
$('groupBy').addEventListener('change', () => {
  updateSort();
  markDirty();
});
for (const id of ['orderBy', 'direction', 'pageSize']) $(id).addEventListener('change', markDirty);
$('prevPage').addEventListener('click', () =>
  task(null, () =>
    runQuery({
      ...state.result.definition,
      offset: Math.max(0, state.result.offset - state.result.limit),
    }),
  ),
);
$('nextPage').addEventListener('click', () =>
  task(null, () =>
    runQuery({ ...state.result.definition, offset: state.result.offset + state.result.limit }),
  ),
);
$('leaveReportBtn').addEventListener('click', () => {
  state.report = null;
  renderReportContext();
});
$('exportQueryBtn').addEventListener('click', () =>
  task($('exportQueryBtn'), () =>
    downloadFile('/api/query/export', 'POST', state.result.definition, 'yangdata-result.csv'),
  ),
);
$('exportDatasetBtn').addEventListener('click', () =>
  task($('exportDatasetBtn'), () =>
    downloadFile(
      '/api/datasets/' + state.currentId + '/export',
      'GET',
      undefined,
      'yangdata-dataset.csv',
    ),
  ),
);
$('renameDatasetBtn').addEventListener('click', () => {
  $('renameName').value = current().name;
  setError('renameError', null);
  $('renameDialog').showModal();
});
$('renameForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('renameSubmit'),
    async () => {
      const data = current();
      await api('/api/datasets/' + data.id, 'PATCH', {
        name: $('renameName').value,
        version: data.version,
      });
      await refreshLists();
      $('renameDialog').close();
      toast('Đã cập nhật tên tập dữ liệu.');
    },
    'renameError',
  );
});
$('deleteDatasetBtn').addEventListener('click', () => {
  const dataset = current(),
    linked = state.reports.filter((report) => report.datasetId === dataset.id).length;
  askDelete(
    'Xóa tập dữ liệu?',
    'Xóa “' +
      dataset.name +
      '” gồm ' +
      format(dataset.rowCount) +
      ' dòng và ' +
      linked +
      ' báo cáo liên quan. Bạn có thể xuất CSV trước khi xóa.',
    async () => {
      await api('/api/datasets/' + dataset.id, 'DELETE', { version: dataset.version });
      state.currentId = null;
      state.report = null;
      state.result = null;
      state.querySeq++;
      await refreshLists();
      if (state.datasets.length) await selectDataset(state.datasets[0].id);
      else {
        renderStats();
        renderReportContext();
      }
      toast('Đã xóa tập dữ liệu và các báo cáo liên quan.');
    },
  );
});
$('confirmSubmit').addEventListener('click', () =>
  task(
    $('confirmSubmit'),
    async () => {
      await confirmation();
      $('confirmDialog').close();
    },
    'confirmError',
  ),
);
$('csvInput').addEventListener('input', invalidateImport);
$('csvFile').addEventListener('change', () =>
  task(
    null,
    async () => {
      const file = $('csvFile').files[0];
      if (!file) return;
      $('csvInput').value = '';
      invalidateImport();
      const revision = state.importRevision;
      if (file.size > 524288) throw new Error('CSV tối đa 512 KB.');
      let source;
      try {
        source = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
      } catch {
        if (revision !== state.importRevision) return;
        throw new Error('Không đọc được UTF-8. Hãy lưu tệp dạng CSV UTF-8.');
      }
      if (revision !== state.importRevision) return;
      $('csvInput').value = source;
      if (!$('importName').value)
        $('importName').value = file.name.replace(/\.csv$/i, '').slice(0, 120);
      invalidateImport();
      await inspect();
    },
    'importError',
  ),
);
$('inspectBtn').addEventListener('click', () => task($('inspectBtn'), inspect, 'importError'));
$('importForm').addEventListener('submit', (event) => {
  event.preventDefault();
  if (!state.previewValid) return;
  task(
    $('importSubmit'),
    async () => {
      const result = await api('/api/datasets', 'POST', {
        name: $('importName').value,
        csv: $('csvInput').value,
        types: state.importTypes,
      });
      await refreshLists();
      $('importDialog').close();
      await selectDataset(result.dataset.id);
      toast(
        result.reused
          ? 'Tệp trùng nội dung: đã mở tập dữ liệu hiện có.'
          : 'Đã nhập và lưu tập dữ liệu.',
      );
    },
    'importError',
  );
});
$('saveReportBtn').addEventListener('click', () => {
  $('reportName').value =
    state.report?.name ||
    (
      current().name +
      ' — ' +
      (state.result.definition.aggregate?.fn.toUpperCase() || 'Bảng lọc')
    ).slice(0, 120);
  $('saveAsNew').checked = false;
  $('saveAsLabel').hidden = !state.report;
  $('reportDialogTitle').textContent = state.report ? 'Cập nhật báo cáo' : 'Lưu báo cáo';
  setError('reportError', null);
  $('reportDialog').showModal();
});
$('reportForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('reportSubmit'),
    async () => {
      const update = state.report && !$('saveAsNew').checked;
      const result = await api(
        '/api/reports' + (update ? '/' + state.report.id : ''),
        update ? 'PUT' : 'POST',
        {
          name: $('reportName').value,
          definition: state.result.definition,
          ...(update ? { version: state.report.version } : {}),
        },
      );
      state.report = result.report;
      await refreshLists();
      $('reportDialog').close();
      renderReportContext();
      toast(update ? 'Đã cập nhật báo cáo.' : 'Đã lưu báo cáo. Mở lại trong “Báo cáo đã lưu”.');
    },
    'reportError',
  );
});
async function boot() {
  const workspace = await api('/api/workspace');
  state.csrf = workspace.csrf;
  await refreshLists();
  if (state.datasets.length) await selectDataset(state.datasets[0].id);
  else updateControls();
}
task(null, boot);
