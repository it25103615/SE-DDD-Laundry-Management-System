(function () {
  const form = document.getElementById("promotion-form");
  const table = document.getElementById("promotion-table");
  const title = document.getElementById("promotion-form-title");
  const saveButton = document.getElementById("save-promotion");
  const resetButton = document.getElementById("reset-promotion-form");
  let csrfPromise = null;

  let promotions = [];

  function money(value) {
    const number = Number(value || 0);
    return `LKR ${number.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  }

  function label(value) {
    return String(value || "-").replaceAll("_", " ");
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function showMessage(id, message) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = message || "";
    element.hidden = !message;
  }

  function clearMessages() {
    showMessage("promotion-success", "");
    showMessage("promotion-error", "");
  }

  function promotionError(error, action) {
    if (error.status === 404) return "Promotion could not be found.";
    if (error.status === 400) {
      return `${action} failed. Check for duplicate codes, invalid discount values, date range, minimum amount or unsupported discount type.`;
    }
    if (error.status === 401 || error.status === 403) {
      return "You are not authorised to manage promotions.";
    }
    return `${action} failed. Please try again.`;
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

    return fetch(path, { ...options, headers, credentials: "same-origin" }).then(async (response) => {
      if (!response.ok) {
        const error = new Error(`Request failed with status ${response.status}`);
        error.status = response.status;
        throw error;
      }
      if (response.status === 204) return null;
      return response.json();
    });
  }

  function promotionPayload() {
    const data = new FormData(form);
    return {
      promotionCode: String(data.get("promotionCode") || "").trim(),
      promotionName: String(data.get("promotionName") || "").trim(),
      discountType: data.get("discountType"),
      discountValue: Number(data.get("discountValue")),
      minimumOrderAmount: Number(data.get("minimumOrderAmount")),
      validFrom: data.get("validFrom"),
      validTo: data.get("validTo"),
      active: document.getElementById("promotion-active").checked,
    };
  }

  function renderTable() {
    if (!table) return;
    if (!Array.isArray(promotions)) {
      table.innerHTML = '<tr><td colspan="9">Promotions could not be loaded.</td></tr>';
      return;
    }
    if (!promotions.length) {
      table.innerHTML = '<tr><td colspan="9">No promotions available.</td></tr>';
      return;
    }

    table.innerHTML = promotions
      .map((promotion) => {
        const active = Boolean(promotion.active);
        const statusClass = active ? "status_success" : "status_warning";
        const statusText = active ? "Active" : "Inactive";
        const toggleText = active ? "Deactivate" : "Activate";
        const validRange = `${escapeHtml(promotion.validFrom)} to ${escapeHtml(promotion.validTo)}`;
        return `
          <tr>
            <td>#${escapeHtml(promotion.promotionID)}</td>
            <td>${escapeHtml(promotion.promotionCode)}</td>
            <td>${escapeHtml(promotion.promotionName)}</td>
            <td>${label(promotion.discountType)}</td>
            <td>${escapeHtml(promotion.discountValue)}</td>
            <td>${money(promotion.minimumOrderAmount)}</td>
            <td>${validRange}</td>
            <td><span class="status ${statusClass}">${statusText}</span></td>
            <td>
              <button class="link" type="button" data-action="edit" data-promotion-id="${escapeHtml(promotion.promotionID)}">Edit</button>
              <button class="link" type="button" data-action="toggle" data-promotion-id="${escapeHtml(promotion.promotionID)}">${toggleText}</button>
            </td>
          </tr>`;
      })
      .join("");
  }

  async function loadPromotions() {
    if (!table) return;
    table.innerHTML = '<tr><td colspan="9">Loading promotions...</td></tr>';
    try {
      promotions = await api("/api/promotions");
      renderTable();
    } catch (error) {
      promotions = [];
      table.innerHTML = '<tr><td colspan="9">Promotions could not be loaded.</td></tr>';
    }
  }

  function resetForm() {
    form.reset();
    document.getElementById("promotion-id").value = "";
    document.getElementById("promotion-active").checked = true;
    title.textContent = "Create Promotion";
    saveButton.textContent = "Create Promotion";
    clearMessages();
  }

  function fillForm(promotion) {
    document.getElementById("promotion-id").value = promotion.promotionID;
    document.getElementById("promotion-code").value = promotion.promotionCode || "";
    document.getElementById("promotion-name").value = promotion.promotionName || "";
    document.getElementById("discount-type").value = promotion.discountType || "PERCENTAGE";
    document.getElementById("discount-value").value = promotion.discountValue ?? "";
    document.getElementById("minimum-order-amount").value = promotion.minimumOrderAmount ?? 0;
    document.getElementById("valid-from").value = promotion.validFrom || "";
    document.getElementById("valid-to").value = promotion.validTo || "";
    document.getElementById("promotion-active").checked = Boolean(promotion.active);
    title.textContent = `Edit Promotion #${promotion.promotionID}`;
    saveButton.textContent = "Update Promotion";
    clearMessages();
    form.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function savePromotion(event) {
    event.preventDefault();
    clearMessages();
    const promotionID = document.getElementById("promotion-id").value;
    const editing = Boolean(promotionID);
    const action = editing ? "Update" : "Create";

    saveButton.disabled = true;
    try {
      await api(editing ? `/api/promotions/${encodeURIComponent(promotionID)}` : "/api/promotions", {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify(promotionPayload()),
      });
      resetForm();
      showMessage("promotion-success", editing ? "Promotion updated successfully." : "Promotion created successfully.");
      await loadPromotions();
    } catch (error) {
      showMessage("promotion-error", promotionError(error, action));
    } finally {
      saveButton.disabled = false;
    }
  }

  async function togglePromotion(promotion) {
    clearMessages();
    const active = !promotion.active;
    try {
      await api(`/api/promotions/${encodeURIComponent(promotion.promotionID)}/active?active=${active}`, {
        method: "PATCH",
      });
      showMessage("promotion-success", active ? "Promotion activated successfully." : "Promotion deactivated successfully.");
      await loadPromotions();
    } catch (error) {
      showMessage("promotion-error", promotionError(error, active ? "Activate" : "Deactivate"));
    }
  }

  if (form) form.addEventListener("submit", savePromotion);
  if (resetButton) resetButton.addEventListener("click", resetForm);
  if (table) {
    table.addEventListener("click", (event) => {
      const button = event.target.closest("button[data-action]");
      if (!button) return;
      const promotionID = Number(button.dataset.promotionId);
      const promotion = promotions.find((item) => Number(item.promotionID) === promotionID);
      if (!promotion) {
        showMessage("promotion-error", "Promotion could not be found.");
        return;
      }
      if (button.dataset.action === "edit") fillForm(promotion);
      if (button.dataset.action === "toggle") togglePromotion(promotion);
    });
  }

  loadPromotions();
})();
