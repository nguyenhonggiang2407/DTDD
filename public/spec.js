import {
  $,
  el,
  button,
  api,
  init,
  run,
  toast,
  empty,
  stat,
  clean,
  showDialog,
  closeDialog,
  confirmDelete,
  history,
  exportLink,
} from './shared.js';
let items = [],
  selected = null,
  editingContract = null,
  editingEndpoint = null,
  endpointRecord = null;
const form = $('#contract-form'),
  endpointForm = $('#endpoint-form');
function current() {
  return items.find((item) => item.id === selected);
}
async function refresh() {
  items = (await api('/api/spec/items')).items;
  if (!items.some((i) => i.id === selected)) selected = items[0]?.id ?? null;
  render();
}
async function demo() {
  await run(async () => {
    const result = await api('/api/spec/demo', 'POST');
    await refresh();
    toast(result.added ? 'Đã thêm Library API mẫu.' : 'Bản thiết kế mẫu đã có.');
  });
}
function render() {
  const endpoints = items.flatMap((i) => i.data.endpoints);
  stat('stat-contracts', items.length);
  stat('stat-endpoints', endpoints.length);
  stat('stat-methods', new Set(endpoints.map((e) => e.method)).size);
  const q = clean($('#search').value.trim()),
    list = $('#contract-list');
  list.replaceChildren();
  const filtered = items.filter((item) => !q || clean(JSON.stringify(item.data)).includes(q));
  stat('contract-count', filtered.length);
  if (!filtered.length)
    list.append(
      empty(
        'Bắt đầu từ một endpoint rõ ràng.',
        'Tạo bản thiết kế hoặc thử một API thư viện giả lập.',
        !items.length,
        demo,
      ),
    );
  for (const item of filtered) {
    const card = button('', 'item-card' + (item.id === selected ? ' selected' : ''), () => {
      selected = item.id;
      render();
    });
    card.setAttribute('aria-label', 'Mở ' + item.data.title);
    const top = el('div', 'card-top');
    top.append(
      el('span', 'pill neutral', item.data.release),
      el('span', '', item.data.endpoints.length + ' endpoint'),
    );
    card.append(
      top,
      el('h3', '', item.data.title),
      el('p', '', item.data.description.slice(0, 100) || 'Chưa ghi mô tả.'),
    );
    const bottom = el('div', 'card-bottom');
    bottom.append(el('span', '', 'OpenAPI 3.1'), el('span', '', 'Bản lưu ' + item.version));
    card.append(bottom);
    list.append(card);
  }
  renderDetail();
}
function renderDetail() {
  const area = $('#contract-detail');
  area.replaceChildren();
  const record = current();
  if (!record) {
    area.append(
      empty(
        'Để người khác hiểu API của bạn.',
        'Mô tả đường dẫn, ví dụ dữ liệu và phản hồi, rồi xuất tài liệu để chia sẻ.',
      ),
    );
    return;
  }
  const d = record.data,
    panel = el('div', 'panel'),
    head = el('div', 'panel-head');
  head.append(
    el('h2', '', d.title),
    el(
      'p',
      'muted',
      'API ' + d.release + ' · ' + d.endpoints.length + ' endpoint · Bản lưu ' + record.version,
    ),
  );
  const tools = el('div', 'panel-tools');
  tools.append(
    button('Thông tin API', 'button small', () => openContract(record)),
    button('＋ Endpoint', 'button small primary', () => openEndpoint(record)),
    button('Xem OpenAPI', 'button small', () => run(() => previewOpenAPI(record))),
    exportLink('spec', record, 'json', '↓ JSON'),
    exportLink('spec', record, 'md', '↓ Markdown'),
    button('Phiên bản', 'button small', () => run(() => history('spec', record))),
    button('Xóa', 'button small danger', () =>
      confirmDelete(d.title, async () => {
        await api('/api/spec/items/' + record.id, 'DELETE', { version: record.version });
        await refresh();
      }),
    ),
  );
  head.append(tools);
  panel.append(head);
  const body = el('div', 'panel-content');
  if (d.description) body.append(el('p', 'summary', d.description), el('hr', 'section-divider'));
  body.append(el('h3', '', 'Các endpoint'));
  if (!d.endpoints.length)
    body.append(
      el('p', 'muted', 'Chưa có endpoint. Nhấn “＋ Endpoint” để mô tả thao tác đầu tiên.'),
    );
  for (const e of d.endpoints) {
    const detail = el('details', 'endpoint'),
      summary = el('summary');
    summary.append(
      el('span', 'method ' + e.method, e.method),
      el('code', '', e.path),
      el('span', '', e.status),
    );
    detail.append(summary);
    const content = el('div', 'endpoint-body');
    content.append(el('p', 'summary', e.summary));
    for (const [label, value] of [
      ['Request mẫu', e.request],
      ['Response mẫu', e.response],
    ]) {
      if (value !== null)
        content.append(
          el('h4', '', label),
          el('pre', 'code', JSON.stringify(JSON.parse(value), null, 2)),
        );
    }
    const actions = el('div', 'endpoint-tools');
    actions.append(
      button('Chỉnh sửa endpoint', 'button small', () => openEndpoint(record, e)),
      button('Xóa endpoint', 'button small danger', () =>
        confirmDelete(
          e.method + ' ' + e.path,
          async () => {
            await api('/api/spec/items/' + record.id, 'PUT', {
              version: record.version,
              data: {
                ...record.data,
                endpoints: record.data.endpoints.filter((item) => item.id !== e.id),
              },
            });
            await refresh();
          },
          'Endpoint được xóa khỏi bản thiết kế. Phiên bản cũ vẫn được giữ trong lịch sử.',
        ),
      ),
    );
    content.append(actions);
    detail.append(content);
    body.append(detail);
  }
  panel.append(body);
  area.append(panel);
}
function openContract(record = null) {
  editingContract = record;
  form.reset();
  $('#contract-dialog-title').textContent = record ? 'Thông tin API' : 'Tạo bản thiết kế';
  for (const key of ['title', 'release', 'description'])
    form.elements[key].value = record?.data[key] || (key === 'release' ? '1.0.0' : '');
  showDialog('contract-dialog');
}
function openEndpoint(record, endpoint = null) {
  endpointRecord = record;
  editingEndpoint = endpoint;
  endpointForm.reset();
  $('#endpoint-dialog-title').textContent = endpoint ? 'Chỉnh sửa endpoint' : 'Thêm endpoint';
  const data = endpoint || {
    method: 'GET',
    path: '',
    summary: '',
    status: 200,
    request: '',
    response: '',
  };
  for (const key of ['method', 'path', 'summary', 'status', 'request', 'response'])
    endpointForm.elements[key].value = data[key] ?? '';
  showDialog('endpoint-dialog');
}
async function previewOpenAPI(record) {
  const doc = await api('/api/spec/items/' + record.id + '/export?format=json');
  $('#openapi-preview').textContent = JSON.stringify(doc, null, 2);
  $('#openapi-download').replaceChildren(exportLink('spec', record, 'json', '↓ Tải OpenAPI JSON'));
  showDialog('openapi-dialog');
}
form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const save = $('#save-contract');
  save.disabled = true;
  await run(async () => {
    const data = Object.fromEntries(new FormData(form));
    data.endpoints = editingContract?.data.endpoints || [];
    const record = editingContract
      ? await api('/api/spec/items/' + editingContract.id, 'PUT', {
          version: editingContract.version,
          data,
        })
      : await api('/api/spec/items', 'POST', { data });
    selected = record.id;
    closeDialog('contract-dialog');
    await refresh();
    toast('Đã lưu bản thiết kế API.');
  });
  save.disabled = false;
});
endpointForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const save = $('#save-endpoint');
  save.disabled = true;
  await run(async () => {
    const endpoint = Object.fromEntries(new FormData(endpointForm));
    endpoint.id = editingEndpoint?.id || crypto.randomUUID();
    endpoint.status = Number(endpoint.status);
    const endpoints = editingEndpoint
      ? endpointRecord.data.endpoints.map((e) => (e.id === editingEndpoint.id ? endpoint : e))
      : [...endpointRecord.data.endpoints, endpoint];
    await api('/api/spec/items/' + endpointRecord.id, 'PUT', {
      version: endpointRecord.version,
      data: { ...endpointRecord.data, endpoints },
    });
    closeDialog('endpoint-dialog');
    await refresh();
    toast('Đã lưu endpoint.');
  });
  save.disabled = false;
});
$('#new-contract').addEventListener('click', () => openContract());
$('#load-demo').addEventListener('click', demo);
$('#refresh').addEventListener('click', () => run(refresh));
$('#search').addEventListener('input', render);
run(async () => {
  await init('spec');
  await refresh();
});
