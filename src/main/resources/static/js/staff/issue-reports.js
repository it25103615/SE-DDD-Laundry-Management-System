/*
 * Issue Reports (html/staff/issue_reports.html) - TC-LP06.
 *
 * Lists every issue report (newest first) with the status of its customer-service case, and
 * submits new reports. Each report is saved by the server as a support case (its type is the
 * issue type) in the CSM queue. Links from other pages can pre-fill the form:
 *   issue_reports.html?orderId=6&orderLineId=12&issue=Missing%20item#new-report
 */
(() => {
  const { $, escape, dateTime, param, badge, api, notice, run } = Processing;

  async function loadIssues() {
    const issues = await api("/issues");
    $("issue-rows").innerHTML = issues.length
      ? issues.map((issue) => `<tr>
          <td>#${issue.caseID}</td>
          <td><a class="link" href="order_processing.html?orderId=${issue.orderID}">#${issue.orderID}</a></td>
          <td>${escape(issue.subject)}</td>
          <td><strong>${escape(issue.issueType)}</strong><br><span class="small muted">${escape(issue.description)}</span></td>
          <td>${escape(issue.reportedBy)}<br><span class="small muted">${escape(dateTime(issue.createdAt))}</span></td>
          <td>${badge(issue.caseStatus || "New")}</td>
          <td><a class="link" href="order_processing.html?orderId=${issue.orderID}">Open</a></td>
        </tr>`).join("")
      : '<tr><td colspan="7" class="muted">No issues have been reported.</td></tr>';
  }

  /** Fills the Item drop-down with the chosen order's lines (optional: "Whole order"). */
  async function loadOrderItems(orderId, selectedLine) {
    $("item").innerHTML = '<option value="">Whole order</option>';
    if (!/^\d+$/.test(orderId)) return;
    try {
      const order = await api(`/orders/${orderId}`);
      $("item").innerHTML += order.lines
        .map((line) => `<option value="${line.orderLineID}"${String(line.orderLineID) === String(selectedLine) ? " selected" : ""}>${escape(line.itemName)} (${line.quantity})</option>`)
        .join("");
    } catch (error) {
      notice(escape(error.message), "error");
    }
  }

  $("order").addEventListener("change", () => loadOrderItems($("order").value.trim().replace(/^#/, "")));

  $("new-report").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!$("new-report").reportValidity()) return;
    const orderID = Number($("order").value.trim().replace(/^#/, ""));
    if (!Number.isInteger(orderID) || orderID < 1) return notice("Enter a valid order number.", "error");

    run(async () => {
      const issue = await api("/issues", "POST", {
        orderID,
        orderLineID: $("item").value ? Number($("item").value) : null,
        issueType: $("issue").value,
        description: $("details").value,
      });
      $("new-report").reset();
      $("item").innerHTML = '<option value="">Whole order</option>';
      notice(`Issue saved for order #${issue.orderID} and sent to customer service as case #${issue.caseID}.`, "success");
      await loadIssues();
    }, event.submitter);
  });

  // Pre-fill from a link, e.g. the "report a missing item" link on Receive Items.
  const orderFromLink = param("orderId");
  if (orderFromLink) {
    $("order").value = orderFromLink;
    loadOrderItems(orderFromLink, param("orderLineId"));
  }
  if (param("issue")) $("issue").value = param("issue");

  run(loadIssues);
})();
