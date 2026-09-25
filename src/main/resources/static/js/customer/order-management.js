(function () {
  "use strict";

  const API = "/api";
  const DRAFT_KEY = "laundryLink.orderDraft";
  const ELIGIBLE_STATUSES = new Set(["Unconfirmed", "Payment Verified", "Awaiting Pickup"]);

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

  function toast(title, detail) {
    if (window.connectedToast) window.connectedToast(title, detail);
    else window.alert(`${title}${detail ? `\n${detail}` : ""}`);
  }

  async function api(path, options) {
    const response = await fetch(`${API}${path}`, options);
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

  async function catalog() {
    const [items, services, pricing] = await Promise.all([
      api("/items"), api("/services"), api("/service-pricing")
    ]);
    return { items, services, pricing };
  }

  function orderContext() {
    const parameters = query();
    const draft = getDraft();
    return {
      userID: Number(parameters.get("userID") || draft.userID || 0),
      orderID: Number(parameters.get("orderID") || draft.lastOrderID || 0)
    };
  }

  async function initServices() {
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
      form.insertAdjacentHTML("afterbegin", `<div class="card" style="margin-bottom:22px"><div class="field"><label for="order-user-id">Customer account ID</label><input class="input" id="order-user-id" type="number" min="1" required value="${draft.userID || ""}" placeholder="Enter your customer ID"></div></div>`);
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        const userID = Number(document.getElementById("order-user-id").value);
        const serviceIDs = Array.from(document.querySelectorAll(".catalog-service:checked"), (input) => Number(input.value));
        if (!userID || serviceIDs.length === 0 || !form.reportValidity()) {
          toast("Choose a customer and at least one service", "Both are required to continue.");
          return;
        }
        saveDraft({ ...getDraft(), userID, services: serviceIDs });
        window.location.href = "new_order_items.html";
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
      const data = await catalog();
      const lines = draft.lines || [];
      const total = lines.reduce((sum, line) => sum + (data.pricing.find((price) => price.itemID === line.itemID && price.serviceID === line.serviceID)?.price || 0) * line.quantity, 0);
      main.innerHTML = `<header class="page_header"><div class="subtitle">NEW ORDER · REVIEW</div><h1>Review your order</h1></header><ol class="step_list"><li class="done">1 Services</li><li class="done">2 Items</li><li class="done">3 Schedule</li><li class="done">4 Instructions</li><li class="active">5 Review</li></ol><section class="card"><h2>Items and services</h2>${lines.map((line) => { const item = data.items.find((entry) => entry.itemID === line.itemID); const service = data.services.find((entry) => entry.serviceID === line.serviceID); const price = data.pricing.find((entry) => entry.itemID === line.itemID && entry.serviceID === line.serviceID); return `<div class="activity_item"><span class="activity_dot"></span><div><strong>${line.quantity} × ${escapeHtml(item?.itemName || "Unknown item")}</strong><p class="muted small">${escapeHtml(service?.serviceName || "Unknown service")}</p></div><strong>${money((price?.price || 0) * line.quantity)}</strong></div>`; }).join("") || "<p class=\"muted\">No items have been selected.</p>"}<div class="top_bar"><h3>Order total</h3><h2>${money(total)}</h2></div></section><form id="confirm-order-form" style="margin-top:20px"><label class="check_row"><input type="checkbox" required>I confirm the item and service selections are correct.</label><div class="actions"><a class="custom_button custom_button_nobg" href="new_order_items.html">Edit items</a><button class="custom_button custom_button_bg" type="submit">Confirm order</button></div></form>`;
      const form = document.getElementById("confirm-order-form");
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!draft.userID || lines.length === 0
          || (draft.services || []).some((serviceID) => !lines.some((line) => line.serviceID === serviceID))
          || !form.reportValidity()) {
          toast("Order details are incomplete", "Return to services and items before confirming.");
          return;
        }
        try {
          const created = await api("/orders", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userID: draft.userID, orderLines: lines }) });
          saveDraft({ ...draft, lastOrderID: created.orderID, lines: [] });
          toast("Order created", `Order #${created.orderID} is ${created.statusLabel}.`);
          window.location.href = `upcoming_order_details.html?userID=${created.userID}&orderID=${created.orderID}`;
        } catch (error) { toast("Order could not be created", error.message); }
      });
    } catch (error) { main.insertAdjacentHTML("beforeend", `<div class="alert">Unable to review this order: ${escapeHtml(error.message)}</div>`); }
  }

  function customerSelector(main, reload) {
    const draft = getDraft();
    main.querySelector("header").insertAdjacentHTML("afterend", `<form id="customer-order-selector" class="card" style="margin-top:20px"><div class="top_bar"><div class="field" style="flex:1"><label for="orders-user-id">Customer account ID</label><input id="orders-user-id" class="input" type="number" min="1" required value="${draft.userID || ""}"></div><button class="custom_button custom_button_bg" type="submit">Load orders</button></div></form>`);
    document.getElementById("customer-order-selector").addEventListener("submit", (event) => { event.preventDefault(); const userID = Number(document.getElementById("orders-user-id").value); if (userID) { saveDraft({ ...getDraft(), userID }); reload(userID); } });
  }

  async function initMyOrders() {
    const main = document.querySelector("main");
    const tbody = document.querySelector("tbody");
    if (!main || !tbody) return;
    const headers = document.querySelectorAll("thead th");
    if (headers[1]) headers[1].textContent = "CUSTOMER ID";
    const load = async (userID) => {
      tbody.innerHTML = "<tr><td colspan=\"6\">Loading orders…</td></tr>";
      try {
        const orders = await api(`/orders/customer/${userID}`);
        tbody.innerHTML = orders.length ? orders.map((order) => `<tr><td><strong>#${order.orderID}</strong></td><td>${order.userID || userID}</td><td>Order lines available in details</td><td>${money(order.orderTotal)}</td><td><span class="status">${escapeHtml(order.statusLabel)}</span></td><td><a class="link" href="${ELIGIBLE_STATUSES.has(order.statusLabel) ? "upcoming_order_details" : "order_details"}.html?userID=${userID}&orderID=${order.orderID}">View${ELIGIBLE_STATUSES.has(order.statusLabel) ? " & modify" : ""}</a></td></tr>`).join("") : "<tr><td colspan=\"6\">No orders found.</td></tr>";
      } catch (error) { tbody.innerHTML = `<tr><td colspan="6">Unable to load orders: ${escapeHtml(error.message)}</td></tr>`; }
    };
    customerSelector(main, load);
    const userID = Number(getDraft().userID || 0);
    if (userID) load(userID); else tbody.innerHTML = "<tr><td colspan=\"6\">Enter a customer account ID to load orders.</td></tr>";
  }

  async function fetchOrderOrExplain(main) {
    const { userID, orderID } = orderContext();
    if (!userID || !orderID) { main.innerHTML = "<section class=\"card\"><h1>Order not selected</h1><p class=\"muted\">Open an order from My Orders.</p><a class=\"custom_button custom_button_bg\" href=\"my_orders.html\">My Orders</a></section>"; return null; }
    try { return await api(`/orders/customer/${userID}/${orderID}`); }
    catch (error) { main.innerHTML = `<section class="card"><h1>Unable to load order</h1><p class="muted">${escapeHtml(error.message)}</p><a class="custom_button custom_button_bg" href="my_orders.html">My Orders</a></section>`; return null; }
  }

  function detailMarkup(order, includeActions) {
    const lines = order.orderLines.map((line) => `<div class="activity_item"><span class="activity_dot"></span><div><strong>${line.quantity} × ${escapeHtml(line.itemName)}</strong><p class="muted small">${escapeHtml(line.serviceName)}</p></div><strong>${money(line.linePrice)}</strong></div>`).join("");
    const history = order.history.length ? order.history.map((entry) => `<div class="activity_item"><span class="activity_dot"></span><div><strong>${escapeHtml(entry.statusAfterLabel || "Status updated")}</strong><p class="muted small">${escapeHtml(entry.statusBeforeLabel || "Initial status")} → ${escapeHtml(entry.statusAfterLabel || "")}</p></div><small>${escapeHtml(entry.logDate || "")} ${escapeHtml(entry.logTime || "")}</small></div>`).join("") : "<p class=\"muted\">No status-history entries have been recorded yet.</p>";
    const eligible = ELIGIBLE_STATUSES.has(order.statusLabel);
    return `<header class="welcome_banner" style="margin-top:34px"><div class="top_bar"><div><div class="subtitle">CUSTOMER ORDER</div><h1>Order #${order.orderID}</h1></div><span class="status">${escapeHtml(order.statusLabel)}</span></div></header><section class="grid grid_two" style="margin-top:22px"><article class="card"><h2>Items and services</h2>${lines}<div class="top_bar"><h3>Order total</h3><h2>${money(order.orderTotal)}</h2></div></article><aside class="card"><h2>Order information</h2><p><strong>Customer ID</strong><br><span class="muted">${order.userID}</span></p><p><strong>Status</strong><br><span class="muted">${escapeHtml(order.statusLabel)}</span></p></aside></section>${includeActions ? `<div class="actions" style="margin-top:20px">${eligible ? `<a class="custom_button custom_button_bg" href="modify_order.html?userID=${order.userID}&orderID=${order.orderID}">Modify order</a>` : ""}<a class="custom_button custom_button_nobg" href="my_orders.html">My Orders</a></div>` : ""}<section class="card" style="margin-top:22px"><h2>Order history</h2>${history}</section>`;
  }

  async function initDetails(upcoming) {
    const main = document.querySelector("main");
    if (!main) return;
    const order = await fetchOrderOrExplain(main);
    if (order) main.innerHTML = detailMarkup(order, upcoming);
  }

  async function initModify() {
    const main = document.querySelector("main");
    if (!main) return;
    const order = await fetchOrderOrExplain(main);
    if (!order) return;
    if (!ELIGIBLE_STATUSES.has(order.statusLabel)) { main.innerHTML = `<section class="card"><h1>Order cannot be modified</h1><p class="muted">${escapeHtml(order.statusLabel)} is beyond the permitted modification stage.</p><a class="custom_button custom_button_bg" href="order_details.html?userID=${order.userID}&orderID=${order.orderID}">View order</a></section>`; return; }
    try {
      const data = await catalog();
      const allServices = data.services.map((service) => service.serviceID);
      main.innerHTML = `<header class="page_header"><div class="subtitle">MODIFY ORDER</div><h1>Edit order #${order.orderID}</h1></header><form id="modify-order-form" style="margin-top:22px"><section class="card"><h2>Items and services</h2><div id="item-list"></div><button id="add-item" class="custom_button custom_button_nobg" type="button">+ Add another item</button></section><aside class="card" style="margin-top:20px"><div class="top_bar"><div><h2>Updated order total</h2></div><h2 id="estimated-total"></h2></div></aside><div class="actions"><a class="custom_button custom_button_nobg" href="order_details.html?userID=${order.userID}&orderID=${order.orderID}">Discard changes</a><button class="custom_button custom_button_bg" type="submit">Save order changes</button></div></form>`;
      const list = document.getElementById("item-list");
      list.innerHTML = order.orderLines.map((line) => lineRow(line, data, allServices)).join("");
      const updateTotal = () => { const total = Array.from(list.querySelectorAll(".item-row")).reduce((sum, row) => { const price = data.pricing.find((entry) => entry.itemID === Number(row.querySelector(".item-name").value) && entry.serviceID === Number(row.querySelector(".item-service").value)); return sum + (price ? price.price * Number(row.querySelector(".item-qty").value || 0) : 0); }, 0); document.getElementById("estimated-total").textContent = money(total); };
      const bindings = bindLineRows(list, data, allServices, updateTotal);
      document.getElementById("add-item").addEventListener("click", () => { list.insertAdjacentHTML("beforeend", lineRow({}, data, allServices)); bindings.bind(list.lastElementChild); updateTotal(); });
      document.getElementById("modify-order-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const lines = Array.from(list.querySelectorAll(".item-row")).map((row) => ({ itemID: Number(row.querySelector(".item-name").value), serviceID: Number(row.querySelector(".item-service").value), quantity: Number(row.querySelector(".item-qty").value) }));
        if (!lines.length || lines.some((line) => !bindings.priceFor(line.itemID, line.serviceID) || line.quantity <= 0)) { toast("Invalid order lines", "Choose valid items, services, and quantities."); return; }
        try { await api(`/orders/customer/${order.userID}/${order.orderID}/lines`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ orderLines: lines }) }); window.location.href = `order_details.html?userID=${order.userID}&orderID=${order.orderID}`; }
        catch (error) { toast("Order could not be updated", error.message); }
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
