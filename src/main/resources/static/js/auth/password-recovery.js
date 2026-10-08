(() => {
  const forgot = document.getElementById('forgot-password-form');
  const reset = document.getElementById('reset-password-form');
  const message = document.getElementById('recovery-message');
  const token = new URLSearchParams(location.hash.slice(1)).get('token');
  // Remove reset credentials from the address bar after reading them.
  if (reset && location.hash) history.replaceState(null, '', location.pathname);
  function show(text, error = false) {
    message.textContent = text;
    message.className = `alert ${error ? 'alert_error' : 'alert_success'} form_message visible`;
    message.hidden = false;
  }
  async function post(path, body) {
    const csrfResponse = await fetch('/api/auth/csrf', { headers: { Accept: 'application/json' } });
    if (!csrfResponse.ok) throw new Error('Could not connect. Please try again.');
    const csrf = await csrfResponse.json();
    const response = await fetch('/api/auth/' + path, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || data.detail || 'The request failed. Please try again.');
    return data;
  }
  if (forgot) forgot.addEventListener('submit', async event => {
    event.preventDefault();
    if (!forgot.reportValidity()) return;
    const button = event.submitter;
    button.disabled = true;
    try { const result = await post('forgot-password', { email: forgot.elements.email.value.trim() }); show(result.message); }
    catch (error) { show(error.message, true); }
    finally { button.disabled = false; }
  });
  if (reset) {
    const password = reset.elements.password;
    const confirmation = reset.elements.confirmPassword;
    const validate = () => {
      password.setCustomValidity(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,72}$/.test(password.value) ? '' : 'Use 8–72 characters with uppercase, lowercase, a number and a special character, without spaces.');
      confirmation.setCustomValidity(confirmation.value === password.value ? '' : 'Passwords do not match.');
    };
    password.addEventListener('input', validate);
    confirmation.addEventListener('input', validate);
    if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) {
      show('Open the reset link from your email or request a new link.', true);
      reset.querySelectorAll('input, button').forEach(control => { control.disabled = true; });
    }
    reset.addEventListener('submit', async event => {
      event.preventDefault(); validate();
      if (!token || !reset.reportValidity()) return;
      const button = event.submitter;
      button.disabled = true;
      try {
        const result = await post('reset-password', { token, password: password.value, confirmPassword: confirmation.value });
        reset.reset(); reset.hidden = true; show(result.message);
      } catch (error) { show(error.message, true); button.disabled = false; }
    });
  }
})();
