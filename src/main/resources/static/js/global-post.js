/* Shared presentation helpers. Feature scripts report their own API outcomes. */
(function () {
  function toast(title, detail) {
    const previous = document.querySelector('.connected_toast');
    if (previous) previous.remove();
    const node = document.createElement('div');
    node.className = 'connected_toast';
    node.setAttribute('role', 'status');
    node.textContent = String(title || '');
    if (detail) {
      const description = document.createElement('small');
      description.textContent = String(detail);
      node.appendChild(description);
    }
    document.body.appendChild(node);
    setTimeout(() => node.remove(), 3200);
  }
  window.connectedToast = toast;
  if (new URLSearchParams(location.search).get('view') === 'list') {
    const board = document.querySelector('.board_columns');
    if (board) board.classList.add('list_mode');
  }
  document.querySelectorAll('nav a').forEach(link => {
    try {
      if (new URL(link.href).pathname === location.pathname) link.setAttribute('aria-current', 'page');
    } catch (_) {}
  });
})();
