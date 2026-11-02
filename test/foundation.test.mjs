import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname, basename } from 'node:path';
import { Store, LEASE_MS } from '../lib/store.mjs';
import { Queue } from '../lib/queue.mjs';
import { parseCSV, processJob, csvCell } from '../lib/processors.mjs';
import { submission, sha } from '../lib/validation.mjs';
const data = (extra = {}) => ({ title: 'CSV clean', type: 'csv-clean', input: 'name\nA\nA\n', options: {}, ...extra });
const job = (type, input, options = {}) => submission({ title: 'Test job', type, input, options });
function fixture() {
  let now = 100000;
  const store = new Store(':memory:', () => now), owner = store.newSession().session.id;
  return { store, owner, advance: ms => { now += ms; } };
}
async function until(fn, limit = 4000) {
  const start = Date.now();
  while (!fn()) { if (Date.now() - start > limit) throw Error('Timed out'); await new Promise(r => setTimeout(r, 10)); }
}
class FakeWorker extends EventEmitter { terminated = false; async terminate() { this.terminated = true; return 0; } }
test('CSV reads BOM, CRLF, quoted commas, multiline fields and escaped quotes', () => {
  assert.deepEqual(parseCSV('\ufeffname,note\r\n"Giang, N.","two\nlines"\r\n"A ""quote""",ok\r\n'), [['name','note'],['Giang, N.','two\nlines'],['A "quote"','ok']]);
});
test('cleaning reports rows, duplicates, blanks and verified output hash', () => {
  const output = processJob(job('csv-clean', 'name,group\n A ,x\nA,x\n\nB,\n'));
  assert.equal(output.report.rowsRead,4); assert.equal(output.report.rowsWritten,2);
  assert.equal(output.report.duplicatesRemoved,1); assert.equal(output.report.blankRowsRemoved,1);
  assert.equal(output.report.emptyCells,1); assert.equal(output.sha256,sha(output.content));
});
test('processing flags preserve requested whitespace and duplicates', () => {
  const output = processJob(job('csv-clean','name\n A \n A \n',{trim:false,dedupe:false}));
  assert.deepEqual(output.report.preview,[[' A '],[' A ']]);
});
test('malformed quotes, widths and duplicate headers are permanent errors', () => {
  for (const input of ['a,b\n"x,y','a,b\nx',' a ,a\nx,y','a,b\n"x"oops,y'])
    assert.throws(()=>processJob(job('csv-clean',input)),err=>err.retryable===false);
});
test('CSV neutralizes formula-like strings and retains numeric JSON values', () => {
  assert.equal(csvCell('=HYPERLINK("bad")'),'"\'=HYPERLINK(""bad"")"');
  assert.equal(csvCell('@cmd'),'"\'@cmd"'); assert.equal(csvCell(-3),'"-3"');
});
test('JSON union keys retain booleans, nested values and missing cells', () => {
  const o=processJob(job('json-csv','[{"name":"A","done":false},{"name":"B","tags":["x"],"done":true}]'));
  assert.deepEqual(o.report.columns,['name','done','tags']); assert.equal(o.report.emptyCells,1);
  assert.match(o.content,/"false"/); assert.match(o.content,/\[""x""\]/);
});
test('prototype-like JSON properties remain plain data', () => {
  const o=processJob(job('json-csv','[{"__proto__":{"polluted":true},"constructor":"value"}]'));
  assert.ok(o.report.columns.includes('__proto__')); assert.equal({}.polluted,undefined);
});
test('JSON rejects invalid shapes, excessive columns and depth', () => {
  for (const input of ['{','{}','[1]','[]',JSON.stringify([Object.fromEntries(Array.from({length:31},(_,i)=>['c'+i,i]))]),'['+'{"x":'.repeat(10)+'1'+'}'.repeat(10)+']'])
    assert.throws(()=>processJob(job('json-csv',input)),err=>err.retryable===false);
});
test('Vietnamese text report counts words, lines and frequencies', () => {
  const o=processJob(job('text-stats','Đỏ xanh đỏ.\nXanh đỏ 😀'));
  assert.equal(o.report.words,5); assert.equal(o.report.lines,2);
  assert.equal(o.report.topWords[0].word,'đỏ'); assert.equal(o.report.topWords[0].count,3);
});
test('submission rejects oversized input, NUL and nonboolean flags', () => {
  assert.throws(()=>job('unknown','a')); assert.throws(()=>job('text-stats','a'.repeat(262145)));
  assert.throws(()=>job('csv-clean','a',{trim:'false'})); assert.throws(()=>job('text-stats','a\u0000b'));
});
test('idempotency is owner-scoped and conflicts when payload changes', () => {
  const {store,owner}=fixture();
  try {
    const a=store.create(owner,data(),'idempotent-key'),b=store.create(owner,data({options:{trim:true,dedupe:true}}),'idempotent-key');
    assert.equal(a.job.id,b.job.id); assert.equal(b.replayed,true);
    assert.throws(()=>store.create(owner,data({title:'Changed'}),'idempotent-key'),{status:409});
    assert.notEqual(store.create(store.newSession().session.id,data(),'idempotent-key').job.id,a.job.id);
  } finally {store.close();}
});
test('finished deletion keeps idempotency tombstone', () => {
  const {store,owner}=fixture();
  try {
    const {job}=store.create(owner,data(),'tombstone-key'),cancelled=store.cancel(owner,job.id,job.version);
    store.remove(owner,job.id,cancelled.version);
    assert.throws(()=>store.create(owner,data(),'tombstone-key'),{status:410});
  } finally {store.close();}
});
test('FIFO respects one running task per workspace', () => {
  const {store,owner}=fixture();
  try {
    const other=store.newSession().session.id,a=store.create(owner,data(),'fifo-key-a').job,b=store.create(owner,data(),'fifo-key-b').job,c=store.create(other,data(),'fifo-key-c').job;
    const ca=store.claim(),cc=store.claim();
    assert.equal(ca.id,a.id); assert.equal(cc.id,c.id); assert.equal(store.claim(),null);
    store.complete(ca.id,ca.token,processJob(ca)); assert.equal(store.claim().id,b.id);
  } finally {store.close();}
});
test('pause does not block other workspaces', () => {
  const {store,owner}=fixture();
  try {
    const other=store.newSession().session.id;
    store.create(owner,data(),'pause-key-a'); const b=store.create(other,data(),'pause-key-b').job;
    store.pause(owner,true); assert.equal(store.claim().id,b.id); assert.equal(store.claim(),null);
    store.pause(owner,false); assert.ok(store.claim());
  } finally {store.close();}
});
test('retry backoff is bounded and permanent errors stop immediately', () => {
  const {store,owner,advance}=fixture();
  try {
    const {job}=store.create(owner,data(),'backoff-key',{maxAttempts:2}),a=store.claim();
    store.fail(a.id,a.token,{code:'TEMP',message:'Temporary',retryable:true});
    assert.equal(store.get(owner,job.id).status,'retry_wait'); assert.equal(store.claim(),null);
    advance(1000); const b=store.claim();
    store.fail(b.id,b.token,{code:'TEMP',message:'Temporary',retryable:true}); advance(60000);
    assert.equal(store.get(owner,job.id).status,'failed'); assert.equal(store.claim(),null);
  } finally {store.close();}
});
test('running cancellation rejects stale versions and late results', () => {
  const {store,owner}=fixture();
  try {
    const {job}=store.create(owner,data(),'cancel-key'),a=store.claim();
    assert.throws(()=>store.cancel(owner,job.id,job.version),{status:409});
    store.cancel(owner,job.id,store.get(owner,job.id).version);
    assert.equal(store.complete(job.id,a.token,processJob(a)),false); assert.equal(store.heartbeat(job.id,a.token),false);
  } finally {store.close();}
});
test('heartbeat extends lease without increasing user-visible version', () => {
  const {store,owner,advance}=fixture();
  try {
    const {job}=store.create(owner,data(),'heartbeat-key'),a=store.claim(),v=store.get(owner,job.id).version;
    advance(LEASE_MS-1); assert.equal(store.heartbeat(job.id,a.token),true);
    advance(2); assert.equal(store.recoverExpired(),0); assert.equal(store.get(owner,job.id).version,v);
    assert.equal(store.complete(job.id,a.token,processJob(a)),true);
  } finally {store.close();}
});
test('hard restart recovers expired lease and fences the previous token', () => {
  const dir=mkdtempSync(join(tmpdir(),'yq-test-')); let now=100000,a,b;
  try {
    a=new Store(join(dir,'q.sqlite'),()=>now); const owner=a.newSession().session.id,{job}=a.create(owner,data(),'recover-key'),old=a.claim();
    a.close();a=null;now+=LEASE_MS+1;b=new Store(join(dir,'q.sqlite'),()=>now);
    assert.equal(b.recoverExpired(),1);now+=1000;const fresh=b.claim();
    assert.notEqual(fresh.token,old.token);assert.equal(b.complete(old.id,old.token,processJob(old)),false);
    assert.equal(b.complete(fresh.id,fresh.token,processJob(fresh)),true);assert.equal(b.get(owner,job.id).attempts,2);
  } finally {
    if(a)a.close();if(b)b.close();
    const target=resolve(dir);assert.equal(dirname(target),resolve(tmpdir()));assert.ok(basename(target).startsWith('yq-test-'));rmSync(target,{recursive:true,force:true});
  }
});
test('checksum mismatches cannot become successful output', () => {
  const {store,owner}=fixture();
  try {
    const {job}=store.create(owner,data(),'checksum-key'),a=store.claim(),out=processJob(a);out.sha256='bad';
    assert.throws(()=>store.complete(job.id,a.token,out));assert.equal(store.get(owner,job.id).status,'running');
  } finally {store.close();}
});
test('manual retry creates a child and preserves failure history', () => {
  const {store,owner}=fixture();
  try {
    const {job}=store.create(owner,data(),'original-key'),a=store.claim();
    store.fail(a.id,a.token,{code:'INPUT',message:'Bad input',retryable:false});
    const failed=store.get(owner,job.id),child=store.retry(owner,job.id,failed.version,'child-run-key').job;
    assert.equal(child.parentId,job.id);assert.equal(child.attempts,0);
    assert.equal(store.get(owner,job.id).error.code,'INPUT');
    assert.equal(store.retry(owner,job.id,failed.version,'child-run-key').replayed,true);
  } finally {store.close();}
});
test('owner boundaries, active quota and expiry cleanup include events and keys', () => {
  const {store,owner,advance}=fixture();
  try {
    const other=store.newSession().session.id;let first;
    for(let i=0;i<10;i++){const item=store.create(owner,data(),'quota-key-'+i).job;if(i===0)first=item;}
    assert.throws(()=>store.create(owner,data(),'overflow-key'),{status:409});
    assert.throws(()=>store.get(other,first.id),{status:404});assert.throws(()=>store.events(other,first.id),{status:404});
    advance(7*86400000+1);store.newSession();
    assert.equal(store.db.prepare('SELECT count(*) n FROM jobs WHERE owner=?').get(owner).n,0);
    assert.equal(store.db.prepare('SELECT count(*) n FROM idempotency WHERE owner=?').get(owner).n,0);
  } finally {store.close();}
});
test('real worker produces persisted output and phase events', async () => {
  const store=new Store(':memory:'),owner=store.newSession().session.id,queue=new Queue(store,{intervalMs:10});
  try {
    const {job}=store.create(owner,data(),'real-worker-key');queue.start();
    await until(()=>store.get(owner,job.id).status==='succeeded');
    assert.equal(store.get(owner,job.id).result.report.duplicatesRemoved,1);assert.ok(store.events(owner,job.id).some(e=>e.kind==='stage'));
  } finally {await queue.close();store.close();}
});
test('timeout stops a stuck worker with bounded attempts', async () => {
  const store=new Store(':memory:'),owner=store.newSession().session.id,worker=new FakeWorker(),queue=new Queue(store,{intervalMs:5,timeoutMs:20,spawn:()=>worker});
  try {
    const {job}=store.create(owner,data(),'timeout-key',{maxAttempts:1});queue.start();
    await until(()=>store.get(owner,job.id).status==='failed');
    assert.equal(worker.terminated,true);assert.equal(store.get(owner,job.id).error.code,'JOB_TIMEOUT');
  } finally {await queue.close();store.close();}
});
test('temporary worker failure retries successfully', async () => {
  let now=100000,calls=0;
  const store=new Store(':memory:',()=>now),owner=store.newSession().session.id,queue=new Queue(store,{intervalMs:5,spawn:job=>{
    const w=new FakeWorker();calls++;queueMicrotask(()=>w.emit('message',calls===1?{kind:'failure',error:{code:'TEMP',message:'Temporary',retryable:true}}:{kind:'result',output:processJob(job)}));return w;
  }});
  try {
    const {job}=store.create(owner,data(),'transient-key');queue.start();
    await until(()=>store.get(owner,job.id).status==='retry_wait');now+=1000;queue.tick();
    await until(()=>store.get(owner,job.id).status==='succeeded');assert.equal(store.get(owner,job.id).attempts,2);
  } finally {await queue.close();store.close();}
});
test('shutdown returns unfinished work to retry state', async () => {
  const store=new Store(':memory:'),owner=store.newSession().session.id,w=new FakeWorker(),queue=new Queue(store,{spawn:()=>w});
  try {
    const {job}=store.create(owner,data(),'shutdown-key');queue.start();await queue.close();
    assert.equal(store.get(owner,job.id).status,'retry_wait');assert.equal(w.terminated,true);
  } finally {await queue.close();store.close();}
});
