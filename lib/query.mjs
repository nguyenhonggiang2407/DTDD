import {object,check,quote,numeric} from './validation.mjs';
const OPS={eq:'=',ne:'<>',gt:'>',gte:'>=',lt:'<',lte:'<='};
const FUNCTIONS={count:'COUNT',sum:'SUM',avg:'AVG',min:'MIN',max:'MAX'};
export function compile(dataset,raw={}){
 object(raw);
 const allowed=['datasetId','columns','filters','groupBy','aggregate','orderBy','direction','limit','offset'];
 check(Object.keys(raw).every(k=>allowed.includes(k)),'Chỉ hỗ trợ truy vấn qua trình thiết kế; không nhận SQL tự do.');
 const fields=dataset.columns,find=key=>{const f=fields.find(c=>c.key===key);check(f,'Cột không thuộc tập dữ liệu.');return f;};
 const filters=raw.filters??[];check(Array.isArray(filters)&&filters.length<=8,'Tối đa 8 điều kiện AND.');
 const params=[],where=[];
 for(const filter of filters){
  object(filter);const col=find(filter.column),name=quote(col.key),op=filter.op;
  if(op==='isnull'||op==='notnull'){where.push(name+(op==='isnull'?' IS NULL':' IS NOT NULL'));continue;}
  check(Object.hasOwn(OPS,op)||op==='contains'||op==='starts','Toán tử lọc không hợp lệ.');
  const value=filter.value;check(typeof value==='string'||typeof value==='number','Giá trị lọc không hợp lệ.');
  if(op==='contains'||op==='starts'){
   check(col.type==='text','Chỉ tìm chuỗi trên cột văn bản.');const s=String(value).normalize('NFC');check(s.length>0&&s.length<=200,'Giá trị tìm cần 1–200 ký tự.');
   const escaped=s.replace(/[\\%_]/g,c=>'\\'+c);where.push(name+" LIKE ? ESCAPE '\\'");params.push((op==='contains'?'%':'')+escaped+'%');
  }else{
   check(!['gt','gte','lt','lte'].includes(op)||col.type==='number','So sánh lớn/nhỏ chỉ dùng cho cột số.');
   check(col.type!=='number'||numeric(value),'Giá trị lọc cần là số hợp lệ.');
   check(String(value).length<=200,'Giá trị lọc tối đa 200 ký tự.');
   where.push(name+' '+OPS[op]+' ?');params.push(col.type==='number'?Number(value):String(value).normalize('NFC'));
  }
 }
 const aggregation=raw.aggregate??null,group=raw.groupBy?find(raw.groupBy):null;
 let output=[],select=[],groups='',metric=false;
 if(aggregation){
  object(aggregation);check(Object.hasOwn(FUNCTIONS,aggregation.fn),'Hàm tổng hợp không hợp lệ.');
  const field=aggregation.column==='*'?null:find(aggregation.column);
  check(field||aggregation.fn==='count','Chỉ COUNT hỗ trợ *.');
  check(!field||aggregation.fn==='count'||field.type==='number','Tổng hợp số cần cột kiểu số.');
  if(group){select.push(quote(group.key)+' AS out_0');output.push({key:group.key,name:group.name,type:group.type});groups=' GROUP BY '+quote(group.key);}
  const alias='out_'+output.length,expression=FUNCTIONS[aggregation.fn]+'('+(field?quote(field.key):'*')+')';
  select.push(expression+' AS '+alias);output.push({key:'value',name:FUNCTIONS[aggregation.fn]+'('+(field?field.name:'*')+')',type:'number'});metric=true;
 }else{
  check(!group,'Cần chọn hàm tổng hợp trước khi nhóm.');
  const chosen=raw.columns??fields.map(c=>c.key);
  check(Array.isArray(chosen)&&chosen.length>0&&chosen.length<=30&&new Set(chosen).size===chosen.length,'Chọn từ 1 đến 30 cột, không trùng.');
  output=chosen.map(find).map(c=>({...c}));select=output.map((c,i)=>quote(c.key)+' AS out_'+i);
 }
 const direction=raw.direction??'asc';check(['asc','desc'].includes(direction),'Chiều sắp xếp không hợp lệ.');
 const order=raw.orderBy??(metric?'value':'');
 let ordering='';
 if(order){
  const index=output.findIndex(c=>c.key===order);check(index>=0,'Cột sắp xếp cần có trong kết quả.');
  ordering=' ORDER BY out_'+index+' '+direction.toUpperCase();
 }else if(!metric)ordering=' ORDER BY row_no ASC';
 const limit=raw.limit??50,offset=raw.offset??0;
 check(Number.isInteger(limit)&&limit>=1&&limit<=500,'Giới hạn mỗi trang từ 1 đến 500.');
 check(Number.isInteger(offset)&&offset>=0&&offset<=5000,'Offset từ 0 đến 5.000.');
 const base='SELECT '+select.join(', ')+' FROM '+quote(dataset.table)+(where.length?' WHERE '+where.join(' AND '):'')+groups;
 return {sql:base+ordering+' LIMIT ? OFFSET ?',params:[...params,limit,offset],countSQL:'SELECT COUNT(*) AS n FROM ('+base+')',countParams:params,output,limit,offset,
  definition:{datasetId:dataset.id,columns:metric?[]:output.map(c=>c.key),filters:filters.map(f=>({...f})),groupBy:group?.key||'',aggregate:aggregation?{fn:aggregation.fn,column:aggregation.column}:null,orderBy:order,direction,limit,offset}};
}
