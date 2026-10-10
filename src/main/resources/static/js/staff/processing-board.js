/*
 * Processing Board (html/staff/processing_board.html).
 * Loads every order currently in processing and shows it in the column for its stage.
 * In Shop and Verifying Items share "Received", Washing and Dry Clean share "Cleaning",
 * Drying and Ironing share "Drying & ironing"; each card opens the order's page.
 */
(() => {
  const { $, escape, dateTime, api, notice } = Processing;

  // Board columns in workflow order, with the status IDs each one holds (four columns to fit
  // the board layout; each card still shows its exact status).
  const COLUMNS = [
    { title: "Received", statuses: [7, 8] },            // In Shop (to be counted) and Verifying Items
    { title: "Cleaning", statuses: [9, 19] },           // Washing or Dry Clean
    { title: "Drying & ironing", statuses: [10, 11] },
    { title: "Quality inspection", statuses: [21] },    // quality check, packing, mark as ready
  ];

  // Shown on the card for every route except the wash route (the usual one).
  const ROUTE_LABELS = { DRY_CLEAN: "Dry clean", SHOE_CLEAN: "Shoe cleaning", IRONING: "Ironing only" };

  /** One order card linking to its Order Processing page. */
  function card(order) {
    const issues = order.openIssues ? ` · <span class="status status_error">${order.openIssues} OPEN ISSUE${order.openIssues > 1 ? "S" : ""}</span>` : "";
    const route = ROUTE_LABELS[order.route] ? ` · ${ROUTE_LABELS[order.route]}` : "";
    return `<a class="order_card" href="order_processing.html?orderId=${order.orderID}" style="display:block;text-decoration:none">
        <strong>#${order.orderID} · ${escape(order.customerName)}</strong>
        <p class="muted small">${order.itemCount} item${order.itemCount === 1 ? "" : "s"}${route}</p>
        <p class="small">${escape(order.statusLabel)} · updated ${escape(dateTime(order.lastUpdated))}${issues}</p>
      </a>`;
  }

  /**
   * Switches the board between grid (columns side by side) and list (columns stacked).
   * The button always offers the view you are not in, and the choice is kept in ?view=list
   * so a refresh or shared link opens the same view.
   */
  function setView(list) {
    $("board").classList.toggle("list_mode", list);
    const toggle = $("view-toggle");
    toggle.textContent = list ? "Grid view" : "List view";
    toggle.setAttribute("aria-pressed", String(list));
    history.replaceState(null, "", list ? "?view=list" : location.pathname);
  }

  async function load() {
    try {
      const orders = await api("/orders");
      $("board-count").textContent = `${orders.length} order${orders.length === 1 ? "" : "s"} in processing`;
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

  // global-post.js has already put list_mode on the board when the page was opened with ?view=list,
  // so read the starting view from the board itself to get the button label right.
  $("view-toggle").addEventListener("click", () => setView(!$("board").classList.contains("list_mode")));
  setView($("board").classList.contains("list_mode"));

  load();
})();
