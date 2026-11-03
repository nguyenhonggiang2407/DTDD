const $ = (id) => document.getElementById(id);
const state = {
  csrf: '',
  resources: [],
  bookings: [],
  slots: [],
  currentId: null,
  selectedStart: null,
  requestKey: crypto.randomUUID(),
  unknown: false,
  hold: null,
  replayed: false,
  busy: new Set(),
  loading: false,
  seq: 0,
  serverOffset: 0,
  today: '',
  maxDay: '',
  target: null,
};
const labels = {
  HELD: 'Đang giữ chỗ',
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
};
const fmtTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const fmtDate = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const fmtDay = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  weekday: 'long',
  day: 'numeric',
  month: 'numeric',
});
function node(tag, className, text) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== undefined) el.textContent = String(text);
  return el;
}
const resource = () => state.resources.find((r) => r.id === state.currentId);
const slot = () => state.slots.find((s) => s.startsAt === state.selectedStart);
const validDay = () =>
  /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test($('dayInput').value) &&
  $('dayInput').value >= state.today &&
  $('dayInput').value <= state.maxDay;
const serverNow = () => Date.now() + state.serverOffset;
const effective = (booking) =>
  booking.status === 'HELD' && Date.parse(booking.expiresAt) <= serverNow()
    ? 'EXPIRED'
    : booking.status;
const shiftDay = (day, amount) =>
  new Date(Date.parse(day + 'T00:00:00Z') + amount * 86400000).toISOString().slice(0, 10);
const dayText = (day) => fmtDay.format(new Date(day + 'T05:00:00Z'));
function syncTime(value) {
  if (typeof value === 'number') state.serverOffset = value - Date.now();
}
function setError(id, e) {
  if (!id) return;
  $(id).hidden = !e;
  $(id).textContent = e?.message || '';
}
let toastTimer;
function toast(message, bad = false) {
  clearTimeout(toastTimer);
  $('toast').textContent = message;
  $('toast').classList.toggle('bad', bad);
  $('toast').hidden = false;
  toastTimer = setTimeout(() => ($('toast').hidden = true), bad ? 8000 : 4000);
}
async function api(path, method = 'GET', body, { headers = {}, download = false } = {}) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: {
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(method === 'GET' ? {} : { 'X-CSRF-Token': state.csrf }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    const failure = new Error('Chưa nhận được phản hồi từ máy chủ. Hãy thử lại.');
    failure.network = true;
    throw failure;
  }
  if (!response.ok) {
    if (response.status === 401) $('expired').hidden = false;
    const problem = await response.json().catch(() => ({})),
      failure = new Error(problem.error || 'Không thể xử lý yêu cầu. Hãy thử lại.');
    failure.status = response.status;
    throw failure;
  }
  if (download) return response.blob();
  if (response.status === 204) return null;
  const result = await response.json();
  syncTime(result.serverTime);
  return result;
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
    else toast(error.message, true);
    if (error.status === 409 || error.status === 410) {
      await refresh().catch(() => {});
      if ($('reservationDialog').open && state.hold) {
        await api('/api/reservations/' + state.hold.id)
          .then((data) => {
            state.hold = data.reservation;
            renderReservation();
          })
          .catch(() => {});
      }
    }
  } finally {
    if (button) {
      state.busy.delete(button);
      button.disabled = false;
      button.removeAttribute('aria-busy');
    }
    controls();
  }
}
function newIntent(clearSlot = false) {
  state.requestKey = crypto.randomUUID();
  state.unknown = false;
  if (clearSlot) state.selectedStart = null;
  $('unknownNote').hidden = true;
  setError('holdError', null);
  controls();
}
function validUnits() {
  const value = Number($('units').value);
  return resource() && Number.isInteger(value) && value >= 1 && value <= resource().capacity;
}
function controls() {
  document
    .querySelectorAll('[data-create]')
    .forEach((button) => (button.disabled = !state.csrf || state.busy.has(button)));
  $('demoBtn').disabled = !state.csrf || state.busy.has($('demoBtn'));
  $('holdBtn').disabled =
    !validDay() ||
    !resource() ||
    !slot() ||
    !validUnits() ||
    (!slot()?.canHold && !state.unknown) ||
    state.loading ||
    state.busy.has($('holdBtn'));
  $('holdBtn').textContent = state.unknown ? '↻ Nhận lại kết quả' : '◷ Giữ chỗ 5 phút';
  $('unknownNote').hidden = !state.unknown;
  $('prevDay').disabled = !validDay() || $('dayInput').value <= state.today;
  $('nextDay').disabled = !validDay() || $('dayInput').value >= state.maxDay;
}
function renderResources() {
  $('resourceList').replaceChildren();
  for (const r of state.resources) {
    const button = node('button', 'resource-nav' + (r.id === state.currentId ? ' active' : ''));
    button.append(
      node('span', '', '▦'),
      node('span', 'name', r.name),
      node('small', '', r.capacity),
    );
    button.title = r.name;
    button.addEventListener('click', () => task(button, () => selectResource(r.id)));
    $('resourceList').append(button);
  }
  if (!state.resources.length) $('resourceList').append(node('p', 'muted', 'Chưa có tài nguyên.'));
  $('resourceSelect').replaceChildren(...state.resources.map((r) => new Option(r.name, r.id)));
  if (state.currentId) $('resourceSelect').value = state.currentId;
  const r = resource();
  $('empty').hidden = state.resources.length > 0;
  $('workbench').hidden = !r;
  if (r) {
    $('resourceName').textContent = r.name;
    $('resourceMeta').textContent = r.capacity + ' đơn vị đặt đồng thời · 08:00–20:00, UTC+7';
    $('units').max = r.capacity;
  }
}
function renderStats() {
  $('statResources').textContent = state.resources.length;
  $('statConfirmed').textContent = state.bookings.filter(
    (b) => effective(b) === 'CONFIRMED',
  ).length;
  $('statHeld').textContent = state.bookings.filter((b) => effective(b) === 'HELD').length;
}
function renderSlots() {
  const grid = $('slotGrid');
  grid.replaceChildren();
  $('dayCaption').textContent = dayText($('dayInput').value || state.today);
  $('slotSummary').textContent = state.loading
    ? 'Đang cập nhật…'
    : state.slots.filter((s) => s.canHold).length + ' khung còn đủ chỗ';
  for (const s of state.slots) {
    const time = fmtTime.format(new Date(s.startsAt)),
      button = node('button', 'slot' + (s.startsAt === state.selectedStart ? ' selected' : ''));
    button.type = 'button';
    button.dataset.time = time;
    button.dataset.start = s.startsAt;
    button.disabled = state.loading || !s.canHold;
    button.setAttribute(
      'aria-label',
      time + ' — ' + (s.canHold ? 'còn ' + s.availableUnits + ' đơn vị' : 'không thể đặt'),
    );
    button.setAttribute('aria-pressed', String(s.startsAt === state.selectedStart));
    button.append(
      node('strong', '', time),
      node('small', '', s.canHold ? s.availableUnits + ' còn trống' : 'Không thể đặt'),
    );
    button.addEventListener('click', () => {
      state.selectedStart = s.startsAt;
      newIntent();
      renderSlots();
      selectedInfo();
    });
    grid.append(button);
  }
  selectedInfo();
  controls();
}
function selectedInfo() {
  const s = slot(),
    box = $('selectedInfo');
  box.replaceChildren();
  if (!s) {
    box.append(
      node('span', '', 'CHƯA CHỌN GIỜ'),
      node('strong', '', 'Chọn một ô giờ bên cạnh'),
      node('small', '', 'Khung còn đủ đơn vị sẽ cho phép giữ chỗ.'),
    );
    return;
  }
  box.append(
    node('span', '', 'KHUNG GIỜ ĐÃ CHỌN'),
    node(
      'strong',
      '',
      fmtTime.format(new Date(s.startsAt)) + ' – ' + fmtTime.format(new Date(s.endsAt)),
    ),
    node(
      'small',
      '',
      fmtDate.format(new Date(s.startsAt)) +
        ' · ' +
        $('units').value +
        ' đơn vị · ' +
        resource().name,
    ),
  );
}
function filteredBookings() {
  const status = $('statusFilter').value,
    q = $('search').value.trim().toLowerCase();
  return state.bookings.filter(
    (b) =>
      (!status || effective(b) === status) &&
      (!q || b.title.toLowerCase().includes(q) || b.resourceName.toLowerCase().includes(q)),
  );
}
function renderBookings() {
  const rows = filteredBookings();
  $('bookingList').replaceChildren();
  $('listMeta').textContent =
    rows.length +
    ' lượt đặt · ' +
    ($('allDays').checked
      ? 'Tất cả ngày'
      : validDay()
        ? dayText($('dayInput').value)
        : 'Chọn ngày hợp lệ');
  if (!rows.length) {
    $('bookingList').append(node('p', 'booking-empty', 'Chưa có lượt đặt khớp bộ lọc.'));
    return;
  }
  for (const b of rows) {
    const button = node('button', 'booking-row'),
      content = node('div'),
      time = node('div', 'row-time'),
      status = effective(b);
    content.append(
      node('strong', '', b.title),
      node('small', '', b.resourceName + ' · ' + b.units + ' đơn vị'),
    );
    time.append(
      node(
        'span',
        '',
        fmtTime.format(new Date(b.startsAt)) + '–' + fmtTime.format(new Date(b.endsAt)),
      ),
      node('small', '', fmtDate.format(new Date(b.startsAt))),
    );
    button.append(
      node('span', 'row-icon', '▦'),
      content,
      time,
      node('span', 'status ' + status.toLowerCase(), labels[status]),
    );
    button.addEventListener('click', () => task(button, () => openReservation(b.id)));
    $('bookingList').append(button);
  }
}
async function refresh() {
  const sequence = ++state.seq;
  state.loading = true;
  controls();
  if (state.slots.length) renderSlots();
  try {
    const data = await api('/api/resources');
    if (sequence !== state.seq) return;
    state.resources = data.resources;
    if (!resource()) {
      state.currentId = state.resources[0]?.id || null;
      state.selectedStart = null;
      state.unknown = false;
    }
    renderResources();
    const r = resource();
    if (!r) {
      state.slots = [];
      state.bookings = [];
      renderStats();
      return;
    }
    if (!validDay() || !validUnits()) {
      state.slots = [];
      state.bookings = [];
      renderBookings();
      renderStats();
      return;
    }
    const query = new URLSearchParams({
      resourceId: r.id,
      day: $('dayInput').value,
      minutes: $('minutes').value,
      units: $('units').value,
    });
    const listQuery = new URLSearchParams({ resourceId: r.id });
    if (!$('allDays').checked) listQuery.set('day', $('dayInput').value);
    const [availability, bookings] = await Promise.all([
      api('/api/availability?' + query),
      api('/api/reservations?' + listQuery),
    ]);
    if (sequence !== state.seq || state.currentId !== r.id) return;
    state.slots = availability.slots;
    state.bookings = bookings.reservations;
    renderBookings();
    renderStats();
  } finally {
    if (sequence === state.seq) {
      state.loading = false;
      if (resource()) renderSlots();
      controls();
    }
  }
}
async function selectResource(id) {
  state.currentId = id;
  $('units').value = '1';
  state.slots = [];
  newIntent(true);
  $('search').value = '';
  $('statusFilter').value = '';
  await refresh();
}
function renderReservation() {
  const b = state.hold;
  if (!b) return;
  const status = effective(b);
  $('dialogTitle').textContent = b.title;
  $('dialogStatus').textContent = labels[status];
  $('dialogStatus').className = 'status ' + status.toLowerCase();
  $('replayedNotice').hidden = !state.replayed;
  $('dialogFacts').replaceChildren();
  for (const [key, value] of [
    ['Tài nguyên', b.resourceName],
    ['Ngày', fmtDate.format(new Date(b.startsAt))],
    [
      'Khung giờ',
      fmtTime.format(new Date(b.startsAt)) + ' – ' + fmtTime.format(new Date(b.endsAt)),
    ],
    ['Số đơn vị', b.units],
  ])
    $('dialogFacts').append(node('dt', '', key), node('dd', '', value));
  $('countdownBox').hidden = status !== 'HELD';
  $('confirmBookingBtn').hidden = status !== 'HELD';
  $('confirmBookingBtn').disabled = status !== 'HELD' || state.busy.has($('confirmBookingBtn'));
  $('cancelBookingBtn').hidden = !['HELD', 'CONFIRMED'].includes(status);
  $('cancelBookingBtn').disabled = state.busy.has($('cancelBookingBtn'));
  $('closeFinalBtn').hidden = status === 'HELD';
  if (status === 'HELD') {
    const seconds = Math.max(0, Math.ceil((Date.parse(b.expiresAt) - serverNow()) / 1000));
    $('countdown').textContent =
      String(Math.floor(seconds / 60)).padStart(2, '0') +
      ':' +
      String(seconds % 60).padStart(2, '0');
  }
  $('dialogMessage').textContent =
    status === 'HELD'
      ? 'Đóng cửa sổ vẫn giữ chỗ đến thời hạn. Bạn có thể mở lại từ danh sách.'
      : status === 'CONFIRMED'
        ? 'Lượt đặt đã hoàn tất. Bạn có thể tải lịch ICS từ danh sách.'
        : status === 'EXPIRED'
          ? 'Thời hạn đã hết và chỗ được trả lại. Hãy chọn một khung còn chỗ để tạo lượt mới.'
          : 'Lượt đặt đã hủy. Chỗ đã được trả lại lịch.';
}
async function openReservation(id) {
  const data = await api('/api/reservations/' + id);
  state.hold = data.reservation;
  state.replayed = false;
  setError('reservationError', null);
  renderReservation();
  $('reservationDialog').showModal();
}
function createResourceDialog() {
  $('createForm').reset();
  $('createCapacity').value = '1';
  setError('createError', null);
  $('createDialog').showModal();
}
function download(blob, name) {
  const url = URL.createObjectURL(blob),
    link = node('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
function exportQuery(csv) {
  const params = new URLSearchParams({ resourceId: state.currentId });
  if (!$('allDays').checked) params.set('day', $('dayInput').value);
  if (csv) {
    if ($('statusFilter').value) params.set('status', $('statusFilter').value);
    if ($('search').value.trim()) params.set('q', $('search').value.trim());
  }
  return params;
}
document
  .querySelectorAll('[data-create]')
  .forEach((button) => button.addEventListener('click', createResourceDialog));
document
  .querySelectorAll('[data-close]')
  .forEach((button) => button.addEventListener('click', () => $(button.dataset.close).close()));
$('resourceSelect').addEventListener('change', () =>
  task(null, () => selectResource($('resourceSelect').value)),
);
$('createForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('createSubmit'),
    async () => {
      const data = await api('/api/resources', 'POST', {
        name: $('createName').value,
        capacity: Number($('createCapacity').value),
      });
      $('createDialog').close();
      state.currentId = data.resource.id;
      state.resources.push(data.resource);
      await selectResource(data.resource.id);
      toast('Đã thêm tài nguyên.');
    },
    'createError',
  );
});
$('demoBtn').addEventListener('click', () =>
  task($('demoBtn'), async () => {
    const data = await api('/api/demo', 'POST', {});
    state.currentId = data.resourceId;
    $('dayInput').value = data.day;
    $('units').value = '1';
    newIntent(true);
    await refresh();
    toast('Đã mở lịch mẫu với hai tài nguyên và ba lượt đặt giả lập.');
  }),
);
for (const id of ['dayInput', 'minutes', 'units'])
  $(id).addEventListener('change', () => {
    newIntent(true);
    if (id === 'units' && !validUnits()) {
      toast('Số đơn vị phải từ 1 đến sức chứa của tài nguyên.', true);
      return;
    }
    task(null, refresh);
  });
$('holdTitle').addEventListener('input', () => newIntent());
$('prevDay').addEventListener('click', () => {
  const next = shiftDay($('dayInput').value, -1);
  if (next >= state.today) {
    $('dayInput').value = next;
    newIntent(true);
    task(null, refresh);
  }
});
$('nextDay').addEventListener('click', () => {
  const next = shiftDay($('dayInput').value, 1);
  if (next <= state.maxDay) {
    $('dayInput').value = next;
    newIntent(true);
    task(null, refresh);
  }
});
$('holdForm').addEventListener('submit', (event) => {
  event.preventDefault();
  if (!slot() || !validUnits()) return;
  task(
    $('holdBtn'),
    async () => {
      const selected = slot(),
        body = {
          resourceId: state.currentId,
          title: $('holdTitle').value,
          startsAt: selected.startsAt,
          endsAt: selected.endsAt,
          units: Number($('units').value),
        },
        key = state.requestKey;
      let data;
      try {
        data = await api('/api/holds', 'POST', body, { headers: { 'Idempotency-Key': key } });
      } catch (error) {
        if (state.requestKey === key && (error.network || error.status >= 500)) {
          state.unknown = true;
          controls();
        }
        throw error;
      }
      state.hold = data.reservation;
      state.replayed = data.replayed;
      state.unknown = false;
      setError('reservationError', null);
      await refresh();
      renderReservation();
      $('reservationDialog').showModal();
    },
    'holdError',
  );
});
$('confirmBookingBtn').addEventListener('click', () =>
  task(
    $('confirmBookingBtn'),
    async () => {
      const b = state.hold,
        data = await api('/api/reservations/' + b.id + '/confirm', 'POST', { version: b.version });
      state.hold = data.reservation;
      state.replayed = false;
      newIntent(true);
      await refresh();
      renderReservation();
      toast('Đã xác nhận lượt đặt.');
    },
    'reservationError',
  ),
);
$('cancelBookingBtn').addEventListener('click', () =>
  task(
    $('cancelBookingBtn'),
    async () => {
      const b = state.hold,
        data = await api('/api/reservations/' + b.id + '/cancel', 'POST', { version: b.version });
      state.hold = data.reservation;
      state.replayed = false;
      newIntent();
      await refresh();
      renderReservation();
      toast('Đã hủy lượt đặt và trả lại chỗ.');
    },
    'reservationError',
  ),
);
$('renameBtn').addEventListener('click', () => {
  state.target = { ...resource() };
  $('renameName').value = state.target.name;
  setError('renameError', null);
  $('renameDialog').showModal();
});
$('renameForm').addEventListener('submit', (event) => {
  event.preventDefault();
  task(
    $('renameSubmit'),
    async () => {
      const target = state.target;
      await api('/api/resources/' + target.id, 'PATCH', {
        name: $('renameName').value,
        version: target.version,
      });
      await refresh();
      $('renameDialog').close();
      toast('Đã cập nhật tên tài nguyên.');
    },
    'renameError',
  );
});
$('deleteBtn').addEventListener('click', () => {
  state.target = { ...resource() };
  $('deleteMessage').textContent =
    'Xóa “' +
    state.target.name +
    '” sẽ xóa mọi lượt đặt liên quan, gồm cả lịch đã xác nhận. Hãy xuất CSV/ICS trước nếu muốn giữ lại.';
  setError('deleteError', null);
  $('deleteDialog').showModal();
});
$('deleteSubmit').addEventListener('click', () =>
  task(
    $('deleteSubmit'),
    async () => {
      const target = state.target;
      await api('/api/resources/' + target.id, 'DELETE', { version: target.version });
      state.currentId = null;
      state.slots = [];
      state.bookings = [];
      newIntent(true);
      await refresh();
      $('deleteDialog').close();
      toast('Đã xóa tài nguyên và lịch liên quan.');
    },
    'deleteError',
  ),
);
$('refreshBtn').addEventListener('click', () => task($('refreshBtn'), refresh));
$('statusFilter').addEventListener('change', renderBookings);
$('search').addEventListener('input', renderBookings);
$('allDays').addEventListener('change', () => task(null, refresh));
$('csvBtn').addEventListener('click', () =>
  task($('csvBtn'), async () =>
    download(
      await api('/api/export.csv?' + exportQuery(true), 'GET', undefined, { download: true }),
      'yangslots.csv',
    ),
  ),
);
$('icsBtn').addEventListener('click', () =>
  task($('icsBtn'), async () =>
    download(
      await api('/api/calendar.ics?' + exportQuery(false), 'GET', undefined, { download: true }),
      'yangslots.ics',
    ),
  ),
);
async function boot() {
  const data = await api('/api/workspace');
  state.csrf = data.csrf;
  state.today = data.today;
  state.maxDay = data.maxDay;
  $('dayInput').min = state.today;
  $('dayInput').max = state.maxDay;
  $('dayInput').value = shiftDay(state.today, 1);
  await refresh();
  controls();
}
task(null, boot);
setInterval(() => {
  renderStats();
  if ($('reservationDialog').open) renderReservation();
}, 1000);
setInterval(() => {
  if (!document.hidden && state.csrf && !state.busy.size)
    refresh().catch((error) => {
      if (error.status === 401) $('expired').hidden = false;
    });
}, 5000);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.csrf) refresh().catch(() => {});
});
