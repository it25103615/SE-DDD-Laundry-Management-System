(() => {
 const {$,escape:e,api,notice,run,date}=Support;
 let rows=[],editing=null;
 function clear() {editing=null;$('setting-form').reset();$('setting-title').textContent='Create setting';}
 async function load() {
   const [settings,activity]=await Promise.all([api('/settings'),api('/activity')]);rows=settings;
   $('settings').innerHTML=rows.length?rows.map(s=>`<article class="history-entry"><strong>${e(s.key)}</strong><p>${e(s.value)}</p><p class="small muted">${e(s.description)}</p><div class="actions"><button class="custom_button" data-edit="${s.id}">Edit</button><button class="custom_button danger" data-delete="${s.id}">Delete</button></div></article>`).join(''):'<p>No settings yet. Create the first operational setting.</p>';
   $('activity').innerHTML=activity.length?activity.map(a=>`<div class="history-entry"><strong>${e(a.action)}</strong> · ${e(a.details)}<div class="small muted">${e(a.actor)} · ${e(date(a.createdAt))}</div></div>`).join(''):'<p>No administrative changes yet.</p>';
 }
 $('cancel-setting').onclick=clear;
 $('setting-form').onsubmit=event=>{event.preventDefault();run(async()=>{
   const data={key:$('key').value.trim(),value:$('value').value.trim(),description:$('description').value.trim(),version:editing?.version??null};
   if(!data.value || !data.description)throw new Error('Value and description cannot be blank.');
   await api(editing?'/settings/'+editing.id:'/settings',editing?'PUT':'POST',data);clear();await load();notice('Setting saved to the database.','success');
 },event.submitter);};
 $('settings').onclick=event=>{const b=event.target.closest('button');if(!b)return;
   const id=Number(b.dataset.edit || b.dataset.delete),row=rows.find(r=>r.id===id);if(!row)return;
   if(b.dataset.edit){editing=row;for(const key of ['key','value','description'])$(key).value=row[key];$('setting-title').textContent='Edit setting';$('key').focus();}
   else run(async()=>{if(!confirm('Delete setting '+row.key+'?'))return;await api('/settings/'+id+'?version='+row.version,'DELETE');if(editing?.id===id)clear();await load();notice('Setting deleted.','success');},b);
 };
 run(async()=>{await Support.init();await load();});
})();
