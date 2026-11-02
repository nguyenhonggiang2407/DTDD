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
  stageNames,
  channels,
} from './shared.js';
const stages = ['idea', 'draft', 'review', 'ready', 'published'];
const transitions = {
  idea: ['draft'],
  draft: ['review'],
  review: ['ready', 'draft'],
  ready: ['published', 'review'],
  published: ['draft'],
};
let workspace,
  items = [],
  editing = null,
  view = matchMedia('(max-width: 740px)').matches ? 'calendar' : 'board';
const form = $('#post-form');
async function refresh() {
  items = (await api('/api/launch/items')).items;
  render();
}
function filtered() {
  const q = clean($('#search').value.trim()),
    channel = $('#channel-filter').value,
    stage = $('#stage-filter').value;
  const from = $('#from-filter').value,
    to = $('#to-filter').value;
  return items.filter(
    (item) =>
      (!q || clean(JSON.stringify(item.data)).includes(q)) &&
      (!channel || item.data.channel === channel) &&
      (!stage || item.data.stage === stage) &&
      (!from || item.data.due >= from) &&
      (!to || (item.data.due && item.data.due <= to)),
  );
}
function postCard(item) {
  const d = item.data,
    card = el('article', 'post-card');
  card.dataset.id = item.id;
  const label = el('div', 'card-top');
  label.append(el('span', 'pill neutral', channels[d.channel]));
  card.append(
    label,
    button(d.title, 'post-title', () => openEditor(item)),
  );
  if (d.caption)
    card.append(el('p', '', d.caption.slice(0, 100) + (d.caption.length > 100 ? '…' : '')));
  const meta = el('div', 'post-meta');
  meta.append(el('span', '', d.owner || 'Chưa giao người'), el('span', '', viDate(d.due)));
  card.append(meta);
  if (d.campaign) card.append(el('p', 'hint', d.campaign));
  const select = el('select', 'stage-select');
  select.setAttribute('aria-label', 'Chuyển giai đoạn ' + d.title);
  const placeholder = el('option', '', 'Chuyển giai đoạn…');
  placeholder.value = '';
  select.append(placeholder);
  for (const stage of transitions[d.stage]) {
    const option = el('option', '', '→ ' + stageNames[stage]);
    option.value = stage;
    select.append(option);
  }
  select.addEventListener('change', async () => {
    if (!select.value) return;
    select.disabled = true;
    const ok = await run(async () => {
      await api('/api/launch/items/' + item.id + '/stage', 'PATCH', {
        version: item.version,
        stage: select.value,
      });
      await refresh();
      toast('Đã cập nhật giai đoạn.');
    });
    if (!ok) {
      select.value = '';
      select.disabled = false;
    }
  });
  card.append(select);
  return card;
}
function render() {
  stat('stat-posts', items.length);
  stat('stat-ready', items.filter((i) => i.data.stage === 'ready').length);
  const end = new Date(Date.parse(workspace.today + 'T00:00:00Z') + 7 * 86400000)
    .toISOString()
    .slice(0, 10);
  stat(
    'stat-week',
    items.filter(
      (i) => i.data.stage !== 'published' && i.data.due >= workspace.today && i.data.due < end,
    ).length,
  );
  const records = filtered(),
    area = $('#content-board');
  area.replaceChildren();
  const params = new URLSearchParams();
  for (const [key, id] of [
    ['q', '#search'],
    ['channel', '#channel-filter'],
    ['stage', '#stage-filter'],
    ['from', '#from-filter'],
    ['to', '#to-filter'],
  ])
    if ($(id).value) params.set(key, $(id).value);
  $('#export-csv').href = '/api/launch/export.csv?' + params;
  if (!items.length) {
    area.className = '';
    area.append(
      empty(
        'Một ý tưởng nhỏ cũng đáng được ghi lại.',
        'Thêm bài viết đầu tiên hoặc thử một bộ kế hoạch giả lập.',
        true,
        demo,
      ),
    );
    return;
  }
  if (!records.length) {
    area.className = '';
    area.append(
      empty('Chưa có bài viết phù hợp.', 'Thử thay đổi từ khóa, kênh, giai đoạn hoặc khoảng ngày.'),
    );
    return;
  }
  if (view === 'board') {
    area.className = 'board';
    for (const stage of stages) {
      const lane = el('section', 'lane');
      lane.setAttribute('aria-label', stageNames[stage]);
      const matches = records.filter((i) => i.data.stage === stage),
        title = el('div', 'lane-title');
      title.append(el('span', '', stageNames[stage]), el('span', 'count', matches.length));
      lane.append(title);
      if (!matches.length) lane.append(el('div', 'lane-empty', 'Chưa có nội dung'));
      matches.forEach((item) => lane.append(postCard(item)));
      area.append(lane);
    }
  } else {
    area.className = '';
    const groups = new Map();
    for (const item of [...records].sort((a, b) =>
      (a.data.due || '9999').localeCompare(b.data.due || '9999'),
    )) {
      const key = item.data.due || '';
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(item);
    }
    for (const [day, group] of groups) {
      const block = el('section', 'calendar-day'),
        entries = el('div', 'calendar-posts');
      block.append(el('h3', '', day ? viDate(day) : 'Chưa xếp lịch'));
      for (const item of group) {
        const row = el('div', 'calendar-post');
        row.append(
          el('span', 'pill neutral', channels[item.data.channel]),
          button(item.data.title, '', () => openEditor(item)),
          el('span', '', stageNames[item.data.stage]),
        );
        entries.append(row);
      }
      block.append(entries);
      area.append(block);
    }
  }
}
async function demo() {
  await run(async () => {
    const result = await api('/api/launch/demo', 'POST');
    await refresh();
    toast(result.added ? 'Đã thêm 3 bài viết mẫu.' : 'Kế hoạch mẫu đã có.');
  });
}
function openEditor(record = null) {
  editing = record;
  form.reset();
  $('#post-dialog-title').textContent = record ? 'Chỉnh sửa bài viết' : 'Thêm bài viết';
  $('#initial-stage-field').hidden = Boolean(record);
  $('#post-stage-hint').hidden = !record;
  const d = record?.data || {
    title: '',
    campaign: '',
    channel: 'facebook',
    due: '',
    owner: 'Giang',
    caption: '',
    notes: '',
    stage: 'idea',
  };
  for (const key of ['title', 'campaign', 'channel', 'due', 'owner', 'caption', 'notes'])
    form.elements[key].value = d[key];
  form.elements.stage.value = record ? 'idea' : d.stage;
  $('#caption-count').textContent =
    d.caption.length + ' ký tự · Phân tách thread bằng một dòng ---.';
  const extra = $('#post-extra');
  extra.replaceChildren();
  if (record) {
    extra.append(
      exportLink('launch', record, 'md', '↓ Bản đã lưu'),
      button('Phiên bản', 'button small', () =>
        run(async () => {
          closeDialog('post-dialog');
          await history('launch', record);
        }),
      ),
      button('Xóa', 'button small danger', () => {
        closeDialog('post-dialog');
        confirmDelete(d.title, async () => {
          await api('/api/launch/items/' + record.id, 'DELETE', { version: record.version });
          await refresh();
        });
      }),
    );
  }
  showDialog('post-dialog');
}
form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const save = $('#save-post');
  save.disabled = true;
  await run(async () => {
    const data = Object.fromEntries(new FormData(form));
    if (editing) data.stage = editing.data.stage;
    if (editing)
      await api('/api/launch/items/' + editing.id, 'PUT', { version: editing.version, data });
    else await api('/api/launch/items', 'POST', { data });
    closeDialog('post-dialog');
    await refresh();
    toast('Đã lưu bài viết.');
  });
  save.disabled = false;
});
form.elements.caption.addEventListener('input', () => {
  $('#caption-count').textContent =
    form.elements.caption.value.length + ' ký tự · Phân tách thread bằng một dòng ---.';
});
$('#new-post').addEventListener('click', () => openEditor());
$('#load-demo').addEventListener('click', demo);
$('#refresh').addEventListener('click', () => run(refresh));
for (const id of ['#search', '#channel-filter', '#stage-filter', '#from-filter', '#to-filter'])
  $(id).addEventListener(id === '#search' ? 'input' : 'change', render);
$('#clear-filters').addEventListener('click', () => {
  for (const id of ['#search', '#channel-filter', '#stage-filter', '#from-filter', '#to-filter'])
    $(id).value = '';
  render();
});
function setView(next) {
  view = next;
  for (const id of ['board', 'calendar']) {
    $('#' + id + '-view').classList.toggle('active', next === id);
    $('#' + id + '-view').setAttribute('aria-pressed', next === id);
  }
  render();
}
$('#board-view').addEventListener('click', () => setView('board'));
$('#calendar-view').addEventListener('click', () => setView('calendar'));
run(async () => {
  workspace = await init('launch');
  await refresh();
  setView(view);
});
