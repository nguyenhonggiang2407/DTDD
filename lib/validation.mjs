import { createHash } from 'node:crypto';
export const TYPES = ['csv-clean', 'json-csv', 'text-stats'];
export const STATES = ['queued', 'running', 'retry_wait', 'succeeded', 'failed', 'cancelled'];
export const ACTIVE = ['queued', 'running', 'retry_wait'];
export const MAX_INPUT = 256 * 1024;
export class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }
export function check(condition, message, status = 400) { if (!condition) throw new HttpError(status, message); }
export function object(value) { check(value !== null && typeof value === 'object' && !Array.isArray(value), 'Dữ liệu cần là một object.'); return value; }
export function bool(value) { check(typeof value === 'boolean', 'Trạng thái không hợp lệ.'); return value; }
export function version(value) { check(Number.isInteger(value) && value > 0, 'Thiếu phiên bản tác vụ.'); return value; }
export function key(value) { check(typeof value === 'string' && /^[\w.:-]{8,100}$/.test(value), 'Cần Idempotency-Key từ 8 đến 100 ký tự.'); return value; }
export function submission(raw) {
  object(raw); check(TYPES.includes(raw.type), 'Loại tác vụ không được hỗ trợ.');
  check(typeof raw.title === 'string', 'Tên tác vụ không hợp lệ.');
  const title = raw.title.trim().normalize('NFC');
  check(title.length > 0 && title.length <= 120 && !/[\u0000-\u001f]/.test(title), 'Tên tác vụ cần 1–120 ký tự, không chứa ký tự điều khiển.');
  check(typeof raw.input === 'string' && raw.input.trim().length > 0, 'Hãy nhập dữ liệu cần xử lý.');
  check(!raw.input.includes('\u0000'), 'Dữ liệu không được chứa byte NUL.');
  check(Buffer.byteLength(raw.input) <= MAX_INPUT, 'Dữ liệu tối đa 256 KB.', 413);
  const options = object(raw.options ?? {});
  return { type: raw.type, title, input: raw.input, options: {
    trim: options.trim === undefined ? true : bool(options.trim),
    dedupe: options.dedupe === undefined ? true : bool(options.dedupe),
  } };
}
export const sha = content => createHash('sha256').update(content).digest('hex');
export const digest = (data, parent = null) => sha(JSON.stringify({ title: data.title, type: data.type, input: data.input, options: data.options, parent }));
export function normalize(value) { return value.toLowerCase().replaceAll('đ', 'd').normalize('NFD').replace(/[\u0300-\u036f]/g, ''); }
