const $ = (selector) => document.querySelector(selector);
let csrf = '',
  timer;
export const moduleNames = {
  minutes: 'YangMinutes',
  launch: 'YangLaunch',
  spec: 'YangSpec',
  home: 'YangLab',
};
export const stageNames = {
  idea: 'Ý tưởng',
  draft: 'Bản nháp',
  review: 'Chờ duyệt',
  ready: 'Sẵn sàng',
  published: 'Đã đăng',
};
export const channels = {
  facebook: 'Facebook',
  linkedin: 'LinkedIn',
  blog: 'Blog',
  x: 'X / Thread',
};
export { $ };
export function el(tag, className = '', text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
export function button(label, className, handler) {
  const node = el('button', className, label);
  node.type = 'button';
  node.addEventListener('click', handler);
  return node;
}
export function clean(value) {
  return value
    .toLowerCase()
    .replaceAll('đ', 'd')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}
export function viDate(day) {
  return day ? day.split('-').reverse().join('/') : 'Chưa có hạn';
}
export async function api(path, method = 'GET', data) {
  const response = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: {
      'X-CSRF-Token': csrf,
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }),
    },
    body: method === 'GET' ? undefined : JSON.stringify(data ?? {}),
  });
  if (!response.ok) {
    let message = 'Không thể kết nối. Vui lòng thử lại.';
    try {
      const payload = await response.json();
      message = payload.error || message;
    } catch {}
    const err = new Error(message);
    err.status = response.status;
    throw err;
  }
  return response.status === 204 ? null : response.json();
}
export function error(message) {
  const target = document.querySelector('dialog[open] .dialog-error') || $('#app-error');
  if (target) {
    target.textContent = message || '';
    target.hidden = !message;
  }
}
export async function run(task) {
  document.querySelectorAll('.dialog-error,#app-error').forEach((node) => {
    node.hidden = true;
    node.textContent = '';
  });
  try {
    await task();
    return true;
  } catch (err) {
    error(err.message || 'Có lỗi kết nối.');
    return false;
  }
}
export function toast(message) {
  clearTimeout(timer);
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  timer = setTimeout(() => {
    $('#toast').hidden = true;
  }, 3200);
}
export function empty(title, description, sample = false, loadDemo) {
  const node = el('div', 'empty');
  node.append(el('span', 'empty-symbol', '▧'), el('h3', '', title), el('p', '', description));
  if (sample) node.append(button('Thử dữ liệu mẫu →', 'button', loadDemo));
  return node;
}
export function stat(id, value) {
  $('#' + id).textContent = value;
}
export function closeDialog(id) {
  $('#' + id).close();
}
export function showDialog(id) {
  const dialog = $('#' + id);
  dialog.querySelectorAll('.dialog-error').forEach((node) => {
    node.hidden = true;
  });
  dialog.showModal();
}
export async function confirmDelete(
  title,
  handler,
  description = 'Bản ghi và lịch sử phiên bản sẽ bị xóa. Hãy xuất dữ liệu cần giữ trước khi tiếp tục.',
) {
  $('#confirm-name').textContent = title;
  $('#confirm-description').textContent = description;
  showDialog('confirm-dialog');
  $('#confirm-yes').onclick = async () => {
    const b = $('#confirm-yes');
    b.disabled = true;
    await run(async () => {
      await handler();
      $('#confirm-dialog').close();
      toast('Đã xóa dữ liệu.');
    });
    b.disabled = false;
  };
}
export async function history(module, record) {
  const { revisions } = await api('/api/' + module + '/items/' + record.id + '/history');
  const list = $('#history-list');
  list.replaceChildren();
  for (const revision of revisions) {
    const node = el('article', 'history-item');
    node.append(el('strong', '', 'Phiên bản ' + revision.version + ' · ' + revision.data.title));
    node.append(el('p', '', new Date(revision.at).toLocaleString('vi-VN')));
    node.append(
      el(
        'p',
        '',
        revision.data.summary ||
          revision.data.caption ||
          revision.data.description ||
          'Chưa có mô tả.',
      ),
    );
    if (revision.data.actions)
      node.append(
        el(
          'p',
          '',
          revision.data.actions.map((a) => (a.done ? '✓ ' : '○ ') + a.description).join('\n'),
        ),
      );
    if (revision.data.endpoints)
      node.append(
        el('p', '', revision.data.endpoints.map((e) => e.method + ' ' + e.path).join('\n')),
      );
    list.append(node);
  }
  showDialog('history-dialog');
}
export function exportLink(module, record, format, label) {
  const a = el('a', 'button small', label);
  a.href = '/api/' + module + '/items/' + record.id + '/export?format=' + format;
  a.download = '';
  return a;
}
export async function init(module) {
  $('#sidebar').innerHTML =
    '<a class="brand" href="/"><img src="/assets/favicon.svg" alt=""><span>YangLab<small>WORK SMALL. MAKE IT COUNT.</small></span></a><div class="workspace-label">Giang’s workspace<small>Bộ công cụ cho công việc mỗi ngày</small></div><div class="nav-label">KHÔNG GIAN LÀM VIỆC</div><nav><a class="nav-link" href="/"><span>⌂</span>Tổng quan<small>00</small></a><a class="nav-link" href="/minutes"><span>▤</span>YangMinutes<small>01</small></a><a class="nav-link" href="/launch"><span>▦</span>YangLaunch<small>02</small></a><a class="nav-link" href="/spec"><span>⌘</span>YangSpec<small>03</small></a></nav><div class="side-tip"><strong>Gọn từ việc nhỏ.</strong>Ghi rõ việc cần làm, giữ đúng phiên bản và xuất dữ liệu khi cần.</div><div class="side-bottom"><div class="private"><span class="dot"></span>Workspace riêng theo trình duyệt</div><a class="author" href="https://github.com/nguyenhonggiang2407" target="_blank" rel="noopener noreferrer"><span class="avatar">NG</span><span><strong>Nguyễn Hồng Giang</strong><small>Backend / Full-stack · Yang</small></span></a></div>';
  const active = module === 'home' ? '/' : '/' + module;
  $('#sidebar')
    .querySelectorAll('.nav-link')
    .forEach((a) => {
      if (a.getAttribute('href') === active) {
        a.classList.add('active');
        a.setAttribute('aria-current', 'page');
      }
    });
  $('#topbar').innerHTML =
    '<div class="breadcrumb"><a href="/">Workspace</a><span>/</span><strong></strong></div><div class="top-meta"><span><span class="dot"></span>Lưu trên máy chủ của bạn</span><span id="today-label"></span></div>';
  $('#topbar strong').textContent = moduleNames[module];
  const dialogs = el('div');
  dialogs.innerHTML =
    '<dialog id="confirm-dialog"><div class="dialog-head"><h2>Xóa dữ liệu này?</h2><button class="icon-button" data-close="confirm-dialog" aria-label="Hủy xóa">×</button></div><p id="confirm-name" class="muted"></p><p class="fineprint" id="confirm-description"></p><p class="dialog-error" role="alert" hidden></p><div class="dialog-actions"><button class="button" data-close="confirm-dialog">Giữ lại</button><button class="button danger" id="confirm-yes">Xóa dữ liệu</button></div></dialog><dialog id="history-dialog"><div class="dialog-head"><h2>Lịch sử phiên bản</h2><button class="icon-button" data-close="history-dialog" aria-label="Đóng lịch sử">×</button></div><p class="hint">Giữ tối đa 20 phiên bản gần nhất của bản ghi.</p><div id="history-list"></div></dialog><div class="toast" id="toast" role="status" aria-live="polite" hidden></div>';
  document.body.append(dialogs);
  document.querySelectorAll('label.field').forEach((label, index) => {
    const control = label.querySelector('input,textarea,select');
    if (!control) return;
    const name = [...label.childNodes]
      .filter((n) => n.nodeType === Node.TEXT_NODE)
      .map((n) => n.textContent)
      .join(' ')
      .trim();
    if (name) control.setAttribute('aria-label', name);
    const hint = label.querySelector('.hint');
    if (hint) {
      hint.id ||= 'field-hint-' + index;
      control.setAttribute('aria-describedby', hint.id);
    }
  });
  document
    .querySelectorAll('[data-close]')
    .forEach((b) => b.addEventListener('click', () => $('#' + b.dataset.close).close()));
  const workspace = await api('/api/workspace');
  csrf = workspace.csrf;
  $('#today-label').textContent = viDate(workspace.today) + ' · VN';
  document.body.dataset.ready = 'true';
  return workspace;
}
