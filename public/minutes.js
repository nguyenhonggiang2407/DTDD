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
  viDate,
  showDialog,
  closeDialog,
  confirmDelete,
  history,
  exportLink,
} from './shared.js';
let workspace,
  items = [],
  selected = null,
  editing = null,
  stagedActions = [];
const form = $('#meeting-form');
function current() {
  return items.find((item) => item.id === selected);
}
async function refresh() {
  const result = await api('/api/minutes/items');
  items = result.items;
  if (!items.some((i) => i.id === selected)) selected = items[0]?.id ?? null;
  render();
}
async function demo() {
  await run(async () => {
    const result = await api('/api/minutes/demo', 'POST');
    await refresh();
    toast(result.added ? 'Đã thêm một cuộc họp mẫu.' : 'Cuộc họp mẫu đã có trong workspace.');
  });
}
function render() {
  const actions = items.flatMap((item) => item.data.actions);
  stat('stat-meetings', items.length);
  stat('stat-open', actions.filter((a) => !a.done).length);
  stat('stat-overdue', actions.filter((a) => !a.done && a.due && a.due < workspace.today).length);
  const query = clean($('#search').value.trim());
  const filtered = items.filter(
    (item) => !query || clean(JSON.stringify(item.data)).includes(query),
  );
  stat('meeting-count', filtered.length);
  $('#meeting-list').replaceChildren();
  if (!filtered.length)
    $('#meeting-list').append(
      empty(
        'Chưa có cuộc họp phù hợp.',
        'Tạo một cuộc họp hoặc thử dữ liệu mẫu để bắt đầu.',
        !items.length,
        demo,
      ),
    );
  for (const item of filtered) {
    const d = item.data;
    const card = button('', 'item-card' + (item.id === selected ? ' selected' : ''), () => {
      selected = item.id;
      render();
    });
    card.setAttribute('aria-label', 'Mở ' + d.title);
    const top = el('div', 'card-top');
    top.append(
      el('span', 'pill neutral', viDate(d.date)),
      el('span', '', d.participants.length + ' thành viên'),
    );
    card.append(
      top,
      el('h3', '', d.title),
      el('p', '', d.summary.slice(0, 90) || 'Chưa ghi kết luận.'),
    );
    const bottom = el('div', 'card-bottom');
    const done = d.actions.filter((a) => a.done).length;
    bottom.append(
      el('span', '', done + '/' + d.actions.length + ' việc hoàn thành'),
      el('span', '', 'Phiên bản ' + item.version),
    );
    card.append(bottom);
    $('#meeting-list').append(card);
  }
  renderDetail();
}
function renderDetail() {
  const area = $('#meeting-detail');
  area.replaceChildren();
  const record = current();
  if (!record) {
    area.append(
      empty(
        'Mọi cuộc họp cần một điểm kết.',
        'Ghi rõ quyết định và việc cần làm. Chọn một cuộc họp để xem chi tiết.',
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
      viDate(d.date) +
        ' · ' +
        (d.participants.join(', ') || 'Chưa ghi thành viên') +
        ' · v' +
        record.version,
    ),
  );
  const tools = el('div', 'panel-tools');
  tools.append(
    button('Chỉnh sửa', 'button small', () => openEditor(record)),
    exportLink('minutes', record, 'md', '↓ Markdown'),
    exportLink('minutes', record, 'ics', '↓ Lịch ICS'),
    button('Phiên bản', 'button small', () => run(() => history('minutes', record))),
    button('Xóa', 'button small danger', () =>
      confirmDelete(d.title, async () => {
        await api('/api/minutes/items/' + record.id, 'DELETE', { version: record.version });
        await refresh();
      }),
    ),
  );
  head.append(tools);
  panel.append(head);
  const content = el('div', 'panel-content');
  content.append(
    el('h3', '', 'Kết luận sau cuộc họp'),
    el('p', 'summary', d.summary || 'Chưa ghi kết luận.'),
  );
  content.append(el('hr', 'section-divider'), el('h3', '', 'Công việc tiếp theo'));
  const tasks = el('div', 'task-list');
  if (!d.actions.length)
    tasks.append(
      el('p', 'muted', 'Chưa có công việc. Thêm bên dưới hoặc lấy từ checklist trong ghi chép.'),
    );
  for (const action of d.actions) {
    const row = el('label', 'task-row' + (action.done ? ' completed' : ''));
    const checkbox = el('input');
    checkbox.type = 'checkbox';
    checkbox.checked = action.done;
    checkbox.setAttribute('aria-label', 'Hoàn thành ' + action.description);
    checkbox.addEventListener('change', async () => {
      checkbox.disabled = true;
      const ok = await run(async () => {
        await api('/api/minutes/items/' + record.id + '/actions/' + action.id, 'PATCH', {
          version: record.version,
          done: checkbox.checked,
        });
        await refresh();
      });
      if (!ok) {
        checkbox.checked = action.done;
        checkbox.disabled = false;
      }
    });
    const main = el('div', 'task-main');
    main.append(el('div', 'task-title', action.description));
    const meta = el('div', 'task-meta');
    meta.append(
      el('span', '', action.assignee || 'Chưa phân công'),
      el(
        'span',
        'pill ' +
          (action.done
            ? 'done'
            : action.due && action.due < workspace.today
              ? 'overdue'
              : 'neutral'),
        action.done ? 'Đã xong' : viDate(action.due),
      ),
    );
    if (action.priority === 'high') meta.append(el('span', 'task-priority', 'Ưu tiên cao'));
    main.append(meta);
    row.append(checkbox, main);
    tasks.append(row);
  }
  content.append(tasks);
  const quick = el('form', 'add-task');
  for (const [name, placeholder, type] of [
    ['description', 'Thêm một việc cần làm…', 'text'],
    ['assignee', 'Người phụ trách', 'text'],
    ['due', 'Hạn hoàn thành', 'date'],
  ]) {
    const input = el('input');
    input.name = name;
    input.type = type;
    input.placeholder = placeholder;
    input.setAttribute('aria-label', placeholder);
    input.maxLength = name === 'description' ? 300 : 60;
    if (name === 'description') input.required = true;
    quick.append(input);
  }
  const add = el('button', 'button small', '＋ Thêm');
  add.type = 'submit';
  quick.append(add);
  quick.addEventListener('submit', (event) => {
    event.preventDefault();
    run(async () => {
      const values = new FormData(quick);
      const data = {
        ...record.data,
        actions: [
          ...record.data.actions,
          {
            id: crypto.randomUUID(),
            description: values.get('description'),
            assignee: values.get('assignee'),
            due: values.get('due'),
            priority: 'medium',
            done: false,
          },
        ],
      };
      await api('/api/minutes/items/' + record.id, 'PUT', { version: record.version, data });
      await refresh();
      toast('Đã thêm công việc.');
    });
  });
  content.append(quick);
  const notes = el('details', 'notes');
  notes.append(
    el('summary', '', 'Đọc ghi chép đầy đủ'),
    el('pre', '', d.notes || 'Chưa có ghi chép.'),
  );
  content.append(notes);
  panel.append(content);
  area.append(panel);
}
function openEditor(record = null) {
  editing = record;
  stagedActions = record ? structuredClone(record.data.actions) : [];
  form.reset();
  $('#extract-preview').hidden = true;
  $('#meeting-dialog-title').textContent = record ? 'Chỉnh sửa cuộc họp' : 'Tạo cuộc họp';
  const d = record?.data || {
    title: '',
    date: workspace.today,
    participants: [],
    summary: '',
    notes: '',
  };
  for (const key of ['title', 'date', 'summary', 'notes']) form.elements[key].value = d[key];
  form.elements.participants.value = d.participants.join(', ');
  showDialog('meeting-dialog');
}
$('#meeting-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const save = $('#save-meeting');
  save.disabled = true;
  await run(async () => {
    const data = Object.fromEntries(new FormData(form));
    data.participants = data.participants
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    data.actions = stagedActions;
    const record = editing
      ? await api('/api/minutes/items/' + editing.id, 'PUT', { version: editing.version, data })
      : await api('/api/minutes/items', 'POST', { data });
    selected = record.id;
    closeDialog('meeting-dialog');
    await refresh();
    toast('Đã lưu cuộc họp.');
  });
  save.disabled = false;
});
$('#extract-actions').addEventListener('click', () =>
  run(async () => {
    const result = await api('/api/minutes/extract', 'POST', { notes: form.elements.notes.value });
    const preview = $('#extract-preview');
    preview.replaceChildren();
    preview.hidden = false;
    preview.append(el('strong', '', result.actions.length + ' công việc được nhận diện.'));
    for (const action of result.actions)
      preview.append(
        el(
          'div',
          '',
          (action.done ? '✓ ' : '○ ') +
            action.description +
            ' · ' +
            action.assignee +
            ' · ' +
            viDate(action.due),
        ),
      );
    for (const warning of result.warnings) preview.append(el('div', '', warning));
    if (result.actions.length)
      preview.append(
        button('Dùng danh sách này khi lưu', 'button small', () => {
          stagedActions = result.actions;
          toast('Sẽ lưu ' + result.actions.length + ' công việc cùng cuộc họp.');
        }),
      );
  }),
);
$('#new-meeting').addEventListener('click', () => openEditor());
$('#load-demo').addEventListener('click', demo);
$('#refresh').addEventListener('click', () => run(refresh));
$('#search').addEventListener('input', render);
run(async () => {
  workspace = await init('minutes');
  await refresh();
});
