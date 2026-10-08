(() => {
 const {$,escape:e,api,run,money,date}=Support;
 let page=0;
 async function load() {
   const query=new URLSearchParams({page});if($('order-id').value)query.set('orderId',$('order-id').value);
   const rows=await api('/orders?'+query);
   $('orders').innerHTML=rows.length?rows.map(r=>`<tr><td>#${r.id}</td><td>${e(r.customer)}</td><td>${e(r.status)}</td><td>${e(money(r.orderValue))}</td><td><button class="custom_button" data-id="${r.id}">View history</button></td></tr>`).join(''):'<tr><td colspan="5">No orders found.</td></tr>';
   $('previous').disabled=page===0;$('next').disabled=rows.length<25;$('page-label').textContent='Page '+(page+1);
 }
 $('order-search').onsubmit=event=>{event.preventDefault();page=0;run(load,event.submitter);};
 $('previous').onclick=()=>{if(page){page--;run(load);}};$('next').onclick=()=>{page++;run(load);};
 $('orders').onclick=event=>{const b=event.target.closest('[data-id]');if(b)run(async()=>{
   const rows=await api('/orders/'+b.dataset.id+'/history');$('order-history').hidden=false;$('history-title').textContent='Order #'+b.dataset.id+' — status history';
   $('history').innerHTML=rows.length?rows.map(r=>`<div class="history-entry">${e(r.beforeStatus)} → <strong>${e(r.afterStatus)}</strong><div class="small muted">${e(date(r.logDate))} ${e(r.logTime)}</div></div>`).join(''):'<p>No status events recorded for this order.</p>';
 },b);};
 run(async()=>{await Support.init();await load();});
})();
