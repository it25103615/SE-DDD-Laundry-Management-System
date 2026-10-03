(function () {
  const page = document.body.dataset.paymentAdminPage;
  let csrfPromise = null;

  function money(value) {
    const number = Number(value || 0);
    return `LKR ${number.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  }

  function label(value) {
    return String(value || "-").replaceAll("_", " ");
  }

  function dateTimeLabel(value) {
    if (!value) return "-";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return String(value).replace("T", " ");
    return parsed.toLocaleString();
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

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  async function csrf() {
    if (!csrfPromise) {
      csrfPromise = fetch("/api/auth/csrf", {
        headers: { "Accept": "application/json" },
        credentials: "same-origin",
      }).then((response) => response.json());
    }
    return csrfPromise;
  }

  async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    };
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
      const token = await csrf();
      headers[token.headerName] = token.token;
    }

    return fetch(path, {
      ...options,
      headers,
      credentials: "same-origin",
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

  function paymentFilterQuery() {
    const form = document.getElementById("payment-filters");
    const query = new URLSearchParams();
    if (!form) return "";

    const data = new FormData(form);
    ["search", "status", "orderID", "customerID"].forEach((field) => {
      const value = String(data.get(field) || "").trim();
      if (value) query.set(field, value);
    });

    const text = query.toString();
    return text ? `?${text}` : "";
  }

  async function paymentRecords(query = "") {
    return api(`/api/payments/management${query}`);
  }

  async function billingDetails(orderID) {
    return api(`/api/billing/orders/${encodeURIComponent(orderID)}/invoice`);
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
      const records = await paymentRecords(paymentFilterQuery());
      const collected = records.reduce((total, record) => total + Number(record.paidAmount || 0), 0);
      const outstanding = records.reduce((total, record) => total + Number(record.outstandingAmount || 0), 0);
      const failed = records.filter((record) => record.paymentStatus === "REJECTED" || /failed/i.test(record.orderStatus || "")).length;

      setText("collected-total", money(collected));
      setText("outstanding-total", money(outstanding));
      setText("failed-total", failed);

      if (!table) return;
      if (!records.length) {
        table.innerHTML = '<tr><td colspan="7">No payment records found.</td></tr>';
        return;
      }

      table.innerHTML = records
        .map(
          (record) => `
            <tr>
              <td>#${record.paymentID}</td>
              <td>#${record.orderID}</td>
              <td>Customer #${record.customerID}</td>
              <td>${money(record.amount)}</td>
              <td>${statusMarkup(record.paymentStatus)}</td>
              <td>${escapeHtml(label(record.orderStatus))}</td>
              <td><a class="link" href="payment_detail.html?paymentID=${encodeURIComponent(record.paymentID)}">View</a></td>
            </tr>`,
        )
        .join("");
    } catch (error) {
      setText("collected-total", "Unavailable");
      setText("outstanding-total", "Unavailable");
      setText("failed-total", "-");
      if (table) table.innerHTML = '<tr><td colspan="7">Payment records could not be loaded.</td></tr>';
    }
  }

  function fillDetail(record, billing) {
    setText("detail-title", `Payment #${record.paymentID}`);
    setText("detail-subtitle", `Review payment for order #${record.orderID}.`);
    setText("detail-amount", money(record.amount));
    setText("detail-payment-id", record.paymentID);
    setText("detail-order-id", `#${record.orderID}`);
    setText("detail-customer-id", `Customer #${record.customerID}`);
    setText("detail-payment-status", label(record.paymentStatus));
    setText("detail-payment-method", label(record.paymentMethod));
    setText("detail-payment-reference", record.transactionReference || "-");
    setText("detail-processed-at", dateTimeLabel(record.processedAt));
    setText("detail-order-status", label(record.orderStatus));
    setText("detail-subtotal", money(billing && billing.subtotal));
    setText("detail-discount", money(billing && billing.discountAmount));
    setText("detail-payable", money(billing && billing.finalPayableAmount != null ? billing.finalPayableAmount : record.payableAmount));
    setText("detail-paid", money(record.paidAmount));
    setText("detail-outstanding", money(record.outstandingAmount));

    const verified = record.paymentStatus === "VERIFIED";
    const rejected = record.paymentStatus === "REJECTED";
    const payable = record.paymentStatus === "PAID";
    const canVerify = payable && Number(record.outstandingAmount || 0) === 0;
    const approve = document.getElementById("approve-payment");
    const reject = document.getElementById("reject-payment");
    const finalStatus = document.getElementById("detail-final-status");

    if (approve) {
      approve.hidden = !canVerify;
      approve.disabled = !canVerify;
    }
    if (reject) {
      reject.hidden = !canVerify;
      reject.disabled = !canVerify;
    }

    if (finalStatus) {
      if (verified || rejected) {
        finalStatus.hidden = false;
        finalStatus.className = `alert ${verified ? "alert_success" : "alert_error"}`;
        finalStatus.textContent = verified
          ? "This payment has already been accepted. No further action is required."
          : "This payment has already been rejected. No further action is available.";
      } else if (!canVerify) {
        finalStatus.hidden = false;
        finalStatus.className = "alert";
        finalStatus.textContent = "This payment is not eligible for verification yet.";
      } else {
        finalStatus.hidden = true;
        finalStatus.textContent = "";
      }
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
      const billing = await billingDetails(currentRecord.orderID);
      fillDetail(currentRecord, billing);
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
        if (approve && currentRecord.paymentStatus === "PAID") approve.disabled = false;
        if (reject && currentRecord.paymentStatus === "PAID") reject.disabled = false;
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

  if (page === "list") {
    const filters = document.getElementById("payment-filters");
    const clearFilters = document.getElementById("clear-payment-filters");
    if (filters) {
      filters.addEventListener("submit", (event) => {
        event.preventDefault();
        loadListPage();
      });
    }
    if (clearFilters && filters) {
      clearFilters.addEventListener("click", () => {
        filters.reset();
        loadListPage();
      });
    }
    loadListPage();
  }
  if (page === "detail") loadDetailPage();
})();
