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

  function cleanValue(value) {
    return value == null || value === "" ? "N/A" : label(value);
  }

  function paymentIdLabel(value) {
    return value == null ? "-" : `#${value}`;
  }

  function methodLabel(value) {
    return value == null || value === "" ? "Not paid" : label(value);
  }

  function customerLabel(record) {
    const name = String(record.customerName || "").trim();
    const fallback = record.customerID == null ? "Customer" : `Customer #${record.customerID}`;
    return name ? `${name} (#${record.customerID})` : fallback;
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
        try {
          error.body = await response.json();
        } catch (ignore) {
          error.body = null;
        }
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
    ["search", "status", "method", "orderID", "customerID"].forEach((field) => {
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
    if (status === "REFUNDED") {
      return `<span class="status">${readable}</span>`;
    }
    if (status === "REJECTED") {
      return `<span class="status status_error">${readable}</span>`;
    }
    return `<span class="status status_warning">${readable}</span>`;
  }

  function refundStatusMarkup(status) {
    if (!status) return "";
    const className = status === "REFUNDED"
      ? "status_success"
      : status === "REJECTED"
        ? "status_error"
        : "status_warning";
    return `<br><span class="status ${className}">Refund ${label(status)}</span>`;
  }

  async function loadListPage() {
    const table = document.getElementById("manager-payment-table");
    try {
      const records = await paymentRecords(paymentFilterQuery());
      const verifiedTotal = records
        .filter((record) => record.paymentStatus === "VERIFIED")
        .reduce((total, record) => total + Number(record.amount || 0), 0);
      const collected = verifiedTotal;
      const outstandingByOrder = new Map();
      records.forEach((record) => {
        if (record.orderID != null && !outstandingByOrder.has(record.orderID)) {
          outstandingByOrder.set(record.orderID, Number(record.outstandingAmount || 0));
        }
      });
      const outstanding = Array.from(outstandingByOrder.values()).reduce((total, value) => total + value, 0);
      const failed = records.filter((record) => record.paymentStatus === "REJECTED" || /failed/i.test(record.orderStatus || "")).length;

      setText("collected-total", money(collected));
      setText("outstanding-total", money(outstanding));
      setText("failed-total", failed);

      if (!table) return;
      if (!records.length) {
        table.innerHTML = '<tr><td colspan="8">No payment or billing records found.</td></tr>';
        return;
      }

      table.innerHTML = records
        .map((record) => {
          const canOpenPayment = record.paymentID != null;
          const action = canOpenPayment
            ? `<a class="link" href="payment_detail.html?paymentID=${encodeURIComponent(record.paymentID)}">View</a>`
            : '<span class="muted">No payment yet</span>';
          return `
            <tr>
              <td>${paymentIdLabel(record.paymentID)}</td>
              <td>#${record.orderID}</td>
              <td>${escapeHtml(customerLabel(record))}</td>
              <td>${money(record.payableAmount)}</td>
              <td>${escapeHtml(methodLabel(record.paymentMethod))}</td>
              <td>${statusMarkup(record.paymentStatus)}${refundStatusMarkup(record.refundStatus)}</td>
              <td>${escapeHtml(dateTimeLabel(record.recordDate || record.processedAt))}</td>
              <td>${action}</td>
            </tr>`;
        })
        .join("");
    } catch (error) {
      console.error("Payment management records failed to load", error);
      setText("collected-total", "Unavailable");
      setText("outstanding-total", "Unavailable");
      setText("failed-total", "-");
      if (table) table.innerHTML = '<tr><td colspan="8">Payment and billing records could not be loaded.</td></tr>';
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
    setText("detail-payment-method", cleanValue(record.paymentMethod));
    setText("detail-payment-reference", cleanValue(record.transactionReference));
    setText("detail-processed-at", dateTimeLabel(record.processedAt));
    setText("detail-order-status", label(record.orderStatus));
    setText("detail-subtotal", money(billing && billing.subtotal));
    setText("detail-discount", money(billing && billing.discountAmount));
    setText("detail-payable", money(billing && billing.finalPayableAmount != null ? billing.finalPayableAmount : record.payableAmount));
    setText("detail-paid", money(record.paidAmount));
    setText("detail-outstanding", money(record.outstandingAmount));
    setText("detail-refund-amount", money(record.refundAmount));
    setText("detail-refund-date", dateTimeLabel(record.refundedAt));
    setText("detail-refund-reason", cleanValue(record.refundReason));
    setText("detail-refund-status", cleanValue(record.refundStatus));
    setText("detail-refund-requested", dateTimeLabel(record.refundRequestedAt));
    setText("detail-refund-requested-by", record.refundRequestedBy == null ? "Customer" : `Customer #${record.refundRequestedBy}`);

    const refunded = record.paymentStatus === "REFUNDED";
    const refundRequested = record.refundStatus === "REQUESTED";
    const hasRefundWorkflow = !!record.refundStatus;
    ["detail-refund-amount-row", "detail-refund-reason-row", "detail-refund-status-row", "detail-refund-requested-row", "detail-refund-requested-by-row"].forEach((id) => {
      const element = document.getElementById(id);
      if (element) element.hidden = !hasRefundWorkflow;
    });
    const refundDate = document.getElementById("detail-refund-date-row");
    if (refundDate) refundDate.hidden = !refunded;

    const verified = record.paymentStatus === "VERIFIED";
    const rejected = record.paymentStatus === "REJECTED";
    const payable = record.paymentStatus === "PAID";
    const canVerify = payable && Number(record.outstandingAmount || 0) === 0;
    const approve = document.getElementById("approve-payment");
    const reject = document.getElementById("reject-payment");
    const refundPanel = document.getElementById("refund-panel");
    const finalStatus = document.getElementById("detail-final-status");

    if (approve) {
      approve.hidden = !canVerify;
      approve.disabled = !canVerify;
    }
    if (reject) {
      reject.hidden = !canVerify;
      reject.disabled = !canVerify;
    }
    if (refundPanel) {
      refundPanel.hidden = !refundRequested;
    }
    setText("refund-panel-amount", money(record.amount));
    setText("refund-panel-reason", cleanValue(record.refundReason));

    if (finalStatus) {
      if (verified || rejected || refunded) {
        finalStatus.hidden = false;
        finalStatus.className = `alert ${rejected ? "alert_error" : "alert_success"}`;
        finalStatus.textContent = verified
          ? (refundRequested
            ? "This payment has a refund request waiting for review."
            : "This payment has already been accepted. No further action is required.")
          : rejected
            ? "This payment has already been rejected. No further action is available."
            : "This verified payment has been refunded. No further action is available.";
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
    const approveRefund = document.getElementById("approve-refund");
    const rejectRefund = document.getElementById("reject-refund");
    let currentRecord = null;

    async function refresh() {
      showMessage("detail-error", "");
      currentRecord = await api(`/api/payments/management/${encodeURIComponent(paymentID)}`);
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

    async function updateRefund(action) {
      if (!currentRecord) return;
      showMessage("detail-success", "");
      showMessage("detail-error", "");
      if (approveRefund) approveRefund.disabled = true;
      if (rejectRefund) rejectRefund.disabled = true;
      try {
        const result = await api(`/api/payments/management/${currentRecord.paymentID}/refund/${action}`, { method: "POST" });
        showMessage("detail-success", result.message || "Refund request updated.");
        await refresh();
      } catch (error) {
        const message = error.body && error.body.message
          ? error.body.message
          : "Refund request could not be updated.";
        showMessage("detail-error", message);
      } finally {
        if (approveRefund && currentRecord && currentRecord.refundStatus === "REQUESTED") approveRefund.disabled = false;
        if (rejectRefund && currentRecord && currentRecord.refundStatus === "REQUESTED") rejectRefund.disabled = false;
      }
    }

    if (!paymentID) {
      showMessage("detail-error", "Payment ID is missing.");
      return;
    }

    if (approve) approve.addEventListener("click", () => updateStatus("approve"));
    if (reject) reject.addEventListener("click", () => updateStatus("reject"));
    if (approveRefund) approveRefund.addEventListener("click", () => updateRefund("approve"));
    if (rejectRefund) rejectRefund.addEventListener("click", () => updateRefund("reject"));

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
