document.addEventListener('DOMContentLoaded',async()=>{
  try{
    const response=await fetch('/api/account/profile',{headers:{Accept:'application/json'}});
    if(!response.ok)return;
    const user=await response.json();
    const greeting=document.getElementById('dashboard-greeting')||document.getElementById('greeting');
    if(greeting)greeting.textContent=`Welcome back, ${user.name}`;
    const badge=document.getElementById('dashboard-badge')||document.getElementById('profile_badge');
    if(badge)badge.textContent=String(user.name||'User').split(/\s+/).slice(0,2).map(part=>part[0]).join('').toUpperCase();
  }catch{}
});
