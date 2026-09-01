(function () {
  //Find and keep a reference to the element with id task_modal
  var modal = document.getElementById("task_modal");

  //Find and keep a reference to the element with id task_modal_box
  var modalBox = document.getElementById("task_modal_box");

  //If one of the element is not found (is null) then return/exit the function
  if (!modal || !modalBox) return;

  //define closeModal function:
  //  hide the modal
  //  find all elements inside modalBox with class "dynamic_content"
  //  for each one found:
  //    remove it from the HTML page
  function closeModal() {
    modal.hidden = true;
    modalBox.querySelectorAll(".dynamic_content").forEach(function (n) {
      n.remove();
    });
  }

  //listen for click events on modal:
  //  when a click happens:
  //    if the actual clicked element IS the modal itself (not something inside it):
  //      call closeModal()
  modal.addEventListener("click", function (e) {
    if (e.target === modal) closeModal();
  });

  //Select the part of the modal box that contains the element data-modal-close
  //  Add an event listner to it that listens to click events
  //    run closeModal when the event is triggered
  modalBox
    .querySelector("[data-modal-close]")
    .addEventListener("click", closeModal);

  //Add an event listner to the html page
  //  If the escape key is pressed and the modal is not hidden
  //    Then close the modal
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !modal.hidden) closeModal();
  });

  //Given the text passed into the function
  //  convert the text to all lowercase
  //  If it is one of the following statues:  delivered, paid, active, complete, resolved, ready, excellent, success
  //    Then return a string indicating success
  //  Else if it is one of the following statues: error, failed, cancel, damage, danger
  //    Then return a string indicating an error
  //  Else if it is one of the following statues: pending, warning, today, priority, await, due, review
  //    Then return a string indicating a warning
  //  Otherwise
  //    Return a string indicating information
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

  //set elements's (el) text to text
  //remove all status_* classes from the element
  //add the appropriate status class based on text in the element (via classify())
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

  //get type and capitalized label from row parameter's data attributes
  //build header string: "{Type} Task | Order #{order} | Scheduled for {time}"
  //build address block: name, address, phone (as clickable tel link)
  //build details block: bag count (singular/plural) + notes
  //if mode is "accept":
  //  build actions HTML: "Accept Assignment" button, "Cancel" button
  //else:
  //  build a dropdown of status options, marking row's current status as selected
  //  build actions HTML: status dropdown, notes textarea, Cancel Assignment button, Close/OK buttons
  //combine everything into one HTML string for the modal content block
  //return that HTML string
  function renderContent(row, mode) {
    var type = row.dataset.type;
    var cap = type.charAt(0).toUpperCase() + type.slice(1);

    var header = `${cap} Task | Order #${row.dataset.order} | Scheduled for ${row.dataset.time}`;

    var addressBlock = `
      ${row.dataset.name}
      <br>
      ${row.dataset.address}
      <br>
      <a class="link" href="tel:${row.dataset.phone}">
        ${row.dataset.phone}
      </a>
    `;

    var bags = row.dataset.bags || "1";

    var detailsBlock = `
      <strong>
        ${bags} bag${bags === "1" ? "" : "s"}
      </strong>
      <br>
      <span class="muted">
        ${row.dataset.note || ""}
      </span>
    `;

    var actionsHtml = "";

    if (mode === "accept") {
      actionsHtml = `
        <div class="actions" style="margin-top:22px;flex-direction:column;align-items:stretch">
          <button type="button" class="custom_button custom_button_bg" data-accept>
            Accept Assignment
          </button>
          <button 
            type="button" 
            class="custom_button custom_button_border" 
            data-modal-close 
            style="margin-top:12px"
          >
            Cancel
          </button>
        </div>
      `;
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
          return `
            <option value="${s}" ${s === row.dataset.status ? " selected" : ""}>
              ${s}
            </option>
          `;
        })
        .join("");

      actionsHtml = `
        <div class="field">
          <label>Status</label>
          <select class="input" data-status-select>
            ${options}
          </select>
        </div>
        <div class="field">
          <label>Notes (optional)</label>
          <textarea class="input" data-notes-input>
            ${row.dataset.workNotes || ""}
          </textarea>
        </div>
        <button 
          type="button" 
          class="custom_button custom_button_border" 
          data-cancel-assignment 
          style="width:100%;color:#a33a3a;border-color:#a33a3a;margin:4px 0 16px"
        >
          Cancel Assignment
        </button>
        <div class="actions" style="justify-content:flex-end">
          <button type="button" class="custom_button custom_button_border" data-modal-close>Close</button>
          <button type="button" class="custom_button custom_button_bg" data-save>OK</button>
        </div>
      `;
    }

    return `
      <div class="dynamic_content">
        <div class="subtitle">${cap.toUpperCase()} TASK</div>
        <h2>${header}</h2>
        <div class="modal_grid">
          <div class="table_wrap">
            <table>
              <thead>
                <tr>
                  <th>${cap} address</th>
                  <th>${cap} details</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>${addressBlock}</td>
                  <td>${detailsBlock}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div>${actionsHtml}</div>
        </div>
      </div>
    `;
  }

  //remove any existing dynamic content from modalBox (content that was rendered previously)
  //insert freshly rendered content into modalBox, passing mode through
  //show the modal
  //if mode is "accept":
  //  attach click handler to Accept Assignment button
  //else:
  //  attach click handler to Save button
  //  attach click handler to Cancel Assignment button
  //attach click handler(s) to Close button(s)
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

  //count rows with data-order inside section's tbody
  //if a count element exists, set its text to "N singular" or "N plural"
  //if an empty-row placeholder exists, show it only when count is 0
  function updateCount(section, singular, plural) {
    var rows = section.querySelectorAll("tbody tr[data-order]");
    var n = rows.length;

    var countEl = section.querySelector(".task_count");
    if (countEl) countEl.textContent = n + " " + (n === 1 ? singular : plural);

    var emptyRow = section.querySelector(".empty_row");
    if (emptyRow) emptyRow.style.display = n ? "none" : "";
  }

  //for each pickup section on the page: updateCount with "pickup"/"pickups"
  //for each delivery section on the page: updateCount with "delivery"/"deliveries"
  //if a pool section exists on the page:
  //  count rows in the pool section (unassigned tasks)
  //  if a remaining-text element exists, set its text to "Friday, 14 August · N task(s) remaining"
  function refreshCounts() {
    document.querySelectorAll(".pickup_section").forEach(function (s) {
      updateCount(s, "pickup", "pickups");
    });

    document.querySelectorAll(".delivery_section").forEach(function (s) {
      updateCount(s, "delivery", "deliveries");
    });

    var pool = document.querySelector('.task_section[data-role="pool"]');
    if (pool) {
      var remaining = pool.querySelectorAll("tbody tr[data-order]").length;
      var remainingText = document.getElementById("remaining_text");
      if (remainingText)
        remainingText.textContent = `Friday, 14 August · ${remaining} task${remaining === 1 ? "" : "s"} remaining`;
    }
  }

  //create a new table row element
  //copy all data-* attributes from sourceRow onto the new row
  //build row HTML: time, order number, address, empty status span, Accept button (only if role is "pool")
  //set the status badge text/class from sourceRow's saved status
  //attach click handler to the row: open modal in "work" mode if role is "mine", else "accept" mode
  //return the new row
  function buildRow(sourceRow, role) {
    var tr = document.createElement("tr");
    Object.keys(sourceRow.dataset).forEach(function (key) {
      tr.dataset[key] = sourceRow.dataset[key];
    });

    tr.innerHTML = `
      <td>
        ${sourceRow.dataset.time}
      </td><td>
        #${sourceRow.dataset.order}
      </td><td>
        ${sourceRow.dataset.address} 
      </td><td>
        <span class="status"></span>
      </td><td>
        ${role === "mine" ? "" : '<button type="button" class="link">Accept</button>'}
      </td>
    `;

    setStatus(tr.querySelector(".status"), sourceRow.dataset.status);

    tr.addEventListener("click", function () {
      openModal(tr, role === "mine" ? "work" : "accept");
    });

    return tr;
  }

  //find the "mine" table body matching row's task type (if none, bail out)
  //set row's status to "Pending"
  //build a new row for the "mine" table and append it
  //remove the original row from its current (pool) table
  //refresh counts
  //close the modal
  function acceptTask(row) {
    var target = document.querySelector(
      `.task_section[data-role="mine"].${row.dataset.type}_section tbody`,
    );
    if (!target) return;

    row.dataset.status = "Pending";

    target.appendChild(buildRow(row, "mine"));

    row.remove();
    refreshCounts();
    closeModal();
  }

  //find the pool table body matching row's task type
  //if a pool table exists on the page:
  //  set row's status to "Pending"
  //  build a new row with role "pool" and append it to the pool table
  //(if no pool table exists on the page, the row is just dropped)
  //remove the original row from its current (mine) table
  //refresh counts
  //close the modal
  //show a toast notification (if toast function available)
  function cancelAssignment(row) {
    var target = document.querySelector(
      `.task_section[data-role="pool"].${row.dataset.type}_section tbody`,
    );

    if (target) {
      row.dataset.status = "Pending";
      target.appendChild(buildRow(row, "pool"));
    }

    row.remove();
    refreshCounts();
    closeModal();
    toastFallback();
  }

  //if a global connectedToast function exists:
  //  call it with "Assignment cancelled" message
  function toastFallback() {
    if (typeof window.connectedToast === "function")
      window.connectedToast(
        "Assignment cancelled",
        "Sent back to the task list.",
      );
  }

  //read selected status and notes from modal inputs
  //store status and notes back onto row's data attributes
  //update row's visible status badge (setStatus)
  //close the modal
  function saveWork(row) {
    var status = modalBox.querySelector("[data-status-select]").value;
    var notes = modalBox.querySelector("[data-notes-input]").value;

    row.dataset.status = status;
    row.dataset.workNotes = notes;

    setStatus(row.querySelector(".status"), status);

    closeModal();
  }

  //select all elements matching:
  //  ".task_section[data-role='mine'] tbody tr[data-order]"
  //for each matching row:
  //  attach a click event listener to the row
  //  when clicked:
  //    call openModal(row, "work"), passing this row as the task to display
  document
    .querySelectorAll('.task_section[data-role="mine"] tbody tr[data-order]')
    .forEach(function (row) {
      row.addEventListener("click", function () {
        openModal(row, "work");
      });
    });

  //select all elements matching:
  //  '.task_section[data-role="pool"] tbody tr[data-order]'
  //for each matching row:
  //  attach a click event listener to the row
  //  when clicked:
  //    call openModal(row, "accept"), passing the clicked row as the task to display
  document
    .querySelectorAll('.task_section[data-role="pool"] tbody tr[data-order]')
    .forEach(function (row) {
      row.addEventListener("click", function () {
        openModal(row, "accept");
      });
    });

  //refresh counts
  refreshCounts();
})();
