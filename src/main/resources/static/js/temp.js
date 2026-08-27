(function () {
  var stages = Array.from(document.querySelectorAll(".timeline-stage")),
    checks = Array.from(document.querySelectorAll(".stage-check")),
    paid = document.getElementById("payment-paid"),
    dispatch = document.getElementById("dispatch-button"),
    lock = document.getElementById("dispatch-lock"),
    status = document.getElementById("order-status"),
    bar = document.getElementById("progress-bar"),
    percent = document.getElementById("progress-percent");
  function update() {
    var done = checks.filter(function (c) {
      return c.checked;
    }).length;
    var pct = Math.round((done / checks.length) * 100);
    bar.style.width = pct + "%";
    percent.textContent = pct + "%";
    stages.forEach(function (stage, i) {
      var check = checks[i],
        marker = stage.querySelector(".stage-marker"),
        note = stage.querySelector("em");
      stage.classList.remove("completed", "current", "locked");
      if (check.checked) {
        stage.classList.add("completed");
        marker.textContent = "✓";
        if (!note.textContent.startsWith("Completed"))
          note.textContent = "Completed · just now";
        check.disabled = false;
      } else if (i === done) {
        stage.classList.add("current");
        marker.textContent = String(i + 1);
        note.textContent = "Current stage";
        check.disabled = false;
      } else {
        stage.classList.add("locked");
        marker.textContent = String(i + 1);
        note.textContent = "Waiting for previous stage";
        check.disabled = true;
      }
    });
    var operationalDone = done === checks.length;
    var paymentDone = paid.checked;
    if (operationalDone) {
      status.textContent = "READY FOR DISPATCH";
      status.className = "status status_success";
    } else {
      var next = stages[done] && stages[done].querySelector("strong");
      status.textContent = next
        ? next.textContent.replace(/^\d+\.\s*/, "").toUpperCase()
        : "PROCESSING";
    }
    dispatch.disabled = !(operationalDone && paymentDone);
    if (operationalDone && paymentDone) {
      lock.className = "alert alert_success";
      lock.innerHTML =
        "<strong>Dispatch unlocked</strong><br>Processing is complete and payment is verified.";
    } else if (operationalDone) {
      lock.className = "alert alert_error";
      lock.innerHTML =
        "<strong>Waiting for payment</strong><br>All processing is complete, but dispatch remains locked until payment is verified.";
    } else {
      lock.className = "alert alert_error";
      lock.innerHTML =
        "<strong>Dispatch locked</strong><br>Complete all processing stages" +
        (paymentDone ? " before dispatch." : " and verify the due payment.");
    }
  }
  checks.forEach(function (c, i) {
    c.addEventListener("change", function () {
      if (!c.checked) {
        checks.slice(i + 1).forEach(function (next) {
          next.checked = false;
        });
      }
      update();
      if (window.connectedToast)
        connectedToast(
          c.checked ? "Stage completed" : "Stage reopened",
          stages[i].querySelector("strong").textContent,
        );
    });
  });
  paid.addEventListener("change", function () {
    var badge = document.getElementById("payment-badge");
    badge.textContent = paid.checked ? "PAID" : "PAYMENT DUE";
    badge.className = paid.checked
      ? "status status_success"
      : "status status_error";
    update();
    if (window.connectedToast)
      connectedToast(
        paid.checked ? "Payment verified" : "Payment marked unpaid",
        "Dispatch permission has been updated.",
      );
  });
  dispatch.addEventListener("click", function () {
    status.textContent = "DISPATCHED";
    if (window.connectedToast)
      connectedToast(
        "Order dispatched",
        "The order is now ready for rider assignment.",
      );
    dispatch.disabled = true;
    dispatch.textContent = "Dispatched ✓";
  });
  document.getElementById("save-notes").addEventListener("click", function () {
    if (window.connectedToast)
      connectedToast(
        "Notes saved",
        "The processing note is visible to the next staff member.",
      );
  });
  var itemChecks = Array.from(document.querySelectorAll(".item-check"));
  itemChecks.forEach(function (c) {
    c.addEventListener("change", function () {
      var count = itemChecks.filter(function (x) {
          return x.checked;
        }).length,
        badge = document.getElementById("scope-count");
      badge.textContent = count + " OF " + itemChecks.length + " CHECKED";
      badge.className =
        count === itemChecks.length
          ? "status status_success"
          : "status status_warning";
    });
  });
  update();
})();
