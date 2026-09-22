document.addEventListener('DOMContentLoaded', async () => {
  const byId = id => document.getElementById(id);
  try {
    const response = await fetch('/api/customer/dashboard', {headers:{Accept:'application/json'}});
    if (response.status === 401 || response.redirected) { location.href='/html/auth/login.html'; return; }
    if (!response.ok) throw new Error('Your dashboard could not load. Refresh the page after signing in.');
    const data = await response.json();
    const name = String(data.name || 'Customer').trim();
    byId('welcome').textContent = `Welcome back, ${name} 👋`;
    byId('profile-badge').textContent = name.split(/\s+/).slice(0,2).map(part=>part[0]).join('').toUpperCase();
    byId('total-orders').textContent = data.orders;
    byId('support-count').textContent = data.openSupport;
    const latest = data.latestOrder;
    byId('active-orders').textContent = data.activeOrders;
    byId('order-summary').textContent = data.activeOrders ? (latest.status || 'In progress') : 'No active order';
    const orderArea = byId('latest-order');
    if (latest.id) {
      const title = document.createElement('h3'); title.className='latest-id'; title.textContent=`Order #${latest.id}`;
      const status = document.createElement('p'); status.className='muted'; status.textContent=`Current status: ${latest.status || 'Awaiting update'}`;
      orderArea.replaceChildren(title,status);
    } else {
      const empty = document.createElement('p'); empty.className='muted'; empty.textContent='No orders yet. When you place one, its latest status will appear here.';
      orderArea.replaceChildren(empty);
    }
  } catch(error) {
    byId('dashboard-notice').hidden=false;
    byId('dashboard-notice').textContent=error.message;
  }
});
