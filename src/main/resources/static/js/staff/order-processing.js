/*
 * Order Processing (html/staff/order_processing.html?orderId=N) - TC-LP05, LP07 to LP10.
 *
 * Loads one order from GET /api/processing/orders/{id} and draws:
 *  - the timeline for the order's route (wash: Washing -> Drying -> Ironing, or
 *    dry clean: Dry Clean -> Ironing), with completed / current / locked stages;
 *  - the items, the customer's instructions (the order's note and preferences, and the
 *    address's delivery instruction), the status history and issues;
 *  - the actions the server says are allowed next: move to the next stage, a manual status
 *    change (invalid moves are refused by the server, TC-LP08), the quality check with a
 *    rework stage when it fails (TC-LP09), packing, and "Mark as Ready" (TC-LP10).
 * After every action the page reloads the order so it always shows the saved state.
 * Buttons are created here (not in the HTML) so global-post.js does not attach demo toasts.
 */
(() => {
  const { $, escape, dateTime, param, badge, api, notice, run } = Processing;
  const orderId = param("orderId");
  let order = null;

  /** Timeline stages for each route; `status` is the order status that stage corresponds to. */
  const STAGES = {
    WASH: [
      { status: 7, title: "Order received", text: "Items counted against the order at the shop." },
      { status: 8, title: "Verifying items", text: "Items checked, tagged and condition recorded." },
      { status: 9, title: "Washing", text: "Wash every item with its selected service." },
      { status: 10, title: "Drying", text: "Dry items according to their care requirements." },
      { status: 11, title: "Ironing and finishing", text: "Press, fold or hang items as requested." },
      { qc: true, title: "Quality check", text: "Check item count, finish and customer instructions." },
      { packed: true, title: "Packed and ready", text: "Seal the order and release it for delivery." },
    ],
    DRY_CLEAN: [
      { status: 7, title: "Order received", text: "Items counted against the order at the shop." },
      { status: 8, title: "Verifying items", text: "Items checked, tagged and condition recorded." },
      { status: 19, title: "Dry cleaning", text: "Dry clean every item (no drying stage)." },
      { status: 11, title: "Ironing and finishing", text: "Press and hang items as requested." },
      { qc: true, title: "Quality check", text: "Check item count, finish and customer instructions." },
      { packed: true, title: "Packed and ready", text: "Seal the order and release it for delivery." },
    ],
  };

  /** Statuses offered in the manual status change (the server decides what is allowed). */
  const STATUS_CHOICES = [
    [8, "Verifying Items"], [9, "Washing"], [19, "Dry Clean"], [10, "Drying"],
    [11, "Ironing"], [12, "Awaiting Delivery"],
  ];

  /** How many timeline stages are finished, from the status and the latest quality check. */
  function completedStages(stages) {
    if (order.statusID === 12) return stages.length;               // released: everything done
    const index = stages.findIndex((stage) => stage.status === order.statusID);
    if (index < 0) return 0;
    const check = order.latestQualityCheck;
    if (order.statusID === 11 && check && check.result === "Passed") {
      return check.packed ? index + 3 : index + 2;                 // ironing + QC (+ packing) done
    }
    return index;                                                  // everything before the current stage
  }

  function renderTimeline() {
    const stages = STAGES[order.route] || STAGES.WASH;
    const done = completedStages(stages);
    $("staff-timeline").innerHTML = stages.map((stage, i) => {
      const state = i < done ? "completed" : i === done ? "current" : "locked";
      const label = state === "completed" ? "Completed" : state === "current" ? "Current stage" : "Locked";
      return `<li class="timeline-stage ${state}"><label>
          <span class="stage-marker">${state === "completed" ? "✓" : i + 1}</span>
          <span><strong>${i + 1}. ${stage.title}</strong><small>${stage.text}</small><em>${label}</em></span>
        </label></li>`;
    }).join("");
    const percent = Math.round((done / stages.length) * 100);
    $("progress-percent").textContent = percent + "%";
    $("progress-bar").style.width = percent + "%";
    $("route-note").textContent = order.route === "DRY_CLEAN"
      ? "Dry clean route: Dry Clean goes straight to Ironing."
      : "Wash route: Washing, then Drying, then Ironing.";
  }

  function renderDetails() {
    const items = order.lines.reduce((total, line) => total + line.quantity, 0);
    document.title = `Process Order #${order.orderID} | LaundryLink`;
    $("order-title").textContent = `Order #${order.orderID}`;
    $("order-summary").textContent = `${order.customerName} · ${items} item${items === 1 ? "" : "s"}`;
    $("order-status").innerHTML = badge(order.statusLabel);

    $("line-rows").innerHTML = order.lines.map((line) => `<tr>
        <td>${escape(line.itemName)}</td><td>${escape(line.serviceName)}</td><td>${line.quantity}</td>
        <td>${line.receivedQuantity ?? '<span class="muted">Not received</span>'}</td>
        <td>${escape(line.itemCondition || "—")}</td></tr>`).join("");

    // What the customer asked for, in up to three parts:
    //  - the note written when placing the order (orderInstructions);
    //  - the preferences ticked when placing the order (orderPreferences, readable labels);
    //  - TC-LP05: the delivery instruction saved on the customer's address, exactly as written.
    // Text typed by the customer is escaped; pre-wrap keeps the line breaks of a longer note.
    const asWritten = (text) => `<span style="white-space:pre-wrap;overflow-wrap:anywhere">${escape(text)}</span>`;
    const preferences = order.orderPreferences || [];
    const instructionParts = [];
    if (order.orderInstructions) instructionParts.push(asWritten(order.orderInstructions));
    if (preferences.length) {
      instructionParts.push(`<strong>Preferences</strong><br>${preferences.map((label) => escape(label)).join("<br>")}`);
    }
    if (order.deliveryInstruction) {
      // When the order has no note, say so, so this heading does not sit straight under the box title.
      if (!order.orderInstructions) instructionParts.unshift('<span class="muted">No note for this order.</span>');
      instructionParts.push(`<strong>Delivery instruction</strong><br>${asWritten(order.deliveryInstruction)}`);
    }
    $("instruction").innerHTML = `<strong>Customer instructions</strong><br>${
      instructionParts.length ? instructionParts.join("<br><br>") : '<span class="muted">No instructions.</span>'}`;

    $("history-rows").innerHTML = order.history.length
      ? order.history.map((h) => `<tr><td>${escape(h.fromStatus || "—")}</td><td>${escape(h.toStatus)}</td><td>${escape(dateTime(h.changedAt))}</td></tr>`).join("")
      : '<tr><td colspan="3" class="muted">No status changes yet.</td></tr>';

    $("issue-list").innerHTML = order.issues.length
      ? order.issues.map((issue) => `<p class="small"><strong>${escape(issue.issueType)}</strong> · ${escape(issue.subject)}
          ${badge(issue.caseStatus || "New")}<br>${escape(issue.description)}<br>
          <span class="muted">${escape(issue.reportedBy)} · ${escape(dateTime(issue.createdAt))}</span></p>`).join("")
      : '<p class="muted small">No issues reported.</p>';
    $("report-link").href = `issue_reports.html?orderId=${order.orderID}#new-report`;
  }

  /** The actions card: only what the server says is possible right now. */
  function renderActions() {
    const check = order.latestQualityCheck;
    const parts = [`<h2>Next step</h2>`];

    if (order.statusID === 7) {
      parts.push(`<p class="muted">Count the items before processing starts.</p>
        <a class="custom_button custom_button_bg" href="receive_items.html?orderId=${order.orderID}">Receive items →</a>`);
    } else if (order.nextStatus) {
      parts.push(`<button class="custom_button custom_button_bg" type="button" data-action="next"
        data-status="${order.nextStatus.statusID}" style="width:100%">Move to ${escape(order.nextStatus.label)} →</button>`);
    } else if (order.statusID === 12) {
      parts.push(`<div class="alert alert_success"><strong>Released for delivery.</strong><br>The order is waiting for a delivery rider.</div>`);
    }

    // TC-LP09: quality check after Ironing (failed -> choose a rework stage).
    if (order.statusID === 11) {
      if (check) {
        parts.push(`<p class="small" style="margin-top:14px">Latest check: ${badge(check.result)}${check.packed ? " " + badge("Packed") : ""}
          ${check.reworkStatusLabel ? "<br>Rework: " + escape(check.reworkStatusLabel) : ""}
          <br><span class="muted">${escape(check.checkedBy)} · ${escape(dateTime(check.checkedAt))}</span></p>`);
      }
      parts.push(`<h3 style="margin-top:18px">Quality check</h3>
        <div class="field"><label for="qc-result">Result</label>
          <select class="input" id="qc-result"><option>Passed</option><option>Failed</option></select></div>
        <div class="field" id="rework-field" style="display:none"><label for="qc-rework">Rework stage</label>
          <select class="input" id="qc-rework">${order.reworkOptions.map((o) => `<option value="${o.statusID}">${escape(o.label)}</option>`).join("")}</select></div>
        <label class="check_row" id="packed-field"><input type="checkbox" id="qc-packed" /> Order is packed</label>
        <div class="field"><label for="qc-notes">Notes</label><textarea class="input" id="qc-notes" maxlength="250"></textarea></div>
        <button class="custom_button custom_button_nobg" type="button" data-action="quality">Save quality check</button>`);
      if (check && check.result === "Passed" && !check.packed) {
        parts.push(`<button class="custom_button custom_button_nobg" type="button" data-action="pack" style="margin-top:10px">Mark as packed</button>`);
      }
    }

    // TC-LP10: only enabled once the latest check passed and the order is packed.
    if (order.statusID !== 12) {
      parts.push(`<div class="alert ${order.readyForDispatch ? "alert_success" : "alert_error"}" style="margin-top:18px">
          ${order.readyForDispatch ? "<strong>Ready to release</strong><br>Quality check passed and packed."
            : "<strong>Not ready yet</strong><br>Finish ironing, pass the quality check and pack the order."}</div>
        <button class="custom_button custom_button_bg" type="button" data-action="ready"
          style="width:100%;margin-top:12px${order.readyForDispatch ? "" : ";opacity:.45;cursor:not-allowed"}"
          ${order.readyForDispatch ? "" : "disabled"}>Mark as Ready →</button>`);
    }

    // Manual status change (TC-LP08 uses this to try an invalid skip).
    parts.push(`<h3 style="margin-top:22px">Set status</h3>
      <p class="muted small">Moves are checked against the order's route; skipped stages are refused.</p>
      <div class="field"><select class="input" id="set-status" aria-label="New status">
        ${STATUS_CHOICES.map(([id, label]) => `<option value="${id}">${label}</option>`).join("")}</select></div>
      <button class="custom_button custom_button_nobg" type="button" data-action="set-status">Update status</button>`);

    $("actions").innerHTML = parts.join("");

    // Show the rework stage only for a failed check, and "packed" only for a passed one.
    // (style.display is used because the site CSS sets display on these, which beats `hidden`.)
    const result = $("qc-result");
    if (result) {
      result.addEventListener("change", () => {
        const failed = result.value === "Failed";
        $("rework-field").style.display = failed ? "" : "none";
        $("packed-field").style.display = failed ? "none" : "";
      });
    }
  }

  async function load() {
    order = await api(`/orders/${encodeURIComponent(orderId)}`);
    renderDetails();
    renderTimeline();
    renderActions();
  }

  /** Performs a request, then reloads the order and shows a success message. */
  async function act(request, success) {
    order = await request();
    renderDetails();
    renderTimeline();
    renderActions();
    notice(escape(success), "success");
  }

  // One click handler for every action button in the card (they are re-created on each render).
  $("actions").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const id = order.orderID;
    const actions = {
      next: () => act(() => api(`/orders/${id}/status`, "PUT", { statusID: Number(button.dataset.status) }),
        "Order moved to the next stage."),
      "set-status": () => act(() => api(`/orders/${id}/status`, "PUT", { statusID: Number($("set-status").value) }),
        "Status updated."),
      quality: () => {
        const failed = $("qc-result").value === "Failed";
        return act(() => api(`/orders/${id}/quality-check`, "POST", {
          result: $("qc-result").value,
          reworkStatusID: failed ? Number($("qc-rework").value) : null,
          packed: !failed && $("qc-packed").checked,
          notes: $("qc-notes").value,
        }), failed ? "Quality check failed - order sent back for rework." : "Quality check passed.");
      },
      pack: () => act(() => api(`/orders/${id}/pack`, "POST"), "Order marked as packed."),
      ready: () => act(() => api(`/orders/${id}/ready`, "POST"),
        "Order is now Awaiting Delivery. The customer has been notified."),
    };
    notice("");
    run(actions[button.dataset.action], button);
  });

  if (!orderId) {
    $("order-summary").textContent = "";
    notice('No order selected. Open an order from the <a class="link" href="processing_board.html">Processing Board</a>.', "error");
  } else {
    run(load);
  }
})();
