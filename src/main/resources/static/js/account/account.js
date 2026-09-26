(function () {
  const json = (form) => Object.fromEntries(new FormData(form).entries());
  const message = (text, error) => {
    const node = document.querySelector("[data-account-message]");
    if (node) { node.textContent = text; node.className = error ? "alert alert_error form_message visible" : "alert alert_success form_message visible"; }
    else if (window.connectedToast) window.connectedToast(text);
  };
  let csrf;
  async function csrfHeaders(method) {
    if (["GET", "HEAD", "OPTIONS"].includes((method || "GET").toUpperCase())) return {};
    if (!csrf) csrf = await fetch("/api/auth/csrf", { credentials: "same-origin" }).then(response => response.json());
    return { [csrf.headerName]: csrf.token };
  }
  async function request(url, options) {
    const headers = await csrfHeaders(options.method);
    const response = await fetch(url, { credentials: "same-origin", headers: { "Content-Type": "application/json", ...headers, ...(options.headers || {}) }, ...options });
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || "Request failed."); }
    return response.status === 204 ? null : response.json();
  }
  const loginError = new URLSearchParams(location.search).get("error");
  if (loginError) message("We could not log you in. Check your email and password.", true);

  const register = document.querySelector("#register-form");
  if (register) {
    const password = register.elements.password;
    const confirmation = register.elements.confirm_password;
    const strengthLabel = document.querySelector("#strength-label");
    const strengthBar = document.querySelector("#strength-meter span");
    const button = document.querySelector("#register-button");

    const updatePasswordFeedback = () => {
      const value = password.value;
      const checks = [
        value.length >= 8,
        /[a-z]/.test(value),
        /[A-Z]/.test(value),
        /\d/.test(value),
        /[^A-Za-z0-9\s]/.test(value)
      ];
      const score = checks.filter(Boolean).length;
      const levels = [
        { label: "Not entered", color: "#d7dce2" },
        { label: "Very weak", color: "#d64545" },
        { label: "Weak", color: "#e67e22" },
        { label: "Fair", color: "#d6a800" },
        { label: "Good", color: "#4f9d69" },
        { label: "Strong", color: "#16834b" }
      ];
      strengthBar.style.width = `${score * 20}%`;
      strengthBar.style.backgroundColor = levels[score].color;
      strengthLabel.textContent = levels[score].label;
      confirmation.setCustomValidity(!confirmation.value || confirmation.value === value ? "" : "Passwords do not match.");
    };
    password.addEventListener("input", updatePasswordFeedback);
    confirmation.addEventListener("input", updatePasswordFeedback);
    updatePasswordFeedback();

    register.addEventListener("submit", async (event) => {
      event.preventDefault();
      updatePasswordFeedback();
      if (!register.reportValidity()) return;
      const data = json(register); const names = data.name.trim().split(/\s+/);
      button.disabled = true;
      button.textContent = "Creating account…";
      try {
        await request("/api/auth/register", { method: "POST", body: JSON.stringify({ firstName: names.shift(), middleName: names.length > 1 ? names.shift() : null, lastName: names.join(" ") || "-", email: data.email, password: data.password, confirmPassword: data.confirm_password, phoneNumber: data.phone }) });
        message("Account created. Signing you in…", false);
        const login = document.createElement("form");
        login.method = "post";
        login.action = "/api/auth/login";
        for (const [name, value] of [["email", data.email], ["password", data.password]]) {
          const input = document.createElement("input"); input.type = "hidden"; input.name = name; input.value = value; login.appendChild(input);
        }
        document.body.appendChild(login);
        login.submit();
      } catch (error) {
        message(error.message, true);
        button.disabled = false;
        button.textContent = "Create account";
      }
    });
  }
  const forgot = document.querySelector("#forgot-password-form");
  if (forgot) forgot.addEventListener("submit", async (event) => { event.preventDefault(); try { await request("/api/auth/forgot-password", { method: "POST", body: JSON.stringify(json(forgot)) }); } catch (error) { message(error.message, true); } });
  const reset = document.querySelector("#reset-password-form");
  if (reset) reset.addEventListener("submit", async (event) => { event.preventDefault(); try { await request("/api/auth/reset-password", { method: "POST", body: JSON.stringify(json(reset)) }); } catch (error) { message(error.message, true); } });

  const profile = document.querySelector("#profile-form");
  if (profile) {
    request("/api/account/profile", { method: "GET" }).then(user => { profile.elements.name.value = [user.firstName, user.middleName, user.lastName].filter(Boolean).join(" "); profile.elements.phone.value = user.phoneNumber; profile.elements.email.value = user.email; }).catch(error => message(error.message, true));
    profile.addEventListener("submit", async (event) => { event.preventDefault(); const data = json(profile), names = data.name.trim().split(/\s+/); try { await request("/api/account/profile", { method: "PUT", body: JSON.stringify({ firstName: names.shift(), middleName: names.length > 1 ? names.shift() : null, lastName: names.join(" ") || "-", email: data.email, phoneNumber: data.phone }) }); message("Profile updated."); } catch (error) { message(error.message, true); } });
  }
  const password = document.querySelector("#password-form");
  if (password) password.addEventListener("submit", async (event) => { event.preventDefault(); const data = json(password); try { await request("/api/account/password", { method: "PUT", body: JSON.stringify({ currentPassword: data.current_password, newPassword: data.new_password, confirmPassword: data.confirm_password }) }); password.reset(); message("Password updated."); } catch (error) { message(error.message, true); } });
  const addressForm = document.querySelector("#add-address");
  if (addressForm) addressForm.addEventListener("submit", async (event) => { event.preventDefault(); const data = json(addressForm), id = addressForm.dataset.editId; try { await request(id ? `/api/account/addresses/${id}` : "/api/account/addresses", { method: id ? "PUT" : "POST", body: JSON.stringify({ nickname: data.label, street: data.street, city: data.city, state: data.province, deliveryInstructions: null, isDefault: false }) }); location.reload(); } catch (error) { message(error.message, true); } });
  const addressList = document.querySelector("#address-list");
  if (addressList) {
    const esc = value => String(value || "").replace(/[&<>\"]/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
    request("/api/account/addresses", { method: "GET" }).then(addresses => { addressList.innerHTML = addresses.map(address => `<article class="card" data-address='${JSON.stringify(address).replace(/'/g, "&#39;")}'>${address.isDefault ? '<span class="status">DEFAULT</span>' : ''}<h3>${esc(address.nickname)}</h3><p class="muted">${esc(address.street)}<br>${esc(address.city)}<br>${esc(address.state)}</p><div class="actions"><button class="link" type="button" data-address-edit="${address.addressID}">Edit</button>${address.isDefault ? '' : `<button class="link" type="button" data-address-default="${address.addressID}">Set as default</button>`}<button class="link danger" type="button" data-address-delete="${address.addressID}">Delete</button></div></article>`).join(""); }).catch(error => message(error.message, true));
    addressList.addEventListener("click", async event => { if (event.target.dataset.addressEdit && addressForm) { const address = JSON.parse(event.target.closest("article").dataset.address); addressForm.dataset.editId = address.addressID; addressForm.elements.label.value = address.nickname; addressForm.elements.street.value = address.street; addressForm.elements.city.value = address.city; addressForm.elements.province.value = address.state; addressForm.querySelector("h2").textContent = "Edit address"; addressForm.scrollIntoView(); return; } const id = event.target.dataset.addressDefault || event.target.dataset.addressDelete; if (!id) return; try { await request(event.target.dataset.addressDefault ? `/api/account/addresses/${id}/default` : `/api/account/addresses/${id}`, { method: event.target.dataset.addressDefault ? "PATCH" : "DELETE" }); location.reload(); } catch (error) { message(error.message, true); } });
  }
  const staffTable = document.querySelector("#staff-accounts tbody");
  if (staffTable) { const esc = value => String(value || "").replace(/[&<>\"]/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]); request("/api/admin/accounts", { method: "GET" }).then(users => { staffTable.innerHTML = users.map(user => `<tr><td>${esc(user.firstName)} ${esc(user.lastName)}</td><td>${esc(user.type)}</td><td>${esc(user.email)}<br>${esc(user.phoneNumber)}</td><td><span class="status">Not tracked</span></td><td>Not tracked</td><td></td></tr>`).join(""); }).catch(error => message(error.message, true)); }
  const staffForm = document.querySelector("#staff-account-form"), showStaffForm = document.querySelector("#show-staff-account-form");
  if (showStaffForm && staffForm) showStaffForm.addEventListener("click", () => { staffForm.hidden = !staffForm.hidden; });
  if (staffForm) staffForm.addEventListener("submit", async event => { event.preventDefault(); const data = json(staffForm), names = data.name.trim().split(/\s+/); try { await request("/api/admin/accounts", { method: "POST", body: JSON.stringify({ firstName: names.shift(), middleName: names.length > 1 ? names.shift() : null, lastName: names.join(" ") || "-", email: data.email, phoneNumber: data.phone, type: data.type, password: data.password }) }); location.reload(); } catch (error) { message(error.message, true); } });
})();
