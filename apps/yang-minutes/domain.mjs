// ActionItem fields are adapted from the retained MIT meeting data models.
import {
  object,
  text,
  date,
  list,
  id,
  choice,
  bool,
  requireThat,
  dayAfter,
} from '../../lib/validation.mjs';

export function extractActions(notes) {
  notes = text(notes, 'Nội dung họp', 40000, false);
  const actions = [],
    warnings = [];
  for (const [index, line] of notes.split(/\r?\n/).entries()) {
    if (!/^\s*-\s*\[[ xX]\]/.test(line)) continue;
    const match = line.match(/^\s*-\s*\[([ xX])\]\s*@([^|]+)\|\s*(\d{4}-\d{2}-\d{2})\s*\|\s*(.+)$/);
    if (!match) {
      warnings.push('Dòng ' + (index + 1) + ': cần mẫu - [ ] @Tên | YYYY-MM-DD | Công việc');
      continue;
    }
    try {
      actions.push({
        id: id(),
        description: text(match[4], 'Công việc', 300),
        assignee: text(match[2], 'Người phụ trách', 60),
        due: date(match[3]),
        priority: 'medium',
        done: match[1].toLowerCase() === 'x',
      });
    } catch (err) {
      warnings.push('Dòng ' + (index + 1) + ': ' + err.message);
    }
  }
  requireThat(actions.length <= 40, 'Mỗi cuộc họp tối đa 40 công việc.');
  return { actions, warnings };
}
export function validate(input) {
  object(input);
  const actions = list(input.actions ?? [], 40, 'Công việc').map((raw) => {
    object(raw);
    return {
      id: id(raw.id),
      description: text(raw.description, 'Công việc', 300),
      assignee: text(raw.assignee, 'Người phụ trách', 60, false),
      due: date(raw.due, 'Hạn hoàn thành', true),
      priority: choice(raw.priority ?? 'medium', ['low', 'medium', 'high'], 'Ưu tiên'),
      done: bool(raw.done ?? false),
    };
  });
  requireThat(new Set(actions.map((a) => a.id)).size === actions.length, 'ID công việc bị trùng.');
  return {
    title: text(input.title, 'Tên cuộc họp', 120),
    date: date(input.date, 'Ngày họp'),
    participants: list(input.participants ?? [], 16, 'Thành viên').map((p) =>
      text(p, 'Tên thành viên', 60),
    ),
    summary: text(input.summary, 'Kết luận', 6000, false),
    notes: text(input.notes, 'Nội dung họp', 40000, false),
    actions,
  };
}
export function markdown(record) {
  const d = record.data;
  return [
    '# ' + d.title,
    '',
    'Ngày: ' + d.date,
    'Thành viên: ' + (d.participants.join(', ') || 'Chưa ghi'),
    '',
    '## Kết luận',
    '',
    d.summary || 'Chưa ghi kết luận.',
    '',
    '## Công việc sau họp',
    '',
    ...d.actions.map(
      (a) =>
        '- [' +
        (a.done ? 'x' : ' ') +
        '] ' +
        a.description +
        ' — ' +
        (a.assignee || 'Chưa phân công') +
        (a.due ? ' · Hạn ' + a.due : ''),
    ),
    '',
    '## Ghi chép gốc',
    '',
    d.notes,
    '',
    'Xuất từ YangMinutes · Phiên bản ' + record.version,
    '',
  ].join('\n');
}
const escapeICS = (value) =>
  value
    .replaceAll('\\', '\\\\')
    .replaceAll('\n', '\\n')
    .replaceAll(';', '\\;')
    .replaceAll(',', '\\,')
    .replaceAll('\r', '');
function fold(line) {
  const out = [];
  let current = '';
  for (const char of line) {
    if (Buffer.byteLength(current + char) > 73) {
      out.push(current);
      current = ' ';
    }
    current += char;
  }
  out.push(current);
  return out.join('\r\n');
}
export function calendar(record, now = Date.now()) {
  const stamp = new Date(now)
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d+Z$/, 'Z');
  const rows = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//YangLab//YangMinutes//VI',
    'CALSCALE:GREGORIAN',
  ];
  for (const action of record.data.actions.filter((a) => a.due))
    rows.push(
      'BEGIN:VTODO',
      'UID:' + action.id + '@yangminutes.local',
      'DTSTAMP:' + stamp,
      'DUE;VALUE=DATE:' + action.due.replaceAll('-', ''),
      'SUMMARY:' + escapeICS(action.description),
      'DESCRIPTION:' + escapeICS(record.data.title + ' — ' + action.assignee),
      'STATUS:' + (action.done ? 'COMPLETED' : 'NEEDS-ACTION'),
      'END:VTODO',
    );
  rows.push('END:VCALENDAR');
  return rows.map(fold).join('\r\n') + '\r\n';
}
export function demo(day) {
  const due = dayAfter(day, 2);
  return [
    {
      title: 'Khởi động dự án học kỳ',
      date: day,
      participants: ['Giang', 'Linh', 'Minh'],
      summary: 'Chốt phạm vi MVP, giao việc cụ thể và rà soát tiến độ vào buổi họp tiếp theo.',
      notes:
        'Dữ liệu minh họa, không phải biên bản họp thật.\n\n- [ ] @Giang | ' +
        due +
        ' | Hoàn thiện tài liệu API\n- [ ] @Linh | ' +
        due +
        ' | Kiểm tra luồng đăng ký\n- [x] @Minh | ' +
        day +
        ' | Chuẩn bị bộ dữ liệu mẫu',
      actions: [
        {
          id: id(),
          description: 'Hoàn thiện tài liệu API',
          assignee: 'Giang',
          due,
          priority: 'high',
          done: false,
        },
        {
          id: id(),
          description: 'Kiểm tra luồng đăng ký',
          assignee: 'Linh',
          due,
          priority: 'medium',
          done: false,
        },
        {
          id: id(),
          description: 'Chuẩn bị bộ dữ liệu mẫu',
          assignee: 'Minh',
          due: day,
          priority: 'low',
          done: true,
        },
      ],
    },
  ];
}
