(function () {
  const ORDER_DRAFT_KEY = "laundryLink.orderDraft";
  const page = document.body.dataset.paymentPage;
  let csrfPromise = null;

  function params() {
    return new URLSearchParams(window.location.search);
  }

  function readOrderDraft() {
    try {
      return JSON.parse(sessionStorage.getItem(ORDER_DRAFT_KEY)) || {};
    } catch (error) {
      return {};
    }
  }

  function saveOrderDraft(draft) {
    sessionStorage.setItem(ORDER_DRAFT_KEY, JSON.stringify(draft || {}));
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

  function orderID() {
    const queryOrderID = params().get("orderID");
    const draft = readOrderDraft();
    if (queryOrderID) {
      if (String(draft.lastOrderID || "") !== queryOrderID) {
        saveOrderDraft({ ...draft, lastOrderID: Number(queryOrderID) || queryOrderID });
      }
      return queryOrderID;
    }
    return draft.lastOrderID ? String(draft.lastOrderID) : "";
  }

  function money(value) {
    const number = Number(value || 0);
    return `LKR ${number.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  }

  function displayOutstanding(status) {
    return Number(status.outstandingAmount || 0);
  }

  function paymentFlowUrl(pageName, id, extra = {}) {
    const search = new URLSearchParams();
    search.set("orderID", id);
    Object.entries(extra).forEach(([key, value]) => {
      if (value) search.set(key, value);
    });
    return `${pageName}?${search.toString()}`;
  }

  function methodLabel(method) {
    if (method === "CASH") return "Cash";
    if (method === "CARD") return "Credit/Debit Card";
    return "Not recorded";
  }

  function dateTimeLabel(value) {
    if (!value) return "Not recorded";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return String(value).replace("T", " ");
    return parsed.toLocaleString();
  }

  function statusLabel(status) {
    return String(status || "Unknown").replaceAll("_", " ");
  }

  function refundLabel(payment) {
    if (!payment.refundStatus) return "";
    const parts = [];
    parts.push(`Refund ${statusLabel(payment.refundStatus)}`);
    if (payment.refundStatus === "REQUESTED" && payment.refundRequestedAt) parts.push(dateTimeLabel(payment.refundRequestedAt));
    if (payment.refundStatus === "REFUNDED" && payment.refundAmount != null) parts.push(money(payment.refundAmount));
    if (payment.refundStatus === "REFUNDED" && payment.refundedAt) parts.push(dateTimeLabel(payment.refundedAt));
    if (payment.refundStatus === "REJECTED" && payment.refundProcessedAt) parts.push(dateTimeLabel(payment.refundProcessedAt));
    return ` · ${parts.join(" · ")}`;
  }

  function canRequestRefund(payment) {
    return payment && (payment.paymentStatus === "PAID" || payment.paymentStatus === "VERIFIED") && !payment.refundStatus;
  }

  function ensureRefundDialog() {
    let dialog = document.getElementById("refund-request-dialog");
    if (dialog) return dialog;
    dialog = document.createElement("div");
    dialog.id = "refund-request-dialog";
    dialog.className = "card";
    dialog.hidden = true;
    dialog.style.cssText = "position:fixed;inset:10% auto auto 50%;transform:translateX(-50%);z-index:50;max-width:520px;width:calc(100% - 32px);box-shadow:0 18px 48px rgba(0,0,0,.22)";
    dialog.innerHTML = `
      <span class="muted small">REQUEST REFUND</span>
      <h2 id="refund-dialog-title">Request Refund</h2>
      <p class="muted" id="refund-dialog-payment">Payment</p>
      <p class="muted" id="refund-dialog-amount">Refund amount</p>
      <div class="field">
        <label for="refund-dialog-reason">Reason</label>
        <textarea class="input" id="refund-dialog-reason" rows="4" maxlength="255" placeholder="Enter refund reason"></textarea>
      </div>
      <p class="alert alert_error" id="refund-dialog-error" hidden></p>
      <div class="actions">
        <button class="custom_button custom_button_bg" id="refund-dialog-submit" type="button">Submit Refund Request</button>
        <button class="custom_button custom_button_nobg" id="refund-dialog-cancel" type="button">Cancel</button>
      </div>`;
    document.body.appendChild(dialog);
    return dialog;
  }

  function openRefundDialog(payment, afterSubmit) {
    const dialog = ensureRefundDialog();
    const reason = document.getElementById("refund-dialog-reason");
    const error = document.getElementById("refund-dialog-error");
    const submit = document.getElementById("refund-dialog-submit");
    const cancel = document.getElementById("refund-dialog-cancel");
    setText("refund-dialog-title", `Payment #${payment.paymentID}`);
    setText("refund-dialog-payment", `Order #${payment.orderID}`);
    setText("refund-dialog-amount", `Refund amount: ${money(payment.amount)}`);
    if (reason) reason.value = "";
    showError(error, "");
    dialog.hidden = false;
    if (reason) reason.focus();

    const close = () => {
      dialog.hidden = true;
      if (submit) submit.onclick = null;
      if (cancel) cancel.onclick = null;
    };
    if (cancel) cancel.onclick = close;
    if (submit) submit.onclick = async () => {
      const text = reason ? reason.value.trim() : "";
      showError(error, "");
      if (!text) {
        showError(error, "Enter a refund reason.");
        return;
      }
      submit.disabled = true;
      try {
        await api(`/api/payments/${payment.paymentID}/refund-request`, {
          method: "POST",
          body: JSON.stringify({ reason: text }),
        });
        close();
        if (afterSubmit) await afterSubmit();
      } catch (requestError) {
        const message = requestError.body && requestError.body.message
          ? requestError.body.message
          : "Refund request could not be submitted.";
        showError(error, message);
      } finally {
        submit.disabled = false;
      }
    };
  }

  function cleanValue(value) {
    return value == null || value === "" ? "N/A" : String(value);
  }

  // Turns HTML special characters into harmless text. Use it on any saved value
  // (promotion code, promotion name, ...) before placing it in an innerHTML string,
  // so text such as <img onerror=...> is shown as text instead of running as markup.
  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function promotionDiscountLabel(promotion) {
    const value = Number(promotion.discountValue || 0);
    if (promotion.discountType === "PERCENTAGE") {
      return `${value.toLocaleString(undefined, { maximumFractionDigits: 2 })}% OFF`;
    }
    return `${money(value)} OFF`;
  }

  function dateLabel(value) {
    if (!value) return "N/A";
    const parsed = new Date(`${value}T00:00:00`);
    if (Number.isNaN(parsed.getTime())) return String(value);
    return parsed.toLocaleDateString();
  }

  async function api(path, options) {
    const method = (options && options.method ? options.method : "GET").toUpperCase();
    const headers = {
      "Accept": "application/json",
      "Content-Type": "application/json",
      ...(options && options.headers ? options.headers : {}),
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

  function showError(element, message) {
    if (!element) return;
    element.textContent = message;
    element.hidden = !message;
  }

  function showMessage(element, message, success) {
    if (!element) return;
    element.textContent = message || "";
    element.hidden = !message;
    element.className = `alert ${success ? "alert_success" : "alert_error"}`;
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value;
  }

  async function getStatus(id) {
    return api(`/api/payments/orders/${id}/status`);
  }

  async function getInvoice(id) {
    return api(`/api/billing/orders/${id}/invoice`);
  }

  async function getAvailablePromotions() {
    return api("/api/promotions/available");
  }

  function displayInvoice(invoice) {
    setText("billing-order-label", `Order #${invoice.orderID}`);
    setText("billing-subtotal", money(invoice.subtotal));
    setText("billing-bulk-discount", `- ${money(invoice.automaticBulkDiscount)}`);
    setText("billing-promotion-discount", `- ${money(invoice.promotionDiscount)}`);
    setText("billing-total-discount", `- ${money(invoice.totalDiscount ?? invoice.discountAmount)}`);
    setText("billing-final", money(invoice.finalPayableAmount));
    setText("billing-final-payable", money(invoice.finalPayableAmount));
    const appliedCode = sessionStorage.getItem(`laundrylinkPromotionCode:${invoice.orderID}`);
    const hasPromotionDiscount = Number(invoice.promotionDiscount || 0) > 0;
    setText("billing-promotion-label", hasPromotionDiscount && appliedCode ? `Promotion Discount (${appliedCode})` : "Promotion Discount");
    displayBulkDiscount(invoice);
  }

  function displayBulkDiscount(invoice) {
    const subtotal = Number(invoice.subtotal || 0);
    const saved = Number(invoice.automaticBulkDiscount || 0);
    const remaining = Math.max(0, 5000 - subtotal);
    setText("bulk-discount-title", saved > 0 ? "Bulk Order Discount" : "Bulk Order Discount");
    setText(
      "bulk-discount-message",
      saved > 0
        ? "10% off orders of LKR 5,000 or more. Automatically applied - no code required."
        : `Spend ${money(remaining)} more to receive 10% off.`
    );
    setText("bulk-discount-savings", saved > 0 ? `You saved: ${money(saved)}` : "Not applied");
  }

  function displayAvailablePromotions(promotions, id) {
    const list = document.getElementById("available-promotions-list");
    const count = document.getElementById("available-promotions-count");
    if (!list) return;
    if (!Array.isArray(promotions) || !promotions.length) {
      list.innerHTML = '<div class="card">No active promotions available.</div>';
      if (count) count.textContent = "0 available";
      return;
    }

    if (count) count.textContent = `${promotions.length} available`;
    list.innerHTML = promotions
      .map((promotion) => `
        <article class="card">
          <span class="muted small">${escapeHtml(cleanValue(promotion.promotionName))}</span>
          <h3>${escapeHtml(cleanValue(promotion.promotionCode))}</h3>
          <p><strong>${promotionDiscountLabel(promotion)}</strong></p>
          <p class="muted">Minimum order: ${money(promotion.minimumOrderAmount)}</p>
          <p class="muted">Valid until: ${dateLabel(promotion.validTo)}</p>
          <button class="custom_button custom_button_border" type="button" data-promotion-code="${escapeHtml(cleanValue(promotion.promotionCode))}">Apply</button>
        </article>`)
      .join("");

    list.querySelectorAll("[data-promotion-code]").forEach((button) => {
      button.addEventListener("click", () => {
        const input = document.getElementById("promotion-code");
        const form = document.getElementById("promotion-form");
        if (input) input.value = button.dataset.promotionCode || "";
        if (form) form.requestSubmit();
      });
    });
  }

  async function refreshBillingAndStatus(id, payLink) {
    const [invoice, status] = await Promise.all([getInvoice(id), getStatus(id)]);
    displayInvoice(invoice);
    if (payLink) {
      displayPaymentStatus(id, status, payLink);
    }
    return { invoice, status };
  }

  // A cancelled order can no longer be paid. The server refuses the payment as well; this just
  // keeps the pages from offering it. status.orderStatus is the order's current status label.
  const CANCELLED_ORDER_MESSAGE = "This order has been cancelled and can no longer be paid.";

  function isCancelledOrder(status) {
    return String((status && status.orderStatus) || "").toLowerCase() === "cancelled";
  }

  function displayPaymentStatus(id, status, payLink) {
    const outstandingAmount = displayOutstanding(status);
    setText("outstanding-amount", money(outstandingAmount));
    const statusText = status.status === "PENDING"
      ? "Payment submitted - awaiting verification"
      : status.status.replaceAll("_", " ");
    setText("payment-order-line", `Order #${id} · ${statusText}`);
    if (payLink) {
      payLink.href = paymentFlowUrl("payment_method.html", id);
      if (isCancelledOrder(status)) {
        payLink.textContent = "Order cancelled";
        payLink.setAttribute("aria-disabled", "true");
        payLink.removeAttribute("href");
      } else if (status.status === "PENDING") {
        payLink.textContent = "Awaiting verification";
        payLink.setAttribute("aria-disabled", "true");
        payLink.removeAttribute("href");
      } else if (outstandingAmount <= 0) {
        payLink.textContent = "Paid";
        payLink.setAttribute("aria-disabled", "true");
        payLink.removeAttribute("href");
      } else {
        payLink.textContent = "Pay now";
        payLink.removeAttribute("aria-disabled");
      }
    }
  }

  function promotionMessageForError(error) {
    if (error.status === 404) return "Promotion code does not exist.";
    if (error.status === 400) return "Promotion could not be applied to this order.";
    return "Promotion could not be checked. Please try again.";
  }

  async function applyPromotion(id, code) {
    const validation = await api(`/api/promotions/${encodeURIComponent(code)}/orders/${id}/validate`);
    if (!validation.valid) {
      return validation.message || "Promotion is not valid for this order.";
    }

    await api(`/api/promotions/${encodeURIComponent(code)}/orders/${id}/apply`, { method: "POST" });
    sessionStorage.setItem(`laundrylinkPromotionCode:${id}`, code.toUpperCase());
    await refreshBillingAndStatus(id, document.getElementById("pay-now-link"));
    return "";
  }

  async function loadPaymentsPage() {
    const id = orderID();
    const historyBody = document.getElementById("payment-history-body");
    const payLink = document.getElementById("pay-now-link");
    const promotionForm = document.getElementById("promotion-form");
    const promotionInput = document.getElementById("promotion-code");
    const promotionMessage = document.getElementById("promotion-message");
    const applyButton = document.getElementById("apply-promotion-button");

    if (!id) {
      setText("outstanding-amount", "Unavailable");
      setText("payment-order-line", "Open an order before reviewing payment.");
        setText("billing-order-label", "Invoice");
        setText("billing-subtotal", "Unavailable");
        setText("billing-bulk-discount", "Unavailable");
        setText("billing-promotion-discount", "Unavailable");
        setText("billing-total-discount", "Unavailable");
        setText("billing-final", "Unavailable");
        setText("billing-final-payable", "Unavailable");
        showMessage(promotionMessage, "CROSS-MODULE CHANGE REQUIRED: this page needs a real orderID from Order Management navigation.", false);
      if (payLink) {
        payLink.setAttribute("aria-disabled", "true");
        payLink.removeAttribute("href");
      }
    } else {
      try {
        const invoice = await getInvoice(id);
        displayInvoice(invoice);
      } catch (error) {
        setText("billing-order-label", "Invoice");
        setText("billing-subtotal", "Unavailable");
        setText("billing-bulk-discount", "Unavailable");
        setText("billing-promotion-discount", "Unavailable");
        setText("billing-total-discount", "Unavailable");
        setText("billing-final", "Unavailable");
        setText("billing-final-payable", "Unavailable");
      }

      try {
        const status = await getStatus(id);
        displayPaymentStatus(id, status, payLink);
      } catch (error) {
        setText("outstanding-amount", "Unavailable");
        setText("payment-order-line", "Could not load outstanding balance");
        if (payLink) {
          payLink.setAttribute("aria-disabled", "true");
          payLink.removeAttribute("href");
        }
      }
    }

    try {
      displayAvailablePromotions(await getAvailablePromotions(), id);
    } catch (error) {
      const list = document.getElementById("available-promotions-list");
      const count = document.getElementById("available-promotions-count");
      if (list) list.innerHTML = '<div class="card">Available promotions could not be loaded.</div>';
      if (count) count.textContent = "Unavailable";
    }

    if (promotionForm) {
      promotionForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const code = promotionInput ? promotionInput.value.trim() : "";
        showMessage(promotionMessage, "", false);
        if (!id) {
          showMessage(promotionMessage, "Open an order before applying a promotion.", false);
          return;
        }
        if (!code) {
          showMessage(promotionMessage, "Enter a promotion code.", false);
          return;
        }

        if (applyButton) applyButton.disabled = true;
        try {
          const validationMessage = await applyPromotion(id, code);
          if (validationMessage) {
            showMessage(promotionMessage, validationMessage, false);
          } else {
            showMessage(promotionMessage, "Promotion applied successfully.", true);
          }
        } catch (error) {
          showMessage(promotionMessage, promotionMessageForError(error), false);
        } finally {
          if (applyButton) applyButton.disabled = false;
        }
      });
    }

    try {
      const history = await api("/api/payments/history");
      if (!historyBody) return;
      if (!Array.isArray(history)) {
        historyBody.innerHTML = '<tr><td colspan="8">Payment history could not be loaded.</td></tr>';
        return;
      }
      if (!history.length) {
        historyBody.innerHTML = '<tr><td colspan="8">No payment history available.</td></tr>';
        return;
      }
      historyBody.innerHTML = history
        .filter((payment) => payment && payment.paymentID && payment.orderID)
        .map((payment) => {
          const receiptUrl = `receipt.html?paymentID=${encodeURIComponent(payment.paymentID)}&orderID=${encodeURIComponent(payment.orderID)}`;
          const action = canRequestRefund(payment)
            ? `<button class="custom_button custom_button_border refund-request-button" type="button" data-payment-id="${payment.paymentID}">Request Refund</button>`
            : `<a class="link" href="${receiptUrl}">View Receipt</a>`;
          return `
            <tr>
              <td>Payment #${payment.paymentID}</td>
              <td>#${payment.orderID}</td>
              <td>${money(payment.amount)}</td>
              <td>${methodLabel(payment.paymentMethod)}</td>
              <td>${escapeHtml(cleanValue(payment.transactionReference))}</td>
              <td>${statusLabel(payment.paymentStatus)}${refundLabel(payment)}${payment.orderStatus ? ` · ${payment.orderStatus}` : ""}</td>
              <td>${dateTimeLabel(payment.processedAt)}</td>
              <td>${action}</td>
            </tr>`;
        })
        .join("");
      historyBody.querySelectorAll(".refund-request-button").forEach((button) => {
        button.addEventListener("click", () => {
          const payment = history.find((item) => String(item.paymentID) === String(button.dataset.paymentId));
          if (payment) openRefundDialog(payment, () => window.location.reload());
        });
      });
      if (!historyBody.innerHTML) {
        historyBody.innerHTML = '<tr><td colspan="8">Payment history could not be loaded.</td></tr>';
      }
    } catch (error) {
      if (historyBody) historyBody.innerHTML = '<tr><td colspan="8">Payment history could not be loaded.</td></tr>';
    }
  }

  async function loadMethodPage() {
    const id = orderID();
    const form = document.getElementById("payment-method-form");
    const errorBox = document.getElementById("method-error");
    const backLink = document.getElementById("method-back-link");
    const continueButton = form ? form.querySelector('button[type="submit"]') : null;
    setText("method-order-title", id ? `Order #${id}` : "Order not selected");
    if (!id) {
      showError(errorBox, "Open an order before selecting a payment method.");
      setText("method-amount", "Unavailable");
      if (continueButton) continueButton.disabled = true;
      return;
    }

    if (backLink) backLink.href = paymentFlowUrl("payments.html", id);
    if (continueButton) continueButton.disabled = true;
    try {
      const { status } = await refreshBillingAndStatus(id);
      const outstandingAmount = displayOutstanding(status);
      setText("method-amount", money(outstandingAmount));
      if (isCancelledOrder(status)) {
        showError(errorBox, CANCELLED_ORDER_MESSAGE);
      } else if (status.status === "PENDING") {
        showError(errorBox, "Payment submitted - awaiting verification.");
      } else if (outstandingAmount <= 0) {
        showError(errorBox, "This order does not have an outstanding balance.");
      } else if (continueButton) {
        continueButton.disabled = false;
      }
    } catch (error) {
      showError(errorBox, "Could not load payment amount for this order.");
    }

    if (!form) return;
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const selected = form.querySelector('input[name="paymentMethod"]:checked');
      if (!selected) {
        showError(errorBox, "Please select a payment method.");
        return;
      }
      window.location.href = paymentFlowUrl("payment_checkout.html", id, { method: selected.value });
    });
  }

  function clearFieldErrors() {
    document.querySelectorAll("[data-error-for]").forEach((element) => {
      element.textContent = "";
    });
  }

  function fieldError(id, message) {
    const element = document.querySelector(`[data-error-for="${id}"]`);
    if (element) element.textContent = message;
  }

  function digitsOnly(value, maxLength) {
    return String(value || "").replace(/\D/g, "").slice(0, maxLength);
  }

  function formatCardNumber(value) {
    return digitsOnly(value, 16).replace(/(\d{4})(?=\d)/g, "$1 ");
  }

  function formatExpiry(value) {
    const digits = digitsOnly(value, 4);
    if (digits.length <= 2) return digits;
    return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  }

  function attachCardInputFormatting() {
    const cardNumber = document.getElementById("card-number");
    const expiry = document.getElementById("expiry-date");
    const cvv = document.getElementById("cvv");

    if (cardNumber) {
      cardNumber.addEventListener("input", () => {
        cardNumber.value = formatCardNumber(cardNumber.value);
      });
    }

    if (expiry) {
      expiry.addEventListener("input", () => {
        expiry.value = formatExpiry(expiry.value);
      });
    }

    if (cvv) {
      cvv.addEventListener("input", () => {
        cvv.value = digitsOnly(cvv.value, 3);
      });
    }
  }

  function validExpiry(value) {
    const match = /^(\d{2})\/(\d{2})$/.exec(value.trim());
    if (!match) return "format";
    const month = Number(match[1]);
    const year = Number(`20${match[2]}`);
    if (month < 1 || month > 12) return "month";
    const expiry = new Date(year, month, 0, 23, 59, 59);
    return expiry >= new Date() ? "" : "expired";
  }

  function validateCard() {
    clearFieldErrors();
    let valid = true;
    const nameInput = document.getElementById("cardholder-name");
    const numberInput = document.getElementById("card-number");
    const expiryInput = document.getElementById("expiry-date");
    const cvvInput = document.getElementById("cvv");
    const name = nameInput.value.trim();
    const number = digitsOnly(numberInput.value, 16);
    const expiry = formatExpiry(expiryInput.value);
    const cvv = digitsOnly(cvvInput.value, 3);

    nameInput.value = name;
    numberInput.value = formatCardNumber(number);
    expiryInput.value = expiry;
    cvvInput.value = cvv;

    if (!name) {
      fieldError("cardholder-name", "Cardholder name is required.");
      valid = false;
    }
    if (!/^\d{16}$/.test(number)) {
      fieldError("card-number", "Enter a valid 16-digit card number.");
      valid = false;
    }
    const expiryError = validExpiry(expiry);
    if (expiryError) {
      const message = expiryError === "format"
        ? "Enter expiry date as MM/YY."
        : expiryError === "month"
          ? "Enter a valid expiry month."
          : "Card has expired.";
      fieldError("expiry-date", message);
      valid = false;
    }
    if (!/^\d{3}$/.test(cvv)) {
      fieldError("cvv", "Enter a valid 3-digit CVV.");
      valid = false;
    }

    return { valid, masked: number ? `Card **** ${number.slice(-4)}` : "Credit/Debit Card" };
  }

  async function loadCheckoutPage() {
    const id = orderID();
    const method = params().get("method");
    const form = document.getElementById("payment-checkout-form");
    const errorBox = document.getElementById("checkout-error");
    const button = document.getElementById("final-pay-button");
    const cardFields = document.getElementById("card-fields");
    const cashFields = document.getElementById("cash-fields");
    const backLink = document.getElementById("checkout-back-link");
    const cancelLink = document.getElementById("checkout-cancel-link");
    let amount = null;
    attachCardInputFormatting();

    if (!id) {
      setText("summary-order", "Order not selected");
      setText("summary-amount", "Unavailable");
      showError(errorBox, "Open an order before checkout.");
      if (button) button.disabled = true;
      return;
    }

    if (method !== "CARD" && method !== "CASH") {
      setText("summary-order", `Order #${id}`);
      setText("summary-amount", "Unavailable");
      showError(errorBox, "Select a payment method before checkout.");
      if (button) button.disabled = true;
      if (backLink) backLink.href = paymentFlowUrl("payment_method.html", id);
      if (cancelLink) cancelLink.href = paymentFlowUrl("payments.html", id);
      return;
    }

    if (button) button.disabled = true;
    if (backLink) backLink.href = paymentFlowUrl("payment_method.html", id);
    if (cancelLink) cancelLink.href = paymentFlowUrl("payments.html", id);
    setText("summary-method", methodLabel(method));
    setText("summary-order", `Order #${id}`);
    setText("checkout-subtitle", `Complete payment for order #${id}.`);
    if (method === "CASH") {
      cardFields.hidden = true;
      cashFields.hidden = false;
      setText("checkout-title", "Confirm cash payment");
    }

    try {
      const { status } = await refreshBillingAndStatus(id);
      amount = displayOutstanding(status);
      setText("summary-amount", money(amount));
      setText("checkout-title", method === "CASH" ? `Confirm ${money(amount)}` : `Pay ${money(amount)}`);
      if (isCancelledOrder(status)) {
        showError(errorBox, CANCELLED_ORDER_MESSAGE);
      } else if (status.status === "PENDING") {
        showError(errorBox, "Payment submitted - awaiting verification.");
      } else if (amount <= 0) {
        showError(errorBox, "This order does not have an outstanding balance.");
      } else if (button) {
        button.disabled = false;
      }
    } catch (error) {
      setText("summary-amount", "Unavailable");
      showError(errorBox, "Could not load the outstanding payment amount.");
    }

    if (!form) return;
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      showError(errorBox, "");
      if (amount === null || amount <= 0) {
        showError(errorBox, "Payment amount is not valid.");
        return;
      }

      let displayMethod = methodLabel(method);
      if (method === "CARD") {
        const card = validateCard();
        if (!card.valid) return;
        displayMethod = card.masked;
      }

      button.disabled = true;
      setText("summary-status", "Submitting");
      try {
        const result = await api(`/api/payments/orders/${id}`, {
          method: "POST",
          body: JSON.stringify({ paymentMethod: method, amount }),
        });
        window.location.href = `receipt.html?paymentID=${result.paymentID}&orderID=${id}`;
      } catch (error) {
        button.disabled = false;
        setText("summary-status", "Failed");
        const message = error.status === 409 && error.body && error.body.message
          ? error.body.message
          : error.status === 409
            ? "This order is already paid or has a payment awaiting verification."
          : "Payment failed. Please check the details and try again.";
        showError(errorBox, message);
      }
    });
  }

  async function loadReceiptPage() {
    const queryPaymentID = params().get("paymentID");
    const queryOrderID = params().get("orderID");
    const paymentsLink = document.getElementById("receipt-payments-link");
    const pdfLink = document.getElementById("receipt-pdf-link");
    const refundRequestButton = document.getElementById("receipt-refund-request");
    if (paymentsLink && queryOrderID) paymentsLink.href = paymentFlowUrl("payments.html", queryOrderID);
    if (pdfLink) pdfLink.hidden = true;

    if (!queryPaymentID || !queryOrderID) {
      setText("receipt-title", "Receipt unavailable");
      setText("receipt-reference", "Missing payment or order reference");
      setText("receipt-order", queryOrderID ? `Order #${queryOrderID}` : "Order not selected");
      setText("receipt-amount", "Unavailable");
      setText("receipt-subtotal", "Unavailable");
      setText("receipt-bulk-discount", "Unavailable");
      setText("receipt-promotion-discount", "Unavailable");
      setText("receipt-total-discount", "Unavailable");
      setText("receipt-final-amount", "Unavailable");
      setText("receipt-status", "Status: Unavailable");
      setText("receipt-status-message", "");
      setText("receipt-refund-amount", "");
      setText("receipt-refund-time", "");
      if (refundRequestButton) refundRequestButton.hidden = true;
      setText("receipt-method", "Method: Not recorded");
      setText("receipt-time", "Date/time: Not recorded");
      return;
    }

    try {
      const receipt = await api(`/api/payments/${queryPaymentID}/receipt?orderID=${encodeURIComponent(queryOrderID)}`);
      setText("receipt-title", "Payment receipt");
      setText("receipt-reference", receipt.transactionReference || `Receipt #LL-${receipt.orderID}-${receipt.paymentID}`);
      setText("receipt-order", `Order #${receipt.orderID}`);
      setText("receipt-amount", money(receipt.amountPaid));
      setText("receipt-subtotal", money(receipt.subtotal));
      setText("receipt-bulk-discount", `- ${money(receipt.automaticBulkDiscount)}`);
      setText("receipt-promotion-discount", `- ${money(receipt.promotionDiscount)}`);
      setText("receipt-total-discount", `- ${money(receipt.totalDiscount ?? receipt.discountAmount)}`);
      setText("receipt-final-amount", money(receipt.finalPayableAmount));
      setText("receipt-status", `Status: ${String(receipt.paymentStatus || "Unknown").replaceAll("_", " ")}`);
      setText("receipt-status-message", receipt.paymentStatus === "PENDING"
        ? "Your payment has been submitted and is waiting for verification."
        : receipt.paymentStatus === "PAID" || receipt.paymentStatus === "VERIFIED"
          ? "Your payment has been verified successfully."
          : receipt.paymentStatus === "REJECTED"
            ? "Your payment was rejected."
            : "");
      const refundAmount = document.getElementById("receipt-refund-amount");
      const refundTime = document.getElementById("receipt-refund-time");
      if (receipt.refundStatus) {
        if (refundAmount) {
          refundAmount.hidden = false;
          refundAmount.textContent = receipt.refundStatus === "REQUESTED"
            ? "Refund status: REQUESTED - waiting for review."
            : receipt.refundStatus === "REJECTED"
              ? "Refund status: REJECTED."
              : `Refund amount: ${money(receipt.refundAmount)}`;
        }
        if (refundTime) {
          refundTime.hidden = false;
          refundTime.textContent = receipt.refundStatus === "REQUESTED"
            ? `Requested at: ${dateTimeLabel(receipt.refundRequestedAt)}`
            : receipt.refundStatus === "REJECTED"
              ? `Reviewed at: ${dateTimeLabel(receipt.refundProcessedAt)}`
              : `Refunded at: ${dateTimeLabel(receipt.refundedAt)}`;
        }
      } else {
        if (refundAmount) refundAmount.hidden = true;
        if (refundTime) refundTime.hidden = true;
      }
      if (refundRequestButton) {
        const payment = {
          paymentID: receipt.paymentID,
          orderID: receipt.orderID,
          amount: receipt.amountPaid,
          paymentStatus: receipt.paymentStatus,
          refundStatus: receipt.refundStatus,
        };
        refundRequestButton.hidden = !canRequestRefund(payment);
        refundRequestButton.onclick = () => openRefundDialog(payment, () => window.location.reload());
      }
      setText("receipt-method", `Method: ${methodLabel(receipt.paymentMethod)}`);
      setText("receipt-time", `Date/time: ${dateTimeLabel(receipt.processedAt)}`);
      if (paymentsLink) paymentsLink.href = paymentFlowUrl("payments.html", receipt.orderID);
      if (pdfLink) {
        pdfLink.hidden = false;
        pdfLink.href = `/api/payments/${encodeURIComponent(receipt.paymentID)}/receipt.pdf?orderID=${encodeURIComponent(receipt.orderID)}`;
      }
    } catch (error) {
      setText("receipt-title", "Receipt unavailable");
      setText("receipt-reference", error.status === 403 ? "You do not have access to this receipt" : "Receipt could not be loaded");
      setText("receipt-order", `Order #${queryOrderID}`);
      setText("receipt-amount", "Unavailable");
      setText("receipt-subtotal", "Unavailable");
      setText("receipt-bulk-discount", "Unavailable");
      setText("receipt-promotion-discount", "Unavailable");
      setText("receipt-total-discount", "Unavailable");
      setText("receipt-final-amount", "Unavailable");
      setText("receipt-status", "Status: Unavailable");
      setText("receipt-status-message", "");
      setText("receipt-refund-amount", "");
      setText("receipt-refund-time", "");
      if (refundRequestButton) refundRequestButton.hidden = true;
      if (pdfLink) pdfLink.hidden = true;
      setText("receipt-method", "Method: Not recorded");
      setText("receipt-time", "Date/time: Not recorded");
    }
  }

  if (page === "payments") loadPaymentsPage();
  if (page === "method") loadMethodPage();
  if (page === "checkout") loadCheckoutPage();
  if (page === "receipt") loadReceiptPage();
})();
