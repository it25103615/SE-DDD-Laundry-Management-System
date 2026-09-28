/*
 * Processing Board (html/staff/processing_board.html).
 * Loads every order currently in processing and shows it in the column for its stage.
 * In Shop and Verifying Items share "Received", Washing and Dry Clean share "Cleaning";
 * each card opens the order's page.
 */
(() => {
  const { $, escape, dateTime, api, notice } = Processing;

  // Board columns in workflow order, with the status IDs each one holds (four columns to fit
  // the board layout; each card still shows its exact status).
  const COLUMNS = [
    { title: "Received", statuses: [7, 8] },    // In Shop (to be counted) and Verifying Items
    { title: "Cleaning", statuses: [9, 19] },   // Washing or Dry Clean
    { title: "Drying", statuses: [10] },
    { title: "Ironing & QC", statuses: [11] },  // Ironing, quality check, packing
  ];

  /** One order card linking to its Order Processing page. */
  function card(order) {
    const issues = order.openIssues ? ` · <span class="status status_error">${order.openIssues} OPEN ISSUE${order.openIssues > 1 ? "S" : ""}</span>` : "";
    const route = order.route === "DRY_CLEAN" ? " · Dry clean" : "";
    return `<a class="order_card" href="order_processing.html?orderId=${order.orderID}" style="display:block;text-decoration:none">
        <strong>#${order.orderID} · ${escape(order.customerName)}</strong>
        <p class="muted small">${order.itemCount} item${order.itemCount === 1 ? "" : "s"}${route}</p>
        <p class="small">${escape(order.statusLabel)} · updated ${escape(dateTime(order.lastUpdated))}${issues}</p>
      </a>`;
  }

  async function load() {
    try {
      const orders = await api("/orders");
      $("board").innerHTML = COLUMNS.map((column) => {
        const inColumn = orders.filter((order) => column.statuses.includes(order.statusID));
        return `<div class="board_column">
            <h3>${column.title} · ${inColumn.length}</h3>
            ${inColumn.length ? inColumn.map(card).join("") : '<p class="muted small">No orders</p>'}
          </div>`;
      }).join("");
    } catch (error) {
      $("board").innerHTML = "";
      notice(escape(error.message), "error");
    }
  }

  load();
})();
