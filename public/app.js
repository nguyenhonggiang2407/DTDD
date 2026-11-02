const $ = selector => document.querySelector(selector);
const labels = { queued:'Đang chờ', running:'Đang xử lý', retry_wait:'Chờ thử lại', succeeded:'Thành công', failed:'Lỗi', cancelled:'Đã hủy' };
const types = { 'csv-clean':'Làm sạch CSV', 'json-csv':'JSON → CSV', 'text-stats':'Thống kê văn bản' };
const icons = { 'csv-clean':'▦', 'json-csv':'{ }', 'text-stats':'≡' };
const active = ['queued','running','retry_wait'];
const examples = {
  'csv-clean':'Tên,Nhóm,Số lượng\nSổ tay,Văn phòng,2\n Bút xanh ,Văn phòng,5\nSổ tay,Văn phòng,2\n\nBình nước,Đồ dùng,1\n',
  'json-csv':'[\n  {"name":"API notes","done":false},\n  {"name":"Database notes","done":true}\n]',
  'text-stats':'Một công việc rõ ràng bắt đầu từ một ghi chép rõ ràng.\nGhi chép giúp nhóm biết việc nào cần làm tiếp theo.',
};
let state = { csrf:'', paused:false, jobs:[], stats:{total:0}, selected:null, detail:null, signature:'', maxInput:262144 };
let refreshing = false, toastTimer, searchTimer, submitKey = '';
const form = $('#job-form');
function el(tag, className='', text) {
  const element = document.createElement(tag); if(className)element.className=className;
  if(text!==undefined)element.textContent=text;return element;
}
function button(text, className, fn) { const b=el('button',className,text);b.type='button';b.addEventListener('click',fn);return b; }
function badge(status) { return el('span','badge '+status,labels[status]); }
function bytes(n) { return n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KB':(n/1048576).toFixed(1)+' MB'; }
function time(at) { return new Date(at).toLocaleTimeString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',hour12:false,hour:'2-digit',minute:'2-digit',second:'2-digit'}); }
function error(message='') {
  const dialogs=[...document.querySelectorAll('dialog[open]')],target=dialogs.at(-1)?.querySelector('.dialog-error')||$('#app-error');
  target.textContent=message;target.hidden=!message;
}
async function run(task) {
  document.querySelectorAll('.dialog-error,#app-error').forEach(node=>{node.hidden=true;node.textContent='';});
  try {await task();return true;}catch(err){error(err.message||'Không thể kết nối. Vui lòng thử lại.');return false;}
}
function toast(message) {clearTimeout(toastTimer);$('#toast').textContent=message;$('#toast').hidden=false;toastTimer=setTimeout(()=>{$('#toast').hidden=true;},3500);}
async function api(path, method='GET', data, idem) {
  const response=await fetch(path,{method,credentials:'same-origin',headers:{
    'X-CSRF-Token':state.csrf,...(method==='GET'?{}:{'Content-Type':'application/json'}),...(idem?{'Idempotency-Key':idem}:{}),
  },body:method==='GET'?undefined:JSON.stringify(data??{})});
  if(!response.ok){
    let message='Máy chủ chưa phản hồi. Vui lòng thử lại.';
    try{message=(await response.json()).error||message;}catch{}
    if(response.status===409&&message.includes('Idempotency-Key'))message='Yêu cầu trước đã được nhận. Đóng hộp thoại rồi tạo lượt mới nếu muốn gửi dữ liệu khác.';
    const err=new Error(message);err.status=response.status;throw err;
  }
  return response.status===204?null:response.json();
}
function queueBanner() {
  $('#queue-status').textContent=state.paused?'Tạm dừng nhận tác vụ mới':'Đang nhận tác vụ mới';
  $('#queue-description').textContent=state.paused?'Các tác vụ đã nhận vẫn tiếp tục. Tác vụ đang chờ sẽ giữ nguyên.':'Worker xử lý ở nền. Bạn có thể xem nhật ký từng lượt chạy.';
  $('#queue-dot').classList.toggle('paused',state.paused);
  $('#toggle-queue').textContent=state.paused?'Tiếp tục':'Tạm dừng';
  $('#toggle-queue').disabled=false;
}
function empty(title,description,sample=false) {
  const node=el('div','empty');node.append(el('span','','▤'),el('h3','',title),el('p','',description));
  node.append(button(sample?'Thử dữ liệu mẫu →':'Xóa bộ lọc','button',sample?loadDemo:()=>{$('#state-filter').value='';$('#search').value='';run(()=>refresh(true));}));
  return node;
}
function renderRows() {
  for(const [id,value]of[['stat-total',state.stats.total],['stat-active',state.stats.active],['stat-success',state.stats.succeeded],['stat-failed',state.stats.failed],['nav-total',state.stats.total],['nav-success',state.stats.succeeded],['nav-failed',state.stats.failed],['job-count',state.jobs.length]])$('#'+id).textContent=value||0;
  document.querySelectorAll('[data-state]').forEach(b=>{const on=b.dataset.state===$('#state-filter').value;b.classList.toggle('active',on);b.setAttribute('aria-pressed',on);});
  const area=$('#jobs');area.replaceChildren();
  if(!state.jobs.length){area.append(empty(state.stats.total?'Chưa có tác vụ phù hợp.':'Bắt đầu từ một tác vụ nhỏ.',state.stats.total?'Đổi từ khóa hoặc trạng thái để xem các lượt chạy khác.':'Tạo một tác vụ hoặc thử dữ liệu mẫu trước khi gửi tệp của bạn.',!state.stats.total));return;}
  for(const job of state.jobs){
    const article=el('article','job-row'),row=button('','row-main',()=>run(()=>openJob(job.id)));
    row.setAttribute('aria-label','Xem '+job.title);
    const name=el('div','job-name'),text=el('div');
    text.append(el('strong','',job.title),el('small','',types[job.type]+' · '+bytes(job.inputBytes)));
    name.append(el('span','type-icon',icons[job.type]),text);
    const attempt=el('span','attempt',job.attempts+' / '+job.maxAttempts);attempt.append(el('small','','lượt thử'));
    row.append(name,badge(job.status),attempt,el('span','job-time',time(job.created)));article.append(row);area.append(article);
  }
}
async function refresh(force=false) {
  if(refreshing)return;refreshing=true;
  try {
    const params=new URLSearchParams({q:$('#search').value,state:$('#state-filter').value});
    const snapshot=await api('/api/queue?'+params);
    state.jobs=snapshot.jobs;state.stats=snapshot.stats;state.paused=snapshot.paused;
    queueBanner();renderRows();
    $('#sync-status').textContent='Cập nhật '+time(snapshot.serverTime);
    $('#sync-status').classList.remove('offline');
    if(state.selected&&$('#job-dialog').open){
      const latest=state.jobs.find(j=>j.id===state.selected);
      if(force||!state.detail||latest?.version!==state.detail.version||active.includes(state.detail.status))await loadDetail(state.selected);
    }
  }finally{refreshing=false;}
}
async function loadDetail(id) {
  try {
    const data=await api('/api/jobs/'+encodeURIComponent(id));
    state.detail=data.job;
    const signature=data.job.id+':'+data.job.version+':'+data.events.length;
    if(signature!==state.signature){state.signature=signature;renderDetail(data);}
  }catch(err){if(err.status===404){$('#job-dialog').close();state.selected=null;state.detail=null;state.signature='';}else throw err;}
}
async function openJob(id) {
  state.selected=id;state.signature='';
  await loadDetail(id);if(state.detail&&!$('#job-dialog').open)$('#job-dialog').showModal();
}
function metric(parent,label,value) {const box=el('div');box.append(el('strong','',value),el('small','',label));parent.append(box);}
function renderResult(job) {
  $('#result').hidden=!job.result;
  if(!job.result)return;
  const result=job.result,report=result.report;
  $('#download-result').href='/api/jobs/'+job.id+'/download';$('#download-result').download=result.filename;
  $('#result-info').textContent=result.filename+' · '+bytes(result.bytes)+(job.durationMs!==null?' · '+job.durationMs+' ms':'');
  $('#result-sha').textContent=result.sha256;
  const summary=$('#result-summary');summary.replaceChildren();
  const preview=$('#result-preview');preview.replaceChildren();
  if(report.kind==='table'){
    metric(summary,'dòng đầu vào',report.rowsRead);metric(summary,'dòng đầu ra',report.rowsWritten);metric(summary,'cột',report.columns.length);
    if(report.duplicatesRemoved!==undefined)metric(summary,'dòng trùng đã bỏ',report.duplicatesRemoved);
    if(report.blankRowsRemoved!==undefined)metric(summary,'dòng trống đã bỏ',report.blankRowsRemoved);
    metric(summary,'ô còn trống',report.emptyCells);
    preview.append(el('p','hint','Tối đa 8 dòng đầu. Ô dài được rút gọn; tệp tải xuống giữ nội dung đầy đủ.'));
    const table=el('table'),head=el('thead'),tr=el('tr');
    for(const column of report.columns)tr.append(el('th','',column));head.append(tr);table.append(head);
    const body=el('tbody');for(const row of report.preview||[]){const r=el('tr');for(const cell of row)r.append(el('td','',cell));body.append(r);}table.append(body);preview.append(table);
  }else{
    metric(summary,'từ',report.words);metric(summary,'dòng',report.lines);metric(summary,'ký tự',report.characters);metric(summary,'từ khác nhau',report.uniqueWords);
    const max=Math.max(1,report.topWords[0]?.count||0);
    for(const word of report.topWords){const row=el('div','word-row'),progress=el('progress');progress.max=max;progress.value=word.count;progress.setAttribute('aria-label',word.word+': '+word.count+' lần');row.append(el('span','',word.word),progress,el('span','',word.count));preview.append(row);}
    if(!report.topWords.length)preview.append(el('p','hint','Không có token chữ hoặc số trong văn bản.'));
  }
}
function confirmAction(title,description,label,task) {
  $('#confirm-title').textContent=title;$('#confirm-description').textContent=description;$('#confirm-action').textContent=label;
  $('#confirm-dialog .dialog-error').hidden=true;$('#confirm-dialog').showModal();
  $('#confirm-action').onclick=async()=>{
    $('#confirm-action').disabled=true;
    await run(async()=>{await task();$('#confirm-dialog').close();});
    $('#confirm-action').disabled=false;
  };
}
function renderDetail(data) {
  const job=data.job;$('#detail-title').textContent=job.title;$('#detail-type').textContent=types[job.type].toUpperCase();
  $('#detail-status').replaceChildren(badge(job.status));$('#detail-id').textContent=job.id;
  $('#detail-attempt').textContent='Lượt '+job.attempts+' / '+job.maxAttempts;
  $('#detail-input').textContent=job.input;
  const tools=$('#detail-tools');tools.replaceChildren();
  if(active.includes(job.status))tools.append(button('Hủy tác vụ','button danger',()=>confirmAction('Hủy tác vụ này?','Tác vụ đang xử lý sẽ dừng và không ghi kết quả mới. Bạn có thể tạo một lượt chạy khác.','Hủy tác vụ',async()=>{
    await api('/api/jobs/'+job.id+'/cancel','POST',{version:job.version});await loadDetail(job.id);await refresh(true);toast('Đã hủy tác vụ.');
  })));
  if(['failed','cancelled'].includes(job.status)){
    tools.append(button('Sửa và gửi mới','button primary',()=>{$('#job-dialog').close();openCreate(job);}),
      button('Chạy lại nguyên dữ liệu','button',()=>run(async()=>{
        const result=await api('/api/jobs/'+job.id+'/retry','POST',{version:job.version},crypto.randomUUID());
        await refresh();await openJob(result.job.id);toast('Đã tạo một lượt chạy mới, giữ nguyên lịch sử lượt trước.');
      })));
  }else tools.append(button('Tạo lượt tương tự','button',()=>{$('#job-dialog').close();openCreate(job);}));
  if(!active.includes(job.status))tools.append(button('Xóa bản ghi','button danger',()=>confirmAction('Xóa bản ghi này?','Tác vụ, dữ liệu và nhật ký sẽ được xóa khỏi workspace. Hãy tải kết quả cần giữ trước khi tiếp tục.','Xóa bản ghi',async()=>{
    await api('/api/jobs/'+job.id,'DELETE',{version:job.version});$('#job-dialog').close();state.selected=null;state.detail=null;state.signature='';await refresh(true);toast('Đã xóa bản ghi.');
  })));
  const failure=$('#detail-failure');failure.hidden=!job.error;
  if(job.error){failure.replaceChildren(el('strong','',job.error.code),el('p','',job.error.message));if(job.status==='retry_wait')failure.append(el('p','hint','Sẽ thử lại sau '+time(job.nextRun)+'.'));}
  renderResult(job);
  const timeline=$('#timeline');timeline.replaceChildren();
  for(const event of data.events){const row=el('div','event '+event.kind);row.append(el('time','',time(event.at)),el('span','',event.message));timeline.append(row);}
}
function updateSize() {
  const n=new TextEncoder().encode($('#job-input').value).length;
  $('#input-size').textContent=bytes(n)+' / 256 KB';
}
function updateType() {$('#csv-options').hidden=$('#job-type').value!=='csv-clean';updateSize();}
function openCreate(source=null) {
  form.reset();submitKey=crypto.randomUUID();
  if(source){$('#job-title').value=source.title.slice(0,120);$('#job-type').value=source.type;$('#job-input').value=source.input;$('#trim').checked=source.options.trim;$('#dedupe').checked=source.options.dedupe;}
  $('#submit-hint').textContent=state.paused?'Hàng đợi đang tạm dừng. Tác vụ mới sẽ chờ đến khi bạn tiếp tục.':'Nội dung chỉ được xử lý trên máy chủ chạy ứng dụng này.';
  $('#create-dialog .dialog-error').hidden=true;updateType();$('#create-dialog').showModal();
}
async function loadDemo() {
  $('#load-demo').disabled=true;
  await run(async()=>{
    const result=await api('/api/demo','POST');await refresh(true);
    toast(result.added?(result.paused?'Đã thêm dữ liệu mẫu. Bấm “Tiếp tục” để bắt đầu xử lý.':'Đã thêm dữ liệu mẫu.'):'Bộ dữ liệu mẫu đã được thêm trước đó.');
  });$('#load-demo').disabled=false;
}
$('#new-job').addEventListener('click',()=>openCreate());
$('#load-demo').addEventListener('click',loadDemo);
$('#toggle-queue').addEventListener('click',async()=>{
  $('#toggle-queue').disabled=true;
  await run(async()=>{await api('/api/queue/pause','POST',{paused:!state.paused});await refresh(true);});
  $('#toggle-queue').disabled=false;
});
$('#refresh').addEventListener('click',()=>run(()=>refresh(true)));
$('#state-filter').addEventListener('change',()=>run(()=>refresh(true)));
$('#search').addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>run(()=>refresh(true)),200);});
document.querySelectorAll('[data-state]').forEach(b=>b.addEventListener('click',()=>{$('#state-filter').value=b.dataset.state;$('#search').value='';run(()=>refresh(true));}));
document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>$('#'+b.dataset.close).close()));
$('#job-dialog').addEventListener('close',()=>{state.selected=null;state.detail=null;state.signature='';});
$('#job-type').addEventListener('change',updateType);
$('#job-input').addEventListener('input',updateSize);
$('#fill-example').addEventListener('click',()=>{$('#job-input').value=examples[$('#job-type').value];updateSize();});
$('#file-input').addEventListener('change',()=>run(async()=>{
  const file=$('#file-input').files[0];if(!file)return;
  if(file.size>state.maxInput)throw Error('Tệp vượt quá 256 KB.');
  let text;try{text=new TextDecoder('utf-8',{fatal:true}).decode(await file.arrayBuffer());}catch{throw Error('Tệp cần được lưu bằng UTF-8.');}
  $('#job-input').value=text;
  if(!$('#job-title').value)$('#job-title').value=file.name.slice(0,100);
  if(/\.csv$/i.test(file.name))$('#job-type').value='csv-clean';
  else if(/\.json$/i.test(file.name))$('#job-type').value='json-csv';
  else $('#job-type').value='text-stats';
  updateType();
}));
form.addEventListener('submit',async event=>{
  event.preventDefault();$('#submit-job').disabled=true;
  await run(async()=>{
    if(new TextEncoder().encode($('#job-input').value).length>state.maxInput)throw Error('Dữ liệu vượt quá 256 KB.');
    const payload={title:$('#job-title').value,type:$('#job-type').value,input:$('#job-input').value,options:{trim:$('#trim').checked,dedupe:$('#dedupe').checked}};
    const result=await api('/api/jobs','POST',payload,submitKey);
    $('#create-dialog').close();await refresh();await openJob(result.job.id);toast(result.replayed?'Đây là tác vụ đã được nhận trước đó.':'Đã đưa tác vụ vào hàng đợi.');
  });$('#submit-job').disabled=false;
});
run(async()=>{
  const workspace=await api('/api/workspace');state.csrf=workspace.csrf;state.maxInput=workspace.maxInputBytes;state.paused=workspace.paused;
  await refresh();document.body.dataset.ready='true';
  setInterval(()=>{if(!document.hidden)refresh().catch(()=>{$('#sync-status').textContent='Mất kết nối · đang thử lại';$('#sync-status').classList.add('offline');});},1500);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)run(()=>refresh(true));});
});
