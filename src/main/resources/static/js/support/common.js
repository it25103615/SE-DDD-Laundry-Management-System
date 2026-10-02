/* Shared HTTP and rendering helpers. No fabricated success notifications. */
window.Support = (() => {
  let csrf;
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => new Intl.NumberFormat('en-LK',{style:'currency',currency:'LKR'}).format(Number(value || 0));
  const date = value => {
    if (!value) return 'Time unavailable';
    const parsed=new Date(value);
    return Number.isNaN(parsed.getTime())?String(value):parsed.toLocaleString('en-LK',{dateStyle:'medium',timeStyle:'short'});
  };
  function notice(message,kind='') { $('notice').textContent=message; $('notice').className='notice '+kind; $('notice').hidden=!message; }
  async function api(path,method='GET',body) {
    const headers={'Accept':'application/json'};
    if(csrf && method!=='GET') headers[csrf.headerName]=csrf.token;
    if(body!==undefined) headers['Content-Type']='application/json';
    const response=await fetch('/api/support'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
    if(!response.ok) {
      const data=await response.json().catch(()=>({}));
      throw new Error(data.message || data.detail || (response.status===404?'Support API unavailable. Open this page through the running Spring Boot application.':('Request failed ('+response.status+').')));
    }
    return response.status===204?null:response.json();
  }
  async function run(task,button) {
    if(button) button.disabled=true;
    try { await task(); } catch(error) { notice(error.message,'error'); }
    finally { if(button) button.disabled=false; }
  }
  async function init() {
    notice('Connecting to the database…');
    csrf=await fetch('/api/auth/csrf').then(response=>response.json());
    let context;
    try { context=await api('/context'); } catch(error) { $('identity').textContent='Connection unavailable'; throw error; }
    $('identity').textContent=context.actor.name+' · '+context.actor.role;
    notice('');
    return context;
  }
  function table(id,rows,columns,empty='No records found.') {
    $(id).innerHTML=rows.length?rows.map(row=>'<tr>'+columns.map(c=>'<td>'+escape(typeof c==='function'?c(row):row[c])+'</td>').join('')+'</tr>').join(''):'<tr><td colspan="'+columns.length+'">'+escape(empty)+'</td></tr>';
  }
  return {$,escape,money,date,notice,api,run,init,table};
})();

(() => {
  const page=document.body.dataset.page;
  if(!['service-catalog','staff-accounts','roles-permissions'].includes(page)) return;
  const {$,escape:e,money,date,api,notice,run}=Support;

  async function catalog() {
    let data,editing;
    const clear=()=>{editing=null;$('service-form').reset();$('service-active').checked=true;$('turnaround').value=48;$('service-form-title').textContent='Add service';};
    const render=()=>{
      const names=new Map(data.services.map(s=>[String(s.id),s.name]));
      $('service-rows').innerHTML=data.services.length?data.services.map(s=>`<tr><td><strong>${e(s.name)}</strong><br><small>${e(s.description)}</small></td><td>${e(s.turnaroundHours)} hours</td><td><span class="status">${s.active?'ACTIVE':'INACTIVE'}</span></td><td>${e(s.pricedItems)}</td><td><button class="custom_button" data-edit-service="${s.id}">Edit</button></td></tr>`).join(''):'<tr><td colspan="5">No services exist yet.</td></tr>';
      $('price-service').innerHTML=data.services.map(s=>`<option value="${s.id}">${e(s.name)}</option>`).join('');
      $('price-item').innerHTML=data.items.map(i=>`<option value="${i.id}">${e(i.name)}</option>`).join('');
      $('price-rows').innerHTML=data.prices.length?data.prices.map(p=>`<tr><td>${e(names.get(String(p.serviceId))||('Service #'+p.serviceId))}</td><td>${e(p.item)}</td><td>${e(money(p.price))}</td></tr>`).join(''):'<tr><td colspan="3">No item prices have been recorded.</td></tr>';
      syncPrice();
    };
    const load=async()=>{data=await api('/administration/catalog');render();};
    const syncPrice=()=>{const found=data.prices.find(p=>String(p.serviceId)===$('price-service').value&&String(p.itemId)===$('price-item').value);$('price').value=found?Number(found.price).toFixed(2):'';};
    $('service-rows').onclick=event=>{const button=event.target.closest('[data-edit-service]');if(!button)return;const row=data.services.find(s=>String(s.id)===button.dataset.editService);editing=row;$('service-name').value=row.name;$('service-description').value=row.description;$('turnaround').value=row.turnaroundHours;$('service-active').checked=row.active;$('service-form-title').textContent='Edit service #'+row.id;$('service-name').focus();};
    $('cancel-service').onclick=clear;
    $('service-form').onsubmit=event=>{event.preventDefault();run(async()=>{const input={name:$('service-name').value.trim(),description:$('service-description').value.trim(),turnaroundHours:Number($('turnaround').value),active:$('service-active').checked,version:editing?.version??null};await api(editing?'/administration/services/'+editing.id:'/administration/services',editing?'PUT':'POST',input);clear();await load();notice('Service saved.','success');},event.submitter);};
    $('price-service').onchange=syncPrice;$('price-item').onchange=syncPrice;
    $('price-form').onsubmit=event=>{event.preventDefault();run(async()=>{await api('/administration/services/'+$('price-service').value+'/prices','PUT',{itemId:Number($('price-item').value),price:Number($('price').value)});await load();notice('Item price saved.','success');},event.submitter);};
    await load();
  }

  async function staff(context) {
    let rows=[],editing;
    const clear=()=>{editing=null;$('staff-form').reset();$('staff-active').checked=true;$('staff-form-title').textContent='Add account';$('staff-password').required=true;};
    const render=()=>{$('staff-rows').innerHTML=rows.length?rows.map(r=>`<tr><td><strong>${e(r.name)}</strong></td><td>${e(r.role)}</td><td>${e(r.email)}<br><small>${e(r.phone)}</small></td><td><span class="status">${r.active?'ACTIVE':'INACTIVE'}</span></td><td>${e(date(r.updatedAt))}</td><td><button class="custom_button" data-edit-staff="${r.id}">Edit</button></td></tr>`).join(''):'<tr><td colspan="6">No operational accounts match this search.</td></tr>';};
    const load=async()=>{const query=new URLSearchParams({search:$('search-staff').value.trim()});rows=await api('/administration/staff?'+query);render();};
    const activity=async()=>{const items=await api('/administration/activity');$('admin-activity').innerHTML=items.length?items.slice(0,12).map(a=>`<div class="history-entry"><strong>${e(a.action)}</strong><div>${e(a.details)}</div><small class="muted">${e(a.actor)} · ${e(date(a.createdAt))}</small></div>`).join(''):'<p class="muted">No administrative changes recorded yet.</p>';};
    $('staff-search').onsubmit=event=>{event.preventDefault();run(load,event.submitter);};
    $('staff-rows').onclick=event=>{const button=event.target.closest('[data-edit-staff]');if(!button)return;const row=rows.find(r=>String(r.id)===button.dataset.editStaff);editing=row;$('first-name').value=row.firstName;$('last-name').value=row.lastName;$('staff-email').value=row.email;$('staff-phone').value=row.phone;$('staff-role').value=row.role;$('staff-active').checked=row.active;$('staff-password').value='';$('staff-password').required=false;$('staff-form-title').textContent='Edit account #'+row.id;$('first-name').focus();};
    $('cancel-staff').onclick=clear;
    $('staff-form').onsubmit=event=>{event.preventDefault();run(async()=>{const input={firstName:$('first-name').value.trim(),lastName:$('last-name').value.trim(),email:$('staff-email').value.trim(),phone:$('staff-phone').value.trim(),role:$('staff-role').value,password:$('staff-password').value||null,active:$('staff-active').checked,version:editing?.version??null};await api(editing?'/administration/staff/'+editing.id:'/administration/staff',editing?'PUT':'POST',input);clear();await Promise.all([load(),activity()]);notice('Operational account saved.','success');},event.submitter);};
    clear();await Promise.all([load(),activity()]);
  }

  async function roles() {
    const data=await api('/administration/roles');
    $('role-rows').innerHTML=data.roles.map(role=>`<tr><td><strong>${e(role.label)}</strong><br><small>${e(role.role)}</small></td><td>${role.permissions.map(e).join(' · ')}</td></tr>`).join('');
  }

  run(async()=>{const context=await Support.init();if(page==='service-catalog')await catalog();if(page==='staff-accounts')await staff(context);if(page==='roles-permissions')await roles();});
})();
