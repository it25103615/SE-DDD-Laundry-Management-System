const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/js/support/reports.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness() {
 const elements=new Map(),requests=[],downloads=[];let message='';
 const get=id=>{if(!elements.has(id))elements.set(id,{value:'',disabled:true,selectedOptions:[],listeners:{},after(){},setAttribute(){},setCustomValidity(value){this.validationMessage=value;},addEventListener(event,handler){this.listeners[event]=handler;}});return elements.get(id);};
 const run=async task=>{try{await task();}catch(error){message=error.message;}};
 const context={Support:{$:get,escape:String,money:String,table(){},notice:text=>message=text,run,init:async()=>{},api:path=>new Promise((resolve,reject)=>requests.push({path,resolve,reject}))},document:{createElement:tag=>tag==='a'?{click(){downloads.push(this.download);}}:{setAttribute(){}}},URLSearchParams,URL:{createObjectURL:()=>'',revokeObjectURL(){}},Blob,setTimeout:()=>{}};
 vm.runInNewContext(source,context);
 return {get,requests,downloads,message:()=>message,change(id,value,event='input'){get(id).value=value;get(id).listeners[event]();},submit(){get('report-filters').onsubmit({preventDefault(){}});},export(){get('export').onclick();}};
}
function result(from='All time',to='All time') {return {period:{from,to,serviceId:'All services'},summary:{totalOrders:0},statuses:[],services:[],customers:[],support:{},alerts:[],serviceOptions:[]};}
test('invalid dates immediately report an error and block generation/export; correction clears validity',async()=>{
 const h=harness();await tick();h.requests[0].resolve(result());await tick();
 h.change('from','2026-09-30');h.change('to','2026-07-08');
 assert.equal(h.get('to').min,'2026-09-30');assert.equal(h.message(),'Through Date must be on or after From Date.');
 assert.equal(h.get('to').validationMessage,h.message());h.submit();h.export();assert.equal(h.requests.length,1);assert.equal(h.downloads.length,0);
 h.change('to','2026-09-30');assert.equal(h.get('to').validationMessage,'');
 h.change('from','');assert.equal(h.get('to').min,'');
});
for(const [from,to] of [['2026-09-30','2026-09-30'],['2026-07-08','2026-09-30'],['','2026-09-30'],['2026-09-30',''],['','']])test(`valid/open range ${from}/${to} generates and exports`,async()=>{
 const h=harness();await tick();h.change('from',from);h.change('to',to);h.submit();
 h.requests[1].resolve(result(from,to));await tick();assert.equal(h.get('export').disabled,false);h.export();assert.deepEqual(h.downloads,['laundrylink-report.csv']);
});
for(const filter of ['from','to','service'])test(`${filter} changes invalidate exports until regenerated`,async()=>{
 const h=harness();await tick();h.requests[0].resolve(result());await tick();
 h.change(filter,filter==='service'?'1':'2026-09-30','change');assert.equal(h.get('export').disabled,true);h.export();assert.equal(h.downloads.length,0);
 h.submit();h.requests[1].resolve(result());await tick();assert.equal(h.get('export').disabled,false);
});
test('old success and failure cannot replace newer report or validation notice',async()=>{
 const h=harness();await tick();h.change('from','2026-09-30');h.submit();h.requests[1].resolve(result('2026-09-30'));await tick();
 h.requests[0].resolve(result('old'));await tick();assert.match(h.get('period').textContent,/2026-09-30/);
 h.submit();h.change('to','2026-07-08');h.requests[2].reject(new Error('outdated failure'));await tick();
 assert.equal(h.get('export').disabled,true);assert.equal(h.message(),'Through Date must be on or after From Date.');
});
test('editing away and back still invalidates pending response; failed generation stays disabled',async()=>{
 const h=harness();await tick();h.change('from','2026-09-30');h.change('from','');h.requests[0].resolve(result());await tick();assert.equal(h.get('export').disabled,true);
 h.submit();h.requests[1].reject(new Error('Server unavailable'));await tick();assert.equal(h.get('export').disabled,true);assert.equal(h.message(),'Server unavailable');
});
test('export guard checks current filters even without an input event',async()=>{
 const h=harness();await tick();h.requests[0].resolve(result());await tick();h.get('service').value='2';h.export();assert.equal(h.downloads.length,0);assert.equal(h.get('export').disabled,true);
});
