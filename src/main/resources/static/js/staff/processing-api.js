/*
 * Shared helpers for the staff laundry-processing pages (processing board, receive items,
 * order processing, issue reports, staff dashboard). Each page script uses window.Processing.
 *
 * - api(): calls /api/processing with JSON, adding the CSRF token Spring Security needs for
 *   POST/PUT. On an error it throws an Error whose message is the server's {"message": ...};
 *   error.status and error.data carry the HTTP status and full response body (the receive
 *   endpoint returns its mismatches with status 422).
 * - notice(): shows a success/error/info banner in the page's #notice element.
 * - Small formatting helpers shared by the pages.
 */
window.Processing = (() => {
  let csrf;

  /** Shortcut for document.getElementById. */
  const $ = (id) => document.getElementById(id);

  /** Escapes text before it is placed inside HTML, so data can never inject markup. */
  const escape = (value) =>
    String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

  /** Formats an ISO date-time from the API as e.g. "28 Sept 2026, 10:15 am". */
  const dateTime = (value) => {
    if (!value) return "—";
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime())
      ? String(value)
      : parsed.toLocaleString("en-LK", { dateStyle: "medium", timeStyle: "short" });
  };

  /** Reads a query-string parameter, e.g. param("orderId") on order_processing.html?orderId=6. */
  const param = (name) => new URLSearchParams(location.search).get(name);

  /**
   * Picks the badge colour for a status label (the global-pre.js badge colouring only runs once
   * at page load, so dynamically added badges need it applied here).
   */
  function statusClass(label) {
    const text = String(label || "").toLowerCase();
    if (/awaiting delivery|passed|resolved|closed|ready/.test(text)) return "status status_success";
    if (/failed|damage|missing|mismatch/.test(text)) return "status status_error";
    if (/in shop|verifying|new|review|assigned|open/.test(text)) return "status status_warning";
    return "status status_info";
  }

  /** A status badge as HTML. */
  const badge = (label) => `<span class="${statusClass(label)}">${escape(String(label || "").toUpperCase())}</span>`;

  /** Shows a message banner in #notice; kind is "success", "error" or "" (neutral). */
  function notice(message, kind = "") {
    const box = $("notice");
    if (!box) return;
    box.hidden = !message;
    box.className = "alert" + (kind ? " alert_" + kind : "");
    box.innerHTML = message || "";
    if (message) box.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }

  /** Calls the processing API and returns the parsed JSON (see the file comment for errors). */
  async function api(path, method = "GET", body) {
    const headers = { Accept: "application/json" };
    if (method !== "GET") {
      // Spring Security requires the CSRF token on every state-changing request.
      csrf ||= await fetch("/api/auth/csrf").then((r) => (r.ok ? r.json() : Promise.reject(new Error("Unable to verify this action. Please sign in again."))));
      headers[csrf.headerName] = csrf.token;
    }
    if (body !== undefined) headers["Content-Type"] = "application/json";

    const response = await fetch("/api/processing" + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    // Not signed in / wrong role: Spring redirects to the login or portal page instead of JSON.
    if (response.redirected || !(response.headers.get("content-type") || "").includes("json")) {
      if (response.ok || response.redirected) throw new Error("Please sign in with a staff account to use laundry processing.");
    }
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error((data && data.message) || `Request failed (${response.status}).`);
      error.status = response.status;
      error.data = data;
      throw error;
    }
    return data;
  }

  /** Runs an async action with its button disabled, showing any error in #notice. */
  async function run(task, button) {
    if (button) button.disabled = true;
    try {
      await task();
    } catch (error) {
      notice(escape(error.message), "error");
    } finally {
      if (button) button.disabled = false;
    }
  }

  return { $, escape, dateTime, param, statusClass, badge, notice, api, run };
})();
