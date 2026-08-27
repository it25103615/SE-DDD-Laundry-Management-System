(function () {
  function toast(t, d) {
    var o = document.querySelector(".connected_toast");
    if (o) o.remove();
    var b = document.createElement("div");
    b.className = "connected_toast";
    b.innerHTML = "✓ " + t + (d ? "<small>" + d + "</small>" : "");
    document.body.appendChild(b);
    setTimeout(function () {
      b.remove();
    }, 3200);
  }
  window.connectedToast = toast;
  document.querySelectorAll("[data-demo]").forEach(function (e) {
    e.addEventListener("click", function () {
      toast(e.dataset.demo, "The frontend demonstration has been updated.");
    });
  });
  document.querySelectorAll("form").forEach(function (f) {
    var a = f.getAttribute("action");
    if (!a || a === "#") {
      f.addEventListener("submit", function (e) {
        e.preventDefault();
        if (f.reportValidity())
          toast("Saved successfully", "Ready for backend integration.");
      });
    }
  });
  document.querySelectorAll('button[type="button"]').forEach(function (b) {
    if (
      b.dataset.demo ||
      b.id === "add-item" ||
      b.id === "edit-add-item" ||
      b.classList.contains("remove-item")
    )
      return;
    b.addEventListener("click", function () {
      if (b.textContent.indexOf("Deactivate") > -1) {
        if (confirm("Deactivate this demonstration account?"))
          toast("Account deactivated");
      } else toast(b.textContent.trim() || "Action completed");
    });
  });
  var q = new URLSearchParams(location.search);
  if (q.get("view") === "list") {
    var board = document.querySelector(".board_columns");
    if (board) board.classList.add("list_mode");
  }
  document.querySelectorAll("nav a").forEach(function (a) {
    try {
      if (new URL(a.href).pathname === location.pathname)
        a.setAttribute("aria-current", "page");
    } catch (e) {}
  });
})();
