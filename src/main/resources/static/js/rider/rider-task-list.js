(function () {
  var modal = document.getElementById("task_modal");
  var modalBox = document.getElementById("task_modal_box");
  if (!modal || !modalBox) return;

  function closeModal() {
    modal.hidden = true;
    modalBox.querySelectorAll(".dynamic_content").forEach(function (n) {
      n.remove();
    });
  }
  modal.addEventListener("click", function (e) {
    if (e.target === modal) closeModal();
  });
  modalBox
    .querySelector("[data-modal-close]")
    .addEventListener("click", closeModal);
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !modal.hidden) closeModal();
  });

  function classify(text) {
    var t = text.toLowerCase();
    if (
      /delivered|paid|active|complete|resolved|ready|excellent|success/.test(t)
    )
      return "status_success";
    if (/error|failed|cancel|damage|danger/.test(t)) return "status_error";
    if (/pending|warning|today|priority|await|due|review/.test(t))
      return "status_warning";
    return "status_info";
  }
  function setStatus(el, text) {
    el.textContent = text;
    el.classList.remove(
      "status_success",
      "status_warning",
      "status_error",
      "status_info",
    );
    el.classList.add(classify(text));
  }
  function typeLabel(type) {
    return type === "pickup" ? "Pickup" : "Delivery";
  }

  function renderContent(row, mode) {
    var type = row.dataset.type,
      cap = typeLabel(type);
    var header =
      cap +
      " Task | Order #" +
      row.dataset.order +
      " | Scheduled for " +
      row.dataset.time;
    var addressBlock =
      row.dataset.name +
      "<br>" +
      row.dataset.address +
      '<br><a class="link" href="tel:' +
      row.dataset.phone +
      '">' +
      row.dataset.phone +
      "</a>";
    var bags = row.dataset.bags || "1";
    var detailsBlock =
      "<strong>" +
      bags +
      " bag" +
      (bags === "1" ? "" : "s") +
      '</strong><br><span class="muted">' +
      (row.dataset.note || "") +
      "</span>";
    var actionsHtml;

    if (mode === "accept") {
      actionsHtml =
        '<div class="actions" style="margin-top:22px;flex-direction:column;align-items:stretch">' +
        '<button type="button" class="custom_button custom_button_bg" data-accept>Accept Assignment</button>' +
        '<button type="button" class="custom_button custom_button_border" data-modal-close style="margin-top:12px">Cancel</button>' +
        "</div>";
    } else {
      var statuses = [
        "Pending",
        "En route",
        "Picked up",
        "Delivered",
        "Failed delivery",
      ];
      var options = statuses
        .map(function (s) {
          return (
            '<option value="' +
            s +
            '"' +
            (s === row.dataset.status ? " selected" : "") +
            ">" +
            s +
            "</option>"
          );
        })
        .join("");
      actionsHtml =
        '<div class="field"><label>Status</label><select class="input" data-status-select>' +
        options +
        "</select></div>" +
        '<div class="field"><label>Notes (optional)</label><textarea class="input" data-notes-input>' +
        (row.dataset.workNotes || "") +
        "</textarea></div>" +
        '<button type="button" class="custom_button custom_button_border" data-cancel-assignment style="width:100%;color:#a33a3a;border-color:#a33a3a;margin:4px 0 16px">Cancel Assignment</button>' +
        '<div class="actions" style="justify-content:flex-end">' +
        '<button type="button" class="custom_button custom_button_border" data-modal-close>Close</button>' +
        '<button type="button" class="custom_button custom_button_bg" data-save>OK</button>' +
        "</div>";
    }

    return (
      '<div class="dynamic_content">' +
      '<div class="subtitle">' +
      cap.toUpperCase() +
      " TASK</div>" +
      "<h2>" +
      header +
      "</h2>" +
      '<div class="modal_grid">' +
      '<div class="table_wrap"><table><thead><tr><th>' +
      cap +
      " address</th><th>" +
      cap +
      " details</th></tr></thead>" +
      "<tbody><tr><td>" +
      addressBlock +
      "</td><td>" +
      detailsBlock +
      "</td></tr></tbody></table></div>" +
      "<div>" +
      actionsHtml +
      "</div>" +
      "</div>" +
      "</div>"
    );
  }

  function openModal(row, mode) {
    modalBox.querySelectorAll(".dynamic_content").forEach(function (n) {
      n.remove();
    });
    modalBox.insertAdjacentHTML("beforeend", renderContent(row, mode));
    modal.hidden = false;
    if (mode === "accept") {
      modalBox
        .querySelector("[data-accept]")
        .addEventListener("click", function () {
          acceptTask(row);
        });
    } else {
      modalBox
        .querySelector("[data-save]")
        .addEventListener("click", function () {
          saveWork(row);
        });
      modalBox
        .querySelector("[data-cancel-assignment]")
        .addEventListener("click", function () {
          cancelAssignment(row);
        });
    }
    modalBox.querySelectorAll("[data-modal-close]").forEach(function (btn) {
      btn.addEventListener("click", closeModal);
    });
  }

  function updateCount(section, singular, plural) {
    var rows = section.querySelectorAll("tbody tr[data-order]");
    var n = rows.length;
    var countEl = section.querySelector(".task_count");
    if (countEl) countEl.textContent = n + " " + (n === 1 ? singular : plural);
    var emptyRow = section.querySelector(".empty_row");
    if (emptyRow) emptyRow.style.display = n ? "none" : "";
  }

  function refreshCounts() {
    document.querySelectorAll(".pickup_section").forEach(function (s) {
      updateCount(s, "pickup", "pickups");
    });
    document.querySelectorAll(".delivery_section").forEach(function (s) {
      updateCount(s, "delivery", "deliveries");
    });
    var remaining = document.querySelectorAll(
      '.task_section[data-role="pool"] tbody tr[data-order]',
    ).length;
    var remainingText = document.getElementById("remaining_text");
    if (remainingText)
      remainingText.textContent =
        "Friday, 14 August · " +
        remaining +
        " task" +
        (remaining === 1 ? "" : "s") +
        " remaining";
  }

  function buildRow(sourceRow, role) {
    var tr = document.createElement("tr");
    Object.keys(sourceRow.dataset).forEach(function (key) {
      tr.dataset[key] = sourceRow.dataset[key];
    });
    tr.innerHTML =
      "<td>" +
      sourceRow.dataset.time +
      "</td><td>#" +
      sourceRow.dataset.order +
      "</td><td>" +
      sourceRow.dataset.address +
      '</td><td><span class="status"></span></td><td>' +
      (role === "mine"
        ? ""
        : '<button type="button" class="link">Accept</button>') +
      "</td>";
    setStatus(tr.querySelector(".status"), sourceRow.dataset.status);
    tr.addEventListener("click", function () {
      openModal(tr, role === "mine" ? "work" : "accept");
    });
    return tr;
  }

  function acceptTask(row) {
    var target = document.querySelector(
      '.task_section[data-role="mine"].' + row.dataset.type + "_section tbody",
    );
    row.dataset.status = "Pending";
    target.appendChild(buildRow(row, "mine"));
    row.remove();
    refreshCounts();
    closeModal();
  }

  function cancelAssignment(row) {
    var target = document.querySelector(
      '.task_section[data-role="pool"].' + row.dataset.type + "_section tbody",
    );
    row.dataset.status = "Pending";
    target.appendChild(buildRow(row, "pool"));
    row.remove();
    refreshCounts();
    closeModal();
  }

  function saveWork(row) {
    var status = modalBox.querySelector("[data-status-select]").value;
    var notes = modalBox.querySelector("[data-notes-input]").value;
    row.dataset.status = status;
    row.dataset.workNotes = notes;
    setStatus(row.querySelector(".status"), status);
    closeModal();
  }

  document
    .querySelectorAll('.task_section[data-role="pool"] tbody tr[data-order]')
    .forEach(function (row) {
      row.addEventListener("click", function () {
        openModal(row, "accept");
      });
    });

  refreshCounts();
})();
