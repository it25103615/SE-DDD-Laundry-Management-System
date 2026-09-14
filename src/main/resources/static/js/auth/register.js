document.addEventListener("DOMContentLoaded", async () => {
  const form = document.getElementById("register-form");
  const message = document.getElementById("register-message");
  const button = document.getElementById("register-button");
  let csrf;

  try {
    csrf = await fetch("/api/auth/csrf").then((response) => response.json());
  } catch {
    message.hidden = false;
    message.className = "alert alert_error form_message";
    message.textContent = "The server is unavailable. Start the application and try again.";
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity() || !csrf) return;
    button.disabled = true;
    message.hidden = true;
    const payload = {
      fullName: document.getElementById("name").value.trim(),
      phone: document.getElementById("phone").value.trim(),
      email: document.getElementById("email").value.trim(),
      password: document.getElementById("password").value,
      address: document.getElementById("address").value.trim(),
    };
    try {
      const response = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify(payload),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Registration failed. Check your details.");
      location.href = "login.html?registered=true";
    } catch (error) {
      message.hidden = false;
      message.className = "alert alert_error form_message";
      message.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });
});
