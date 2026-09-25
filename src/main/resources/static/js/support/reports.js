(() => {
 const {$,escape:e,api,notice,run,money,table}=Support;
 let report;
 async function load() {
   $('export').disabled=true;report=null;
   const params=new URLSearchParams();
   for(const key of ['from','to']) if($(key).value) params.set(key,$(key).value);
   if($('service').value)params.set('serviceId',$('service').value);
   if($('from').value && $('to').value && $('from').value>$('to').value)throw new Error('Start date must be on or before end date.');
   notice('Generating report…');
   report=await api('/reports?'+params);
   const s=report.summary;
   $('metrics').innerHTML=[['TOTAL ORDERS',s.totalOrders],['RECORDED PAYMENTS',money(s.recordedPayments)],['ORDER VALUE',money(s.orderValue)],['CUSTOMERS WITH ORDERS',s.customers],['DELIVERED / COMPLETED',s.deliveredOrders],['ORDERS WITHOUT A LOG DATE',s.undatedOrders]].map(([label,value])=>`<article class="card metric"><span class="muted small">${e(label)}</span><strong>${e(value)}</strong></article>`).join('');
   $('period').textContent='Period: '+report.period.from+' to '+report.period.to+' · '+($('service').selectedOptions[0]?.textContent || 'All services');
   const selected=$('service').value;
   $('service').innerHTML='<option value="">All services</option>'+report.serviceOptions.map(s=>`<option value="${s.id}">${e(s.name)}</option>`).join('');$('service').value=selected;
   $('status-chart').innerHTML=report.statuses.length?report.statuses.map(r=>`<div class="chart-row"><span>${e(r.status)}</span><div class="bar" aria-hidden="true"><span style="width:${100*r.orders/Math.max(s.totalOrders,1)}%"></span></div><strong>${r.orders}</strong></div>`).join(''):'<p>No orders in this period.</p>';
   const support=report.support;
   $('support-metrics').innerHTML=`<p><strong>${support.openCases}</strong> open cases · ${support.totalCases} total</p><p>Average rating: <strong>${support.averageRating==null?'No ratings yet':Number(support.averageRating).toFixed(2)+' / 5'}</strong> (${support.ratingCount} ratings)</p>`;
   $('alerts').innerHTML=report.alerts.map(a=>`<article class="card metric"><span class="muted small">${e(a.label)}</span><strong>${e(a.total)}</strong></article>`).join('');
   table('services',report.services,['service','orders','items',r=>money(r.value)]);
   table('customers',report.customers,['customer','orders',r=>money(r.orderValue),r=>money(r.recordedPayments)]);
   $('export').disabled=false;notice('Report generated from database records.','success');
 }
 function csvCell(value) {
   let text=String(value??'');if(/^[\s]*[=+\-@]/.test(text))text="'"+text;
   return '"'+text.replace(/"/g,'""')+'"';
 }
 $('report-filters').onsubmit=event=>{event.preventDefault();run(load,event.submitter);};
 $('export').onclick=()=>{
   if(!report)return;
   const rows=[['LaundryLink business report'],['From',report.period.from,'To',report.period.to,'Service ID',report.period.serviceId],['Basis','Order first activity cohort; payments are lifetime recorded totals for selected orders.'],['Summary metric','Value'],...Object.entries(report.summary),[],['Service','Orders','Items','Line value (LKR)'],...report.services.map(r=>[r.service,r.orders,r.items,r.value]),[],['Order status','Orders'],...report.statuses.map(r=>[r.status,r.orders]),[],['Customer (top 100)','Orders','Order value (LKR)','Recorded payments (LKR)'],...report.customers.map(r=>[r.customer,r.orders,r.orderValue,r.recordedPayments])];
   const url=URL.createObjectURL(new Blob(['\uFEFF'+rows.map(r=>r.map(csvCell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}));
   const a=document.createElement('a');a.href=url;a.download='laundrylink-report.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 };
 run(async()=>{await Support.init();await load();});
})();
