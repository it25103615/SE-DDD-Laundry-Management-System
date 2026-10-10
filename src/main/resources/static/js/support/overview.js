(() => {
 const {$,escape:e,api,date,run}=Support;
 const slug=value=>String(value||'').toLowerCase().replace(/\s+/g,'-');
 const casesPage=location.pathname.startsWith('/html/support/')?'cases.html':'complaints.html';
 function render(id,rows,empty) {
   $(id).innerHTML=rows.length?rows.map(row=>`<a class="overview-case" href="${casesPage}?caseId=${encodeURIComponent(row.id)}"><span class="overview-case-main"><small>CASE #${e(row.id)} · ${e(row.type)}</small><strong>${e(row.subject)}</strong><span>${e(row.customer)} · ${e(row.assignee||'Unassigned')}</span></span><span class="overview-case-state"><span class="case-badge status-${slug(row.status)}"><span aria-hidden="true"></span>${e(row.status)}</span><span class="priority-badge priority-${slug(row.priority)}">${e(row.priority)} priority</span><time>${e(date(row.updatedAt))}</time></span><span aria-hidden="true">→</span></a>`).join(''):`<p class="overview-empty">${e(empty)}</p>`;
 }
 async function load() {
   try {
     const [summary,pending,recent]=await Promise.all([api('/cases/summary'),api('/cases?status=pending'),api('/cases')]);
     for(const [id,key] of [['total','total'],['pending','pending'],['progress','inProgress'],['resolved','resolved'],['high','highPriority']])$('overview-'+id).textContent=summary[key]??0;
     const rank={High:0,Normal:1,Low:2};
     render('attention-cases',pending.sort((a,b)=>(rank[a.priority]??3)-(rank[b.priority]??3)).slice(0,5),'No pending cases. Your queue is up to date.');
     render('recent-cases',recent.slice(0,5),'No support cases yet.');
   } catch(error) {
     for(const id of ['attention-cases','recent-cases'])$(id).textContent='Cases could not be loaded. Refresh the page to try again.';
     throw error;
   } finally { for(const id of ['attention-cases','recent-cases'])$(id).setAttribute('aria-busy','false'); }
 }
 run(async()=>{
   await Support.init();await load();
   let refreshing=false;
   setInterval(async()=>{if(document.hidden||refreshing)return;refreshing=true;try{await run(load);}finally{refreshing=false;}},15000);
 });
})();
