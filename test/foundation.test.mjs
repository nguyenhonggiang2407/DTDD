import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inspectCSV, previewCSV } from '../lib/datasets.mjs';
import { compile } from '../lib/query.mjs';
import { csvFile } from '../lib/csv.mjs';
const parse = (csv) => inspectCSV({ csv });

test('CSV handles BOM, escaped quotes, embedded newlines, NFC and empty rows', () => {
  const data = parse('\ufeffTên,Ghi chú,Số\r\n" Hà ","a""b\r\nc",2\r\n,,\r\nĐà Nẵng,,3\r\n');
  assert.deepEqual(
    data.columns.map((c) => c.name),
    ['Tên', 'Ghi chú', 'Số'],
  );
  assert.deepEqual(data.rows, [
    ['Hà', 'a"b\nc', 2],
    ['Đà Nẵng', null, 3],
  ]);
  assert.equal(data.columns[1].nullable, true);
  assert.equal(data.rowCount, 2);
});
test('type inference preserves zero-padded identifiers and never treats all-null as numeric', () => {
  const data = parse('id,amount,empty,scientific\n001,-12.5,,1e3\n002,0,,2e3\n');
  assert.deepEqual(
    data.columns.map((c) => c.type),
    ['text', 'number', 'text', 'text'],
  );
  assert.deepEqual(data.rows[0], ['001', -12.5, null, '1e3']);
  assert.equal(parse('n\n1\n1.0\n').columns[0].distinct, 1);
  assert.equal(parse('n\n9007199254740992\n').columns[0].type, 'text');
});
test('explicit text types preserve values and invalid numeric overrides are rejected', () => {
  assert.equal(inspectCSV({ csv: 'n\n123\n', types: { c0: 'text' } }).rows[0][0], '123');
  assert.throws(() => inspectCSV({ csv: 'id\n001\n', types: { c0: 'number' } }), /không phải số/);
  for (const types of [{ c00: 'text' }, { c1: 'text' }, { c0: 'sql' }, []])
    assert.throws(() => inspectCSV({ csv: 'n\n1\n', types }));
});
test('headers are unique after trim, casefold and NFC; missing data is rejected', () => {
  for (const csv of [
    'a,A\n1,2\n',
    'a, a \n1,2\n',
    'é,e\u0301\n1,2\n',
    ',b\n1,2\n',
    'a,b\n',
    'a\n\n',
  ]) {
    assert.throws(() => parse(csv));
  }
});
test('invalid widths, controls, malformed quotes and resource limits fail before storage', () => {
  for (const csv of [
    'a,b\n1\n',
    'a\n"x\n',
    'a\n1\u0000\n',
    'a\nx"y\n',
    'a\n"x"z\n',
    'a\n' + 'x'.repeat(2001) + '\n',
  ]) {
    assert.throws(() => parse(csv));
  }
  assert.throws(() => parse('a\n' + 'x'.repeat(524288)), /512 KB/);
  assert.throws(() => parse('a\n' + Array.from({ length: 5001 }, () => 1).join('\n')), /5.000/);
  assert.throws(
    () => parse(Array.from({ length: 31 }, (_, i) => 'c' + i).join(',') + '\n'),
    /30 cột/,
  );
});
test('preview is bounded and canonical digest ignores line endings and outer whitespace', () => {
  const data = previewCSV({
    csv: 'note\n' + Array.from({ length: 8 }, () => 'x'.repeat(130)).join('\n'),
  });
  assert.equal(data.preview.length, 5);
  assert.equal(data.preview[0][0].length, 121);
  assert.equal(parse('a,b\n X ,1\n').digest, parse('\ufeffa,b\r\nX,1\r\n').digest);
  assert.notEqual(
    parse('a\n1\n').digest,
    inspectCSV({ csv: 'a\n1\n', types: { c0: 'text' } }).digest,
  );
});
test('spreadsheet exports quote all fields, guard string formulas and retain negative numbers', () => {
  const out = csvFile([
    ['name', 'value'],
    ['=CMD()', -5],
    ['+SUM(A1)', 'a"b'],
    ['-1', null],
  ]);
  assert.match(out, /"'=CMD\(\)","-5"/);
  assert.match(out, /"'\+SUM/);
  assert.match(out, /"a""b"/);
  assert.match(out, /"'-1",""/);
  assert.equal(out.charCodeAt(0), 0xfeff);
});
const dataset = {
  id: 'dataset-1',
  table: 'd_123',
  columns: [
    { key: 'c0', name: 'area', type: 'text' },
    { key: 'c1', name: 'sales', type: 'number' },
  ],
};
test('compiler binds hostile values and obtains identifiers only from owned schema', () => {
  const query = compile(dataset, { filters: [{ column: 'c0', op: 'eq', value: "' OR 1=1--" }] });
  assert.deepEqual(query.params, ["' OR 1=1--", 50, 0]);
  assert.ok(!query.sql.includes('OR 1=1'));
  assert.throws(() => compile(dataset, { columns: ['c0; DROP TABLE datasets'] }), /không thuộc/);
  assert.throws(() => compile({ ...dataset, table: 'd_x"; DROP TABLE sessions;--' }));
});
test('compiler rejects raw SQL, unsupported operators, invalid limits and output sorts', () => {
  for (const raw of [
    { sql: 'DELETE FROM sessions' },
    { filters: [{ column: 'c0', op: 'between', value: 'x' }] },
    { limit: 0 },
    { limit: 501 },
    { offset: -1 },
    { offset: 5001 },
    { direction: 'desc;drop' },
    { columns: ['c0', 'c0'] },
    { columns: ['c0'], orderBy: 'c1' },
    { groupBy: 'c0' },
    { filters: [{ column: 'c0', op: 'gt', value: '1' }] },
  ]) {
    assert.throws(() => compile(dataset, raw));
  }
});
test('numeric filters and aggregates enforce column types', () => {
  for (const raw of [
    { filters: [{ column: 'c1', op: 'eq', value: '001' }] },
    { filters: [{ column: 'c1', op: 'contains', value: '1' }] },
    { aggregate: { fn: 'sum', column: 'c0' } },
    { aggregate: { fn: 'sum', column: '*' } },
    { aggregate: { fn: 'eval', column: 'c1' } },
  ]) {
    assert.throws(() => compile(dataset, raw));
  }
  const compiled = compile(dataset, {
    groupBy: 'c0',
    aggregate: { fn: 'sum', column: 'c1' },
    orderBy: 'value',
    direction: 'desc',
  });
  assert.match(compiled.sql, /SUM\("c1"\) AS out_1/);
  assert.match(compiled.sql, /GROUP BY "c0" ORDER BY out_1 DESC/);
});
