// "Delete account" section on the profile page (html/account/profile.html).
//
// A customer reads the warning, presses "Delete my account", and a pop-up asks for the account
// password. The server verifies it, deactivates the account, and ends the current session.
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

  // A redirect (for example to login after session expiry) is not a successful deletion.
  async function deleteAccount(passwordValue) {
    const tokenResponse = await fetch("/api/auth/csrf", { headers: { Accept: "application/json" } });
    if (!tokenResponse.ok || tokenResponse.redirected) throw new Error("Unable to verify this action. Sign in again and retry.");
    const csrf = await tokenResponse.json();
    const response = await fetch("/api/account/profile", {
      method: "DELETE",
      headers: { "Content-Type": "application/json", Accept: "application/json", [csrf.headerName]: csrf.token },
      body: JSON.stringify({ currentPassword: passwordValue }),
    });
    if (response.redirected) throw new Error("Your session expired or this action was refused. Sign in again and retry.");
    const result = await response.json().catch(() => null);
    if (!response.ok || !result) throw new Error(result?.message || "We could not deactivate your account. Try again.");
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
      location.href = "/html/auth/login.html?logout=true";
    } catch (failure) {
      showDialogError(failure.message || "We could not delete your account. Try again.");
      if (!dialog.open) showPageError(failure.message || "We could not deactivate your account. Try again.");
    } finally {
      confirmButton.disabled = false;
    }
  });
});
