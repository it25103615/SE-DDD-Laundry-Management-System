(function () {
  const MANAGER_ID = sessionStorage.getItem("laundrylinkManagerID") || "11";
  const page = document.body.dataset.paymentAdminPage;

  function money(value) {
    const number = Number(value || 0);
    return `LKR ${number.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  }

  function label(value) {
    return String(value || "-").replaceAll("_", " ");
  }

  function params() {
    return new URLSearchParams(window.location.search);
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value;
  }

  function showMessage(id, message) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = message || "";
    element.hidden = !message;
  }

  function api(path, options) {
    return fetch(path, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        "X-User-ID": MANAGER_ID,
        ...(options && options.headers ? options.headers : {}),
      },
    }).then(async (response) => {
      if (!response.ok) {
        const error = new Error(`Request failed with status ${response.status}`);
        error.status = response.status;
        throw error;
      }
      if (response.status === 204) return null;
      return response.json();
    });
  }

  async function paymentRecords() {
    return api("/api/payments/management");
  }

  function statusMarkup(status) {
    const readable = label(status);
    if (status === "PAID" || status === "VERIFIED") {
      return `<span class="status status_success">${readable}</span>`;
    }
    if (status === "REJECTED") {
      return `<span class="status status_error">${readable}</span>`;
    }
    return `<span class="status status_warning">${readable}</span>`;
  }

  async function loadListPage() {
    const table = document.getElementById("manager-payment-table");
    try {
      const records = await paymentRecords();
      const collected = records.reduce((total, record) => total + Number(record.paidAmount || 0), 0);
      const outstanding = records.reduce((total, record) => total + Number(record.outstandingAmount || 0), 0);
      const failed = records.filter((record) => record.paymentStatus === "REJECTED" || /failed/i.test(record.orderStatus || "")).length;

      setText("collected-total", money(collected));
      setText("outstanding-total", money(outstanding));
      setText("failed-total", failed);

      if (!table) return;
      if (!records.length) {
        table.innerHTML = '<tr><td colspan="6">No payment records found.</td></tr>';
        return;
      }

      table.innerHTML = records
        .map(
          (record) => `
            <tr>
              <td>#${record.orderID}</td>
              <td>Customer #${record.customerID}</td>
              <td>Recorded</td>
              <td>${money(record.amount)}</td>
              <td>${statusMarkup(record.paymentStatus)}</td>
              <td><a class="link" href="payment_detail.html?paymentID=${record.paymentID}">View</a></td>
            </tr>`,
        )
        .join("");
    } catch (error) {
      setText("collected-total", "Unavailable");
      setText("outstanding-total", "Unavailable");
      setText("failed-total", "-");
      if (table) table.innerHTML = '<tr><td colspan="6">Payment records could not be loaded.</td></tr>';
    }
  }

  function fillDetail(record) {
    setText("detail-title", `Payment #${record.paymentID}`);
    setText("detail-subtitle", `Review payment for order #${record.orderID}.`);
    setText("detail-amount", money(record.amount));
    setText("detail-payment-id", record.paymentID);
    setText("detail-order-id", `#${record.orderID}`);
    setText("detail-customer-id", `Customer #${record.customerID}`);
    setText("detail-payment-status", label(record.paymentStatus));
    setText("detail-order-status", label(record.orderStatus));
    setText("detail-payable", money(record.payableAmount));
    setText("detail-paid", money(record.paidAmount));
    setText("detail-outstanding", money(record.outstandingAmount));

    const verified = record.paymentStatus === "VERIFIED";
    const rejected = record.paymentStatus === "REJECTED";
    const complete = verified || rejected;
    const approve = document.getElementById("approve-payment");
    const reject = document.getElementById("reject-payment");
    const finalStatus = document.getElementById("detail-final-status");

    if (approve) approve.hidden = complete;
    if (reject) reject.hidden = complete;

    if (finalStatus) {
      finalStatus.hidden = !complete;
      finalStatus.className = `alert ${verified ? "alert_success" : "alert_error"}`;
      finalStatus.textContent = verified
        ? "This payment has already been accepted. No further action is required."
        : "This payment has already been rejected. No further action is available.";
    }
  }

  async function loadDetailPage() {
    const paymentID = Number(params().get("paymentID"));
    const approve = document.getElementById("approve-payment");
    const reject = document.getElementById("reject-payment");
    let currentRecord = null;

    async function refresh() {
      showMessage("detail-error", "");
      const records = await paymentRecords();
      currentRecord = records.find((record) => Number(record.paymentID) === paymentID);
      if (!currentRecord) {
        showMessage("detail-error", "Payment record could not be found.");
        return;
      }
      fillDetail(currentRecord);
    }

    async function updateStatus(action) {
      if (!currentRecord) return;
      showMessage("detail-success", "");
      showMessage("detail-error", "");
      if (approve) approve.disabled = true;
      if (reject) reject.disabled = true;
      try {
        const result = await api(`/api/payments/management/${currentRecord.paymentID}/${action}`, { method: "POST" });
        showMessage("detail-success", result.message || "Payment status updated.");
        await refresh();
      } catch (error) {
        showMessage("detail-error", error.status === 409
          ? "This payment cannot be updated until the full amount has been paid."
          : "Payment status could not be updated.");
        if (approve) approve.disabled = false;
        if (reject) reject.disabled = false;
      }
    }

    if (!paymentID) {
      showMessage("detail-error", "Payment ID is missing.");
      return;
    }

    if (approve) approve.addEventListener("click", () => updateStatus("approve"));
    if (reject) reject.addEventListener("click", () => updateStatus("reject"));

    try {
      await refresh();
    } catch (error) {
      showMessage("detail-error", "Payment details could not be loaded.");
    }
  }

  if (page === "list") loadListPage();
  if (page === "detail") loadDetailPage();
})();
