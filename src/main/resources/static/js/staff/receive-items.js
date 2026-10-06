/*
 * Receive Items (html/staff/receive_items.html) - TC-LP01 to LP04.
 *
 * 1. Staff enter an order number; the order and its lines are loaded.
 * 2. For each line they enter the received quantity (at least 1) and its condition.
 * 3. Confirm sends the counts. The server either saves them and moves the order to
 *    Verifying Items (200; the order details are then hidden, leaving only the success message),
 *    or saves nothing and returns the mismatched lines (422), which are
 *    shown with a link to report a missing item or count mismatch.
 */
(() => {
  const { $, escape, param, badge, api, notice, run } = Processing;
  let order = null;

  /** Loads an order and builds one row per order line. */
  async function loadOrder(orderId) {
    notice("");
    order = await api(`/orders/${encodeURIComponent(orderId)}`);
    $("order-section").hidden = false;
    $("order-title").textContent = `Order #${order.orderID}`;
    const items = order.lines.reduce((total, line) => total + line.quantity, 0);
    $("order-summary").textContent = `${order.customerName} · Expected ${items} item${items === 1 ? "" : "s"}`;
    $("order-status").innerHTML = badge(order.statusLabel);

    // Quantities start empty so staff have to type what they actually counted.
    $("line-rows").innerHTML = order.lines.map((line) => `
      <tr data-line="${line.orderLineID}">
        <td>${escape(line.itemName)}</td>
        <td>${escape(line.serviceName)}</td>
        <td>${line.quantity}</td>
        <td><input class="input received" type="number" min="1" step="1"
              value="${line.receivedQuantity ?? ""}" aria-label="${escape(line.itemName)} received" /></td>
        <td><select class="input condition" aria-label="${escape(line.itemName)} condition">
              ${["As expected", "Stained", "Damaged"].map((c) =>
                `<option${c === (line.itemCondition || "As expected") ? " selected" : ""}>${c}</option>`).join("")}
            </select></td>
      </tr>`).join("");

    // Only an In Shop order (status 7) can be received.
    const receivable = order.statusID === 7;
    $("receive-form").querySelectorAll("input, select, button").forEach((el) => (el.disabled = !receivable));
    if (!receivable) {
      notice(`Order #${order.orderID} is ${escape(order.statusLabel)}. Items can only be received while an order is In Shop. ` +
        `<a class="link" href="order_processing.html?orderId=${order.orderID}">Open the order →</a>`);
    }
  }

  /** Sends the counts; handles the 422 mismatch response separately from real errors. */
  async function confirmReceipt() {
    const rows = [...$("line-rows").querySelectorAll("tr")];
    const lines = rows.map((row) => ({
      orderLineID: Number(row.dataset.line),
      receivedQuantity: row.querySelector(".received").value === "" ? null : Number(row.querySelector(".received").value),
      condition: row.querySelector(".condition").value,
    }));

    // Quick checks before calling the server (TC-LP03: 0 or negative is never sent).
    if (lines.some((line) => line.receivedQuantity === null)) return notice("Enter the received quantity for every item.", "error");
    if (lines.some((line) => !Number.isInteger(line.receivedQuantity) || line.receivedQuantity < 1)) {
      return notice("Quantity must be at least 1 for every item. Nothing was saved.", "error");
    }
    if (!$("counted").checked) return notice("Tick the box to confirm you have counted and checked every item.", "error");

    try {
      const result = await api(`/orders/${order.orderID}/receive`, "POST", { lines });
      // Receipt succeeded: hide the order details so only the success message is left, and
      // drop ?orderId= so a page refresh does not bring the received order back.
      const receivedId = order.orderID;
      $("order-section").hidden = true;
      order = null;
      history.replaceState(null, "", "receive_items.html");
      // The order has left In Shop, so refresh the drop-down; it no longer lists this order.
      await loadReceivableOrders();
      notice(`${escape(result.message)} <a class="link" href="order_processing.html?orderId=${receivedId}">Open the order →</a>`, "success");
    } catch (error) {
      if (error.status !== 422) throw error;
      // TC-LP02 / LP04: counts do not match, nothing was saved.
      const mismatches = error.data.mismatches;
      const fewer = mismatches.some((m) => m.received < m.ordered);
      const issueType = fewer ? "Missing item" : "Item count mismatch";
      notice(`<strong>Count mismatch - nothing was saved.</strong><br>` +
        mismatches.map((m) => `${escape(m.item)}: received ${m.received}, ordered ${m.ordered}`).join("<br>") +
        `<br>Recount the items, or <a class="link" href="issue_reports.html?orderId=${order.orderID}&orderLineId=${mismatches[0].orderLineID}&issue=${encodeURIComponent(issueType)}#new-report">` +
        `report ${fewer ? "a missing item" : "the count mismatch"} →</a>`, "error");
    }
  }

  /**
   * Makes sure the drop-down has an option for this order and selects it. Used for orders that are
   * not in the In Shop list (a ?orderId= link to an already received order, or one just received).
   */
  function selectOrder(orderId, label) {
    const select = $("order-number");
    let option = [...select.options].find((o) => o.value === String(orderId));
    if (!option) {
      option = new Option(label || `#${orderId}`, orderId);
      select.add(option);
    }
    select.value = String(orderId);
  }

  /** Fills the drop-down with the orders that can be received (In Shop, status 7). */
  async function loadReceivableOrders(keepOrderId) {
    const select = $("order-number");
    const orders = await api("/orders?status=7");
    select.innerHTML = '<option value="">Select an order to receive</option>' +
      orders.map((o) => `<option value="${o.orderID}">#${o.orderID} · ${escape(o.customerName)} · ` +
        `${o.itemCount} item${o.itemCount === 1 ? "" : "s"}</option>`).join("");
    // An order the page is already showing stays selected even if it has left the In Shop list.
    if (keepOrderId) selectOrder(keepOrderId);
    const nothingToPick = orders.length === 0 && !keepOrderId;
    if (nothingToPick) notice("No orders are waiting to be received.");
    select.disabled = nothingToPick;
    $("find-form").querySelector("button").disabled = nothingToPick;
  }

  $("find-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const orderId = $("order-number").value;
    if (!orderId) return notice("Select an order to receive.", "error");
    history.replaceState(null, "", `?orderId=${orderId}`);
    run(() => loadOrder(orderId), event.submitter);
  });

  $("receive-form").addEventListener("submit", (event) => {
    event.preventDefault();
    run(confirmReceipt, $("confirm-button"));
  });

  // Opening receive_items.html?orderId=6 loads that order straight away.
  const fromLink = param("orderId");
  run(async () => {
    // The linked order may no longer be In Shop; keepOrderId adds it to the list if it is missing.
    await loadReceivableOrders(fromLink);
    if (fromLink) await loadOrder(fromLink);
  });
})();
