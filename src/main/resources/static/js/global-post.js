(function () {
  //Define a function "toast" that takes a title (t) and optional description (d)
  //  Select an html tag that has the classname connected_toast
  //  If the selection comes back not null/empty, remove the element (only have one visible/shown at a time)
  //
  //  Create a new <div> element
  //  Give it the class "connected_toast"
  //  Set its content to a checkmark + the title
  //    If a description was given, also add it in smaller text below
  //  Add the toast div to the page (end of <body>)
  //  After 3.2 seconds, automatically remove the toast
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

  //Make the toast function callable from outside this file as window.connectedToast
  window.connectedToast = toast;

  //Select every element with a "data-demo" attribute
  //For each one found:
  //  Listen for a click
  //  When clicked, show a toast using the element's data-demo value as the title,
  //    with a fixed description explaining it's a frontend demo
  document.querySelectorAll("[data-demo]").forEach(function (e) {
    e.addEventListener("click", function () {
      toast(e.dataset.demo, "The frontend demonstration has been updated.");
    });
  });

  //Select every <form> element on the page
  //For each element found:
  //  Read its "action" attribute
  //  If there is no action, or the action is just "#" (i.e. it doesn't really submit anywhere):
  //    Listen for the form's submit event
  //    When submitted:
  //      Prevent the real page-reload/submit behavior
  //      Run the browser's built-in validation
  //      If validation passes, show a "Saved successfully" toast (pretend save)
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

  //Select every <button type="button"> on the page
  //For each one found:
  //  Do nothing if it:
  //    - already has a data-demo handler, OR
  //    - is the "add-item" button, OR
  //    - is the "edit-add-item" button, OR
  //    - has the "remove-item" class
  //
  //  Otherwise, listen for a click on it
  //  When clicked:
  //    If the button's text contains "Deactivate":
  //      Ask the user to confirm via a browser confirm popup
  //      If confirmed, show an "Account deactivated" toast
  //    Else:
  //      Show a toast using the button's own text as the title
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

  //Parse the current page's URL query string (anything after a ? in the URL) into a usable object
  var q = new URLSearchParams(location.search);

  //If the "view" query parameter equals "list":
  //  Find the element with class "board_columns"
  //  If it exists, add the "list_mode" class to switch its layout/styling to list view
  if (q.get("view") === "list") {
    var board = document.querySelector(".board_columns");
    if (board) board.classList.add("list_mode");
  }

  //Select every link inside a <nav> element
  //For each link found:
  //  Try to:
  //    Compare the link's URL path to the current page's URL path
  //    If they match, mark this link as the current page
  //  If something goes wrong, silently ignore it and move on
  document.querySelectorAll("nav a").forEach(function (a) {
    try {
      if (new URL(a.href).pathname === location.pathname)
        a.setAttribute("aria-current", "page");
    } catch (e) {}
  });
})();
