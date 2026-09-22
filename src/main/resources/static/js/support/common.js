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
