export class InputError extends Error {
  constructor(code, message) { super(message); this.code = code; this.retryable = false; }
}
function ensure(condition, code, message) { if (!condition) throw new InputError(code, message); }
export function parseCSV(source) {
  source = source.replace(/^\uFEFF/, '');
  const rows = []; let row = [], field = '', quoted = false, afterQuote = false, touched = false;
  function pushField() { row.push(field); field = ''; afterQuote = false; ensure(row.length <= 30, 'CSV_COLUMNS', 'CSV tối đa 30 cột.'); }
  function pushRow() { pushField(); rows.push(row); row = []; touched = false; ensure(rows.length <= 5001, 'CSV_ROWS', 'CSV tối đa 5.000 dòng dữ liệu.'); }
  for (let i = 0; i < source.length; i++) {
    const c = source[i]; touched = true;
    if (quoted) {
      if (c === '"') {
        if (source[i + 1] === '"') { field += '"'; i++; } else { quoted = false; afterQuote = true; }
      } else field += c;
      continue;
    }
    if (c === ',') { pushField(); continue; }
    if (c === '\n' || c === '\r') { if (c === '\r' && source[i + 1] === '\n') i++; pushRow(); continue; }
    if (afterQuote) { ensure(c === ' ' || c === '\t', 'CSV_QUOTES', 'Có ký tự không hợp lệ sau dấu ngoặc kép.'); continue; }
    if (c === '"') { ensure(field.length === 0, 'CSV_QUOTES', 'Dấu ngoặc kép cần bắt đầu một ô CSV.'); quoted = true; }
    else field += c;
  }
  ensure(!quoted, 'CSV_QUOTES', 'CSV có dấu ngoặc kép chưa đóng.');
  if (touched || row.length || field || afterQuote) pushRow();
  ensure(rows.length > 0, 'CSV_EMPTY', 'CSV chưa có tiêu đề cột.');
  return rows;
}
export function csvCell(value) {
  let output = String(value ?? '');
  if (typeof value === 'string' && /^[=+\-@\t\r]/.test(output)) output = "'" + output;
  return '"' + output.replaceAll('"', '""') + '"';
}
export function csvFile(rows) { return '\ufeff' + rows.map(row => row.map(csvCell).join(',')).join('\r\n') + '\r\n'; }
