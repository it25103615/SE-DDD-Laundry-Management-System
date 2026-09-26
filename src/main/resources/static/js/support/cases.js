(() => {
 const {$,escape:e,api,notice,run,date}=Support;
 let context,current,editing=null,page=0,summaryStatus='',lastFocus=null;
 const staff=()=>context.actor.role!=='CUSTOMER';
 const coordinator=()=>['ADMIN','OWNER','CSM','CUSTOMER_SERVICE_MANAGER'].includes(context.actor.role);
 const slug=value=>String(value||'').toLowerCase().replace(/\s+/g,'-');
 const initials=name=>String(name||'?').split(/\s+/).map(part=>part[0]).join('').slice(0,2).toUpperCase();
 const historyIcon=action=>({'Created':'＋','Edited':'✎','Case updated':'↻','Message added':'✉','Deleted':'×'}[action]||'•');
 const historyTitle=action=>({'Created':'Case submitted','Edited':'Customer edited the case','Case updated':'Assignment or status changed','Message added':'Reply added','Deleted':'Case removed'}[action]||action);
 const historyDetails=item=>{
   if(item.action==='Message added') return 'A reply was added to the conversation.';
   const legacy=String(item.details||'').match(/^(.+?) -> (.+?); (.+?); assignee (\d+)\.\s*(.*)$/);
   return legacy?`Status changed from ${legacy[1]} to ${legacy[2]}\nPriority: ${legacy[3]}\nAssigned staff ID: #${legacy[4]}${legacy[5]?`\nNote: ${legacy[5]}`:''}`:item.details;
 };
 const statusBadge=value=>`<span class="case-badge status-${slug(value)}"><span aria-hidden="true"></span>${e(value)}</span>`;
 const priorityBadge=value=>`<span class="priority-badge priority-${slug(value)}">${e(value)}</span>`;

 function updateNoteHint() {
   const resolution=['Resolved','Closed'].includes($('status').value);
   $('note').required=resolution;
   $('note-hint').textContent=resolution?'Required: explain the resolution before closing this case.':'Optional: add context for the assignee. They can investigate and reply in the conversation.';
 }
 function rating() { const enabled=$('type').value==='Feedback'; $('rating-field').hidden=!enabled; $('rating').required=enabled; if(!enabled) $('rating').value=''; }
 function resetEditor() { editing=null; $('case-form').reset(); $('editor-title').textContent='Send us a message'; $('save-case').textContent='Submit case'; $('cancel-edit').hidden=true; rating(); }
 function setSummarySelection(value) {
   summaryStatus=value;
   document.querySelectorAll('[data-summary-status]').forEach(card=>{
     const selected=card.dataset.summaryStatus===value;
     card.classList.toggle('is-active',selected);
     card.setAttribute('aria-pressed',String(selected));
   });
 }
 async function loadSummary() {
   const data=await api('/cases/summary');
   $('summary-total').textContent=data.total??0;
   $('summary-pending').textContent=data.pending??0;
   $('summary-progress').textContent=data.inProgress??0;
   $('summary-resolved').textContent=data.resolved??0;
 }
 function loadingRows() {
   $('cases').innerHTML=Array.from({length:4},()=>'<tr class="case-skeleton"><td><span></span></td><td><span></span><small></small></td><td><span></span></td><td><span></span></td><td><span></span></td><td><span></span></td></tr>').join('');
 }
 async function load() {
   const wrap=document.querySelector('.case-table-wrap');
   wrap.setAttribute('aria-busy','true');loadingRows();$('visible-count').textContent='Loading…';
   const status=summaryStatus||$('filter-status').value;
   const query=new URLSearchParams({search:$('search').value,status,type:$('filter-type').value,priority:$('filter-priority').value,assigneeId:$('filter-assignee').value,page});
   try {
     const rows=await api('/cases?'+query);
     $('cases').innerHTML=rows.length?rows.map(r=>`<tr class="case-row ${['Resolved','Closed'].includes(r.status)?'is-complete':''} ${r.priority==='High'&&!['Resolved','Closed'].includes(r.status)?'needs-attention':''}" data-case-id="${r.id}"><td><span class="case-number">#${r.id}</span><small>${e(date(r.updatedAt))}</small></td><td><strong>${e(r.subject)}</strong><small>${e(r.customer)}${r.assignee?' · '+e(r.assignee):' · Unassigned'}</small></td><td><span class="type-label">${e(r.type)}</span></td><td>${statusBadge(r.status)}</td><td>${priorityBadge(r.priority)}</td><td><button class="case-open-button" data-open="${r.id}" aria-label="Open case ${r.id}: ${e(r.subject)}">View <span aria-hidden="true">→</span></button></td></tr>`).join(''):'<tr><td colspan="6"><div class="case-empty"><span aria-hidden="true">⌕</span><strong>No matching cases</strong><p>Try clearing a filter or searching with another customer or subject.</p><button class="custom_button" type="button" data-clear-empty>Clear filters</button></div></td></tr>';
     $('previous').disabled=page===0;$('next').disabled=rows.length<25;$('page-label').textContent='Page '+(page+1);
     $('visible-count').textContent=rows.length+(rows.length===1?' case shown':' cases shown');
   } finally { wrap.setAttribute('aria-busy','false'); }
 }
 function closeDetail() {
   $('case-detail').hidden=true;$('detail-backdrop').hidden=true;document.body.classList.remove('case-panel-open');current=null;
   if(lastFocus?.isConnected) lastFocus.focus();
 }
 async function open(id,focus=true) {
   lastFocus=document.activeElement;
   current=await api('/cases/'+id);
   $('case-detail').hidden=false;$('detail-backdrop').hidden=false;document.body.classList.add('case-panel-open');
   $('detail-title').textContent='#'+current.id+' — '+current.subject;
   $('detail-meta').innerHTML=[`<span>${e(current.type)}</span>`,statusBadge(current.status),priorityBadge(current.priority),`<span>${current.orderId?'Order #'+e(current.orderId):'General enquiry'}</span>`,current.rating?`<span>★ ${e(current.rating)}/5</span>`:'',`<span>Submitted ${e(date(current.createdAt))}</span>`,current.assignee?`<span>Assigned to ${e(current.assignee)}</span>`:'<span>Unassigned</span>'].filter(Boolean).join('');
   $('detail-message').innerHTML=`<strong>Customer’s original message</strong><span>${e(current.message)}</span>`;
   $('customer-actions').hidden=staff() || current.status!=='New';$('edit-case').hidden=staff();$('handle-form').hidden=!coordinator();
   $('status').value=current.status;$('priority').value=current.priority;$('assignee').value=current.assigneeId||'';$('note').value='';
   const next={New:['New','Assigned','In Review'],Assigned:['Assigned','In Review'],'In Review':['In Review','Resolved'],Resolved:['Resolved','Closed','Reopened'],Closed:['Closed','Reopened'],Reopened:['Reopened','Assigned','In Review']};
   [...$('status').options].forEach(option=>option.disabled=!next[current.status]?.includes(option.value));updateNoteHint();
   $('messages').innerHTML=current.messages.length?`<div class="conversation-thread">${current.messages.map(message=>{const customer=message.authorRole==='CUSTOMER';return `<article class="message-row ${customer?'from-customer':'from-support'}"><span class="message-avatar" aria-hidden="true">${e(initials(message.author))}</span><div class="message-bubble"><div class="message-heading"><strong>${e(message.author)}</strong><span>${customer?'Customer':'Support team'}</span></div><p>${e(message.message)}</p><time>${e(date(message.sentAt))}</time></div></article>`;}).join('')}</div>`:'<div class="conversation-empty"><span aria-hidden="true">✉</span><strong>No replies yet</strong><span>Messages between the customer and support team will appear here.</span></div>';
   $('history').innerHTML=current.history.length?`<div class="case-timeline">${current.history.map(item=>`<article class="timeline-item history-${slug(item.action)}"><div class="timeline-marker" aria-hidden="true">${historyIcon(item.action)}</div><div class="timeline-card"><div class="timeline-heading"><strong>${e(historyTitle(item.action))}</strong><time>${e(date(item.createdAt))}</time></div><div class="timeline-actor"><span>${e(item.actor)}</span><span class="history-action">${e(item.action)}</span></div><div class="timeline-details">${e(historyDetails(item))}</div></div></article>`).join('')}</div>`:'<div class="conversation-empty"><strong>No history yet</strong><span>Assignments, status changes and resolution notes will appear here.</span></div>';
   $('reply-form').hidden=current.status==='Closed';$('reply').value='';
   requestAnimationFrame(()=>{const thread=$('messages').querySelector('.conversation-thread');if(thread)thread.scrollTop=thread.scrollHeight;});
   if(focus) $('case-detail').focus();
 }
 async function refresh(openCurrent=false) { const id=openCurrent&&current?current.id:null;await Promise.all([load(),loadSummary()]);if(id)await open(id,false); }
 function clearFilters() { $('filters').reset();setSummarySelection('');page=0;run(load); }

 $('type').onchange=rating;$('status').onchange=updateNoteHint;$('cancel-edit').onclick=resetEditor;
 $('filters').onsubmit=event=>{event.preventDefault();page=0;setSummarySelection('');run(load,event.submitter);};
 $('clear-filters').onclick=clearFilters;
 $('case-summary').onclick=event=>{const card=event.target.closest('[data-summary-status]');if(!card)return;$('filter-status').value='';page=0;setSummarySelection(card.dataset.summaryStatus);run(load,card);};
 $('previous').onclick=()=>{if(page>0){page--;run(load);}};$('next').onclick=()=>{page++;run(load);};
 $('cases').onclick=event=>{const clear=event.target.closest('[data-clear-empty]');if(clear){clearFilters();return;}const button=event.target.closest('[data-open]');if(button)run(()=>open(button.dataset.open),button);};
 $('close-detail').onclick=closeDetail;$('detail-backdrop').onclick=closeDetail;
 document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$('case-detail').hidden)closeDetail();});
 $('edit-case').onclick=()=>{editing={id:current.id,version:current.version};for(const [id,key] of [['type','type'],['order','orderId'],['subject','subject'],['message','message'],['rating','rating']])$(id).value=current[key]??'';$('editor-title').textContent='Edit case #'+current.id;$('save-case').textContent='Save changes';$('cancel-edit').hidden=false;rating();closeDetail();$('subject').focus();};
 $('case-form').onsubmit=event=>{event.preventDefault();run(async()=>{const input={type:$('type').value,subject:$('subject').value.trim(),message:$('message').value.trim(),orderId:$('order').value?Number($('order').value):null,rating:$('rating').value?Number($('rating').value):null,version:editing?.version??null};if(!input.subject||!input.message)throw new Error('Subject and message cannot be blank.');const saved=await api(editing?'/cases/'+editing.id:'/cases',editing?'PUT':'POST',input);resetEditor();page=0;await refresh();await open(saved.id);notice('Case saved successfully.','success');},event.submitter);};
 $('delete-case').onclick=()=>run(async()=>{if(!confirm('Delete case #'+current.id+'? It will be removed from active views. Its audit history is retained.'))return;await api('/cases/'+current.id+'?version='+current.version,'DELETE');closeDetail();resetEditor();await refresh();notice('Case deleted.','success');},$('delete-case'));
 $('handle-form').onsubmit=event=>{event.preventDefault();run(async()=>{const note=$('note').value.trim();if(['Resolved','Closed'].includes($('status').value)&&!note)throw new Error('Add a resolution note before resolving or closing this case.');await api('/cases/'+current.id,'PATCH',{status:$('status').value,priority:$('priority').value,assigneeId:$('assignee').value?Number($('assignee').value):null,note,version:current.version});await refresh(true);notice('Case update and history saved.','success');},event.submitter);};
 $('reply-form').onsubmit=async event=>{event.preventDefault();const button=$('send-reply'),label=button.innerHTML;button.innerHTML='Sending…';try{await run(async()=>{const message=$('reply').value.trim();if(!message)throw new Error('Enter a reply.');await api('/cases/'+current.id+'/messages','POST',{message});await refresh(true);notice('Reply sent successfully.','success');},button);}finally{button.innerHTML=label;}};
 run(async()=>{
   context=await Support.init();$('case-editor').hidden=staff();
   $('queue-title').textContent=context.actor.role==='CUSTOMER'?'Your support requests':coordinator()?'Customer service queue':'Complaints assigned to you';
   $('queue-description').textContent=coordinator()?'Review priority, assignment and customer conversations from one workspace.':'Track your cases and continue the conversation.';
   $('order').innerHTML='<option value="">General enquiry</option>'+context.orders.map(order=>`<option value="${order.id}">Order #${order.id}</option>`).join('');
   $('assignee').innerHTML='<option value="">Unassigned — CSM queue</option>'+context.staff.map(person=>`<option value="${person.id}">${e(person.name)} — ${e(person.role)}</option>`).join('');
   $('filter-assignee').innerHTML='<option value="">Anyone</option>'+context.staff.map(person=>`<option value="${person.id}">${e(person.name)}</option>`).join('');
   $('assignee-filter-field').hidden=!coordinator();rating();await Promise.all([load(),loadSummary()]);
   const requested=new URLSearchParams(location.search).get('caseId');if(requested&&/^\d+$/.test(requested))await open(requested);
 });
})();
