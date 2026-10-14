/* Manager overview: authenticated database report, refreshed every 30 seconds. */
(() => {
  const { $, api, escape: e, money, table, notice } = Support;
  let loading = false;
  let lastUpdated = null;
  // Status counts from the last report, and the stage whose breakdown is open (null = none).
  // Kept here so the open stage survives the 30-second refresh.
  let lastStatuses = [];
  let selectedStage = null;

  /* Workflow stages for the "Current order statuses" card. Each stage lists its status
     labels in the order an order moves through them, so the card reads top to bottom
     like the real process instead of being sorted by count. Cancelled is a normal end
     state, so it sits under Closed; only the "Failed" stage needs someone to act. */
  const STAGES = [
    { name: 'Intake', statuses: ['Unconfirmed', 'Payment Verified'] },
    { name: 'Pickup', statuses: ['Awaiting Pickup', 'En Route To Pickup', 'Picked Up', 'En Route To Shop'] },
    { name: 'In shop', statuses: ['In Shop', 'Verifying Items', 'Washing', 'Dry Clean', 'Drying', 'Ironing', 'Quality Inspection'] },
    { name: 'Delivery', statuses: ['Awaiting Delivery', 'En Route To Delivery'] },
    { name: 'Closed', statuses: ['Delivered', 'Completed', 'Cancelled'] },
    { name: 'Failed', statuses: ['Payment Failed', 'Pickup Failed', 'Delivery Failed'], alert: true }
  ];

  /* Builds the card: a tile per stage with its total, then the status breakdown of the
     one stage the manager has clicked (none by default). */
  function renderStatuses(rows) {
    // Order count per status label, as reported by the database.
    const counts = new Map(rows.map(row => [row.status, Number(row.orders) || 0]));
    const known = new Set(STAGES.flatMap(stage => stage.statuses));
    const groups = STAGES.map(stage => ({
      ...stage,
      rows: stage.statuses.map(label => ({ label, orders: counts.get(label) || 0 }))
    }));
    // A label this page does not know (a newly added status, or the report's "Unknown")
    // still has to be shown, so it goes into an extra "Other" group.
    const other = rows.filter(row => !known.has(row.status))
      .map(row => ({ label: row.status, orders: Number(row.orders) || 0 }));
    if (other.length) groups.push({ name: 'Other', rows: other, extra: true });
    groups.forEach(group => { group.total = group.rows.reduce((sum, row) => sum + row.orders, 0); });

    // The open stage is forgotten once it has no orders left (its tile is disabled then).
    const open = groups.find(group => group.name === selectedStage && group.total > 0);
    if (!open) selectedStage = null;

    // The six fixed stages always get a tile, even at zero, so the strip never shifts around.
    // Each tile is a button: clicking it opens that stage's breakdown, clicking again closes it.
    const tiles = groups.map(group =>
      `<button type="button" class="stage_tile${group.alert && group.total > 0 ? ' stage_tile_alert' : ''}${group === open ? ' stage_tile_open' : ''}" data-stage="${e(group.name)}" aria-pressed="${group === open}"${group.total > 0 ? '' : ' disabled'}><span>${e(group.name)}</span><strong>${e(group.total)}</strong></button>`
    ).join('');

    // Nothing is listed until a stage is picked, so the card keeps the same height
    // however many statuses are in use.
    if (!open) return `<div class="stage_strip">${tiles}</div><p class="stage_note muted small">Select a stage to see its statuses.</p>`;

    // One row per status of the open stage that has orders. A bar is that status's share
    // of the stage: a full bar means every order in the stage is in that status.
    const details = open.rows.filter(row => row.orders > 0).map(row =>
      `<div class="status_row"><span>${e(row.label)}</span><div class="status_bar" aria-hidden="true"><span style="width:${100 * row.orders / open.total}%"></span></div><strong>${e(row.orders)}</strong></div>`
    ).join('');

    return `<div class="stage_strip">${tiles}</div><p class="stage_note muted small">${e(open.name)} · bars show each status's share of the ${e(open.total)} ${open.total === 1 ? 'order' : 'orders'} in this stage.</p>${details}`;
  }

  /* Draws the card from the last report. Also called when a tile is clicked, so opening
     a stage does not need another trip to the server. */
  function drawStatuses() {
    $('status-chart').innerHTML = lastStatuses.length
      ? renderStatuses(lastStatuses)
      : '<p class="muted">No orders recorded yet.</p>';
  }

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
      lastStatuses = report.statuses;
      drawStatuses();
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

  // One listener on the card instead of one per tile, because the tiles are rebuilt on
  // every refresh. Clicking the open tile again closes it.
  $('status-chart').addEventListener('click', event => {
    const tile = event.target.closest('.stage_tile');
    if (!tile) return;
    selectedStage = selectedStage === tile.dataset.stage ? null : tile.dataset.stage;
    drawStatuses();
  });
  $('refresh-dashboard').addEventListener('click', load);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) load();
  });
  setInterval(() => { if (!document.hidden) load(); }, 30000);
  load();
})();
