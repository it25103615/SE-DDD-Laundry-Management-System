(() => {
 const {$,escape:e,api,run,money,date,notice}=Support;
 let page=0;
 // placedOn arrives as a plain day (yyyy-mm-dd). Adding a local midnight stops the browser reading
 // it as UTC, which would show the previous day in time zones behind UTC.
 const day=value=>value?new Date(value+'T00:00:00').toLocaleDateString('en-LK',{dateStyle:'medium'}):'—';
 async function load() {
   // Only the filters that are filled in are sent; the server treats a missing one as "any".
   const query=new URLSearchParams({page});if($('order-id').value)query.set('orderId',$('order-id').value);
   if($('customer').value.trim())query.set('customer',$('customer').value.trim());
   if($('status').value)query.set('statusId',$('status').value);
   if($('from').value)query.set('from',$('from').value);if($('to').value)query.set('to',$('to').value);
   const rows=await api('/orders?'+query);
   notice('');   // the search worked: take down an error left by an earlier one (e.g. a bad date range)
   $('orders').innerHTML=rows.length?rows.map(r=>`<tr><td>#${r.id}</td><td>${e(r.customer)}</td><td>${e(r.status)}</td><td>${e(day(r.placedOn))}</td><td>${e(money(r.orderValue))}</td><td><button class="custom_button" data-id="${r.id}">View history</button></td></tr>`).join(''):'<tr><td colspan="6">No orders found.</td></tr>';
   $('previous').disabled=page===0;$('next').disabled=rows.length<25;$('page-label').textContent='Page '+(page+1);
 }
 // Fills the status filter from the status table, so a status added later appears without a page change.
 async function loadStatuses() {
   const response=await fetch('/api/status',{headers:{'Accept':'application/json'}});
   if(!response.ok)throw new Error('Could not load the order statuses.');
   $('status').insertAdjacentHTML('beforeend',(await response.json()).map(s=>`<option value="${e(s.statusID)}">${e(s.statusLabel)}</option>`).join(''));
 }
 $('order-search').onsubmit=event=>{event.preventDefault();page=0;run(load,event.submitter);};
 $('clear').onclick=()=>{$('order-search').reset();page=0;run(load);};
 $('previous').onclick=()=>{if(page){page--;run(load);}};$('next').onclick=()=>{page++;run(load);};
 $('orders').onclick=event=>{const b=event.target.closest('[data-id]');if(b)run(async()=>{
   const rows=await api('/orders/'+b.dataset.id+'/history');$('order-history').hidden=false;$('history-title').textContent='Order #'+b.dataset.id+' — status history';
   // An entry with no status before it is the order's creation row: the order was placed.
   $('history').innerHTML=rows.length?rows.map(r=>`<div class="history-entry">${r.beforeStatus?e(r.beforeStatus)+' → ':'Order placed · '}<strong>${e(r.afterStatus)}</strong><div class="small muted">${e(date(r.logDate))} ${e(r.logTime)}</div></div>`).join(''):'<p>No status events recorded for this order.</p>';
 },b);};
 run(async()=>{await Support.init();await loadStatuses();await load();});
})();
