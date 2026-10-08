document.addEventListener('DOMContentLoaded',async()=>{
  try{
    const response=await fetch('/api/account/profile',{headers:{Accept:'application/json'}});
    if(!response.ok)return;
    const user=await response.json();
    const greeting=document.getElementById('dashboard-greeting')||document.getElementById('greeting');
    if(greeting)greeting.textContent=`Welcome back, ${user.name}`;
    //The profile badge is filled in by the shared navigation bar (global-pre.js)
  }catch{}
});
