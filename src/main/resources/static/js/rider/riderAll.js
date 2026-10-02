/*
 * RIDERALL.JS
 * Shared JavaScript for the rider dashboard and task-list pages.
 *
 * Detects which page is loaded, communicates with the Rider API,
 * renders rider tasks, handles status actions, and refreshes the UI.
 */

(() => {
    const API = "/api/rider";

    // ---------- Small helpers ----------

    /**
     * Converts API data into HTML-safe text so special characters are not interpreted as markup.
     * @param {*} value Any value; null/undefined become "".
     * @returns {string} The escaped string.
     */
    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    /**
     * Formats an API date/time for display as DD/MM/YY h:mm AM/PM. The original value is
     * not changed, only its appearance.
     * @param {string|null} value An ISO date-time string.
     * @returns {string} The formatted text, the original value if unparsable, or "—" if empty.
     */
    function formatTime(value) {
        if (!value) return "—";

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;

        const day = String(date.getDate()).padStart(2, "0");
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const year = String(date.getFullYear()).slice(-2);

        const time = date.toLocaleTimeString([], {
            hour: "numeric",
            minute: "2-digit",
            hour12: true
        });

        return `${day}/${month}/${year} ${time}`;
    }

    /**
     * Sets an element's text if it exists on the current page (dashboard counts etc.).
     * @param {string} id Element ID.
     * @param {*} value Text to show; null/undefined become 0.
     */
    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value ?? 0;
    }

    /**
     * Maps a status label to an existing CSS class so appearance stays consistent without
     * new CSS.
     * @param {string} status The status label.
     * @returns {string} One of status_error, status_warning, status_success, status_info.
     */
    function statusClass(status) {
        const s = String(status || "").toLowerCase();
        if (s.includes("failed") || s.includes("cancel")) return "status_error";
        if (s.includes("await")) return "status_warning";
        if (s.includes("completed") || s.includes("in shop")) return "status_success";
        return "status_info";
    }

    /**
     * Shows a temporary message. Uses the shared toast component when available; otherwise
     * creates a small notification and removes it after three seconds.
     * @param {string} message Main text.
     * @param {string} [detail] Secondary text.
     */
    function toast(message, detail = "") {
        if (typeof window.connectedToast === "function") {
            window.connectedToast(message, detail);
            return;
        }
        const old = document.querySelector(".rider-toast");
        if (old) old.remove();
        const el = document.createElement("div");
        el.className = "rider-toast";
        el.innerHTML = `<strong>${escapeHtml(message)}</strong>${
            detail ? `<small>${escapeHtml(detail)}</small>` : ""
        }`;
        document.body.appendChild(el);
        setTimeout(() => el.remove(), 3000);
    }

    /**
     * Sends a request to the Rider API and centralises response/error handling.
     *
     * Handles Spring redirects (login page or portal for a wrong role), 401 (back to login),
     * 403 (not a rider), other HTTP errors (extracts the backend message), and returns JSON.
     * @param {string} path Path after /api/rider.
     * @param {RequestInit} [options] Fetch options.
     * @returns {Promise<*>} Parsed JSON, or null for 204.
     * @throws {Error} With a user-readable message on any failure.
     */
    async function api(path, options = {}) {
        const response = await fetch(`${API}${path}`, {
            ...options,
            headers: { "Content-Type": "application/json", ...(options.headers || {}) }
        });
        // SecurityConfig blocks non-riders before the API runs, and Spring answers that with a
        // redirect (to the login page, or the portal for a wrong role) rather than JSON.
        if (response.redirected) {
            if (response.url.includes("/html/auth/login.html")) {
                window.location.href = "/html/auth/login.html";
                throw new Error("Please sign in with a rider account.");
            }
            throw new Error("This page is for rider accounts only.");
        }
        // The rider API only serves signed-in rider accounts: send a signed-out user to the
        // login page, and explain a 403 (signed in, but not as a rider).
        if (response.status === 401) {
            window.location.href = "/html/auth/login.html";
            throw new Error("Please sign in with a rider account.");
        }
        if (response.status === 403) {
            throw new Error("This page is for rider accounts only.");
        }
        if (!response.ok) {
            let message = `Request failed (${response.status})`;
            try {
                const body = await response.json();
                message = body.detail || body.message || body.error || message;
            } catch (_) {
                //response had no JSON body
            }
            throw new Error(message);
        }
        return response.status === 204 ? null : response.json();
    }

    // ---------- Modal (shared by both pages) ----------
    // WHY: Both HTML files use the same overlay/id contract.

    const modal = document.getElementById("task_modal");
    const modalContent = document.getElementById("modal_content");

    /**
     * Opens the shared task modal. Reused for available tasks, My Work details and the
     * failure-note form.
     * @param {{orderID: (number|string)}} task Provides the "Order #" title; an empty orderID
     *        hides the title (used by the failure form).
     * @param {string} title Small subtitle above the heading.
     * @param {string} bodyHtml Main content (must already be escaped).
     * @param {string} [buttonsHtml] Action buttons.
     */
    function openModal(task, title, bodyHtml, buttonsHtml = "") {
        if (!modal || !modalContent) return;
        modalContent.innerHTML = `
            <div class="dynamic_content">
                <div class="subtitle">${escapeHtml(title)}</div>
                ${task && task.orderID !== undefined && task.orderID !== ""
                    ? `<h2 id="modal_title">Order #${escapeHtml(task.orderID)}</h2>`
                    : ""}
                ${bodyHtml}
                ${buttonsHtml
                    ? `<div class="actions" style="margin-top:22px;justify-content:flex-end;gap:10px">${buttonsHtml}</div>`
                    : ""}
            </div>`;
        modal.hidden = false;
    }

    /**
     * Closes the modal and clears its content so buttons and details from the previous task
     * do not remain.
     */
    function closeModal() {
        if (!modal || !modalContent) return;
        modal.hidden = true;
        modalContent.innerHTML = "";
    }

    // Enables the shared modal's closing behaviour.
    // The modal closes when the backdrop, a Close button, or the Escape key
    // is used, so each individual modal does not need separate close logic.

    if (modal) {
        modal.addEventListener("click", (event) => {
            if (event.target === modal) closeModal();
        });
        modal.querySelectorAll("[data-modal-close]").forEach((btn) =>
            btn.addEventListener("click", closeModal)
        );
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && !modal.hidden) closeModal();
        });
    }

    /**
     * Builds the task-details table (customer, address, phone, status). Pickup tasks also
     * show the scheduled pickup time; deliveries do not have one.
     * @param {Object} task A RiderTaskDTO.
     * @returns {string} HTML.
     */
    function detailBody(task) {
        const schedule = task.type === "pickup"
            ? `<p><strong>Pickup scheduled</strong><br><span class="muted">${escapeHtml(
                formatTime(task.pickupScheduled)
              )}</span></p>`
            : "";
        return `
            <div class="modal_grid">
                <div class="table_wrap">
                    <table>
                        <tbody>
                            <tr><th>Customer</th><td>${escapeHtml(task.customerName)}</td></tr>
                            <tr><th>Address</th><td>${escapeHtml(task.address)}</td></tr>
                            <tr><th>Phone</th><td><a class="link" href="tel:${escapeHtml(
                                task.phoneNumber
                            )}">${escapeHtml(task.phoneNumber || "\u2014")}</a></td></tr>
                            <tr><th>Status</th><td><span class="status ${statusClass(
                                task.status
                            )}">${escapeHtml(task.status)}</span></td></tr>
                        </tbody>
                    </table>
                </div>
                <div>${schedule}</div>
            </div>`;
    }

    /**
     * Binds every .rider-accept button inside a container to acceptTask().
     * stopPropagation() stops the click from also opening the row's detail popup.
     * @param {HTMLElement} container Row or modal element.
     * @param {Object} task The task to accept.
     */
    function wireActionButtons(container, task) {
        container.querySelectorAll(".rider-accept").forEach((btn) =>
            btn.addEventListener("click", (e) => {
                e.stopPropagation();
                acceptTask(task.deliverID);
            })
        );
    }

    /**
     * Returns the actions a rider may take for a task's type and status.
     *
     * pickup@4: picked-up / pickup-failed / cancel. pickup@6: pickup-delivered.
     * delivery@13: delivery-delivered / delivery-failed / cancel. Anything else: none.
     * Single source of truth for the row dropdown and the popup buttons, so the UI never
     * offers an action the backend would reject.
     * @param {Object} task A RiderTaskDTO.
     * @returns {Array<[string, string]>} [endpointSuffix, buttonLabel] pairs.
     */
    function getStatusActions(task) {
        if (task.type === "pickup" && task.statusID === 4) {
            return [
                ["picked-up", "Picked Up"],
                ["pickup-failed", "Pickup Failed"],
                ["cancel", "Cancel Assignment"]
            ];
        }

        if (task.type === "pickup" && task.statusID === 6) {
            return [
                ["pickup-delivered", "Delivered"]
            ];
        }

        if (task.type === "delivery" && task.statusID === 13) {
            return [
                ["delivery-delivered", "Delivered"],
                ["delivery-failed", "Delivery Failed"],
                ["cancel", "Cancel Assignment"]
            ];
        }

        return [];
    }

    /**
     * Builds the My Work popup buttons from getStatusActions(). Failure and cancel actions get
     * the bordered style.
     * @param {Object} task A RiderTaskDTO.
     * @returns {string} HTML.
     */
    function popupStatusButtons(task) {
        return getStatusActions(task)
            .map(([action, label]) => `
            <button
                type="button"
                class="custom_button ${
                action.includes("failed") || action === "cancel"
                    ? "custom_button_border"
                    : "custom_button_bg"
            } rider-popup-action"
                data-action="${action}">
                ${label}
            </button>
        `)
            .join("");
    }

    /**
     * Opens the details popup. Available tasks get an Accept button; My Work tasks get only the
     * status actions valid for their state. Popup action buttons call handleStatusChange(),
     * the same handler as the row dropdown, so both controls behave identically.
     * @param {Object} task A RiderTaskDTO.
     * @param {"available"|"mine"} mode Which list the task came from.
     */
    function openTaskDetails(task, mode) {
        const closeButton = `
        <button
            type="button"
            class="custom_button custom_button_border"
            data-modal-close>
            Close
        </button>
    `;

        let buttons;

        if (mode === "available") {
            buttons = `
            ${closeButton}

            <button
                type="button"
                class="custom_button custom_button_bg rider-accept"
                data-id="${task.deliverID}">
                Accept Assignment
            </button>
        `;
        } else {
            const statusButtons = popupStatusButtons(task);

            buttons = `
            <div
                style="
                    display:flex;
                    flex-direction:column;
                    align-items:flex-end;
                    gap:10px;
                    width:100%;
                ">
                ${statusButtons}
            </div>

            ${closeButton}
        `;
        }

        // Connects the status-action buttons displayed in the My Work popup.
    // These use the same handler as the row status dropdown to keep both controls
    // consistent and avoid duplicating action logic.

        openModal(
            task,
            `${task.type === "pickup" ? "PICKUP" : "DELIVERY"} TASK`,
            detailBody(task),
            buttons
        );

        modalContent.querySelectorAll("[data-modal-close]").forEach((btn) =>
            btn.addEventListener("click", closeModal)
        );

        wireActionButtons(modalContent, task);

        // My Work popup action buttons
        modalContent.querySelectorAll(".rider-popup-action").forEach((btn) => {
            btn.addEventListener("click", (event) => {
                event.stopPropagation();

                const action = btn.dataset.action;

                // Use the same action handling as the My Work status dropdown.
                handleStatusChange(task, action);
            });
        });
    }

    /**
     * Opens the failure-note form. A note is required before the *-failed endpoint is called;
     * the lists are refreshed after the backend records the failure.
     * @param {number} deliverId The task ID.
     * @param {"pickup"|"delivery"} type Which failure endpoint to call.
     */
    function openFailureModal(deliverId, type) {
        const label = type === "pickup" ? "Pickup Failed" : "Delivery Failed";
        openModal(
            { orderID: "" },
            label,
            `<div class="field">
                <label for="failure_note">Reason / note</label>
                <textarea class="input" id="failure_note" maxlength="250" required
                    placeholder="Enter the reason for the failed attempt"></textarea>
            </div>`,
            `<button type="button" class="custom_button custom_button_border" data-modal-close>Cancel</button>
             <button type="button" class="custom_button custom_button_bg" id="confirm_failure">Submit Failure</button>`
        );

        modalContent.querySelectorAll("[data-modal-close]").forEach((btn) =>
            btn.addEventListener("click", closeModal)
        );

        document.getElementById("confirm_failure")?.addEventListener("click", async () => {
            const note = document.getElementById("failure_note").value.trim();
            if (!note) {
                toast("A failure note is required");
                return;
            }
            try {
                await api(`/tasks/${deliverId}/${type}-failed`, {
                    method: "PUT",
                    body: JSON.stringify({ note })
                });
                closeModal();
                toast(
                    `${type === "pickup" ? "Pickup" : "Delivery"} returned to task list`,
                    "Failure note saved."
                );
                await refreshAll();
            } catch (error) {
                toast("Failure could not be recorded", error.message);
            }
        });
    }

    // ---------- Row rendering ----------

    /**
     * Updates a section's count label, choosing singular or plural automatically.
     * @param {Element|null} section Element containing [data-count].
     * @param {number} count Number of tasks.
     * @param {string} singular Label for 1.
     * @param {string} plural Label otherwise.
     */
    function setCount(section, count, singular, plural) {
        const el = section?.querySelector("[data-count]");
        if (el) el.textContent = `${count} ${count === 1 ? singular : plural}`;
    }

    /**
     * Builds the status control for My Work rows: a dropdown showing the current status plus
     * only the valid next actions, or a plain badge if there are none.
     * @param {Object} task A RiderTaskDTO.
     * @returns {string} HTML.
     */
    function statusControl(task) {
        const options = getStatusActions(task);

        if (!options.length) {
            return `
            <span class="status ${statusClass(task.status)}">
                ${escapeHtml(task.status)}
            </span>
        `;
        }

        return `
        <select
            class="status ${statusClass(task.status)} status-control"
            aria-label="Change status"
        >
            <option value="" selected>
                ${escapeHtml(task.status)}
            </option>
            ${options.map(([value, label]) =>
            `<option value="${value}">${label}</option>`
        ).join("")}
        </select>
    `;
    }

    /**
     * Renders table rows for available or My Work tasks and wires row click, Accept and the
     * status dropdown. Columns follow the UI design: pickups show a TIME column, deliveries do
     * not. An empty-state row is shown when there are no tasks.
     * @param {HTMLElement|null} tbody Target table body.
     * @param {Object[]} tasks RiderTaskDTO list.
     * @param {"available"|"mine"} mode Which table is being rendered.
     */
    function renderRows(tbody, tasks, mode) {
        if (!tbody) return;

        if (!tasks.length) {
            const cols = tbody.closest("table").querySelectorAll("thead th").length;

            tbody.innerHTML = `<tr class="empty_row">
            <td colspan="${cols}" class="muted small"
                style="text-align:center;padding:26px 12px">
                ${mode === "mine"
                ? "No active assignments."
                : "No confirmed tasks available."}
            </td>
        </tr>`;

            return;
        }

        tbody.innerHTML = tasks.map((task) => {

            // Available tasks:
            // TIME | ORDER | ADDRESS | STATUS | ACCEPT
            if (mode === "available") {
                const time = task.type === "pickup"
                    ? `<td>${escapeHtml(formatTime(task.pickupScheduled))}</td>`
                    : "";

                return `<tr data-deliver-id="${task.deliverID}" data-type="${task.type}">
                ${time}
                <td>#${escapeHtml(task.orderID)}</td>
                <td>${escapeHtml(task.address)}</td>
                <td>
                    <span class="status ${statusClass(task.status)}">
                        ${escapeHtml(task.status)}
                    </span>
                </td>
                <td>
                    <button type="button"
                            class="link rider-accept"
                            data-id="${task.deliverID}">
                        Accept
                    </button>
                </td>
            </tr>`;
            }

            // My Work:
            // Pickup:   TIME | ORDER | ADDRESS | STATUS
            // Delivery: ORDER | ADDRESS | STATUS
            const time = task.type === "pickup"
                ? `<td>${escapeHtml(formatTime(task.pickupScheduled))}</td>`
                : "";

            return `<tr data-deliver-id="${task.deliverID}" data-type="${task.type}">
            ${time}
            <td>#${escapeHtml(task.orderID)}</td>
            <td>${escapeHtml(task.address)}</td>
            <td>${statusControl(task)}</td>
        </tr>`;

        }).join("");

        // Row click opens details.
        // Accept and status dropdown do not open the popup.
        tbody.querySelectorAll("tr[data-deliver-id]").forEach((row) => {
            const task = tasks.find(
                (t) => String(t.deliverID) === row.dataset.deliverId
            );

            if (!task) return;

            row.addEventListener("click", (event) => {
                if (event.target.closest("button,a,select")) return;
                openTaskDetails(task, mode);
            });

            wireActionButtons(row, task);

            const control = row.querySelector(".status-control");

            if (control) {
                control.addEventListener("click", (e) => {
                    e.stopPropagation();
                });

                control.addEventListener("change", (e) => {
                    e.stopPropagation();

                    const value = e.target.value;
                    e.target.value = "";

                    handleStatusChange(task, value);
                });
            }
        });
    }

    // ---------- Actions (map 1:1 to RiderController endpoints) ----------

    /**
     * Accepts an available task. The backend assigns the rider and advances the task to en
     * route; the UI is then refreshed so the task moves to My Work.
     * @param {number} deliverId The task ID.
     */
    async function acceptTask(deliverId) {
        try {
            await api(`/tasks/${deliverId}/accept`, { method: "PUT" });
            closeModal();
            toast("Assignment accepted", "The task is now en route.");
            await refreshAll();
        } catch (error) {
            toast("Could not accept assignment", error.message);
        }
    }

    /**
     * Handles every action chosen from a My Work control. Failure actions open the note form,
     * cancel uses its confirmation, and normal changes call their endpoint directly. On error
     * the lists are still refreshed, because the usual cause is that the task changed (409).
     * @param {Object} task A RiderTaskDTO.
     * @param {string} action Endpoint suffix from getStatusActions(); empty is ignored.
     */
    async function handleStatusChange(task, action) {
        if (!action) return;

        if (action === "pickup-failed") return openFailureModal(task.deliverID, "pickup");
        if (action === "delivery-failed") return openFailureModal(task.deliverID, "delivery");
        if (action === "cancel") return cancelTask(task.deliverID);

        const endpoints = {
            "picked-up": ["Pickup recorded", "The task is now en route to the shop."],
            "pickup-delivered": ["Pickup completed", "The laundry is now in shop."],
            "delivery-delivered": ["Delivery completed", "The order is completed."]
        };
        const config = endpoints[action];
        if (!config) return;

        try {
            await api(`/tasks/${task.deliverID}/${action}`, { method: "PUT" });
            toast(config[0], config[1]);
            await refreshAll();
        } catch (error) {
            toast("Action could not be completed", error.message);
            await refreshAll();
        }
    }

    /**
     * Cancels the rider's assignment after confirmation. The backend releases it and returns
     * it to the available pool.
     * @param {number} deliverId The task ID.
     */
    async function cancelTask(deliverId) {
        if (!window.confirm("Cancel this assignment and return it to the task list?")) return;
        try {
            await api(`/tasks/${deliverId}/cancel`, { method: "PUT" });
            closeModal();
            toast("Assignment cancelled", "Sent back to the task list.");
            await refreshAll();
        } catch (error) {
            toast("Assignment could not be cancelled", error.message);
        }
    }

    // ---------- Page-specific loaders ----------
    // WHY: task_list.html has both an available pool and "my work"; dashboard.html only has "my work" + summary.

    // Identifies which rider page is currently loaded by checking for its
    // page-specific element IDs. This allows one JS file to safely serve both
    // dashboard.html and task_list.html without running irrelevant code.

    const hasTaskListPage = !!(
        document.getElementById("available_pickups") || document.getElementById("available_deliveries")
    );
    const hasDashboardWork = !!(
        document.getElementById("dashboard_pickups") || document.getElementById("dashboard_deliveries")
    );
    const hasMyWorkOnTaskList = !!(
        document.getElementById("my_pickups") || document.getElementById("my_deliveries")
    );
    const hasSummaryCounts = !!document.getElementById("available_pickup_count");

    /**
     * Loads the rider's active assignments, splits them into pickups and deliveries and renders
     * them. Shared by the task-list page and the dashboard.
     * @param {string} pickupId tbody ID for pickups.
     * @param {string} deliveryId tbody ID for deliveries.
     */
    async function loadMyWork(pickupId, deliveryId) {
        const myWork = await api("/my-work");

        const pickups = myWork.filter((t) => t.type === "pickup");
        const deliveries = myWork.filter((t) => t.type === "delivery");

        renderRows(document.getElementById(pickupId), pickups, "mine");
        renderRows(document.getElementById(deliveryId), deliveries, "mine");

        setCount(
            document.querySelector('.task_section[data-role="mine"].pickup_section'),
            pickups.length,
            "pickup",
            "pickups"
        );

        setCount(
            document.querySelector('.task_section[data-role="mine"].delivery_section'),
            deliveries.length,
            "delivery",
            "deliveries"
        );
    }

    /**
     * Task-list page loader: the available pool (/tasks) plus My Work.
     * Errors are shown with toast("Could not load tasks", error.message).
     */
    async function loadAvailableAndMine() {
        try {
            const tasks = await api("/tasks");

            const pickups = tasks.filter((t) => t.type === "pickup");
            const deliveries = tasks.filter((t) => t.type === "delivery");

            renderRows(
                document.getElementById("available_pickups"),
                pickups,
                "available"
            );

            renderRows(
                document.getElementById("available_deliveries"),
                deliveries,
                "available"
            );

            setCount(
                document.querySelector('.task_section[data-role="pool"].pickup_section'),
                pickups.length,
                "pickup",
                "pickups"
            );

            setCount(
                document.querySelector('.task_section[data-role="pool"].delivery_section'),
                deliveries.length,
                "delivery",
                "deliveries"
            );

            setText(
                document.getElementById("remaining_text"),
                `${pickups.length + deliveries.length} remaining`
            );

            await loadMyWork("my_pickups", "my_deliveries");
        } catch (error) {
            toast(error.message, "error");
        }
    }

    /**
     * Dashboard loader: only the rider's My Work, not the available pool.
     */
    async function loadDashboardWork() {
        try {
            await loadMyWork(
                "dashboard_pickups",
                "dashboard_deliveries"
            );
        } catch (error) {
            console.error(error);
            toast("Could not load your work", error.message);
        }
    }

    /**
     * Loads the six summary metrics from the API. They come from the backend rather than
     * counting DOM rows, because completed tasks are no longer shown in My Work.
     */
    async function loadSummary() {
        try {
            const summary = await api("/summary");

            setText("available_pickup_count", summary.availablePickup);
            setText("available_delivery_count", summary.availableDelivery);
            setText("completed_pickup_count", summary.completedPickup);
            setText("completed_delivery_count", summary.completedDelivery);
            setText("remaining_pickup_count", summary.remainingPickup);
            setText("remaining_delivery_count", summary.remainingDelivery);
        } catch (error) {
            console.error("Unable to load dashboard summary:", error);
        }
    }

    /**
     * Loads the signed-in rider's profile and updates the badge and greeting.
     * The greeting is always "Good morning"; use new Date().getHours() if time of day matters.
     */
    async function loadCurrentRider() {
        try {
            const rider = await api("/me");
            const badge = document.getElementById("profile_badge");
            if (badge) badge.textContent = rider.initials || "R";

            const greeting = document.getElementById("greeting");
            if (greeting && rider.firstName) {
                greeting.textContent = `Good morning, ${rider.firstName}`;
            }
        } catch (error) {
            console.error("Unable to load rider:", error);
        }
    }

    /**
     * Reloads every section the current page needs. Status actions can move tasks between
     * sections and change dashboard counts, so all relevant data is refreshed.
     */
    async function refreshAll() {
        const jobs = [];
        if (hasTaskListPage || hasMyWorkOnTaskList) jobs.push(loadAvailableAndMine());
        if (hasDashboardWork) jobs.push(loadDashboardWork());
        if (hasSummaryCounts) jobs.push(loadSummary());
        await Promise.all(jobs);
    }

    // Starts the rider page after the HTML has finished loading.
    // The rider profile is loaded first, followed by the page-specific data.

    document.addEventListener("DOMContentLoaded", async () => {
        await loadCurrentRider();
        await refreshAll();
    });
})();
