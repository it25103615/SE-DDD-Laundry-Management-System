// Saved addresses on the profile page (html/account/profile.html).
//
// Lets a customer see their saved addresses, add one, edit one, choose which is the default
// and delete one. Everything goes through /api/account/addresses, which works out who the
// customer is from the login session, so no customer ID is ever sent from this page.
//
// The rules about deleting are also enforced by the server (AddressServiceImpl):
//   - a customer can never delete their only address;
//   - the default address cannot be deleted while it is still the default.
// This page handles the second rule for the customer: it asks which address should become the
// default, makes that change first, and only then deletes the old default.
document.addEventListener("DOMContentLoaded", async () => {
  const section = document.getElementById("addresses-section");
  if (!section) return;

  const API = "/api/account/addresses";
  const $ = (id) => document.getElementById(id);
  const list = $("address-list");
  const message = $("address-message");
  const form = $("address-form");
  const addButton = $("add-address-button");
  const defaultBox = $("address-default");

  let csrf; // token Spring Security needs on every request that changes something
  let addresses = []; // the customer's addresses as last loaded from the server
  let editingID = null; // addressID being edited, or null while adding a new address

  // Shows a green confirmation or a red error at the top of the section.
  function show(text, isError = false) {
    message.textContent = text;
    message.className = "profile-message" + (isError ? " error" : "");
    message.hidden = false;
  }

  // Sends one request to the address API and returns the parsed JSON (null for "no content").
  // Throws an Error carrying the server's own message when the request is refused.
  async function request(path, method = "GET", body) {
    const headers = { Accept: "application/json" };
    if (method !== "GET") headers[csrf.headerName] = csrf.token;
    if (body) headers["Content-Type"] = "application/json";
    const response = await fetch(API + path, {
      method,
      headers,
      credentials: "same-origin",
      body: body ? JSON.stringify(body) : undefined,
    });
    // A session that has expired is redirected to the login page.
    if (response.redirected) {
      location.href = "/html/auth/login.html";
      throw new Error("Please sign in again.");
    }
    if (response.status === 204) return null;
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || "Something went wrong. Please try again.");
    return result;
  }

  // "18 Temple Road, Colombo 05, Western" - skips any part that was left empty.
  const addressLine = (address) =>
    [address.street, address.city, address.state]
      .map((part) => (part || "").trim())
      .filter(Boolean)
      .join(", ");

  // Small helper for building elements with text, so customer-typed text is never run as HTML.
  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  // Redraws the list from the "addresses" array.
  function render() {
    list.replaceChildren();
    if (!addresses.length) {
      list.append(element("p", "muted", "You have no saved addresses yet. Add one so we know where to collect your laundry."));
      return;
    }
    addresses.forEach((address) => {
      const row = element("article", "address-item" + (address.isDefault ? " is-default" : ""));

      const details = element("div");
      const title = element("h3", "", address.nickname || "Saved address");
      if (address.isDefault) title.append(element("span", "status", "DEFAULT"));
      details.append(title, element("p", "", addressLine(address)));
      if (address.deliveryInstructions) {
        details.append(element("p", "muted small", "Instructions: " + address.deliveryInstructions));
      }

      const buttons = element("div", "address-buttons");
      // The default address has no "Make default" button; it already is.
      if (!address.isDefault) {
        const makeDefault = element("button", "custom_button custom_button_nobg", "Make default");
        makeDefault.type = "button";
        makeDefault.addEventListener("click", () => setDefault(address));
        buttons.append(makeDefault);
      }
      const edit = element("button", "custom_button custom_button_nobg", "Edit");
      edit.type = "button";
      edit.addEventListener("click", () => openForm(address));
      const remove = element("button", "custom_button custom_button_nobg address-delete", "Delete");
      remove.type = "button";
      remove.addEventListener("click", () => deleteAddress(address));
      buttons.append(edit, remove);

      row.append(details, buttons);
      list.append(row);
    });
  }

  // Fetches the customer's addresses again and redraws the list.
  async function load() {
    addresses = await request("");
    render();
  }

  // ---- Add / edit form ----

  // Opens the form empty (to add) or filled with an existing address (to edit).
  function openForm(address) {
    editingID = address ? address.addressID : null;
    $("address-form-title").textContent = address ? `Edit "${address.nickname || "address"}"` : "Add a new address";
    $("address-nickname").value = address?.nickname || "";
    $("address-street").value = address?.street || "";
    $("address-city").value = address?.city || "";
    $("address-state").value = address?.state || "";
    $("address-instructions").value = address?.deliveryInstructions || "";
    // The default address stays the default while it is being edited: the box is ticked and
    // locked. The default only moves by making a different address the default.
    const isDefault = Boolean(address?.isDefault);
    defaultBox.checked = isDefault;
    defaultBox.disabled = isDefault;
    $("address-default-label").textContent = isDefault
      ? "This is your default address"
      : "Make this my default address";
    form.hidden = false;
    $("address-nickname").focus();
    form.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function closeForm() {
    form.hidden = true;
    form.reset();
    editingID = null;
  }

  addButton.addEventListener("click", () => openForm(null));
  $("address-cancel-button").addEventListener("click", closeForm);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const saveButton = $("address-save-button");
    saveButton.disabled = true;
    const body = {
      nickname: $("address-nickname").value.trim(),
      street: $("address-street").value.trim(),
      city: $("address-city").value.trim(),
      state: $("address-state").value.trim(),
      deliveryInstructions: $("address-instructions").value.trim() || null,
      isDefault: defaultBox.checked,
    };
    try {
      // PUT replaces an existing address; POST creates a new one.
      if (editingID) await request("/" + editingID, "PUT", body);
      else await request("", "POST", body);
      const wasEditing = Boolean(editingID);
      closeForm();
      await load();
      show(wasEditing ? "Address updated." : "Address added.");
    } catch (error) {
      show(error.message, true);
    } finally {
      saveButton.disabled = false;
    }
  });

  // ---- Changing the default ----

  async function setDefault(address) {
    try {
      await request(`/${address.addressID}/default`, "PATCH");
      await load();
      show(`"${address.nickname}" is now your default address.`);
    } catch (error) {
      show(error.message, true);
    }
  }

  // ---- Deleting ----

  // Opens the pop-up and waits for the customer's answer.
  //   title, text    what to ask
  //   confirmLabel   text of the main button; leave out for a notice with only a close button
  //   cancelLabel    text of the close button (default "Cancel")
  //   choices        optional list of addresses to pick one from (radio buttons)
  // Resolves to null when the customer cancels, otherwise to { choice } where choice is the
  // addressID picked (or undefined when there was nothing to pick).
  function ask({ title, text, confirmLabel, cancelLabel = "Cancel", choices = [] }) {
    const dialog = $("address-dialog");
    const confirm = $("address-dialog-confirm");
    const cancel = $("address-dialog-cancel");
    const choiceBox = $("address-dialog-choices");
    $("address-dialog-title").textContent = title;
    $("address-dialog-message").textContent = text;
    confirm.textContent = confirmLabel || "";
    confirm.hidden = !confirmLabel;
    cancel.textContent = cancelLabel;

    choiceBox.replaceChildren();
    choices.forEach((address, index) => {
      const row = element("label", "radio_row");
      const radio = document.createElement("input");
      radio.type = "radio";
      radio.name = "new-default-address";
      radio.value = String(address.addressID);
      radio.checked = index === 0; // the first one starts selected so there is always an answer
      const label = element("span");
      label.append(element("strong", "", address.nickname || "Saved address"), document.createElement("br"), element("span", "muted", addressLine(address)));
      row.append(radio, label);
      choiceBox.append(row);
    });

    return new Promise((resolve) => {
      // Whichever way the pop-up closes (a button or the Esc key), answer exactly once.
      const finish = (answer) => {
        confirm.onclick = cancel.onclick = dialog.onclose = null;
        if (dialog.open) dialog.close();
        resolve(answer);
      };
      confirm.onclick = () => {
        const picked = choiceBox.querySelector("input:checked");
        finish({ choice: picked ? Number(picked.value) : undefined });
      };
      cancel.onclick = () => finish(null);
      dialog.onclose = () => finish(null);
      dialog.showModal();
    });
  }

  async function deleteAddress(address) {
    const name = `"${address.nickname || "this address"}"`;
    const others = addresses.filter((other) => other.addressID !== address.addressID);

    // Rule 1: the last address can never be deleted.
    if (!others.length) {
      await ask({
        title: "This is your only address",
        text: `${name} is the only address saved on your account, so it cannot be deleted. Add another address first, then delete this one.`,
        cancelLabel: "OK",
      });
      return;
    }

    let newDefault = null;
    if (address.isDefault) {
      // Rule 2: the default cannot be deleted until another address takes its place.
      if (others.length === 1) {
        // Only one address would be left, so it has to become the default. Ask if that is okay.
        const only = others[0];
        const answer = await ask({
          title: "Delete your default address?",
          text: `${name} is your default address. If you delete it, "${only.nickname}" (${addressLine(only)}) will be the only address left and will become your new default. Is that okay?`,
          confirmLabel: `Yes, make "${only.nickname}" my default and delete`,
        });
        if (!answer) return;
        newDefault = only;
      } else {
        // Several addresses would be left, so the customer picks which one becomes the default.
        const answer = await ask({
          title: "Choose a new default address first",
          text: `${name} is your default address, so it cannot be deleted yet. Pick the address that should become your default, or cancel and change your default yourself.`,
          confirmLabel: "Set as default and delete",
          choices: others,
        });
        if (!answer) return;
        newDefault = others.find((other) => other.addressID === answer.choice);
      }
    } else {
      const answer = await ask({
        title: "Delete this address?",
        text: `${name} (${addressLine(address)}) will be removed from your saved addresses. Orders you have already placed keep their address.`,
        confirmLabel: "Delete address",
      });
      if (!answer) return;
    }

    try {
      // The default is moved first; the server refuses to delete an address that is still the default.
      if (newDefault) await request(`/${newDefault.addressID}/default`, "PATCH");
      await request("/" + address.addressID, "DELETE");
      // If the deleted address was open in the form, close the form.
      if (editingID === address.addressID) closeForm();
      await load();
      show(newDefault
        ? `${name} was deleted. "${newDefault.nickname}" is now your default address.`
        : `${name} was deleted.`);
    } catch (error) {
      // Reload so the list shows what actually happened (the default may already have moved).
      await load().catch(() => {});
      show(error.message, true);
    }
  }

  // ---- Start ----

  try {
    // Staff, riders and managers use this profile page too, but only customers have addresses.
    const profile = await fetch("/api/account/profile", { headers: { Accept: "application/json" }, credentials: "same-origin" })
      .then((response) => (response.ok && !response.redirected ? response.json() : null));
    if (!profile || profile.role !== "CUSTOMER") return;
    section.hidden = false;
    csrf = await fetch("/api/auth/csrf").then((response) => response.json());
    await load();
  } catch (error) {
    list.replaceChildren(element("p", "muted", "Your addresses could not be loaded."));
    show(error.message, true);
  }
});
