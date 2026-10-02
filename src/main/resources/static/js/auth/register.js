document.addEventListener("DOMContentLoaded", async () => {
  const form = document.getElementById("register-form");
  const message = document.getElementById("register-message");
  const button = document.getElementById("register-button");
  const phone = document.getElementById("phone");
  const password = document.getElementById("password");
  const confirmation = document.getElementById("confirm-password");
  const strengthLabel = document.getElementById("strength-label");
  const strengthBar = document.querySelector("#strength-meter span");
  let csrf;

  phone.addEventListener("input", () => {
    phone.value = phone.value.replace(/\D/g, "").slice(0, 10);
    phone.setCustomValidity(phone.value.length === 10 ? "" : "Phone number must contain exactly 10 numbers.");
  });

  function validatePasswords() {
    confirmation.setCustomValidity(confirmation.value === password.value ? "" : "Passwords do not match.");
    const checks = [password.value.length >= 8, /[a-z]/.test(password.value), /[A-Z]/.test(password.value), /\d/.test(password.value), /[^A-Za-z0-9\s]/.test(password.value)];
    const score = checks.filter(Boolean).length;
    strengthBar.style.width = `${score * 20}%`;
    strengthLabel.textContent = !password.value ? "Not entered" : score < 3 ? "Weak" : score < 5 ? "Almost there" : "Strong";
  }
  password.addEventListener("input", validatePasswords);
  confirmation.addEventListener("input", validatePasswords);

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
      password: password.value,
      confirmPassword: confirmation.value,
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
