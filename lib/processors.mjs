import { sha } from './validation.mjs';
export class InputError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
    this.retryable = false;
  }
}
function ensure(condition, code, message) {
  if (!condition) throw new InputError(code, message);
}
export function parseCSV(source) {
  source = source.replace(/^\uFEFF/, '');
  const rows = [];
  let row = [],
    field = '',
    quoted = false,
    afterQuote = false,
    touched = false;
  function pushField() {
    row.push(field);
    field = '';
    afterQuote = false;
    ensure(row.length <= 30, 'CSV_COLUMNS', 'CSV tối đa 30 cột.');
  }
  function pushRow() {
    pushField();
    rows.push(row);
    row = [];
    touched = false;
    ensure(rows.length <= 5001, 'CSV_ROWS', 'CSV tối đa 5.000 dòng dữ liệu.');
  }
  for (let i = 0; i < source.length; i++) {
    const c = source[i];
    touched = true;
    if (quoted) {
      if (c === '"') {
        if (source[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          quoted = false;
          afterQuote = true;
        }
      } else field += c;
      continue;
    }
    if (c === ',') {
      pushField();
      continue;
    }
    if (c === '\n' || c === '\r') {
      if (c === '\r' && source[i + 1] === '\n') i++;
      pushRow();
      continue;
    }
    if (afterQuote) {
      ensure(c === ' ' || c === '\t', 'CSV_QUOTES', 'Có ký tự không hợp lệ sau dấu ngoặc kép.');
      continue;
    }
    if (c === '"') {
      ensure(field.length === 0, 'CSV_QUOTES', 'Dấu ngoặc kép cần bắt đầu một ô CSV.');
      quoted = true;
    } else field += c;
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
export function csvFile(rows) {
  return '\ufeff' + rows.map((row) => row.map(csvCell).join(',')).join('\r\n') + '\r\n';
}
function artifact(filename, mime, content, report) {
  return {
    filename,
    mime,
    content,
    bytes: Buffer.byteLength(content),
    sha256: sha(content),
    report,
  };
}
function cleanCSV(job, stage) {
  stage('Đọc cấu trúc CSV');
  const rows = parseCSV(job.input),
    headers = rows[0].map((h) => h.trim().normalize('NFC'));
  ensure(
    headers.every((h) => h && h.length <= 80 && !/[\r\n]/.test(h)),
    'CSV_HEADER',
    'Tên cột cần 1–80 ký tự trên một dòng.',
  );
  ensure(
    new Set(headers).size === headers.length,
    'CSV_HEADER',
    'Tên cột CSV bị trùng sau khi bỏ khoảng trắng.',
  );
  stage('Chuẩn hóa và kiểm tra từng dòng');
  const data = [],
    seen = new Set();
  let duplicatesRemoved = 0,
    blankRowsRemoved = 0;
  for (const row of rows.slice(1)) {
    if (row.every((cell) => !cell.trim())) {
      blankRowsRemoved++;
      continue;
    }
    ensure(row.length === headers.length, 'CSV_WIDTH', 'Số ô ở một dòng không khớp tiêu đề cột.');
    const values = job.options.trim ? row.map((v) => v.trim()) : row;
    const key = JSON.stringify(values);
    if (job.options.dedupe && seen.has(key)) {
      duplicatesRemoved++;
      continue;
    }
    seen.add(key);
    data.push(values);
  }
  stage('Tạo tệp kết quả');
  return artifact('cleaned-data.csv', 'text/csv', csvFile([headers, ...data]), {
    kind: 'table',
    rowsRead: rows.length - 1,
    rowsWritten: data.length,
    duplicatesRemoved,
    blankRowsRemoved,
    emptyCells: data.flat().filter((v) => v === '').length,
    columns: headers,
    preview: data.slice(0, 8).map((row) => row.map((v) => String(v).slice(0, 200))),
  });
}
function jsonCSV(job, stage) {
  stage('Đọc JSON');
  let rows;
  try {
    rows = JSON.parse(job.input.replace(/^\uFEFF/, ''));
  } catch {
    throw new InputError('JSON_PARSE', 'JSON không hợp lệ.');
  }
  ensure(
    Array.isArray(rows) && rows.length > 0 && rows.length <= 5000,
    'JSON_ROWS',
    'Cần một mảng từ 1 đến 5.000 object.',
  );
  const columns = new Set();
  let nodes = 0;
  function inspect(value, depth) {
    ensure(depth <= 8 && ++nodes <= 150000, 'JSON_DEPTH', 'JSON quá sâu hoặc quá nhiều phần tử.');
    if (value && typeof value === 'object')
      Object.values(value).forEach((v) => inspect(v, depth + 1));
  }
  stage('Gom các cột dữ liệu');
  for (const row of rows) {
    ensure(
      row && typeof row === 'object' && !Array.isArray(row),
      'JSON_OBJECT',
      'Mỗi dòng cần là một object.',
    );
    inspect(row, 0);
    for (const name of Object.keys(row)) {
      ensure(
        name.length > 0 && name.length <= 80 && !/[\u0000-\u001f]/.test(name),
        'JSON_COLUMN',
        'Tên thuộc tính JSON không hợp lệ.',
      );
      columns.add(name);
    }
    ensure(columns.size <= 30, 'JSON_COLUMNS', 'Tối đa 30 cột sau khi gom thuộc tính.');
  }
  ensure(columns.size > 0, 'JSON_COLUMN', 'Chưa có thuộc tính để tạo cột.');
  const names = [...columns],
    values = rows.map((row) =>
      names.map((name) => {
        const value = Object.hasOwn(row, name) ? row[name] : '';
        return value && typeof value === 'object' ? JSON.stringify(value) : (value ?? '');
      }),
    );
  stage('Tạo tệp CSV');
  return artifact('converted-data.csv', 'text/csv', csvFile([names, ...values]), {
    kind: 'table',
    rowsRead: rows.length,
    rowsWritten: values.length,
    columns: names,
    emptyCells: values.flat().filter((v) => v === '').length,
    preview: values.slice(0, 8).map((row) => row.map((v) => String(v).slice(0, 200))),
  });
}
function textStats(job, stage) {
  stage('Tách các từ trong văn bản');
  const words =
    job.input
      .normalize('NFC')
      .toLowerCase()
      .match(/[\p{L}\p{N}]+/gu) || [];
  ensure(words.length <= 20000, 'TEXT_WORDS', 'Văn bản tối đa 20.000 từ.');
  const frequencies = new Map();
  for (const word of words) frequencies.set(word, (frequencies.get(word) || 0) + 1);
  stage('Đếm tần suất');
  const topWords = [...frequencies]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'vi'))
    .slice(0, 10)
    .map(([word, count]) => ({ word, count }));
  const report = {
    kind: 'text',
    characters: [...job.input].length,
    words: words.length,
    lines: job.input.split(/\r\n|\r|\n/).length,
    uniqueWords: frequencies.size,
    topWords,
  };
  stage('Tạo báo cáo văn bản');
  const md = [
    '# Thống kê văn bản · YangQueue',
    '',
    '- Ký tự: ' + report.characters,
    '- Từ: ' + report.words,
    '- Dòng: ' + report.lines,
    '- Từ khác nhau: ' + report.uniqueWords,
    '',
    '| Từ | Số lần |',
    '|---|---:|',
    ...topWords.map((w) => '| ' + w.word + ' | ' + w.count + ' |'),
    '',
    'Đếm theo token chữ/số, không phải tóm tắt nội dung bằng mô hình.',
    '',
  ].join('\n');
  return artifact('text-report.md', 'text/markdown', md, report);
}
export function processJob(job, stage = () => {}) {
  if (job.type === 'csv-clean') return cleanCSV(job, stage);
  if (job.type === 'json-csv') return jsonCSV(job, stage);
  if (job.type === 'text-stats') return textStats(job, stage);
  throw new InputError('JOB_TYPE', 'Loại tác vụ không được hỗ trợ.');
}
