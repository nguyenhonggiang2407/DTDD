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
  check(value !== null && typeof value === 'object' && !Array.isArray(value), 'Cần một object.');
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
  check(Number.isInteger(value) && value > 0, 'Thiếu phiên bản bản ghi.');
  return value;
}
export function quote(value) {
  check(/^[a-z][a-z0-9_]*$/i.test(value), 'Định danh nội bộ không hợp lệ.');
  return '"' + value + '"';
}
export const sha = (value) => createHash('sha256').update(value).digest('hex');
export function normalize(value) {
  return value
    .toLowerCase()
    .replaceAll('đ', 'd')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}
export function numeric(value) {
  if (typeof value === 'number')
    return Number.isFinite(value) && Math.abs(value) <= Number.MAX_SAFE_INTEGER;
  if (typeof value !== 'string' || !/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(value)) return false;
  const n = Number(value);
  return Number.isFinite(n) && Math.abs(n) <= Number.MAX_SAFE_INTEGER;
}
