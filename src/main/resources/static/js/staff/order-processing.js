(function () {
  var stages = Array.from(document.querySelectorAll(".timeline-stage"));
  var checks = Array.from(document.querySelectorAll(".stage-check"));

  var paid = document.getElementById("payment-paid");
  var dispatch = document.getElementById("dispatch-button");
  var lock = document.getElementById("dispatch-lock");
  var status = document.getElementById("order-status");
  var bar = document.getElementById("progress-bar");
  var percent = document.getElementById("progress-percent");

  //count how many checkboxes are checked
  //calculate the percentage of orders that are amountDone
  //set progress bar width and text to that percentage
  //
  //for each stage at index i:
  //  clear completed/current/locked classes
  //  if checkbox[i] is checked:
  //    mark stage completed, show ✓, enable checkbox
  //  else if the index is the same as the number of elements marked as done:
  //    mark stage current, show its number, enable checkbox
  //  else:
  //    mark stage locked, show its number, disable checkbox
  //
  //operationalDone = all checkboxes checked
  //paymentDone = payment checkbox checked
  //
  //if operationalDone:
  //  status text = "READY FOR DISPATCH"
  //else:
  //  status text = label of the next incomplete stage (uppercased)
  //
  //dispatch button enabled only if operationalDone AND paymentDone
  //
  //set an alert box:
  //  if both amountDone: success "Dispatch unlocked"
  //  else if only processing amountDone: error "Waiting for payment"
  //  else: error "Dispatch locked" (message mentions whichever is missing)
  function update() {
    var amountDone = checks.filter(function (c) {
      return c.checked;
    }).length;

    var pct = Math.round((amountDone / checks.length) * 100);

    bar.style.width = pct + "%";
    percent.textContent = pct + "%";

    stages.forEach(function (stage, i) {
      var check = checks[i];
      var marker = stage.querySelector(".stage-marker");
      var note = stage.querySelector("em");

      stage.classList.remove("completed", "current", "locked");

      if (check.checked) {
        stage.classList.add("completed");

        marker.textContent = "✓";

        if (!note.textContent.startsWith("Completed")) {
          note.textContent = "Completed · just now";
        }
        check.disabled = false;
      } else if (i === amountDone) {
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

    var operationalDone = amountDone === checks.length;
    var paymentDone = paid.checked;

    if (operationalDone) {
      status.textContent = "READY FOR DISPATCH";
      status.className = "status status_success";
    } else {
      var next =
        stages[amountDone] && stages[amountDone].querySelector("strong");

      if (next) {
        status.textContent = next.textContent
          .replace(/^\d+\.\s*/, "")
          .toUpperCase();
      } else {
        status.textContent = "PROCESSING";
      }
    }

    dispatch.disabled = !(operationalDone && paymentDone);

    if (operationalDone && paymentDone) {
      lock.className = "alert alert_success";
      lock.innerHTML = `
        <strong>
          Dispatch unlocked
        </strong>
        <br>
        Processing is complete and payment is verified.
      `;
    } else if (operationalDone) {
      lock.className = "alert alert_error";
      lock.innerHTML = `
        <strong>
          Waiting for payment
        </strong>
        <br>
        All processing is complete, but dispatch remains locked until payment is verified.
      `;
    } else {
      lock.className = "alert alert_error";
      lock.innerHTML = `
        <strong>
          Dispatch locked
        </strong>
        <br>
        Complete all processing stages ${paymentDone ? "before dispatch." : "and verify the due payment."}
      `;
    }
  }

  //for each checkbox c at index i:
  //  on change:
  //    if c is now unchecked:
  //      uncheck all checkboxes after index i  // unchecking a stage cascades forward
  //
  //    refresh progress bar / stages / dispatch state
  //
  //    if toast system exists:
  //      show toast: "Stage completed" or "Stage reopened", with this stage's title
  checks.forEach(function (c, i) {
    c.addEventListener("change", function () {
      if (!c.checked) {
        checks.slice(i + 1).forEach(function (next) {
          next.checked = false;
        });
      }

      update();

      if (window.connectedToast) {
        connectedToast(
          c.checked ? "Stage completed" : "Stage reopened",
          stages[i].querySelector("strong").textContent,
        );
      }
    });
  });

  //on paid checkbox change
  //  update payment badge text: "PAID" or "PAYMENT DUE"
  //  update badge style: success if paid, error if not
  //
  //  refresh dispatch lock state
  //
  //  if toast system exists:
  //    show toast: "Payment verified" or "Payment marked unpaid"
  paid.addEventListener("change", function () {
    var badge = document.getElementById("payment-badge");

    badge.textContent = paid.checked ? "PAID" : "PAYMENT DUE";
    badge.className = paid.checked
      ? "status status_success"
      : "status status_error";

    update();

    if (window.connectedToast) {
      connectedToast(
        paid.checked ? "Payment verified" : "Payment marked unpaid",
        "Dispatch permission has been updated.",
      );
    }
  });

  //on clicking dispatch:
  //  set status text to "DISPATCHED"
  //  if toast system exists:
  //    show toast: "Order dispatched"
  //  disable dispatch button
  //  change its label to "Dispatched ✓"
  dispatch.addEventListener("click", function () {
    status.textContent = "DISPATCHED";

    if (window.connectedToast) {
      connectedToast(
        "Order dispatched",
        "The order is now ready for rider assignment.",
      );
    }

    dispatch.disabled = true;
    dispatch.textContent = "Dispatched ✓";
  });

  //on clicking save-notes:
  //  if toast system exists:
  //    show toast: "Notes saved"
  document.getElementById("save-notes").addEventListener("click", function () {
    if (window.connectedToast) {
      connectedToast(
        "Notes saved",
        "The processing note is visible to the next staff member.",
      );
    }
  });

  //for each item checkbox c:
  //  on change:
  //    count number of checked item checkboxes
  //    update "scope-count" badge text: "X OF Y CHECKED"
  //    badge style: success if all checked, else warning
  var itemChecks = Array.from(document.querySelectorAll(".item-check"));
  itemChecks.forEach(function (c) {
    c.addEventListener("change", function () {
      var count = itemChecks.filter(function (x) {
        return x.checked;
      }).length;
      var badge = document.getElementById("scope-count");

      badge.textContent = count + " OF " + itemChecks.length + " CHECKED";
      badge.className =
        count === itemChecks.length
          ? "status status_success"
          : "status status_warning";
    });
  });

  update();
})();
