/*
 * Staff Dashboard (html/staff/dashboard.html).
 * Counts orders waiting to be received (In Shop) and in process, counts issues whose
 * customer-service case is still open, and lists orders oldest-update first so the ones
 * waiting longest are handled first. The greeting is filled by account/dashboard-identity.js.
 */
(() => {
  const { $, escape, dateTime, badge, api, notice } = Processing;

  async function load() {
    try {
      const [orders, issues] = await Promise.all([api("/orders"), api("/issues")]);

      $("metric-receive").textContent = orders.filter((order) => order.statusID === 7).length;
      $("metric-process").textContent = orders.filter((order) => order.statusID !== 7).length;
      $("metric-issues").textContent = issues.filter((issue) => !["Resolved", "Closed"].includes(issue.caseStatus)).length;

      // Orders with no status log yet sort first, then the oldest update.
      const byWaiting = [...orders].sort((a, b) => (a.lastUpdated || "").localeCompare(b.lastUpdated || ""));
      $("priority-rows").innerHTML = byWaiting.length
        ? byWaiting.map((order) => `<tr>
            <td>#${order.orderID}</td>
            <td>${escape(order.customerName)}</td>
            <td>${badge(order.statusLabel)}</td>
            <td>${escape(dateTime(order.lastUpdated))}</td>
            <td><a class="link" href="${order.statusID === 7 ? "receive_items" : "order_processing"}.html?orderId=${order.orderID}">Open</a></td>
          </tr>`).join("")
        : '<tr><td colspan="5" class="muted">No orders are being processed.</td></tr>';
    } catch (error) {
      $("priority-rows").innerHTML = "";
      notice(escape(error.message), "error");
    }
  }

  load();
})();
