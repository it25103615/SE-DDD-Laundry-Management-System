(function () {
  "use strict";

  const API = "/api";
  const DRAFT_KEY = "laundryLink.orderDraft";
  // Customer edits allow Payment Failed, or Unconfirmed with no PENDING/PAID/VERIFIED payment. The server
  // enforces this rule and supplies customerCanModify for links and direct page access.
  const ELIGIBLE_STATUSES = new Set(["Unconfirmed", "Payment Failed"]);
  const CANCELLABLE_STATUSES = new Set([
    "Unconfirmed", "Payment Verified", "Awaiting Pickup", "En Route To Pickup",
    "Picked Up", "En Route To Shop", "In Shop", "Verifying Items"
  ]);
  const LOCKED_MESSAGE = "Only Payment Failed orders or Unconfirmed orders without a pending, paid, or verified payment can be modified.";

  const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
  })[character]);
  const money = (value) => `LKR ${Number(value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  const query = () => new URLSearchParams(window.location.search);

  function getDraft() {
    try {
      return JSON.parse(sessionStorage.getItem(DRAFT_KEY)) || { services: [], lines: [] };
    } catch (_) {
      return { services: [], lines: [] };
    }
  }

  function saveDraft(draft) {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  }

  function resetCompletedDraft() {
    const draft = getDraft();
    if (!draft.completed) return;
    // Keep existing-order navigation context, but start a fresh create-order draft.
    saveDraft({ userID: draft.userID, lastOrderID: draft.lastOrderID, services: [], lines: [] });
  }

  function toast(title, detail) {
    if (window.connectedToast) window.connectedToast(title, detail);
    else window.alert(`${title}${detail ? `\n${detail}` : ""}`);
  }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    if (!["GET", "HEAD", "OPTIONS"].includes((options.method || "GET").toUpperCase())) {
      const csrf = await api("/auth/csrf");
      headers.set(csrf.headerName, csrf.token);
    }
    const response = await fetch(`${API}${path}`, { ...options, headers, credentials: "same-origin" });
    if (response.redirected) {
      throw new Error("The order request was redirected. Refresh the page and check your sign-in before trying again.");
    }
    const isJson = /\bapplication\/(?:[\w.-]+\+)?json\b/i.test(response.headers.get("Content-Type") || "");
    if (response.status !== 204 && !isJson) {
      throw new Error(`The order API returned an unexpected response (HTTP ${response.status}). Please refresh and try again.`);
    }
    if (!response.ok) {
      let message = "Please review the entered order information and try again.";
      try {
        const body = await response.json();
        message = body.detail || body.message || message;
      } catch (_) { }
      throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
  }

  // Turns the schedule step's date ("2026-10-02") and time window ("8:00 AM – 11:00 AM") into
  // the start of that window as a local date-time ("2026-10-02T08:00:00"), which the API stores
  // as the delivery row's pickup_scheduled. Returns null when no pickup slot has been chosen.
  function pickupDateTime(schedule) {
    const match = /^(\d{1,2}):(\d{2})\s*(AM|PM)/i.exec(schedule?.time || "");
    if (!schedule?.date || !match) return null;
    let hours = Number(match[1]) % 12;
    if (match[3].toUpperCase() === "PM") hours += 12;
    return `${schedule.date}T${String(hours).padStart(2, "0")}:${match[2]}:00`;
  }

  // The schedule step saves the chosen saved address as its addressID (a number, kept as text
  // in the draft). Returns that number so the API can store it on the delivery row, or null
  // when no saved address was picked; the server requires a valid customer-owned default.
  function pickupAddressID(schedule) {
    const id = Number(schedule?.address);
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  // The signed-in customer's userID, asked from the server once per page (GET
  // /api/account/profile knows who is logged in from the session). The customer never types
  // it. It is also written into the order draft, replacing any ID left there by a different
  // customer who used this browser tab earlier.
  let signedInUserID = null;
  async function currentUserID() {
    if (!signedInUserID) {
      const profile = await api("/account/profile");
      signedInUserID = Number(profile.id) || null;
      if (!signedInUserID) throw new Error("Your account could not be identified. Please sign in again.");
      const draft = getDraft();
      if (draft.userID !== signedInUserID) saveDraft({ ...draft, userID: signedInUserID });
    }
    return signedInUserID;
  }

  async function catalog() {
    const [items, services, pricing] = await Promise.all([
      api("/items"), api("/services"), api("/service-pricing")
    ]);
    return { items, services, pricing };
  }

  // Which order the page is about. Notification links arrive as ?orderId=N (lowercase d, no
  // userID), while the order pages' own links use ?orderID=N&userID=M, so both spellings are
  // accepted. userID is left as 0 when the URL has none: fetchOrderOrExplain then asks the server
  // who is signed in instead of trusting an ID left in the browser draft by someone else.
  function orderContext() {
    const parameters = query();
    const draft = getDraft();
    const urlOrderID = parameters.get("orderID") || parameters.get("orderId");
    return {
      userID: Number(parameters.get("userID") || 0),
      orderID: Number(urlOrderID || draft.lastOrderID || 0)
    };
  }

  async function initServices() {
    resetCompletedDraft();
    const section = document.querySelector("main > section.grid");
    const form = document.getElementById("service-form");
    if (!section || !form) return;
    try {
      const { items, services, pricing } = await catalog();
      const draft = getDraft();
      const selected = new Set(draft.services || []);
      section.innerHTML = services.map((service) => {
        const prices = pricing.filter((entry) => entry.serviceID === service.serviceID);
        const rows = prices.map((entry) => {
          const item = items.find((candidate) => candidate.itemID === entry.itemID);
          return `<tr><td>${escapeHtml(item?.itemName || `Item #${entry.itemID}`)}</td><td><strong>${money(entry.price)}</strong></td></tr>`;
        }).join("") || "<tr><td colspan=\"2\">No current prices available.</td></tr>";
        return `<article class="card"><div class="top_bar"><div><span class="status status_info">SERVICE</span><h2>${escapeHtml(service.serviceName)}</h2></div><label><input type="checkbox" class="catalog-service" value="${service.serviceID}" ${selected.has(service.serviceID) ? "checked" : ""}> Select</label></div><table><tbody>${rows}</tbody></table></article>`;
      }).join("");
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const serviceIDs = Array.from(document.querySelectorAll(".catalog-service:checked"), (input) => Number(input.value));
        if (serviceIDs.length === 0 || !form.reportValidity()) {
          toast("Choose at least one service", "Select a service to continue.");
          return;
        }
        try {
          // The order is placed for whoever is signed in; there is no ID field to fill in.
          const userID = await currentUserID();
          saveDraft({ ...getDraft(), userID, services: serviceIDs });
          window.location.href = "new_order_items.html";
        } catch (error) {
          toast("Unable to continue", error.message);
        }
      });
    } catch (error) {
      section.innerHTML = `<div class="alert" style="background:#fff0f0;color:#9a2727;border-left-color:#e05252">Unable to load the service catalogue: ${escapeHtml(error.message)}</div>`;
    }
  }

  function lineRow(line, data, selectedServices) {
    const availableItems = data.items.filter((item) => data.pricing.some((price) =>
      price.itemID === item.itemID && selectedServices.includes(price.serviceID)));
    const itemID = line?.itemID || availableItems[0]?.itemID;
    const availableServices = data.services.filter((service) => selectedServices.includes(service.serviceID)
      && data.pricing.some((price) => price.itemID === itemID && price.serviceID === service.serviceID));
    const serviceID = availableServices.some((service) => service.serviceID === line?.serviceID)
      ? line.serviceID : availableServices[0]?.serviceID;
    return `<div class="card item-row"><div class="grid grid_three"><div class="field"><label>Item</label><select class="input item-name" required>${availableItems.map((item) => `<option value="${item.itemID}" ${item.itemID === itemID ? "selected" : ""}>${escapeHtml(item.itemName)}</option>`).join("")}</select></div><div class="field"><label>Service</label><select class="input item-service" required>${availableServices.map((service) => `<option value="${service.serviceID}" ${service.serviceID === serviceID ? "selected" : ""}>${escapeHtml(service.serviceName)}</option>`).join("")}</select></div><div class="field"><label>Quantity</label><input class="input item-qty" type="number" min="1" value="${Number(line?.quantity || 1)}" required></div></div><div class="top_bar"><button class="link remove-item" type="button">Remove</button><strong class="line-total"></strong></div></div>`;
  }

  function bindLineRows(container, data, selectedServices, onChange) {
    const priceFor = (itemID, serviceID) => data.pricing.find((price) => price.itemID === Number(itemID) && price.serviceID === Number(serviceID));
    const refresh = (row) => {
      const itemID = row.querySelector(".item-name").value;
      const serviceSelect = row.querySelector(".item-service");
      const prior = Number(serviceSelect.value);
      const services = data.services.filter((service) => selectedServices.includes(service.serviceID)
        && priceFor(itemID, service.serviceID));
      serviceSelect.innerHTML = services.map((service) => `<option value="${service.serviceID}" ${service.serviceID === prior ? "selected" : ""}>${escapeHtml(service.serviceName)}</option>`).join("");
      const price = priceFor(itemID, serviceSelect.value);
      const quantity = Number(row.querySelector(".item-qty").value || 0);
      row.querySelector(".line-total").textContent = price ? money(price.price * quantity) : "Not available";
      onChange();
    };
    const bind = (row) => {
      row.querySelector(".item-name").addEventListener("change", () => refresh(row));
      row.querySelector(".item-service").addEventListener("change", () => refresh(row));
      row.querySelector(".item-qty").addEventListener("input", () => refresh(row));
      row.querySelector(".remove-item").addEventListener("click", () => {
        if (container.children.length > 1) row.remove();
        else toast("Keep at least one item", "An order needs one or more valid lines.");
        onChange();
      });
      refresh(row);
    };
    Array.from(container.children).forEach(bind);
    return { bind, priceFor };
  }

  async function initItems() {
    const list = document.getElementById("item-list");
    const form = list?.closest("form");
    if (!list || !form) return;
    try {
      const data = await catalog();
      const draft = getDraft();
      const selectedServices = draft.services?.length ? draft.services : data.services.map((service) => service.serviceID);
      const initialLines = (draft.lines || []).filter((line) => selectedServices.includes(line.serviceID));
      for (const serviceID of selectedServices) {
        if (initialLines.some((line) => line.serviceID === serviceID)) continue;
        const price = data.pricing.find((entry) => entry.serviceID === serviceID
          && data.items.some((item) => item.itemID === entry.itemID));
        if (!price) {
          const service = data.services.find((entry) => entry.serviceID === serviceID);
          throw new Error(`${service?.serviceName || "A selected service"} has no currently priced items. Return to Services & Prices to update your selection.`);
        }
        initialLines.push({ itemID: price.itemID, serviceID, quantity: 1 });
      }
      list.innerHTML = initialLines.map((line) => lineRow(line, data, selectedServices)).join("");
      const updateTotal = () => {
        const total = Array.from(list.querySelectorAll(".item-row")).reduce((sum, row) => {
          const price = data.pricing.find((entry) => entry.itemID === Number(row.querySelector(".item-name").value) && entry.serviceID === Number(row.querySelector(".item-service").value));
          return sum + (price ? price.price * Number(row.querySelector(".item-qty").value || 0) : 0);
        }, 0);
        document.getElementById("estimated-total").textContent = money(total);
      };
      const bindings = bindLineRows(list, data, selectedServices, updateTotal);
      const originalAddButton = document.getElementById("add-item");
      const addButton = originalAddButton.cloneNode(true);
      originalAddButton.replaceWith(addButton);
      addButton.addEventListener("click", () => {
        list.insertAdjacentHTML("beforeend", lineRow({}, data, selectedServices));
        bindings.bind(list.lastElementChild);
        updateTotal();
      });
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const lines = Array.from(list.querySelectorAll(".item-row")).map((row) => ({
          itemID: Number(row.querySelector(".item-name").value),
          serviceID: Number(row.querySelector(".item-service").value),
          quantity: Number(row.querySelector(".item-qty").value)
        }));
        if (lines.some((line) => !bindings.priceFor(line.itemID, line.serviceID) || line.quantity <= 0)) {
          toast("Invalid item selection", "Choose a currently priced item/service combination.");
          return;
        }
        if (selectedServices.some((serviceID) => !lines.some((line) => line.serviceID === serviceID))) {
          toast("Add items for every selected service", "Each selected service needs at least one item. To remove a service, return to Services & Prices.");
          return;
        }
        saveDraft({ ...getDraft(), lines });
        window.location.href = "new_order_schedule.html";
      });
      updateTotal();
    } catch (error) {
      list.innerHTML = `<div class="alert" style="background:#fff0f0;color:#9a2727;border-left-color:#e05252">Unable to load order items: ${escapeHtml(error.message)}</div>`;
    }
  }

  function initSchedule() {
    const form = document.querySelector("main form");
    if (!form) return;
    const draft = getDraft();
    const date = document.getElementById("pickup-date");
    const time = document.getElementById("pickup-time");
    if (date) date.value = draft.schedule?.date || "";
    if (time) time.value = draft.schedule?.time || "";
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      saveDraft({ ...getDraft(), schedule: { date: date?.value, time: time?.value, address: form.querySelector("input[name=address]:checked")?.value } });
      window.location.href = "new_order_instructions.html";
    });
  }

  function initInstructions() {
    const form = document.querySelector("main form");
    if (!form) return;
    const draft = getDraft();
    form.querySelectorAll("input[name=preference]").forEach((input) => { input.checked = draft.instructions?.preferences?.includes(input.value) || false; });
    const notes = document.getElementById("instructions");
    if (notes) notes.value = draft.instructions?.notes || "";
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const preferences = Array.from(form.querySelectorAll("input[name=preference]:checked"), (input) => input.value);
      saveDraft({ ...getDraft(), instructions: { preferences, notes: notes?.value || "" } });
      window.location.href = "new_order_review.html";
    });
  }

  async function initReview() {
    const main = document.querySelector("main");
    const draft = getDraft();
    if (!main) return;
    try {
      const [data, addresses] = await Promise.all([catalog(), api("/account/addresses")]);
      const selectedAddressID = pickupAddressID(draft.schedule);
      const selectedAddress = selectedAddressID === null
        ? addresses.find((address) => address.isDefault)
        : addresses.find((address) => address.addressID === selectedAddressID);
      const reviewAddressID = selectedAddress?.addressID || null;
      main.dataset.reviewPickupAddress = selectedAddress
        ? [selectedAddress.nickname, selectedAddress.street, selectedAddress.city, selectedAddress.state]
          .map((part) => String(part || "").trim()).filter(Boolean).join(", ")
        : "Pickup address unavailable. Return to Schedule and select a saved address.";
      const lines = draft.lines || [];
      const total = lines.reduce((sum, line) => sum + (data.pricing.find((price) => price.itemID === line.itemID && price.serviceID === line.serviceID)?.price || 0) * line.quantity, 0);
      main.innerHTML = `<header class="page_header"><div class="subtitle">NEW ORDER · REVIEW</div><h1>Review your order</h1></header><ol class="step_list"><li class="done">1 Services</li><li class="done">2 Items</li><li class="done">3 Schedule</li><li class="done">4 Instructions</li><li class="active">5 Review</li></ol><section class="card"><h2>Items and services</h2>${lines.map((line) => { const item = data.items.find((entry) => entry.itemID === line.itemID); const service = data.services.find((entry) => entry.serviceID === line.serviceID); const price = data.pricing.find((entry) => entry.itemID === line.itemID && entry.serviceID === line.serviceID); return `<div class="activity_item"><span class="activity_dot"></span><div><strong>${line.quantity} × ${escapeHtml(item?.itemName || "Unknown item")}</strong><p class="muted small">${escapeHtml(service?.serviceName || "Unknown service")}</p></div><strong>${money((price?.price || 0) * line.quantity)}</strong></div>`; }).join("") || "<p class=\"muted\">No items have been selected.</p>"}<div class="top_bar"><h3>Order total</h3><h2>${money(total)}</h2></div></section><form id="confirm-order-form" style="margin-top:20px"><label class="check_row"><input type="checkbox" required>I confirm the item and service selections are correct.</label><div class="actions"><a class="custom_button custom_button_nobg" href="new_order_items.html">Edit items</a><button class="custom_button custom_button_bg" type="submit">Confirm order</button></div></form>`;
      const form = document.getElementById("confirm-order-form");
      form.querySelector('button[type="submit"]').disabled = !reviewAddressID;
      let submitting = false;
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (submitting) return;
        if (!reviewAddressID) {
          toast("Pickup address unavailable", "Return to Schedule and select a saved address.");
          return;
        }
        if (lines.length === 0
          || (draft.services || []).some((serviceID) => !lines.some((line) => line.serviceID === serviceID))
          || !form.reportValidity()) {
          toast("Order details are incomplete", "Return to services and items before confirming.");
          return;
        }
        const button = form.querySelector('button[type="submit"]');
        submitting = true;
        button.disabled = true;
        try {
          // The order's customer is the signed-in user, asked from the server at this point
          // (not whatever ID an older draft may hold).
          const userID = await currentUserID();
          // The note and the ticked preferences from the instructions step are sent with the
          // order, so they are saved on it (orders.instructions and orders.preferences).
          const created = await api("/orders", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userID, orderLines: lines, pickupScheduled: pickupDateTime(draft.schedule), addressID: reviewAddressID, instructions: draft.instructions?.notes || "", preferences: draft.instructions?.preferences || [] }) });
          saveDraft({ ...draft, userID, lastOrderID: created.orderID, lines: [], completed: true });
          toast("Order created", `Order #${created.orderID} is ${created.statusLabel}.`);
          window.location.href = `upcoming_order_details.html?userID=${created.userID}&orderID=${created.orderID}`;
        } catch (error) {
          submitting = false;
          button.disabled = false;
          toast("Order could not be created", error.message);
        }
      });
    } catch (error) { main.insertAdjacentHTML("beforeend", `<div class="alert">Unable to review this order: ${escapeHtml(error.message)}</div>`); }
  }

  // My Orders: the customer never types an account ID. The signed-in customer is identified by
  // the server session (GET /api/account/profile via currentUserID) and their orders are listed
  // straight away. The server also refuses a customer who asks for another account's orders, so
  // an ID left in the browser draft by someone else is deliberately not used as a fallback.
  // The table has four columns: Order, Total, Status and the View link.
  async function initMyOrders() {
    const tbody = document.querySelector("tbody");
    if (!tbody) return;
    const message = (text) => { tbody.innerHTML = `<tr><td colspan="4">${text}</td></tr>`; };
    // Clicking anywhere on an order row opens that order. A click on the View link itself is left
    // to the link, so ctrl/middle-click still open it in a new tab.
    tbody.addEventListener("click", (event) => {
      const row = event.target.closest("tr[data-href]");
      if (row && !event.target.closest("a")) window.location.href = row.dataset.href;
    });
    message("Loading orders…");
    try {
      const userID = await currentUserID();
      const orders = await api(`/orders/customer/${userID}`);
      tbody.innerHTML = orders.length ? orders.map((order) => {
        // Orders that can still be changed open the page that has the modify button.
        const editable = ELIGIBLE_STATUSES.has(order.statusLabel) && order.customerCanModify === true;
        const href = `${editable ? "upcoming_order_details" : "order_details"}.html?userID=${userID}&orderID=${order.orderID}`;
        // data-href lets a click anywhere on the row open the order (see the click handler below);
        // the View link stays so keyboard and screen reader users can still reach it.
        return `<tr class="order_row" data-href="${href}"><td><strong>#${order.orderID}</strong></td><td>${money(order.orderTotal)}</td><td><span class="status">${escapeHtml(order.statusLabel)}</span></td><td><a class="link" href="${href}">View${editable ? " & modify" : ""}</a></td></tr>`;
      }).join("") : "";
      if (!orders.length) message("No orders found.");
    } catch (error) {
      message(`Unable to load your orders: ${escapeHtml(error.message)} <a class="link" href="/html/auth/login.html">Sign in again</a>`);
    }
  }

  async function fetchOrderOrExplain(main) {
    const context = orderContext();
    const { orderID } = context;
    if (!orderID) { main.innerHTML = "<section class=\"card\"><h1>Order not selected</h1><p class=\"muted\">Open an order from My Orders.</p><a class=\"custom_button custom_button_bg\" href=\"my_orders.html\">My Orders</a></section>"; return null; }
    try {
      // No userID in the link (e.g. a notification): use the signed-in customer.
      const userID = context.userID || await currentUserID();
      return await api(`/orders/customer/${userID}/${orderID}`);
    }
    catch (error) { main.innerHTML = `<section class="card"><h1>Unable to load order</h1><p class="muted">${escapeHtml(error.message)}</p><a class="custom_button custom_button_bg" href="my_orders.html">My Orders</a></section>`; return null; }
  }

  function detailMarkup(order, includeActions) {
    const lines = order.orderLines.map((line) => `<div class="activity_item"><span class="activity_dot"></span><div><strong>${line.quantity} × ${escapeHtml(line.itemName)}</strong><p class="muted small">${escapeHtml(line.serviceName)}</p></div><strong>${money(line.linePrice)}</strong></div>`).join("");
    const history = order.history.length ? order.history.map((entry) => `<div class="activity_item"><span class="activity_dot"></span><div><strong>${escapeHtml(entry.statusAfterLabel || "Status updated")}</strong><p class="muted small">${escapeHtml(entry.statusBeforeLabel || "Initial status")} → ${escapeHtml(entry.statusAfterLabel || "")}</p></div><small>${escapeHtml(entry.logDate || "")} ${escapeHtml(entry.logTime || "")}</small></div>`).join("") : "<p class=\"muted\">No status-history entries have been recorded yet.</p>";
    const eligible = ELIGIBLE_STATUSES.has(order.statusLabel) && order.customerCanModify === true;
    // What the customer asked for when placing the order. order.preferences holds the ticked
    // options as readable labels and order.instructions the note; both are shown escaped, and
    // "None" is shown when the order has neither.
    const preferences = (order.preferences || []).map((label) => escapeHtml(label)).join("<br>") || "None";
    const note = order.instructions ? escapeHtml(order.instructions) : "None";
    const instructions = `<p><strong>Preferences</strong><br><span class="muted">${preferences}</span></p><p><strong>Notes for our team</strong><br><span class="muted" style="white-space:pre-wrap;overflow-wrap:anywhere">${note}</span></p>`;
    return `<header class="welcome_banner" style="margin-top:34px"><div class="top_bar"><div><div class="subtitle">CUSTOMER ORDER</div><h1>Order #${order.orderID}</h1></div><span class="status">${escapeHtml(order.statusLabel)}</span></div></header><section class="grid grid_two" style="margin-top:22px"><article class="card"><h2>Items and services</h2>${lines}<div class="top_bar"><h3>Order total</h3><h2>${money(order.orderTotal)}</h2></div></article><aside class="card"><h2>Order information</h2><p><strong>Customer ID</strong><br><span class="muted">${order.userID}</span></p><p><strong>Status</strong><br><span class="muted">${escapeHtml(order.statusLabel)}</span></p>${instructions}</aside></section>${includeActions ? `<div class="actions" style="margin-top:20px">${eligible ? `<a class="custom_button custom_button_bg" href="modify_order.html?userID=${order.userID}&orderID=${order.orderID}">Modify order</a>` : ""}${CANCELLABLE_STATUSES.has(order.statusLabel) ? `<button id="cancel-order" class="custom_button custom_button_border" type="button">Cancel Order</button>` : ""}<a class="custom_button custom_button_border pay-now-link" href="payments.html?orderID=${order.orderID}">Pay Now</a><a class="custom_button custom_button_nobg" href="my_orders.html">My Orders</a></div>${eligible ? "" : `<p class="muted small" style="margin-top:12px">${LOCKED_MESSAGE}</p>`}` : ""}<section class="card" style="margin-top:22px"><h2>Order history</h2>${history}</section>`;
  }

  function preservePaymentContext(order) {
    const customerID = order?.userID;
    const orderID = order?.orderID;
    if (!customerID || !orderID) return;
    sessionStorage.setItem("laundrylinkCustomerID", String(customerID));
    saveDraft({ ...getDraft(), userID: Number(customerID) || customerID, lastOrderID: Number(orderID) || orderID });
  }

  function bindCancelConfirmation(cancelButton, order) {
    // Append to the body so the modal is independent of the order cards and their transforms.
    const modalStyle = document.createElement("style");
    modalStyle.textContent = `
      #order-cancel-dialog {
        position: fixed; inset: 0; margin: auto;
        width: min(460px, calc(100vw - 32px)); box-sizing: border-box;
        max-height: calc(100dvh - 32px); overflow: auto;
        padding: 28px; border: 1px solid rgba(3, 75, 120, .16);
        border-radius: 18px; background: #fff; color: var(--color-text);
        font: inherit; box-shadow: 0 24px 64px rgba(3, 42, 72, .25);
        z-index: 10000;
      }
      #order-cancel-dialog::backdrop { background: rgba(16, 42, 59, .5); }
      #order-cancel-dialog h2 { margin-top: 0; }
      #order-cancel-dialog .actions { flex-wrap: wrap; }
    `;
    const modal = document.createElement("dialog");
    modal.id = "order-cancel-dialog";
    modal.setAttribute("aria-labelledby", "order-cancel-title");
    modal.setAttribute("aria-describedby", "order-cancel-message");
    modal.innerHTML = `<h2 id="order-cancel-title">Cancel Order</h2>
      <p id="order-cancel-message">Are you sure you want to cancel this order?</p>
      <div class="actions">
        <button class="custom_button custom_button_bg" type="button" data-confirm-cancel>Yes, Cancel Order</button>
        <button class="custom_button custom_button_nobg" type="button" data-keep-order autofocus>Keep Order</button>
      </div>`;
    document.body.append(modalStyle, modal);
    const confirmButton = modal.querySelector("[data-confirm-cancel]");
    const keepButton = modal.querySelector("[data-keep-order]");
    let cancelling = false;
    cancelButton.addEventListener("click", () => {
      if (!cancelButton.disabled && !modal.open) modal.showModal();
    });
    keepButton.addEventListener("click", () => modal.close());
    modal.addEventListener("cancel", event => {
      if (cancelling) event.preventDefault();
    });
    confirmButton.addEventListener("click", async () => {
      if (cancelling) return;
      cancelling = true;
      cancelButton.disabled = confirmButton.disabled = keepButton.disabled = true;
      try {
        await api(`/orders/customer/${order.userID}/${order.orderID}/cancel`, { method: "POST" });
        modal.close();
        window.location.reload();
      } catch (error) {
        cancelling = false;
        cancelButton.disabled = confirmButton.disabled = keepButton.disabled = false;
        modal.close();
        toast("Order could not be cancelled", error.message);
      }
    });
  }

  async function initDetails(upcoming) {
    const main = document.querySelector("main");
    if (!main) return;
    const order = await fetchOrderOrExplain(main);
    if (order) {
      main.innerHTML = detailMarkup(order, upcoming || order.customerCanModify === true || CANCELLABLE_STATUSES.has(order.statusLabel));
      const cancelButton = main.querySelector("#cancel-order");
      if (cancelButton) bindCancelConfirmation(cancelButton, order);
      const payNowLink = main.querySelector(".pay-now-link");
      if (payNowLink) payNowLink.addEventListener("click", () => preservePaymentContext(order));
    }
  }

  function modificationDetailsMarkup(order, addresses) {
    const preferenceLabels = {
      "fragrance-free": "Fragrance-free detergent",
      "hypoallergenic": "Hypoallergenic detergent",
      "hang-dry": "Hang-dry delicate items",
      "hangers": "Return shirts on hangers"
    };
    const windows = [["08:00", "8:00 AM – 11:00 AM"], ["11:00", "11:00 AM – 2:00 PM"],
      ["14:00", "2:00 PM – 5:00 PM"], ["17:00", "5:00 PM – 8:00 PM"]];
    return `<section class="grid grid_two" style="margin-top:22px"><article class="card"><h2>Pickup schedule</h2>
      ${order.pickupScheduled ? `<p class="muted">Current pickup: ${escapeHtml(order.pickupScheduled.replace("T", " "))}</p>` : ""}
      <div class="field"><label for="modify-pickup-date">Pickup date</label><input id="modify-pickup-date" class="input" type="date" required value="${escapeHtml((order.pickupScheduled || "").slice(0, 10))}"></div>
      <div class="field"><label for="modify-pickup-time">Pickup time window</label><select id="modify-pickup-time" class="input" required><option value="">Choose a time</option>${windows.map(([value, label]) => `<option value="${value}" ${value === (order.pickupScheduled || "").slice(11, 16) ? "selected" : ""}>${label}</option>`).join("")}</select></div>
      <div class="field"><label for="modify-pickup-address">Pickup address</label><select id="modify-pickup-address" class="input" required><option value="">Choose a saved address</option>${addresses.map(address => `<option value="${escapeHtml(String(address.addressID))}" ${address.addressID === order.pickupAddressID ? "selected" : ""}>${escapeHtml([address.nickname, address.street, address.city, address.state].filter(Boolean).join(", "))}</option>`).join("")}</select>${addresses.length ? "" : '<p class="muted">You have no saved addresses. Save an address before modifying the order.</p>'}</div>
      </article><article class="card"><h2>Instructions and preferences</h2>
      ${Object.entries(preferenceLabels).map(([code, label]) => `<label class="check_row"><input type="checkbox" name="modify-preference" value="${code}" ${(order.preferenceCodes || []).includes(code) ? "checked" : ""}>${label}</label>`).join("")}
      <div class="field"><label for="modify-instructions">Notes for our team</label><textarea id="modify-instructions" class="input" maxlength="500" rows="4">${escapeHtml(order.instructions || "")}</textarea></div>
      </article></section>`;
  }

  function bindModificationSchedule(form) {
    const date = form.querySelector("#modify-pickup-date");
    const time = form.querySelector("#modify-pickup-time");
    const isoDate = value => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
    const validate = () => {
      const now = new Date();
      const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
      const lastDay = new Date(nextMonth.getFullYear(), nextMonth.getMonth() + 1, 0).getDate();
      nextMonth.setDate(Math.min(now.getDate(), lastDay));
      date.min = isoDate(now);
      date.max = isoDate(nextMonth);
      time.setCustomValidity("");
      Array.from(time.options).forEach(option => {
        if (!option.value) return;
        const [hours, minutes] = option.value.split(":").map(Number);
        option.disabled = date.value === date.min && hours * 60 + minutes <= now.getHours() * 60 + now.getMinutes();
      });
      if (time.selectedOptions[0]?.disabled) time.setCustomValidity("Choose a pickup window that has not started yet.");
    };
    date.addEventListener("change", validate);
    time.addEventListener("change", validate);
    validate();
    const timer = setInterval(validate, 60000);
    window.addEventListener("pagehide", () => clearInterval(timer), { once: true });
    return validate;
  }

  async function initModify() {
    const main = document.querySelector("main");
    if (!main) return;
    const order = await fetchOrderOrExplain(main);
    if (!order) return;
    if (!ELIGIBLE_STATUSES.has(order.statusLabel) || order.customerCanModify !== true) { main.innerHTML = `<section class="card"><h1>Order cannot be modified</h1><p class="muted">${LOCKED_MESSAGE} Current status: ${escapeHtml(order.statusLabel)}.</p><a class="custom_button custom_button_bg" href="order_details.html?userID=${order.userID}&orderID=${order.orderID}">View order</a></section>`; return; }
    try {
      const [data, addresses] = await Promise.all([catalog(), api("/account/addresses")]);
      const allServices = data.services.map((service) => service.serviceID);
      main.innerHTML = `<header class="page_header"><div class="subtitle">MODIFY ORDER</div><h1>Edit order #${order.orderID}</h1></header><form id="modify-order-form" style="margin-top:22px"><section class="card"><h2>Items and services</h2><div id="item-list"></div><button id="add-item" class="custom_button custom_button_nobg" type="button">+ Add another item</button></section><aside class="card" style="margin-top:20px"><div class="top_bar"><div><h2>Updated order total</h2></div><h2 id="estimated-total"></h2></div></aside><div class="actions"><a class="custom_button custom_button_nobg" href="order_details.html?userID=${order.userID}&orderID=${order.orderID}">Discard changes</a><button class="custom_button custom_button_bg" type="submit">Save order changes</button></div></form>`;
      const form = document.getElementById("modify-order-form");
      form.querySelector(".actions").insertAdjacentHTML("beforebegin", modificationDetailsMarkup(order, addresses));
      const validateSchedule = bindModificationSchedule(form);
      const list = document.getElementById("item-list");
      list.innerHTML = order.orderLines.map((line) => lineRow(line, data, allServices)).join("");
      const updateTotal = () => { const total = Array.from(list.querySelectorAll(".item-row")).reduce((sum, row) => { const price = data.pricing.find((entry) => entry.itemID === Number(row.querySelector(".item-name").value) && entry.serviceID === Number(row.querySelector(".item-service").value)); return sum + (price ? price.price * Number(row.querySelector(".item-qty").value || 0) : 0); }, 0); document.getElementById("estimated-total").textContent = money(total); };
      const bindings = bindLineRows(list, data, allServices, updateTotal);
      document.getElementById("add-item").addEventListener("click", () => { list.insertAdjacentHTML("beforeend", lineRow({}, data, allServices)); bindings.bind(list.lastElementChild); updateTotal(); });
      let saving = false;
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (saving) return;
        validateSchedule();
        if (!form.reportValidity()) return;
        const lines = Array.from(list.querySelectorAll(".item-row")).map((row) => ({ itemID: Number(row.querySelector(".item-name").value), serviceID: Number(row.querySelector(".item-service").value), quantity: Number(row.querySelector(".item-qty").value) }));
        if (!lines.length || lines.some((line) => !bindings.priceFor(line.itemID, line.serviceID) || line.quantity <= 0)) { toast("Invalid order lines", "Choose valid items, services, and quantities."); return; }
        const request = {
          orderLines: lines,
          addressID: Number(form.querySelector("#modify-pickup-address").value),
          pickupScheduled: `${form.querySelector("#modify-pickup-date").value}T${form.querySelector("#modify-pickup-time").value}:00`,
          instructions: form.querySelector("#modify-instructions").value,
          preferences: Array.from(form.querySelectorAll('input[name="modify-preference"]:checked'), input => input.value)
        };
        const saveButton = form.querySelector('button[type="submit"]');
        saving = true;
        saveButton.disabled = true;
        try { await api(`/orders/customer/${order.userID}/${order.orderID}/lines`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) }); window.location.href = `order_details.html?userID=${order.userID}&orderID=${order.orderID}`; }
        catch (error) { saving = false; saveButton.disabled = false; toast("Order could not be updated", error.message); }
      });
      updateTotal();
    } catch (error) { main.innerHTML = `<section class="card"><h1>Unable to prepare modification</h1><p class="muted">${escapeHtml(error.message)}</p></section>`; }
  }

  document.addEventListener("DOMContentLoaded", () => {
    const page = window.location.pathname.split("/").pop();
    if (page === "new_order_services.html") initServices();
    else if (page === "new_order_items.html") initItems();
    else if (page === "new_order_schedule.html") initSchedule();
    else if (page === "new_order_instructions.html") initInstructions();
    else if (page === "new_order_review.html") initReview();
    else if (page === "my_orders.html") initMyOrders();
    else if (page === "order_details.html") initDetails(false);
    else if (page === "upcoming_order_details.html") initDetails(true);
    else if (page === "modify_order.html") initModify();
  });
})();
