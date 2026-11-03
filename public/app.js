const $ = (id) => document.getElementById(id);
const state = {
  csrf: '',
  endpoints: [],
  currentId: null,
  events: [],
  busy: new Set(),
  loadSeq: 0,
  event: null,
  renameTarget: null,
};
const sample = {
  type: 'order.created',
  order: { id: 'ORDER-001', items: 3, total: 245000 },
  note: 'Dữ liệu giả lập của YangHooks',
};
const time = (value) =>
  new Date(value).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
const date = (value) => new Date(value).toLocaleString('vi-VN');
const number = (value) => new Intl.NumberFormat('vi-VN').format(value);
function node(tag, className, text) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== undefined) el.textContent = String(text);
  return el;
}
function current() {
  return state.endpoints.find((e) => e.id === state.currentId);
}
let toastTimer, searchTimer;
function toast(message, bad = false) {
  clearTimeout(toastTimer);
  $('toast').textContent = message;
  $('toast').classList.toggle('bad', bad);
  $('toast').hidden = false;
  toastTimer = setTimeout(() => ($('toast').hidden = true), bad ? 8000 : 4000);
}
function error(id, value) {
  if (!id) return;
  $(id).textContent = value?.message || '';
  $(id).hidden = !value;
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
    if (response.status === 401) $('expired').hidden = false;
    const data = await response.json().catch(() => ({}));
    const failure = new Error(data.error || 'Không thể kết nối. Hãy thử lại.');
    failure.status = response.status;
    throw failure;
  }
  if (download) return response.blob();
  return response.status === 204 ? null : response.json();
}
async function task(button, action, errorId) {
  if (button && state.busy.has(button)) return;
  if (button) {
    state.busy.add(button);
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
  }
  error(errorId, null);
  try {
    return await action();
  } catch (failure) {
    if (errorId) error(errorId, failure);
    else toast(failure.message, true);
    if (failure.status === 409) await refresh().catch(() => {});
  } finally {
    if (button) {
      state.busy.delete(button);
      button.disabled = false;
      button.removeAttribute('aria-busy');
    }
    controls();
  }
}
function controls() {
  document
    .querySelectorAll('[data-create]')
    .forEach((button) => (button.disabled = !state.csrf || state.busy.has(button)));
  $('demoBtn').disabled = !state.csrf || state.busy.has($('demoBtn'));
  $('sendBtn').disabled = !current() || state.busy.has($('sendBtn'));
}
function stats() {
  $('statEndpoints').textContent = state.endpoints.length;
  $('statReceived').textContent = number(state.endpoints.reduce((n, e) => n + e.total, 0));
  $('statDuplicates').textContent = number(state.endpoints.reduce((n, e) => n + e.duplicates, 0));
  $('empty').hidden = state.endpoints.length > 0;
  $('workbench').hidden = !current();
}
function renderEndpoints() {
  const list = $('endpointList');
  list.replaceChildren();
  for (const endpoint of state.endpoints) {
    const button = node(
      'button',
      'endpoint-nav' + (endpoint.id === state.currentId ? ' active' : ''),
    );
    button.append(
      node('span', '', endpoint.enabled ? '⌁' : 'Ⅱ'),
      node('span', 'name', endpoint.name),
      node('small', '', endpoint.total),
    );
    button.title = endpoint.name;
    button.addEventListener('click', () => task(button, () => select(endpoint.id)));
    list.append(button);
  }
  if (!state.endpoints.length) list.append(node('p', 'muted', 'Chưa có endpoint.'));
  $('endpointSelect').replaceChildren(...state.endpoints.map((e) => new Option(e.name, e.id)));
  if (state.currentId) $('endpointSelect').value = state.currentId;
}
function metadata() {
  const e = current();
  if (!e) return;
  $('endpointName').textContent = e.name;
  $('endpointMeta').textContent =
    'Tạo ' + date(e.created) + ' · Cấu hình v' + e.version + ' · ' + e.total + '/100 lượt nhận';
  $('endpointUrl').value = location.origin + e.path;
  $('endpointState').textContent = e.enabled ? 'ĐANG NHẬN' : 'TẠM DỪNG';
  $('endpointState').classList.toggle('paused', !e.enabled);
  $('pauseBtn').textContent = e.enabled ? 'Ⅱ Tạm dừng' : '▷ Tiếp tục';
}
function renderEvents() {
  const list = $('eventList');
  list.replaceChildren();
  if (!state.events.length) {
    list.append(
      node(
        'p',
        'inbox-empty',
        $('search').value
          ? 'Không có lượt nhận khớp tìm kiếm.'
          : 'Hộp thư đang chờ.\nKý và gửi một request ở bảng bên cạnh.',
      ),
    );
    return;
  }
  for (const event of state.events) {
    const button = node('button', 'event-row'),
      main = node('div', 'event-main'),
      end = node('div', 'event-time');
    main.append(
      node('strong', '', event.eventType),
      node('small', '', event.deliveryId + ' · ' + number(event.bytes) + ' B'),
      node('div', 'event-preview', event.preview),
    );
    end.append(
      node('span', '', time(event.received)),
      node('b', '', event.duplicates ? '↻ ' + event.duplicates + ' lần trùng' : '✓ VERIFIED'),
    );
    button.append(node('span', 'event-icon', '↓'), main, end);
    button.addEventListener('click', () => task(button, () => openEvent(event.id)));
    list.append(button);
  }
}
async function loadEvents() {
  const endpoint = current();
  if (!endpoint) {
    state.events = [];
    renderEvents();
    return;
  }
  const sequence = ++state.loadSeq,
    result = await api(
      '/api/endpoints/' + endpoint.id + '/events?q=' + encodeURIComponent($('search').value),
    );
  if (sequence !== state.loadSeq || state.currentId !== endpoint.id) return;
  state.events = result.events;
  renderEvents();
}
async function refresh() {
  const result = await api('/api/endpoints');
  state.endpoints = result.endpoints;
  if (!state.endpoints.some((e) => e.id === state.currentId))
    state.currentId = state.endpoints[0]?.id || null;
  renderEndpoints();
  stats();
  metadata();
  await loadEvents();
  controls();
}
async function select(id) {
  state.currentId = id;
  state.loadSeq++;
  $('search').value = '';
  $('sendBody').value = JSON.stringify(sample, null, 2);
  $('deliveryId').value = crypto.randomUUID();
  $('sendMode').value = 'valid';
  $('sendResult').hidden = true;
  error('sendError', null);
  bytes();
  renderEndpoints();
  stats();
  metadata();
  await loadEvents();
}
function keyDialog(secret, name) {
  $('newSecret').value = secret;
  $('keyInfo').textContent =
    'Khóa mới của “' + name + '”. Máy chủ sẽ không trả lại khóa này qua API đọc.';
  $('keyDialog').showModal();
}
function createDialog() {
  $('createForm').reset();
  error('createError', null);
  $('createDialog').showModal();
}
function renderReceipt(result) {
  $('sendResult').hidden = false;
  $('sendResult').classList.toggle('bad', result.httpStatus >= 400);
  $('httpCode').textContent = 'HTTP ' + result.httpStatus;
  $('receiptLabel').textContent =
    result.httpStatus >= 400
      ? 'ĐÃ TỪ CHỐI'
      : result.response.replayed
        ? 'GỬI TRÙNG · CÙNG RECEIPT'
        : 'ĐÃ NHẬN · CHỮ KÝ HỢP LỆ';
  $('receiptJson').textContent = JSON.stringify(result.response, null, 2);
}
function bytes() {
  $('bodyBytes').textContent =
    number(new TextEncoder().encode($('sendBody').value).length) + ' B / 65.536 B';
}
let confirmAction;
function confirm(title, message, action) {
  confirmAction = action;
  $('confirmTitle').textContent = title;
  $('confirmMessage').textContent = message;
  error('confirmError', null);
  $('confirmDialog').showModal();
}
async function openEvent(id) {
  const result = await api('/api/events/' + id),
    e = result.event;
  state.event = e;
  $('eventTitle').textContent = e.eventType;
  $('eventInfo').textContent =
    'Nhận ' + date(e.received) + ' · ' + number(e.bytes) + ' byte · Chữ ký đã được kiểm tra';
  $('eventRaw').textContent = e.raw;
  $('eventHeaders').textContent = JSON.stringify(e.headers, null, 2);
  $('eventFacts').replaceChildren();
  for (const [key, value] of [
    ['Request ID', e.id],
    ['Delivery ID', e.deliveryId],
    ['SHA256 body', e.checksum],
    ['Gửi trùng', e.duplicates],
    ['Lần thấy cuối', date(e.lastSeen)],
  ]) {
    $('eventFacts').append(node('dt', '', key), node('dd', '', value));
  }
  $('eventDialog').showModal();
}
function download(blob, name) {
  const url = URL.createObjectURL(blob),
    a = node('a');
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
document
  .querySelectorAll('[data-create]')
  .forEach((button) => button.addEventListener('click', createDialog));
document
  .querySelectorAll('[data-close]')
  .forEach((button) => button.addEventListener('click', () => $(button.dataset.close).close()));
$('keyDialog').addEventListener('close', () => {
  $('newSecret').value = '';
  document.querySelector('.hero-actions [data-create]').focus({ preventScroll: true });
});
$('createForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('createSubmit'),
    async () => {
      const result = await api('/api/endpoints', 'POST', { name: $('createName').value });
      $('createDialog').close();
      await refresh();
      await select(result.endpoint.id);
      keyDialog(result.secret, result.endpoint.name);
      toast('Endpoint đã sẵn sàng. Lưu khóa mới trước khi đóng.');
    },
    'createError',
  );
});
$('endpointSelect').addEventListener('change', () =>
  task(null, () => select($('endpointSelect').value)),
);
$('demoBtn').addEventListener('click', () =>
  task($('demoBtn'), async () => {
    let endpoint = state.endpoints.find((e) => e.name === 'Đơn hàng mẫu'),
      secret;
    if (!endpoint) {
      const result = await api('/api/endpoints', 'POST', { name: 'Đơn hàng mẫu' });
      endpoint = result.endpoint;
      secret = result.secret;
    }
    for (const [index, data] of [
      sample,
      {
        type: 'payment.confirmed',
        payment: { order: 'ORDER-001', amount: 245000 },
        note: 'Dữ liệu giả lập',
      },
    ].entries()) {
      const result = await api('/api/endpoints/' + endpoint.id + '/send', 'POST', {
        raw: JSON.stringify(data, null, 2),
        deliveryId: 'demo-v1-delivery-' + index,
      });
      if (result.httpStatus >= 400)
        throw new Error(result.response.error || 'Endpoint mẫu chưa nhận được request.');
    }
    await refresh();
    await select(endpoint.id);
    toast('Đã gửi 2 request mẫu bằng HTTP thật.');
    if (secret) keyDialog(secret, endpoint.name);
  }),
);
$('refreshBtn').addEventListener('click', () => task($('refreshBtn'), refresh));
$('search').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => task(null, loadEvents), 200);
});
$('newDeliveryBtn').addEventListener('click', () => {
  $('deliveryId').value = crypto.randomUUID();
});
$('sendBody').addEventListener('input', bytes);
$('sendForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('sendBtn'),
    async () => {
      const id = state.currentId,
        result = await api('/api/endpoints/' + id + '/send', 'POST', {
          raw: $('sendBody').value,
          deliveryId: $('deliveryId').value,
          mode: $('sendMode').value,
        });
      if (id === state.currentId) renderReceipt(result);
      await refresh();
    },
    'sendError',
  );
});
$('copyUrlBtn').addEventListener('click', () =>
  task($('copyUrlBtn'), async () => {
    try {
      await navigator.clipboard.writeText($('endpointUrl').value);
      toast('Đã sao chép URL.');
    } catch {
      $('endpointUrl').select();
      toast('URL đã chọn. Nhấn Ctrl+C để sao chép.');
    }
  }),
);
$('pauseBtn').addEventListener('click', () => {
  const endpoint = { ...current() };
  task($('pauseBtn'), async () => {
    await api('/api/endpoints/' + endpoint.id, 'PATCH', {
      enabled: !endpoint.enabled,
      version: endpoint.version,
    });
    await refresh();
    toast(endpoint.enabled ? 'Đã tạm dừng nhận webhook.' : 'Endpoint đã nhận webhook trở lại.');
  });
});
$('rotateBtn').addEventListener('click', () => {
  const endpoint = { ...current() };
  confirm(
    'Xoay khóa HMAC?',
    'Chữ ký bằng khóa cũ sẽ mất hiệu lực ngay. Lịch sử lượt nhận vẫn được giữ.',
    async () => {
      const result = await api('/api/endpoints/' + endpoint.id + '/rotate', 'POST', {
        version: endpoint.version,
      });
      await refresh();
      $('confirmDialog').close();
      keyDialog(result.secret, result.endpoint.name);
    },
  );
});
$('deleteBtn').addEventListener('click', () => {
  const endpoint = { ...current() };
  confirm(
    'Xóa endpoint?',
    'Xóa “' +
      endpoint.name +
      '” và toàn bộ ' +
      endpoint.total +
      ' receipt liên quan. Bạn có thể xuất JSON trước khi xóa.',
    async () => {
      await api('/api/endpoints/' + endpoint.id, 'DELETE', { version: endpoint.version });
      await refresh();
      if (current()) await select(state.currentId);
      toast('Đã xóa endpoint và lịch sử.');
    },
  );
});
$('confirmSubmit').addEventListener('click', () =>
  task(
    $('confirmSubmit'),
    async () => {
      await confirmAction();
      $('confirmDialog').close();
    },
    'confirmError',
  ),
);
$('renameBtn').addEventListener('click', () => {
  state.renameTarget = { ...current() };
  $('renameName').value = current().name;
  error('renameError', null);
  $('renameDialog').showModal();
});
$('renameForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('renameSubmit'),
    async () => {
      const endpoint = state.renameTarget;
      await api('/api/endpoints/' + endpoint.id, 'PATCH', {
        name: $('renameName').value,
        version: endpoint.version,
      });
      await refresh();
      $('renameDialog').close();
      toast('Đã cập nhật tên endpoint.');
    },
    'renameError',
  );
});
$('exportBtn').addEventListener('click', () =>
  task($('exportBtn'), async () =>
    download(
      await api('/api/endpoints/' + state.currentId + '/export', 'GET', undefined, true),
      'yanghooks-events.json',
    ),
  ),
);
$('downloadEventBtn').addEventListener('click', () =>
  download(
    new Blob([JSON.stringify({ protocol: 'yanghooks-v1', event: state.event }, null, 2)], {
      type: 'application/json',
    }),
    'yanghooks-receipt.json',
  ),
);
async function boot() {
  const workspace = await api('/api/workspace');
  state.csrf = workspace.csrf;
  await refresh();
  if (current()) await select(state.currentId);
  controls();
}
task(null, boot);
setInterval(() => {
  if (!document.hidden && state.csrf)
    refresh().catch((failure) => {
      if (failure.status === 401) $('expired').hidden = false;
    });
}, 4000);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.csrf) refresh().catch(() => {});
});
