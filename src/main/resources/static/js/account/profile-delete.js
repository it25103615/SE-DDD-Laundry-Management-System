// "Delete account" section on the profile page (html/account/profile.html).
//
// A customer reads the warning, presses "Delete my account", and a pop-up asks for the account
// password. Pressing Confirm signs them out and sends them to the login page.
//
// This is a FRONT-END MOCK for now: the server has no delete-account endpoint, so no account is
// actually deleted and the same details still work at the next login. The one place that will
// need a real request later is deleteAccount() below.
document.addEventListener("DOMContentLoaded", async () => {
  const section = document.getElementById("delete-section");
  if (!section) return;

  const $ = (id) => document.getElementById(id);
  const dialog = $("delete-dialog");
  const form = $("delete-form");
  const password = $("delete-password");
  const error = $("delete-error");
  const confirmButton = $("delete-confirm-button");

  // Shows a red message at the top of the page (the same box the rest of the profile page uses).
  function showPageError(text) {
    const box = $("profile-message");
    if (!box) return;
    box.textContent = text;
    box.className = "profile-message error";
    box.hidden = false;
  }

  // Shows or clears the message inside the pop-up, under the password field.
  function showDialogError(text) {
    error.textContent = text || "";
    error.hidden = !text;
  }

  // Only customers see this section. Staff, riders, managers and owners have their accounts
  // managed by an administrator, so for them it stays hidden. The role comes from the same
  // endpoint the rest of the profile page uses; if it cannot be read, the section stays hidden.
  try {
    const response = await fetch("/api/account/profile", { headers: { Accept: "application/json" } });
    if (!response.ok || response.redirected) return;
    const profile = await response.json();
    if (!profile || profile.role !== "CUSTOMER") return;
  } catch (failure) {
    return;
  }
  section.hidden = false;

  // THE PLACE TO CHANGE LATER. Today this only resolves, because the server cannot delete an
  // account yet. When a delete endpoint exists, send the password to it here (with the CSRF
  // token, like the other requests on this page) and throw an Error with the server's message
  // if it is refused, for example when the password is wrong.
  async function deleteAccount(passwordValue) {
    return passwordValue;
  }

  // Signs the user out the same way the Log out button does: Spring Security needs the CSRF
  // token on POST /logout. A successful logout lands on the login page, which shows the
  // "You have been signed out." message because of ?logout=true.
  async function signOut() {
    const csrf = await fetch("/api/auth/csrf").then((response) => (response.ok ? response.json() : Promise.reject(new Error("Unable to verify this action."))));
    await fetch("/logout", { method: "POST", headers: { [csrf.headerName]: csrf.token } });
    location.href = "/html/auth/login.html?logout=true";
  }

  // Closes the pop-up and forgets what was typed, so the password never stays in the page.
  function closeDialog() {
    if (dialog.open) dialog.close();
    password.value = "";
    password.type = "password";
    showDialogError("");
    const toggle = form.querySelector("[data-toggle]");
    if (toggle) {
      toggle.textContent = "Show";
      toggle.setAttribute("aria-pressed", "false");
      toggle.setAttribute("aria-label", "Show delete password");
    }
  }

  $("delete-account-button").addEventListener("click", () => {
    closeDialog();
    dialog.showModal();
    password.focus();
  });

  $("delete-cancel-button").addEventListener("click", closeDialog);

  // Escape closes a <dialog> without firing our Cancel button, so clear the password here too.
  dialog.addEventListener("close", () => {
    password.value = "";
    showDialogError("");
  });

  // Clicking the dark area around the pop-up (the dialog element itself, not its contents) cancels.
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) closeDialog();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!password.value) {
      showDialogError("Enter your password to continue.");
      password.focus();
      return;
    }
    confirmButton.disabled = true;
    try {
      await deleteAccount(password.value);
      closeDialog();
      await signOut();
    } catch (failure) {
      showDialogError(failure.message || "We could not delete your account. Try again.");
      // If signing out failed after the pop-up closed, the page-level box tells the customer.
      if (!dialog.open) showPageError(failure.message || "We could not sign you out. Try again.");
    } finally {
      confirmButton.disabled = false;
    }
  });
});
