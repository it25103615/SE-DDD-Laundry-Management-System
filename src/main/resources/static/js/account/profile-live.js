document.addEventListener('DOMContentLoaded',async()=>{
  const $=id=>document.getElementById(id);
  const destination={CUSTOMER:'/html/customer/dashboard.html',STAFF:'/html/staff/dashboard.html',RIDER:'/html/rider/dashboard.html',MANAGER:'/html/admin/manager/dashboard.html',CSM:'/html/admin/customer-service-manager/dashboard.html',CUSTOMER_SERVICE_MANAGER:'/html/admin/customer-service-manager/dashboard.html',OWNER:'/html/admin/owner/dashboard.html',ADMIN:'/html/admin/owner/dashboard.html'};
  let csrf,account;
  const show=(message,error=false)=>{const box=$('profile-message');box.textContent=message;box.className='profile-message'+(error?' error':'');box.hidden=false;};
  async function api(path,method='GET',body){const headers={Accept:'application/json'};if(csrf&&method!=='GET')headers[csrf.headerName]=csrf.token;if(body)headers['Content-Type']='application/json';const response=await fetch('/api/account/profile'+path,{method,headers,body:body?JSON.stringify(body):undefined});if(response.redirected){location.href='/html/auth/login.html';return;}const result=await response.json().catch(()=>({}));if(!response.ok)throw new Error(result.message||result.detail||'Unable to save your profile.');return result;}
  function render(data){account=data;$('name').value=data.name||'';$('phone').value=String(data.phone||'').trim();$('email').value=data.email||'';$('profile-role').textContent=`Signed in as ${data.role}`;$('profile-badge').textContent=String(data.name||'User').split(/\s+/).slice(0,2).map(part=>part[0]).join('').toUpperCase();$('dashboard-link').href=destination[data.role]||'/html/portal.html';$('home-link').href=$('dashboard-link').href;}
  try{csrf=await fetch('/api/auth/csrf').then(r=>r.json());render(await api(''));}catch(error){show(error.message,true);}
  $('phone').addEventListener('input',()=>{$('phone').value=$('phone').value.replace(/\D/g,'').slice(0,10);});
  document.querySelectorAll('[data-toggle]').forEach(toggle=>toggle.addEventListener('click',()=>{
    const field=$(toggle.dataset.toggle);
    const showing=field.type==='password';
    field.type=showing?'text':'password';
    toggle.textContent=showing?'Hide':'Show';
    toggle.setAttribute('aria-pressed',String(showing));
    toggle.setAttribute('aria-label',`${showing?'Hide':'Show'} ${field.id.replaceAll('-',' ')}`);
    field.focus();
  }));
  const newPassword=$('new-password'),confirmation=$('confirm-password');
  const strongPassword=value=>/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,72}$/.test(value);
  function checkNewPassword(){
    newPassword.setCustomValidity(!newPassword.value||strongPassword(newPassword.value)?'':'Use 8–72 characters with uppercase, lowercase, a number, and a special character. No spaces.');
    confirmation.setCustomValidity(!confirmation.value||confirmation.value===newPassword.value?'':'New passwords do not match.');
  }
  newPassword.addEventListener('input',checkNewPassword);
  confirmation.addEventListener('input',checkNewPassword);
  $('details-form').addEventListener('submit',async event=>{event.preventDefault();if(!event.target.reportValidity())return;const button=event.submitter;button.disabled=true;try{const saved=await api('','PUT',{fullName:$('name').value.trim(),email:$('email').value.trim(),phone:$('phone').value.trim()});render(saved);if(saved.emailChanged){show('Profile saved. Your email changed; signing you out so you can log in with the new email.');setTimeout(async()=>{await fetch('/logout',{method:'POST',headers:{[csrf.headerName]:csrf.token}});location.href='/html/auth/login.html';},2500);}else show('Profile saved. Your dashboard will show the new name when refreshed.');}catch(error){show(error.message,true);}finally{button.disabled=false;}});
  $('password-form').addEventListener('submit',async event=>{event.preventDefault();checkNewPassword();if(!event.target.reportValidity())return;const button=event.submitter;button.disabled=true;try{await api('/password','PUT',{currentPassword:$('current-password').value,newPassword:newPassword.value,confirmPassword:confirmation.value});event.target.reset();document.querySelectorAll('[data-toggle]').forEach(toggle=>{const field=$(toggle.dataset.toggle);field.type='password';toggle.textContent='Show';toggle.setAttribute('aria-pressed','false');});show('Password updated. Use the new password at your next login.');}catch(error){show(error.message,true);}finally{button.disabled=false;}});
});
