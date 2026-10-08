(() => {
  const {$,api,escape:e,date,notice,run}=Support;
  async function load() {
    const data=await api('/rider-assignments');
    $('assignment-rows').innerHTML=data.tasks.length?data.tasks.map(task=>`<tr><td>${e(task.scheduled?date(task.scheduled):'Not scheduled')}</td><td>#${e(task.orderID)}</td><td>${e(task.taskType)}</td><td>${e(task.area)}</td><td><select class="input" data-rider="${task.id}" aria-label="Rider for order ${task.orderID}"><option value="">Select rider</option>${data.riders.map(rider=>`<option value="${rider.id}">${e(rider.name)}</option>`).join('')}</select></td><td><button class="custom_button" type="button" data-assign="${task.id}" ${data.riders.length?'':'disabled'}>Assign</button></td></tr>`).join(''):'<tr><td colspan="6">No tasks are waiting for assignment.</td></tr>';
    if(!data.riders.length)notice('No active riders are available. Add or activate a rider account first.');
  }
  $('refresh-tasks').onclick=()=>run(async()=>{notice('');await load();},$('refresh-tasks'));
  $('assignment-rows').onclick=event=>{
    const button=event.target.closest('[data-assign]');if(!button)return;
    const select=button.closest('tr').querySelector('[data-rider]');
    if(!select.value){notice('Select a rider before assigning.','error');select.focus();return;}
    run(async()=>{
      const result=await api('/rider-assignments/'+button.dataset.assign,'PUT',{riderId:Number(select.value)});
      await load();notice(result.message,'success');
    },button);
  };
  run(async()=>{await Support.init();await load();});
})();
