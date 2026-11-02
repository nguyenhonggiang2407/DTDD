import { randomUUID } from 'node:crypto';

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export function requireThat(condition, message, status = 400) {
  if (!condition) throw new HttpError(status, message);
}
export function object(value, label = 'Dữ liệu') {
  requireThat(
    value && typeof value === 'object' && !Array.isArray(value),
    label + ' cần là một object.',
  );
  return value;
}
export function text(value, label, max = 200, required = true) {
  requireThat(typeof value === 'string' || (!required && value == null), label + ' không hợp lệ.');
  const result = (value ?? '').trim().normalize('NFC');
  requireThat(
    (!required || result.length > 0) && result.length <= max,
    label + ' cần từ ' + (required ? 1 : 0) + ' đến ' + max + ' ký tự.',
  );
  requireThat(
    !/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/u.test(result),
    label + ' chứa ký tự điều khiển.',
  );
  return result;
}
export function date(value, label = 'Ngày', optional = false) {
  if (optional && !value) return '';
  requireThat(
    typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value),
    label + ' cần có dạng YYYY-MM-DD.',
  );
  const parsed = new Date(value + 'T00:00:00Z');
  requireThat(
    Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value,
    label + ' không tồn tại.',
  );
  requireThat(
    value >= '2000-01-01' && value <= '2100-12-31',
    label + ' nằm ngoài phạm vi 2000–2100.',
  );
  return value;
}
export function choice(value, options, label) {
  requireThat(options.includes(value), label + ' không hợp lệ.');
  return value;
}
export function bool(value, label = 'Trạng thái') {
  requireThat(typeof value === 'boolean', label + ' cần là true hoặc false.');
  return value;
}
export function list(value, max, label) {
  requireThat(Array.isArray(value) && value.length <= max, label + ' tối đa ' + max + ' mục.');
  return value;
}
export function id(value) {
  if (!value) return randomUUID();
  requireThat(typeof value === 'string' && /^[a-zA-Z0-9-]{8,48}$/.test(value), 'ID không hợp lệ.');
  return value;
}
export function version(value) {
  requireThat(Number.isInteger(value) && value >= 1, 'Thiếu phiên bản bản ghi.');
  return value;
}
export function normalize(value) {
  return value
    .toLowerCase()
    .replaceAll('đ', 'd')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}
export function today(clock = () => Date.now()) {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(clock()));
  const get = (type) => parts.find((p) => p.type === type).value;
  return [get('year'), get('month'), get('day')].join('-');
}
export function dayAfter(day, count) {
  return new Date(Date.parse(day + 'T00:00:00Z') + count * 86400000).toISOString().slice(0, 10);
}
export function download(res, content, name, type) {
  res.writeHead(200, {
    'Content-Type': type + '; charset=utf-8',
    'Content-Disposition': 'attachment; filename="' + name + '"',
    'Cache-Control': 'no-store',
  });
  res.end(content);
}
