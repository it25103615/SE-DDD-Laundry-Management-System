(() => {
 const {$,escape:e,api,notice,run,date}=Support;
 let context,current,editing=null,page=0,summaryStatus='',lastFocus=null,lastMessageType=null;
 const staff=()=>context.actor.role!=='CUSTOMER';
 const coordinator=()=>['ADMIN','OWNER','CSM','CUSTOMER_SERVICE_MANAGER'].includes(context.actor.role);
 const canSeeHistory=()=>['CSM','CUSTOMER_SERVICE_MANAGER'].includes(context.actor.role);
 // Laundry processing issues are raised by staff for the customer; customers can reply but not edit or delete them.
 const staffRaised=item=>['Damaged item','Existing stain','Missing item','Item count mismatch'].includes(item.type);
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
 function rating() {
   const enabled=$('type').value==='Feedback'; $('rating-field').hidden=!enabled; $('rating').required=enabled; if(!enabled) $('rating').value='';
   $('topic-field').hidden=enabled;$('topic').required=!enabled;if(enabled)$('topic').value='General';
   else if(lastMessageType==='Feedback'&&!editing)$('topic').value='';
   lastMessageType=$('type').value;
   document.querySelectorAll('[data-message-type]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.messageType===$('type').value)));
   $('type-help').textContent=enabled?'Share your experience. Customer service will read and reply to your feedback.':'Choose a topic so customer service can send your case to the right person.';
 }
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
   document.querySelectorAll('[data-queue-type]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.queueType===$('filter-type').value)));
   const wrap=document.querySelector('.case-table-wrap');
   wrap.setAttribute('aria-busy','true');loadingRows();$('visible-count').textContent='Loading…';
   const status=summaryStatus||$('filter-status').value;
   const query=new URLSearchParams({search:$('search').value,status,type:$('filter-type').value,topic:$('filter-topic').value,priority:$('filter-priority').value,assigneeId:$('filter-assignee').value,page});
   try {
     const rows=await api('/cases?'+query);
     $('cases').innerHTML=rows.length?rows.map(r=>`<tr class="case-row ${['Resolved','Closed'].includes(r.status)?'is-complete':''} ${r.priority==='High'&&!['Resolved','Closed'].includes(r.status)?'needs-attention':''}" data-case-id="${r.id}"><td><span class="case-number">#${r.id}</span><small>${e(date(r.updatedAt))}</small></td><td><strong>${e(r.subject)}</strong><small>${e(r.customer)}${r.assignee?' · '+e(r.assignee):' · Unassigned'}</small></td><td><span class="type-label">${e(r.type)}</span><small>${e(r.topic||'General')}</small></td><td>${statusBadge(r.status)}</td><td>${priorityBadge(r.priority)}</td><td><button class="case-open-button" data-open="${r.id}" aria-label="Open case ${r.id}: ${e(r.subject)}">View <span aria-hidden="true">→</span></button></td></tr>`).join(''):'<tr><td colspan="6"><div class="case-empty"><span aria-hidden="true">⌕</span><strong>No matching cases</strong><p>Try clearing a filter or searching with another customer or subject.</p><button class="custom_button" type="button" data-clear-empty>Clear filters</button></div></td></tr>';
     $('previous').disabled=page===0;$('next').disabled=rows.length<25;$('page-label').textContent='Page '+(page+1);
     $('visible-count').textContent=rows.length+(rows.length===1?' case shown':' cases shown');
     if(!staff()) {
       $('cases').innerHTML=rows.length?`<tr><td colspan="6"><div class="customer-request-list">${rows.map(r=>`<article class="customer-request" data-case-id="${r.id}"><div><small>Request #${e(r.id)} · ${e(r.type)}</small><h3>${e(r.subject)}</h3><p>${e(r.topic||'General')} · Updated ${e(date(r.updatedAt))}</p>${statusBadge(r.status)}</div><button class="case-open-button" data-open="${r.id}" aria-label="Open request ${r.id}: ${e(r.subject)}">View updates &amp; reply →</button></article>`).join('')}</div></td></tr>`:'<tr><td colspan="6"><p class="muted">You haven’t submitted any support requests yet. Use the form above to get in touch.</p></td></tr>';
     }
   } finally { wrap.setAttribute('aria-busy','false'); }
 }
 let activeCaseTab='chats';
 function selectCaseTab(name,focus=false) {
   if(name==='history'&&!canSeeHistory())name='chats';
   activeCaseTab=name;
   document.querySelectorAll('[data-case-tab]').forEach(tab=>{
     const selected=tab.dataset.caseTab===name;
     tab.setAttribute('aria-selected',String(selected));tab.tabIndex=selected?0:-1;
     $(tab.getAttribute('aria-controls')).hidden=!selected;
     if(selected&&focus)tab.focus();
   });
   if(name==='chats')requestAnimationFrame(()=>{const thread=$('messages').querySelector('.conversation-thread');if(thread)thread.scrollTop=thread.scrollHeight;});
 }
 document.querySelectorAll('[data-case-tab]').forEach((tab,index,tabs)=>{
   tab.onclick=()=>selectCaseTab(tab.dataset.caseTab);
   tab.onkeydown=event=>{
     const visibleTabs=[...tabs].filter(item=>!item.hidden);let next=visibleTabs.indexOf(tab);
     if(event.key==='ArrowRight')next=(next+1)%visibleTabs.length;
     else if(event.key==='ArrowLeft')next=(next+visibleTabs.length-1)%visibleTabs.length;
     else if(event.key==='Home')next=0;
     else if(event.key==='End')next=visibleTabs.length-1;
     else return;
     event.preventDefault();selectCaseTab(visibleTabs[next].dataset.caseTab,true);
   };
 });
 function renderMessages(item) {
   const chatMessages=[{author:item.customer||'Customer',authorRole:'CUSTOMER',message:item.message,sentAt:item.createdAt},...(item.messages||[])];
   $('messages').innerHTML=chatMessages.length?`<div class="conversation-thread">${chatMessages.map(message=>{const customer=message.authorRole==='CUSTOMER';return `<article class="message-row ${customer?'from-customer':'from-support'}"><span class="message-avatar" aria-hidden="true">${e(initials(message.author))}</span><div class="message-bubble"><div class="message-heading"><strong>${e(message.author)}</strong><span>${customer?'Customer':'Support team'}</span></div><p>${e(message.message)}</p><time>${e(date(message.sentAt))}</time></div></article>`;}).join('')}</div>`:'<div class="conversation-empty"><span aria-hidden="true">✉</span><strong>No replies yet</strong><span>Messages between the customer and support team will appear here.</span></div>';
 }
 function closeDetail() {
   $('case-detail').hidden=true;$('detail-backdrop').hidden=true;document.body.classList.remove('case-panel-open');current=null;
   if(lastFocus?.isConnected) lastFocus.focus();
 }
 function hasDraft() {
   return current&&($('reply').value.trim()||(staff()&&($('note').value.trim()||$('status').value!==current.status||$('priority').value!==current.priority||$('assignee').value!==String(current.assigneeId||''))));
 }
 async function open(id,focus=true,background=false) {
   if(focus)lastFocus=document.activeElement;
   const sameCase=current?.id===Number(id);
   const loaded=await api('/cases/'+id);
   if(background&&current?.id!==Number(id))return;
   if(background&&hasDraft()){renderMessages(loaded);return;}
   renderDetail(loaded,sameCase,focus);
 }
 function renderDetail(loaded,sameCase=true,focus=false) {
   current=loaded;
   current.history=current.history||[];
   $('tab-history').hidden=!canSeeHistory();
   $('tab-details').textContent=staff()?'Details & assignment':'Request details';
   selectCaseTab(sameCase?activeCaseTab:'chats');
   $('case-detail').hidden=false;$('detail-backdrop').hidden=false;document.body.classList.add('case-panel-open');
   $('detail-title').textContent='#'+current.id+' — '+current.subject;
   $('detail-meta').innerHTML=[`<span>${e(current.type)}</span>`,`<span>${e(current.topic||'General')}</span>`,statusBadge(current.status),priorityBadge(current.priority),`<span>${current.orderId?'Order #'+e(current.orderId):'General enquiry'}</span>`,current.rating?`<span>★ ${e(current.rating)}/5</span>`:'',`<span>Submitted ${e(date(current.createdAt))}</span>`,current.assignee?`<span>Assigned to ${e(current.assignee)}</span>`:'<span>Awaiting CSM review</span>'].filter(Boolean).join('');
   $('detail-message').innerHTML=`<strong>Customer’s original message</strong><span>${e(current.message)}</span>`;
   $('routing-help').textContent=current.type==='Feedback'?'Customer service handles feedback directly. Choose Handle this myself, then save the case update.':current.topic==='Payments & billing'?'Suggested team: Manager — payments, billing and reports.':current.topic==='Laundry & items'?'Suggested team: Laundry staff — washing, item damage and missing items.':current.topic==='Pickup & delivery'?'Suggested team: Delivery rider — pickup times and delivery issues.':'Customer service reviews this case first. Handle it yourself or assign the right person.';
   $('handle-myself').hidden=!coordinator();
   $('handle-form').querySelector('h3').textContent=coordinator()?'Assign or resolve this case':'Update or resolve your assigned case';
   if(staff()&&!coordinator())$('routing-help').textContent='Reply in Conversation, move the case to In Review, then resolve it with a note explaining the outcome.';
   $('customer-actions').hidden=staff() || current.status!=='New' || staffRaised(current);$('edit-case').hidden=staff();$('handle-form').hidden=!staff();$('assignee').disabled=!coordinator();
   $('status').value=current.status;$('priority').value=current.priority;$('assignee').value=current.assigneeId||'';$('note').value='';
   const next={New:['New','Assigned','In Review'],Assigned:['Assigned','In Review'],'In Review':['In Review','Resolved'],Resolved:['Resolved','Closed','Reopened'],Closed:['Closed','Reopened'],Reopened:['Reopened','Assigned','In Review']};
   [...$('status').options].forEach(option=>option.disabled=!next[current.status]?.includes(option.value));updateNoteHint();
   renderMessages(current);
   $('history').innerHTML=current.history.length?`<div class="case-timeline">${current.history.map(item=>`<article class="timeline-item history-${slug(item.action)}"><div class="timeline-marker" aria-hidden="true">${historyIcon(item.action)}</div><div class="timeline-card"><div class="timeline-heading"><strong>${e(historyTitle(item.action))}</strong><time>${e(date(item.createdAt))}</time></div><div class="timeline-actor"><span>${e(item.actor)}</span><span class="history-action">${e(item.action)}</span></div><div class="timeline-details">${e(historyDetails(item))}</div></div></article>`).join('')}</div>`:'<div class="conversation-empty"><strong>No history yet</strong><span>Assignments, status changes and resolution notes will appear here.</span></div>';
   $('reply-form').hidden=current.status==='Closed';$('reply').value='';$('chat-notice').textContent='';
   requestAnimationFrame(()=>{const thread=$('messages').querySelector('.conversation-thread');if(thread)thread.scrollTop=thread.scrollHeight;});
   if(focus) $('case-detail').focus();
 }
 async function refresh(openCurrent=false,background=false) { const id=openCurrent&&current?current.id:null;await Promise.all([load(),loadSummary()]);if(id)await open(id,false,background); }
 function clearFilters() { $('filters').reset();setSummarySelection('');page=0;run(load); }

 $('type').onchange=rating;$('status').onchange=updateNoteHint;$('cancel-edit').onclick=resetEditor;
 document.querySelectorAll('[data-message-type]').forEach(button=>button.onclick=()=>{$('type').value=button.dataset.messageType;rating();});
 document.querySelectorAll('[data-queue-type]').forEach(button=>button.onclick=()=>{$('filter-type').value=button.dataset.queueType;page=0;document.querySelectorAll('[data-queue-type]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));run(load);});
 $('handle-myself').onclick=()=>{$('assignee').value=String(context.actor.id);if(['New','Assigned','Reopened'].includes(current.status))$('status').value='In Review';updateNoteHint();$('note').focus();};
 $('filters').onsubmit=event=>{event.preventDefault();page=0;setSummarySelection('');run(load,event.submitter);};
 $('clear-filters').onclick=clearFilters;
 $('case-summary').onclick=event=>{const card=event.target.closest('[data-summary-status]');if(!card)return;$('filter-status').value='';page=0;setSummarySelection(card.dataset.summaryStatus);run(load,card);};
 $('previous').onclick=()=>{if(page>0){page--;run(load);}};$('next').onclick=()=>{page++;run(load);};
 $('cases').onclick=event=>{const clear=event.target.closest('[data-clear-empty]');if(clear){clearFilters();return;}
   // A click anywhere on a row (or on a customer's request card) opens its case, not only the "View" button. The button stays so the
   // case can still be opened with the keyboard. A click that ends a text selection (dragging over
   // the subject to copy it) is ignored, so selecting text does not open the panel.
   const row=event.target.closest('[data-case-id]');if(!row)return;
   const button=row.querySelector('[data-open]');
   if(!event.target.closest('[data-open]')&&String(getSelection()))return;
   run(()=>open(row.dataset.caseId),button);
 };
 $('close-detail').onclick=closeDetail;$('detail-backdrop').onclick=closeDetail;
 document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$('case-detail').hidden)closeDetail();});
 $('edit-case').onclick=()=>{editing={id:current.id,version:current.version};for(const [id,key] of [['type','type'],['order','orderId'],['subject','subject'],['message','message'],['rating','rating'],['topic','topic']])$(id).value=current[key]??'';$('editor-title').textContent='Edit case #'+current.id;$('save-case').textContent='Save changes';$('cancel-edit').hidden=false;rating();closeDetail();$('subject').focus();};
 $('case-form').onsubmit=event=>{event.preventDefault();run(async()=>{const input={type:$('type').value,subject:$('subject').value.trim(),message:$('message').value.trim(),orderId:$('order').value?Number($('order').value):null,rating:$('rating').value?Number($('rating').value):null,topic:$('type').value==='Feedback'?'General':$('topic').value,version:editing?.version??null};if(!input.subject||!input.message)throw new Error('Subject and message cannot be blank.');const saved=await api(editing?'/cases/'+editing.id:'/cases',editing?'PUT':'POST',input);resetEditor();page=0;await refresh();await open(saved.id);notice('Case sent to customer service. Follow the conversation here.','success');},event.submitter);};
 $('delete-case').onclick=()=>run(async()=>{if(!confirm('Delete case #'+current.id+'? It will be removed from active views. Its audit history is retained.'))return;await api('/cases/'+current.id+'?version='+current.version,'DELETE');closeDetail();resetEditor();await refresh();notice('Case deleted.','success');},$('delete-case'));
 $('handle-form').onsubmit=event=>{event.preventDefault();run(async()=>{const note=$('note').value.trim();if(['Resolved','Closed'].includes($('status').value)&&!note)throw new Error('Add a resolution note before resolving or closing this case.');await api('/cases/'+current.id,'PATCH',{status:$('status').value,priority:$('priority').value,assigneeId:$('assignee').value?Number($('assignee').value):null,note,version:current.version});await refresh(true);notice('Case update and history saved.','success');},event.submitter);};
 $('reply-form').onsubmit=async event=>{
   event.preventDefault();const button=$('send-reply');if(button.disabled)return;
   const label=button.innerHTML;button.disabled=true;button.textContent='Sending…';
   try {
     const message=$('reply').value.trim();if(!message)throw new Error('Enter a reply.');
     const saved=await api('/cases/'+current.id+'/messages','POST',{message});
     activeCaseTab='chats';renderDetail(saved);$('chat-notice').textContent='Reply sent. Everyone on this case can see it.';
     Promise.all([load(),loadSummary()]).catch(()=>{$('chat-notice').textContent='Reply saved. The case list could not refresh.';});
   } catch(error) { $('chat-notice').textContent='Reply not sent: '+error.message; }
   finally {button.disabled=false;button.innerHTML=label;}
 };
 run(async()=>{
   context=await Support.init();$('case-editor').hidden=staff();
   document.body.dataset.audience=staff()?'staff':'customer';
   $('queue-title').textContent=context.actor.role==='CUSTOMER'?'My support requests':coordinator()?'All support cases':'Cases assigned to you';
   $('queue-description').textContent=coordinator()?'Review priority, assignment and customer conversations from one workspace.':'Open a request to see its current status and talk to the person helping you.';
   $('order').innerHTML='<option value="">General enquiry</option>'+context.orders.map(order=>`<option value="${order.id}">Order #${order.id}</option>`).join('');
   $('assignee').innerHTML='<option value="">Unassigned — CSM queue</option>'+context.staff.map(person=>`<option value="${person.id}">${e(person.name)} — ${e(person.role)}</option>`).join('');
   $('filter-assignee').innerHTML='<option value="">Anyone</option>'+context.staff.map(person=>`<option value="${person.id}">${e(person.name)}</option>`).join('');
   const initialStatus=new URLSearchParams(location.search).get('status');
   if(['pending','resolved'].includes(initialStatus))setSummarySelection(initialStatus);
   else if([...$('filter-status').options].some(option=>option.value===initialStatus))$('filter-status').value=initialStatus;
   $('assignee-filter-field').hidden=!coordinator();rating();await Promise.all([load(),loadSummary()]);
   const requested=new URLSearchParams(location.search).get('caseId');if(requested&&/^\d+$/.test(requested))await open(requested);
   let polling=false;
   setInterval(async()=>{
     if(document.hidden||polling||$('send-reply').disabled||$('handle-form').querySelector('button[type="submit"]')?.disabled)return;
     // Preserve drafts and optimistic versions while someone is composing a reply or update.
     polling=true;
     try { await refresh(Boolean(current),true); } catch(error) { notice('Live refresh failed: '+error.message,'error'); } finally { polling=false; }
   },15000);
 });
})();
