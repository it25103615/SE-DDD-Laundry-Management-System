document.addEventListener("DOMContentLoaded", async () => {
  const byId = (id) => document.getElementById(id);
  try {
    const response = await fetch("/api/customer/dashboard", {
      headers: { Accept: "application/json" },
    });
    if (response.status === 401 || response.redirected) {
      location.href = "/html/auth/login.html";
      return;
    }
    if (!response.ok)
      throw new Error("Your dashboard could not load. Refresh the page after signing in.");
    const data = await response.json();
    const name = String(data.name || "Customer").trim();
    byId("welcome").textContent = `Welcome back, ${name} 👋`;
    //The profile badge is filled in by the shared navigation bar (global-pre.js)
    byId("total-orders").textContent = data.orders;
    byId("support-count").textContent = data.openSupport;
    byId("support-summary").textContent = data.openSupport ? "Currently open" : "All caught up";
    const latest = data.latestOrder;
    byId("active-orders").textContent = data.activeOrders;
    byId("order-summary").textContent = data.activeOrders
      ? latest?.status || "In progress"
      : "No active orders";
    const orderArea = byId("latest-order");
    if (latest?.id) {
      const title = document.createElement("h3");
      title.className = "latest-id";
      title.textContent = `Order #${latest.id}`;
      const status = document.createElement("p");
      status.className = "muted";
      status.textContent = `Current status: ${latest.status || "Awaiting update"}`;
      orderArea.replaceChildren(title, status);
    } else {
      const empty = document.createElement("div");
      empty.className = "home-empty";
      const illustration = document.createElement("img");
      illustration.src = "../../images/customer/laundry-basket.svg";
      illustration.alt = "";
      const heading = document.createElement("h3");
      heading.textContent = "Ready for your first fresh load?";
      const description = document.createElement("p");
      description.className = "muted";
      description.textContent = "Book a pickup and we’ll take care of the rest.";
      const action = document.createElement("a");
      action.className = "custom_button custom_button_bg";
      action.href = "new_order_services.html";
      action.textContent = "Place your first order";
      empty.append(illustration, heading, description, action);
      orderArea.replaceChildren(empty);
    }
  } catch (error) {
    byId("dashboard-notice").hidden = false;
    byId("dashboard-notice").textContent = error.message;
  }
});
