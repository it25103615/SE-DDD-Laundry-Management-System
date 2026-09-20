(function () {
  const CUSTOMER_ID = sessionStorage.getItem("laundrylinkCustomerID") || "1";
  const DEFAULT_ORDER_ID = "5";
  const TEMP_DEMO_AMOUNTS = { 5: 750 };
  const page = document.body.dataset.paymentPage;

  function params() {
    return new URLSearchParams(window.location.search);
  }

  function orderID() {
    return params().get("orderID") || DEFAULT_ORDER_ID;
  }

  function money(value) {
    const number = Number(value || 0);
    return `LKR ${number.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  }

  function displayOutstanding(id, status) {
    const outstanding = Number(status.outstandingAmount || 0);
    const demoAmount = TEMP_DEMO_AMOUNTS[Number(id)];
    if (outstanding <= 0 && status.status === "REJECTED" && demoAmount) {
      return demoAmount;
    }
    return outstanding;
  }

  function methodLabel(method) {
    return method === "CASH" ? "Cash" : "Credit/Debit Card";
  }

  function api(path, options) {
    return fetch(path, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        "X-User-ID": CUSTOMER_ID,
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

  function showError(element, message) {
    if (!element) return;
    element.textContent = message;
    element.hidden = !message;
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value;
  }

  async function getStatus(id) {
    return api(`/api/payments/orders/${id}/status`);
  }

  function saveOrderContext(id, status) {
    const outstandingAmount = displayOutstanding(id, status);
    sessionStorage.setItem("laundrylinkPaymentOrderID", id);
    sessionStorage.setItem("laundrylinkPaymentAmount", outstandingAmount);
  }

  async function loadPaymentsPage() {
    const id = orderID();
    const historyBody = document.getElementById("payment-history-body");
    const payLink = document.getElementById("pay-now-link");

    try {
      const status = await getStatus(id);
      const outstandingAmount = displayOutstanding(id, status);
      saveOrderContext(id, status);
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
      setText("payment-order-line", "Could not load outstanding balance");
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
