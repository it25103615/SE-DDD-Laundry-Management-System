/* Shared HTTP and rendering helpers. No fabricated success notifications. */
window.Support = (() => {
  let csrf;
  const $ = (id) => document.getElementById(id);
  const escape = (value) =>
    String(value ?? "").replace(
      /[&<>"']/g,
      (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c],
    );
  const money = (value) =>
    new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(
      Number(value || 0),
    );
  const date = (value) => {
    if (!value) return "Time unavailable";
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime())
      ? String(value)
      : parsed.toLocaleString("en-LK", { dateStyle: "medium", timeStyle: "short" });
  };
  function notice(message, kind = "") {
    $("notice").textContent = message;
    $("notice").className = "notice " + kind;
    $("notice").hidden = !message;
    const inline = $("detail-notice");
    if (inline) {
      inline.textContent = message;
      inline.className = "notice " + kind;
      inline.hidden = !message;
    }
    if (inline && !$("case-detail").hidden) $("notice").hidden = true;
  }
  async function api(path, method = "GET", body) {
    const headers = { Accept: "application/json" };
    if (csrf && method !== "GET") headers[csrf.headerName] = csrf.token;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch("/api/support" + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(
        data.message ||
          data.detail ||
          (response.status === 404
            ? "Support API unavailable. Open this page through the running Spring Boot application."
            : "Request failed (" + response.status + ")."),
      );
    }
    return response.status === 204 ? null : response.json();
  }
  async function run(task, button) {
    if (button) button.disabled = true;
    try {
      await task();
    } catch (error) {
      notice(error.message, "error");
    } finally {
      if (button) button.disabled = false;
    }
  }
  async function init() {
    notice("Connecting to the database…");
    csrf = await fetch("/api/auth/csrf").then((response) => response.json());
    let context;
    try {
      context = await api("/context");
    } catch (error) {
      $("identity").textContent = "Connection unavailable";
      throw error;
    }
    $("identity").textContent = context.actor.name + " · " + context.actor.role;
    notice("");
    return context;
  }
  function table(id, rows, columns, empty = "No records found.") {
    $(id).innerHTML = rows.length
      ? rows
          .map(
            (row) =>
              "<tr>" +
              columns
                .map((c) => "<td>" + escape(typeof c === "function" ? c(row) : row[c]) + "</td>")
                .join("") +
              "</tr>",
          )
          .join("")
      : '<tr><td colspan="' + columns.length + '">' + escape(empty) + "</td></tr>";
  }
  return { $, escape, money, date, notice, api, run, init, table };
})();

(() => {
  const page = document.body.dataset.page;
  if (!["service-catalog", "staff-accounts", "roles-permissions"].includes(page)) return;
  const { $, escape: e, money, date, api, notice, run } = Support;
  if (page === "staff-accounts") {
    $("staff-phone").addEventListener("keydown", (event) => {
      if (!event.ctrlKey && !event.metaKey && event.key.length === 1 && !/^[0-9]$/.test(event.key))
        event.preventDefault();
    });
    $("staff-phone").addEventListener("input", (event) => {
      event.target.value = event.target.value.replace(/[^0-9]/g, "").slice(0, 10);
    });
  }

  async function catalog() {
    // "editing" is the service open in the service pop-up (null while a new one is being added);
    // "pricing" is the service whose items are open in the Edit items pop-up.
    let data, editing, pricing;
    const render = () => {
      const names = new Map(data.services.map((s) => [String(s.id), s.name]));
      // Two actions per service: one for the service itself, one for the prices of its items.
      // A service with no items yet has nothing to edit: items are added with "New item".
      $("service-rows").innerHTML = data.services.length
        ? data.services
            .map(
              (s) =>
                `<tr><td><strong>${e(s.name)}</strong><br><small>${e(s.description)}</small></td><td>${e(s.turnaroundHours)} hours</td><td><span class="status">${s.active ? "ACTIVE" : "INACTIVE"}</span></td><td>${e(s.pricedItems)}</td><td><div class="actions"><button class="custom_button" data-edit-service="${s.id}">Edit service</button><button class="custom_button" data-edit-items="${s.id}" ${Number(s.pricedItems) ? "" : 'disabled title="This service has no items yet. Add one with New item."'}>Edit items</button></div></td></tr>`,
            )
            .join("")
        : '<tr><td colspan="5">No services exist yet.</td></tr>';
      $("item-service").innerHTML = data.services
        .map((s) => `<option value="${s.id}">${e(s.name)}</option>`)
        .join("");
      // Suggestions for the name box of the New item pop-up: every item that already exists.
      $("item-names").innerHTML = data.items
        .map((i) => `<option value="${e(i.name)}"></option>`)
        .join("");
      $("price-rows").innerHTML = data.prices.length
        ? data.prices
            .map(
              (p) =>
                `<tr><td>${e(names.get(String(p.serviceId)) || "Service #" + p.serviceId)}</td><td>${e(p.item)}</td><td>${e(money(p.price))}</td></tr>`,
            )
            .join("")
        : '<tr><td colspan="3">No item prices have been recorded.</td></tr>';
      $("new-item").disabled = !data.services.length;
    };
    const load = async () => {
      data = await api("/administration/catalog");
      render();
    };
    // The items of the service open in the Edit items pop-up, and the price box kept in step with
    // the item selected there.
    const pricedItems = () => data.prices.filter((p) => String(p.serviceId) === String(pricing.id));
    const syncPrice = () => {
      const found = pricedItems().find((p) => String(p.itemId) === $("price-item").value);
      $("price").value = found ? Number(found.price).toFixed(2) : "";
    };
    // Opens a pop-up, with the error of an earlier failed save cleared.
    const open = (id) => {
      $(id).querySelector(".dialog-notice").hidden = true;
      $(id).showModal();
    };
    // Submit handler shared by the three pop-up forms. "task" sends the request. If it fails, the
    // message is shown inside the pop-up, which stays open so nothing typed is lost. If it works,
    // the pop-up closes, the tables are reloaded and "message" is shown as the page notice.
    const save = async (event, task, message) => {
      event.preventDefault();
      const dialog = event.target.closest("dialog"),
        error = dialog.querySelector(".dialog-notice"),
        button = event.submitter;
      error.hidden = true;
      if (button) button.disabled = true;
      try {
        await task();
      } catch (failure) {
        error.textContent = failure.message;
        error.hidden = false;
        return;
      } finally {
        if (button) button.disabled = false;
      }
      dialog.close();
      run(async () => {
        await load();
        notice(message, "success");
      });
    };
    document.querySelectorAll("[data-close-dialog]").forEach((button) => {
      button.onclick = () => button.closest("dialog").close();
    });

    $("new-service").onclick = () => {
      editing = null;
      $("service-form").reset();
      $("service-form-title").textContent = "Add service";
      open("service-dialog");
    };
    $("new-item").onclick = () => {
      $("item-form").reset();
      open("item-dialog");
    };
    $("service-rows").onclick = (event) => {
      const button = event.target.closest("[data-edit-service],[data-edit-items]");
      if (!button || button.disabled) return;
      const row = data.services.find(
        (s) => String(s.id) === (button.dataset.editService || button.dataset.editItems),
      );
      if (button.dataset.editService) {
        editing = row;
        $("service-name").value = row.name;
        $("service-description").value = row.description;
        $("turnaround").value = row.turnaroundHours;
        $("service-active").checked = row.active;
        $("service-form-title").textContent = "Edit service";
        open("service-dialog");
        return;
      }
      // Edit items: the dropdown is filled with the items of this service only.
      pricing = row;
      $("items-title").textContent = "Edit items: " + row.name;
      $("price-item").innerHTML = pricedItems()
        .map((p) => `<option value="${p.itemId}">${e(p.item)}</option>`)
        .join("");
      syncPrice();
      open("items-dialog");
    };
    $("price-item").onchange = syncPrice;
    $("service-form").onsubmit = (event) =>
      save(
        event,
        () =>
          api(
            editing ? "/administration/services/" + editing.id : "/administration/services",
            editing ? "PUT" : "POST",
            {
              name: $("service-name").value.trim(),
              description: $("service-description").value.trim(),
              turnaroundHours: Number($("turnaround").value),
              active: $("service-active").checked,
              version: editing?.version ?? null,
            },
          ),
        "Service saved.",
      );
    $("price-form").onsubmit = (event) =>
      save(
        event,
        () =>
          api("/administration/services/" + pricing.id + "/prices", "PUT", {
            itemId: Number($("price-item").value),
            price: Number($("price").value),
          }),
        "Item price saved.",
      );
    // The server reuses an item that already has this name and creates one otherwise. To let the
    // manager pick an existing item from a dropdown instead, send its id to the /prices request
    // above (it also adds an item that is not in the service yet); "data.items" lists every item.
    $("item-form").onsubmit = (event) =>
      save(
        event,
        () =>
          api("/administration/services/" + $("item-service").value + "/items", "POST", {
            name: $("item-name").value.trim(),
            price: Number($("item-price").value),
          }),
        "Item added to the service.",
      );
    await load();
  }

  async function staff(context) {
    let rows = [],
      selected;
    const owner = ["OWNER", "ADMIN"].includes(context.actor.role);
    const canManage = (row) => row.id !== context.actor.id && (owner || row.role !== "MANAGER");
    const clear = () => {
      $("staff-form").reset();
      $("staff-active").checked = true;
      $("staff-form-title").textContent = "Add account";
      $("staff-password").required = true;
    };
    // The role filter is applied here, on the rows already loaded: the list is not paginated, so no
    // request is needed. "rows" stays complete; only the rows that are drawn ("shown") are narrowed.
    const render = () => {
      const role = $("filter-role").value,
        shown = role ? rows.filter((r) => r.role === role) : rows;
      $("staff-rows").innerHTML = shown.length
        ? shown
            .map(
              (r) =>
                `<tr><td><strong>${e(r.name)}</strong></td><td>${e(r.role)}</td><td>${e(r.email)}<br><small>${e(r.phone)}</small></td><td><span class="status">${r.active ? "ACTIVE" : "INACTIVE"}</span></td><td>${e(date(r.updatedAt))}</td><td><div class="actions"><button class="custom_button" data-role-staff="${r.id}" ${canManage(r) ? "" : "disabled"}>Change Role</button><button class="custom_button" data-delete-staff="${r.id}" ${canManage(r) ? "" : "disabled"}>Delete Account</button></div></td></tr>`,
            )
            .join("")
        : '<tr><td colspan="6">No operational accounts match this search.</td></tr>';
    };
    const load = async () => {
      const query = new URLSearchParams({ search: $("search-staff").value.trim() });
      rows = await api("/administration/staff?" + query);
      render();
    };
    const activity = async () => {
      const items = await api("/administration/activity");
      $("admin-activity").innerHTML = items.length
        ? items
            .slice(0, 12)
            .map(
              (a) =>
                `<div class="history-entry"><strong>${e(a.action)}</strong><div>${e(a.details)}</div><small class="muted">${e(a.actor)} · ${e(date(a.createdAt))}</small></div>`,
            )
            .join("")
        : '<p class="muted">No administrative changes recorded yet.</p>';
    };
    $("staff-search").onsubmit = (event) => {
      event.preventDefault();
      run(load, event.submitter);
    };
    // Picking a role redraws the table at once. Clear empties the search box and the role filter,
    // then reloads the full list.
    $("filter-role").onchange = render;
    $("clear-search").onclick = () => {
      $("staff-search").reset();
      run(load);
    };
    if (context.actor.role !== "OWNER") {
      $("staff-role").querySelector('[value="MANAGER"]').remove();
      $("employee-role").querySelector('[value="MANAGER"]').remove();
    }
    $("cancel-role").onclick = () => {
      $("role-dialog").close();
      selected = null;
    };
    $("staff-rows").onclick = (event) => {
      const button = event.target.closest("[data-role-staff],[data-delete-staff]");
      if (!button || button.disabled) return;
      const row = rows.find(
        (r) => String(r.id) === (button.dataset.roleStaff || button.dataset.deleteStaff),
      );
      if (!row || !canManage(row)) return;
      if (button.dataset.roleStaff) {
        selected = row;
        $("role-employee").textContent = row.name + " (" + row.email + ")";
        $("employee-role").value = row.role;
        $("role-dialog").showModal();
        return;
      }
      if (
        !window.confirm(
          "Delete account for " +
            row.name +
            " (" +
            row.email +
            ")? They will lose access. Historical records will be preserved.",
        )
      )
        return;
      run(async () => {
        await api("/administration/staff/" + row.id, "DELETE", { version: row.version });
        await Promise.all([load(), activity()]);
        notice("Employee account deleted.", "success");
      }, button);
    };
    $("role-form").onsubmit = (event) => {
      event.preventDefault();
      if (!selected) return;
      const row = selected,
        role = $("employee-role").value;
      $("role-dialog").close();
      selected = null;
      run(async () => {
        await api("/administration/staff/" + row.id + "/role", "PATCH", {
          role,
          version: row.version,
        });
        await Promise.all([load(), activity()]);
        notice("Employee role saved. The employee must sign in again.", "success");
      }, event.submitter);
    };
    $("cancel-staff").onclick = clear;
    $("staff-form").onsubmit = (event) => {
      event.preventDefault();
      run(async () => {
        const input = {
          firstName: $("first-name").value.trim(),
          lastName: $("last-name").value.trim(),
          email: $("staff-email").value.trim(),
          phone: $("staff-phone").value.trim(),
          role: $("staff-role").value,
          password: $("staff-password").value || null,
          active: $("staff-active").checked,
          version: null,
        };
        await api("/administration/staff", "POST", input);
        clear();
        await Promise.all([load(), activity()]);
        notice("Operational account saved.", "success");
      }, event.submitter);
    };
    clear();
    await Promise.all([load(), activity()]);
  }

  async function roles() {
    const data = await api("/administration/roles");
    $("role-rows").innerHTML = data.roles
      .map(
        (role) =>
          `<tr><td><strong>${e(role.label)}</strong><br><small>${e(role.role)}</small></td><td>${role.permissions.map(e).join(" · ")}</td></tr>`,
      )
      .join("");
  }

  run(async () => {
    const context = await Support.init();
    if (page === "service-catalog") await catalog();
    if (page === "staff-accounts") await staff(context);
    if (page === "roles-permissions") await roles();
  });
})();
