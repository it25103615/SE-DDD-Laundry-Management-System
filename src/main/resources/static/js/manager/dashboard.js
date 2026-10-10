/* Manager overview: authenticated database report, refreshed every 30 seconds. */
(() => {
  const { $, api, escape: e, money, table, notice } = Support;
  let loading = false;
  let lastUpdated = null;

  async function load() {
    if (loading) return;
    loading = true;
    $('refresh-dashboard').disabled = true;
    try {
      const report = await api('/reports');
      const summary = report.summary;
      $('total-orders').textContent = summary.totalOrders;
      $('recorded-payments').textContent = money(summary.recordedPayments);
      $('completed-orders').textContent = summary.deliveredOrders;
      $('open-cases').textContent = report.support.openCases;
      $('status-chart').innerHTML = report.statuses.length
        ? report.statuses.map(row => `<div class="chart-row"><span>${e(row.status)}</span><div class="bar" aria-hidden="true"><span style="width:${Math.max(0, Math.min(100, 100 * Number(row.orders) / Math.max(Number(summary.totalOrders), 1)))}%"></span></div><strong>${e(row.orders)}</strong></div>`).join('')
        : '<p class="muted">No orders recorded yet.</p>';
      table('service-rows', report.services, ['service', 'orders', 'items', row => money(row.value)], 'No service activity recorded yet.');
      $('operational-alerts').innerHTML = report.alerts.map(row => `<div class="operational-alert-row"><strong>${e(row.label)}</strong><span class="status ${Number(row.total) > 0 ? 'status_warning' : 'status_success'}" aria-label="${e(row.total)} ${e(row.label)}">${e(row.total)}</span></div>`).join('');
      lastUpdated = new Date().toLocaleTimeString('en-LK');
      $('dashboard-updated').textContent = `Updated ${lastUpdated} · refreshes every 30 seconds`;
      notice('');
    } catch (error) {
      $('dashboard-updated').textContent = lastUpdated
        ? `Update failed · showing records from ${lastUpdated}`
        : 'Dashboard data unavailable';
      notice(error.message, 'error');
    } finally {
      loading = false;
      $('refresh-dashboard').disabled = false;
    }
  }

  $('refresh-dashboard').addEventListener('click', load);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) load();
  });
  setInterval(() => { if (!document.hidden) load(); }, 30000);
  load();
})();
