import { createHash } from 'node:crypto';
export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export function check(condition, message, status = 400) {
  if (!condition) throw new HttpError(status, message);
}
export function object(value) {
  check(
    value !== null && typeof value === 'object' && !Array.isArray(value),
    'Cần một object JSON.',
  );
  return value;
}
export function text(value, label, max = 120) {
  check(typeof value === 'string', label + ' không hợp lệ.');
  const s = value.trim().normalize('NFC');
  check(
    s.length > 0 && s.length <= max && !/[\u0000-\u001f]/.test(s),
    label + ' cần 1–' + max + ' ký tự.',
  );
  return s;
}
export function version(value) {
  check(Number.isInteger(value) && value > 0, 'Thiếu phiên bản cấu hình.');
  return value;
}
export const sha = (value) => createHash('sha256').update(value).digest('hex');
export function jsonBody(raw) {
  let value;
  try {
    value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(raw));
  } catch {
    throw new HttpError(400, 'Body cần JSON UTF-8 hợp lệ.');
  }
  check(value !== null && typeof value === 'object', 'Body cần object hoặc array JSON.');
  const stack = [[value, 0]];
  let nodes = 0;
  while (stack.length) {
    const [item, depth] = stack.pop();
    check(depth <= 10, 'JSON tối đa 10 mức lồng nhau.');
    check(++nodes <= 5000, 'JSON tối đa 5.000 giá trị.');
    if (item && typeof item === 'object') {
      const entries = Object.entries(item);
      check(entries.length <= 1000, 'Object/array tối đa 1.000 phần tử.');
      for (const [key, val] of entries) {
        check(key.length <= 200, 'Khóa JSON tối đa 200 ký tự.');
        stack.push([val, depth + 1]);
      }
    }
    if (typeof item === 'string') check(item.length <= 10000, 'Chuỗi JSON tối đa 10.000 ký tự.');
  }
  return value;
}
