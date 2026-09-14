(() => {
 const {$,escape:e,api,notice,run,date}=Support;
 let context,current,editing=null,page=0;
 const staff=()=>['ADMIN','MANAGER','OWNER','CSM','CUSTOMER_SERVICE_MANAGER'].includes(context.actor.role);
 function rating() { const enabled=$('type').value==='Feedback'; $('rating-field').hidden=!enabled; $('rating').required=enabled; if(!enabled) $('rating').value=''; }
 function resetEditor() { editing=null; $('case-form').reset(); $('editor-title').textContent='Send us a message'; $('save-case').textContent='Submit case'; $('cancel-edit').hidden=true; rating(); }
 async function load() {
   const query=new URLSearchParams({search:$('search').value,status:$('filter-status').value,type:$('filter-type').value,page});
   const rows=await api('/cases?'+query);
   $('cases').innerHTML=rows.length?rows.map(r=>`<tr><td>#${r.id}</td><td>${e(r.subject)}<br><small>${e(r.customer)}</small></td><td>${e(r.type)}</td><td>${e(r.status)}</td><td>${e(r.priority)}</td><td><button class="custom_button" data-open="${r.id}">Open</button></td></tr>`).join(''):'<tr><td colspan="6">No cases match these filters.</td></tr>';
   $('previous').disabled=page===0; $('next').disabled=rows.length<25; $('page-label').textContent='Page '+(page+1);
 }
 async function open(id,focus=true) {
   current=await api('/cases/'+id);
   $('case-detail').hidden=false; $('detail-title').textContent='#'+current.id+' — '+current.subject;
   $('detail-meta').textContent=[current.type,current.status,current.priority+' priority',current.orderId?'Order #'+current.orderId:'General enquiry',current.rating?'Rating '+current.rating+'/5':null,'Submitted: '+date(current.createdAt)].filter(Boolean).join(' · ');
   $('detail-message').textContent=current.message;
   $('customer-actions').hidden=current.status!=='New';
   $('edit-case').hidden=staff();
   $('handle-form').hidden=!staff();
   $('status').value=current.status; $('priority').value=current.priority; $('assignee').value=current.assigneeId || ''; $('note').value='';
   const next={New:['New','Assigned','In Review'],Assigned:['Assigned','In Review'],'In Review':['In Review','Resolved'],Resolved:['Resolved','Closed','Reopened'],Closed:['Closed','Reopened'],Reopened:['Reopened','Assigned','In Review']};
   [...$('status').options].forEach(o=>o.disabled=!next[current.status]?.includes(o.value));
   $('messages').innerHTML=current.messages.length?current.messages.map(m=>`<div class="history-entry"><strong>${e(m.author)}</strong><div class="small muted">${e(date(m.sentAt))}</div><div>${e(m.message)}</div></div>`).join(''):'<p class="muted">No messages yet.</p>';
   $('history').innerHTML=current.history.length?current.history.map(h=>`<div class="history-entry"><strong>${e(h.action)}</strong><div class="small muted">${e(h.actor)} · ${e(date(h.createdAt))}</div><div>${e(h.details)}</div></div>`).join(''):'<p class="muted">Imported record; earlier actions are not available.</p>';
   $('reply-form').hidden=current.status==='Closed'; $('reply').value='';
   if(focus) $('case-detail').focus();
 }
 $('type').onchange=rating;
 $('cancel-edit').onclick=resetEditor;
 $('filters').onsubmit=event=>{event.preventDefault();page=0;run(load,event.submitter);};
 $('previous').onclick=()=>{if(page>0){page--;run(load);}};
 $('next').onclick=()=>{page++;run(load);};
 $('cases').onclick=event=>{const b=event.target.closest('[data-open]');if(b)run(()=>open(b.dataset.open),b);};
 $('close-detail').onclick=()=>{$('case-detail').hidden=true;current=null;};
 $('edit-case').onclick=()=>{
   editing={id:current.id,version:current.version};
   for(const [id,key] of [['type','type'],['order','orderId'],['subject','subject'],['message','message'],['rating','rating']]) $(id).value=current[key]??'';
   $('editor-title').textContent='Edit case #'+current.id; $('save-case').textContent='Save changes'; $('cancel-edit').hidden=false; rating(); $('subject').focus();
 };
 $('case-form').onsubmit=event=>{event.preventDefault();run(async()=>{
   const input={type:$('type').value,subject:$('subject').value.trim(),message:$('message').value.trim(),orderId:$('order').value?Number($('order').value):null,rating:$('rating').value?Number($('rating').value):null,version:editing?.version??null};
   if(!input.subject || !input.message) throw new Error('Subject and message cannot be blank.');
   const saved=await api(editing?'/cases/'+editing.id:'/cases',editing?'PUT':'POST',input);
   resetEditor();page=0;await load();await open(saved.id);notice('Case saved to the database.','success');
 },event.submitter);};
 $('delete-case').onclick=()=>run(async()=>{
   if(!confirm('Delete case #'+current.id+'? It will be removed from active views. Its audit history is retained.'))return;
   await api('/cases/'+current.id+'?version='+current.version,'DELETE');current=null;resetEditor();$('case-detail').hidden=true;await load();notice('Case deleted.','success');
 },$('delete-case'));
 $('handle-form').onsubmit=event=>{event.preventDefault();run(async()=>{
   const note=$('note').value.trim();if(!note)throw new Error('Enter an action or resolution note.');
   await api('/cases/'+current.id,'PATCH',{status:$('status').value,priority:$('priority').value,assigneeId:$('assignee').value?Number($('assignee').value):null,note,version:current.version});
   await open(current.id,false);await load();notice('Case update and history saved.','success');
 },event.submitter);};
 $('reply-form').onsubmit=event=>{event.preventDefault();run(async()=>{
   const message=$('reply').value.trim();if(!message)throw new Error('Enter a reply.');
   await api('/cases/'+current.id+'/messages','POST',{message});await open(current.id,false);await load();notice('Reply saved to the conversation.','success');
 },event.submitter);};
 run(async()=>{
   context=await Support.init();
   $('case-editor').hidden=staff();$('queue-title').textContent=staff()?'All customer cases':'Your previous messages';
   $('order').innerHTML='<option value="">General enquiry</option>'+context.orders.map(o=>`<option value="${o.id}">Order #${o.id}</option>`).join('');
   $('assignee').innerHTML='<option value="">Unassigned</option>'+context.staff.map(s=>`<option value="${s.id}">${e(s.name)}</option>`).join('');
   rating();await load();
 });
})();
