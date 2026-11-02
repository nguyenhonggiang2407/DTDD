import { parseCSV, InputError } from './csv.mjs';
import { check, object, text, numeric, sha } from './validation.mjs';

export const MAX_CSV = 512 * 1024;
export function inspectCSV(payload) {
  object(payload);
  check(typeof payload.csv === 'string' && payload.csv.length > 0, 'Hãy chọn hoặc dán một tệp CSV.');
  const bytes = Buffer.byteLength(payload.csv, 'utf8');
  check(bytes <= MAX_CSV, 'CSV tối đa 512 KB.', 413);
  check(!/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(payload.csv), 'CSV chứa ký tự điều khiển không được hỗ trợ.');
  let parsed;
  try { parsed = parseCSV(payload.csv); }
  catch (err) { if (err instanceof InputError) check(false, err.message); throw err; }
  const headers = parsed[0].map(value => text(value, 'Tiêu đề cột', 80));
  check(new Set(headers.map(value => value.toLowerCase())).size === headers.length, 'Tiêu đề cột bị trùng sau khi chuẩn hóa.');
  const rows = [];
  for (let i = 1; i < parsed.length; i++) {
    const row = parsed[i].map(value => value.replace(/\r\n?/g, '\n').trim().normalize('NFC'));
    if (row.every(value => value === '')) continue;
    check(row.length === headers.length, 'Dòng ' + (i + 1) + ' có ' + row.length + ' ô, cần ' + headers.length + '.');
    check(row.every(value => value.length <= 2000), 'Mỗi ô tối đa 2.000 ký tự (dòng ' + (i + 1) + ').');
    rows.push(row.map(value => value === '' ? null : value));
  }
  check(rows.length > 0, 'CSV cần ít nhất một dòng dữ liệu.');
  const overrides = payload.types ?? {};
  object(overrides);
  check(Object.keys(overrides).every(key => /^c(?:0|[1-9]\d*)$/.test(key) && Number(key.slice(1)) < headers.length && ['text', 'number'].includes(overrides[key])), 'Kiểu cột không hợp lệ.');
  const columns = headers.map((name, index) => {
    const values = rows.map(row => row[index]).filter(value => value !== null);
    const inferred = values.length > 0 && values.every(numeric) ? 'number' : 'text';
    const type = overrides['c' + index] || inferred;
    check(type !== 'number' || values.every(numeric), 'Cột "' + name + '" chứa giá trị không phải số hợp lệ.');
    return { key: 'c' + index, name, type, nullable: values.length < rows.length, distinct: new Set(rows.map(row => row[index])).size };
  });
  const converted = rows.map(row => row.map((value, index) => value === null ? null : columns[index].type === 'number' ? Number(value) : value));
  for (let index=0; index<columns.length; index++) columns[index].distinct=new Set(converted.map(row=>row[index])).size;
  const digest = sha(JSON.stringify({ columns: columns.map(({ name, type }) => ({ name, type })), rows: converted }));
  return { columns, rows: converted, rowCount: converted.length, bytes, digest };
}
export function previewCSV(payload) {
  const data = inspectCSV(payload);
  return { columns: data.columns, rowCount: data.rowCount, bytes: data.bytes, preview: data.rows.slice(0, 5).map(row => row.map(value => typeof value === 'string' && value.length > 120 ? value.slice(0, 120) + '…' : value)) };
}
