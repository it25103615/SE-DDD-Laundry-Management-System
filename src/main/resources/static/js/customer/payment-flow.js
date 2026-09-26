(function () {
  const ORDER_DRAFT_KEY = "laundryLink.orderDraft";
  const page = document.body.dataset.paymentPage;

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

  function customerID() {
    const draft = readOrderDraft();
    return sessionStorage.getItem("laundrylinkCustomerID") || params().get("userID") || draft.userID || "";
  }

  function orderID() {
    const draft = readOrderDraft();
    return params().get("orderID") || sessionStorage.getItem("laundrylinkPaymentOrderID") || draft.lastOrderID || "";
  }

  function money(value) {
    const number = Number(value || 0);
    return `LKR ${number.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  }

  function displayOutstanding(id, status) {
    return Number(status.outstandingAmount || 0);
  }

  function methodLabel(method) {
    return method === "CASH" ? "Cash" : "Credit/Debit Card";
  }

  function api(path, options) {
    const headers = {
      "Content-Type": "application/json",
      ...(options && options.headers ? options.headers : {}),
    };
    const resolvedCustomerID = customerID();
    if (resolvedCustomerID) {
      headers["X-User-ID"] = resolvedCustomerID;
    }

    return fetch(path, {
      ...options,
      headers,
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

  function saveOrderContext(id, status) {
    const outstandingAmount = displayOutstanding(id, status);
    sessionStorage.setItem("laundrylinkPaymentOrderID", id);
    sessionStorage.setItem("laundrylinkPaymentAmount", outstandingAmount);
  }

  function displayInvoice(invoice) {
    setText("billing-order-label", `Order #${invoice.orderID}`);
    setText("billing-subtotal", money(invoice.subtotal));
    setText("billing-discount", `- ${money(invoice.discountAmount)}`);
    setText("billing-final", money(invoice.finalPayableAmount));
    const appliedCode = sessionStorage.getItem(`laundrylinkPromotionCode:${invoice.orderID}`);
    const hasDiscount = Number(invoice.discountAmount || 0) > 0;
    setText("billing-promotion-label", hasDiscount && appliedCode ? `Promotion discount (${appliedCode})` : "Promotion discount");
  }

  async function refreshBillingAndStatus(id) {
    const [invoice, status] = await Promise.all([getInvoice(id), getStatus(id)]);
    displayInvoice(invoice);
    saveOrderContext(id, status);
    return { invoice, status };
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
    await refreshBillingAndStatus(id);
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
      setText("billing-discount", "Unavailable");
      setText("billing-final", "Unavailable");
      showMessage(promotionMessage, "CROSS-MODULE CHANGE REQUIRED: this page needs a real orderID from Order Management navigation.", false);
      if (payLink) {
        payLink.setAttribute("aria-disabled", "true");
        payLink.removeAttribute("href");
      }
    } else {
      try {
        const { status } = await refreshBillingAndStatus(id);
        const outstandingAmount = displayOutstanding(id, status);
        setText("outstanding-amount", money(outstandingAmount));
        setText("payment-order-line", `Order #${id} · ${status.status.replaceAll("_", " ")}`);
        if (payLink) {
          payLink.href = `payment_method.html?orderID=${id}`;
          if (outstandingAmount <= 0) {
            payLink.textContent = "Paid";
            payLink.setAttribute("aria-disabled", "true");
          } else {
            payLink.textContent = "Pay now";
            payLink.removeAttribute("aria-disabled");
          }
        }
      } catch (error) {
        setText("outstanding-amount", "Unavailable");
        setText("payment-order-line", "Could not load billing or outstanding balance");
        setText("billing-subtotal", "Unavailable");
        setText("billing-discount", "Unavailable");
        setText("billing-final", "Unavailable");
      }
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
      if (!history.length) {
        historyBody.innerHTML = '<tr><td colspan="5">No payments recorded yet.</td></tr>';
        return;
      }
      historyBody.innerHTML = history
        .map(
          (payment) => `
            <tr>
              <td>Recorded</td>
              <td>#${payment.orderID}</td>
              <td>Payment #${payment.paymentID}</td>
              <td>${money(payment.amount)}</td>
              <td><a class="link" href="receipt.html?paymentID=${payment.paymentID}&orderID=${payment.orderID}">Receipt</a></td>
            </tr>`,
        )
        .join("");
    } catch (error) {
      if (historyBody) historyBody.innerHTML = '<tr><td colspan="5">Payment history could not be loaded.</td></tr>';
    }
  }

  async function loadMethodPage() {
    const id = orderID();
    const form = document.getElementById("payment-method-form");
    const errorBox = document.getElementById("method-error");
    setText("method-order-title", `Order #${id}`);
    if (!id) {
      showError(errorBox, "Open an order before selecting a payment method.");
      return;
    }

    try {
      const status = await getStatus(id);
      const outstandingAmount = displayOutstanding(id, status);
      saveOrderContext(id, status);
      setText("method-amount", money(outstandingAmount));
      if (outstandingAmount <= 0) {
        showError(errorBox, "This order does not have an outstanding balance.");
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
      sessionStorage.setItem("laundrylinkPaymentMethod", selected.value);
      window.location.href = `payment_checkout.html?orderID=${id}&method=${selected.value}`;
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

  function validExpiry(value) {
    const match = /^(\d{2})\/(\d{2})$/.exec(value.trim());
    if (!match) return false;
    const month = Number(match[1]);
    const year = Number(`20${match[2]}`);
    if (month < 1 || month > 12) return false;
    const expiry = new Date(year, month, 0, 23, 59, 59);
    return expiry >= new Date();
  }

  function validateCard() {
    clearFieldErrors();
    let valid = true;
    const name = document.getElementById("cardholder-name").value.trim();
    const number = document.getElementById("card-number").value.replace(/\D/g, "");
    const expiry = document.getElementById("expiry-date").value.trim();
    const cvv = document.getElementById("cvv").value.trim();

    if (!name) {
      fieldError("cardholder-name", "Cardholder name is required.");
      valid = false;
    }
    if (!/^\d{13,19}$/.test(number)) {
      fieldError("card-number", "Enter a valid card number.");
      valid = false;
    }
    if (!validExpiry(expiry)) {
      fieldError("expiry-date", "Use a valid future date in MM/YY format.");
      valid = false;
    }
    if (!/^\d{3,4}$/.test(cvv)) {
      fieldError("cvv", "CVV must be 3 or 4 digits.");
      valid = false;
    }

    return { valid, masked: number ? `Card **** ${number.slice(-4)}` : "Credit/Debit Card" };
  }

  async function loadCheckoutPage() {
    const id = orderID();
    const method = params().get("method") || sessionStorage.getItem("laundrylinkPaymentMethod") || "CARD";
    const form = document.getElementById("payment-checkout-form");
    const errorBox = document.getElementById("checkout-error");
    const button = document.getElementById("final-pay-button");
    const cardFields = document.getElementById("card-fields");
    const cashFields = document.getElementById("cash-fields");
    const backLink = document.getElementById("checkout-back-link");
    let amount = Number(sessionStorage.getItem("laundrylinkPaymentAmount") || 0);

    if (!id) {
      setText("summary-order", "Order not selected");
      setText("summary-amount", "Unavailable");
      showError(errorBox, "Open an order before checkout.");
      if (button) button.disabled = true;
      return;
    }

    sessionStorage.setItem("laundrylinkPaymentMethod", method);
    if (backLink) backLink.href = `payment_method.html?orderID=${id}`;
    setText("summary-method", methodLabel(method));
    setText("summary-order", `Order #${id}`);
    setText("checkout-subtitle", `Complete payment for order #${id}.`);
    if (method === "CASH") {
      cardFields.hidden = true;
      cashFields.hidden = false;
      setText("checkout-title", "Confirm cash payment");
    }

    try {
      const status = await getStatus(id);
      saveOrderContext(id, status);
      amount = displayOutstanding(id, status);
      setText("summary-amount", money(amount));
      setText("checkout-title", method === "CASH" ? `Confirm ${money(amount)}` : `Pay ${money(amount)}`);
      if (amount <= 0) showError(errorBox, "This order does not have an outstanding balance.");
    } catch (error) {
      setText("summary-amount", "Unavailable");
      showError(errorBox, "Could not load the outstanding payment amount.");
    }

    if (!form) return;
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      showError(errorBox, "");
      if (amount <= 0) {
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
        sessionStorage.setItem(
          "laundrylinkLastPayment",
          JSON.stringify({ ...result, displayMethod, paidAt: new Date().toLocaleString() }),
        );
        window.location.href = `receipt.html?paymentID=${result.paymentID}&orderID=${id}`;
      } catch (error) {
        button.disabled = false;
        setText("summary-status", "Failed");
        const message = error.status === 409
          ? "This order is already paid or cannot accept this payment."
          : "Payment failed. Please check the details and try again.";
        showError(errorBox, message);
      }
    });
  }

  function loadReceiptPage() {
    let payment = null;
    try {
      payment = JSON.parse(sessionStorage.getItem("laundrylinkLastPayment") || "null");
    } catch (error) {
      payment = null;
    }

    const queryPaymentID = params().get("paymentID");
    const queryOrderID = params().get("orderID") || orderID();
    const paymentID = payment && payment.paymentID ? payment.paymentID : queryPaymentID;
    const amount = payment && payment.amount ? payment.amount : sessionStorage.getItem("laundrylinkPaymentAmount");

    setText("receipt-title", payment ? "Payment successful" : "Payment recorded");
    setText("receipt-reference", paymentID ? `Receipt #LL-${queryOrderID}-${paymentID}` : "Receipt");
    setText("receipt-order", `Order #${queryOrderID}`);
    setText("receipt-amount", money(amount));
    setText("receipt-method", `Method: ${payment && payment.displayMethod ? payment.displayMethod : "Recorded payment"}`);
    setText("receipt-time", `Date/time: ${payment && payment.paidAt ? payment.paidAt : "Not available"}`);
  }

  if (page === "payments") loadPaymentsPage();
  if (page === "method") loadMethodPage();
  if (page === "checkout") loadCheckoutPage();
  if (page === "receipt") loadReceiptPage();
})();
