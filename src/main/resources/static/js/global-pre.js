// Authentication and API calls require Spring Boot. If a project HTML file is
// opened directly from Explorer, move it to the equivalent local server URL.
if (location.protocol === "file:") {
  const normalizedPath = decodeURIComponent(location.pathname).replaceAll("\\", "/");
  const htmlPath = normalizedPath.match(/\/(html\/.*)$/i);
  const targetPath = htmlPath ? `/${htmlPath[1]}` : "/";
  location.replace(`http://localhost:8080${targetPath}`);
}

// ---------------------------------------------------------------------------
// Shared navigation bar
//
// Every page keeps only an empty <nav class="navbar"> or <nav class="dashboard_nav">.
// This section fills it in, so the links, the notification bell, the profile badge,
// the Log out button and the phone menu are written once, here, for every page.
// ---------------------------------------------------------------------------
(() => {
  const SUPPORT = "/html/admin/customer-service-manager";
  const PROFILE_PAGE = "/html/account/profile.html";

  //Where each role lands after signing in (the same targets SecurityConfig sends
  //  a user to after login). The logo and the "Dashboard" link both use this.
  const DASHBOARDS = {
    CUSTOMER: "/html/customer/dashboard.html",
    STAFF: "/html/staff/dashboard.html",
    RIDER: "/html/rider/dashboard.html",
    MANAGER: "/html/admin/manager/dashboard.html",
    CSM: SUPPORT + "/dashboard.html",
    CUSTOMER_SERVICE_MANAGER: SUPPORT + "/dashboard.html",
    OWNER: "/html/admin/owner/dashboard.html",
    ADMIN: "/html/admin/owner/dashboard.html",
  };

  const IDENTITY_KEY = "laundryLink.pageIdentity";
  if (location.pathname === "/html/auth/login.html") {
    const root = document.documentElement;
    const visibility = root.style.getPropertyValue("visibility");
    const priority = root.style.getPropertyPriority("visibility");
    const hide = () => root.style.setProperty("visibility", "hidden", "important");
    let generation = 0;
    const verifyLoginPage = async () => {
      const current = ++generation;
      hide();
      try {
        const response = await fetch("/api/account/profile", {
          headers: { Accept: "application/json" }, cache: "no-store",
          signal: AbortSignal.timeout(10000),
        });
        if (response.ok && !response.redirected) {
          const profile = await response.json();
          if (current !== generation) return;
          const destination = Number.isInteger(profile?.id) && typeof profile.role === "string"
            ? DASHBOARDS[profile.role.toUpperCase()] : null;
          if (destination) { location.replace(destination); return; }
        }
      } catch {
        // Keep login available when the session is absent or verification is unavailable.
      }
      if (current !== generation) return;
      if (visibility) root.style.setProperty("visibility", visibility, priority);
      else root.style.removeProperty("visibility");
    };
    window.addEventListener("pagehide", () => { ++generation; hide(); });
    window.addEventListener("pageshow", event => { if (event.persisted) verifyLoginPage(); });
    verifyLoginPage();
  }
  const HISTORY_IDENTITY_KEY = "laundryLink.protectedIdentity";
  const clearAccountStorage = () => {
    try {
      for (const key of Object.keys(sessionStorage)) {
        if ([IDENTITY_KEY, "laundryLink.nav", "laundryLink.orderDraft", "laundrylinkCustomerID"].includes(key)
            || key.startsWith("laundrylinkPromotionCode:")) sessionStorage.removeItem(key);
      }
    } catch {}
  };
  const pageRoles = [
    ["/html/customer/", ["CUSTOMER"]],
    ["/html/admin/owner/payments_billing.html", ["MANAGER", "OWNER", "ADMIN"]],
    ["/html/admin/owner/payment_detail.html", ["MANAGER", "OWNER", "ADMIN"]],
    ["/html/admin/owner/promotions.html", ["MANAGER", "OWNER", "ADMIN"]],
    ["/html/admin/owner/", ["OWNER", "ADMIN"]],
    ["/html/admin/manager/", ["MANAGER", "OWNER", "ADMIN"]],
    ["/html/admin/customer-service-manager/", ["CSM", "CUSTOMER_SERVICE_MANAGER", "MANAGER", "OWNER", "ADMIN"]],
    ["/html/staff/", ["STAFF", "MANAGER", "OWNER", "ADMIN"]],
    ["/html/rider/", ["RIDER"]],
    ["/html/account/", Object.keys(DASHBOARDS)],
  ].find(([path]) => path.endsWith("/") ? location.pathname.startsWith(path) : location.pathname === path);
  if (pageRoles) {
    const root = document.documentElement;
    const visibility = root.style.getPropertyValue("visibility");
    const priority = root.style.getPropertyPriority("visibility");
    const hide = () => root.style.setProperty("visibility", "hidden", "important");
    let pageIdentity, generation = 0;
    hide();
    const verify = async (restored = false) => {
      const current = ++generation;
      hide();
      try {
        const response = await fetch("/api/account/profile", {
          headers: { Accept: "application/json" }, cache: "no-store",
          signal: AbortSignal.timeout(10000),
        });
        if (!response.ok || response.redirected) throw new Error("Sign in required");
        const profile = await response.json();
        if (!Number.isInteger(profile.id) || typeof profile.role !== "string") throw new Error("Invalid account");
        if (current !== generation) return;
        const role = profile.role.toUpperCase();
        const identity = JSON.stringify([profile.id, role]);
        const entryState = history.state;
        // Preserve other features' state; do not silently convert unsupported state values.
        if (entryState !== null && (typeof entryState !== "object" || Array.isArray(entryState)))
          throw new Error("Unsupported history state");
        const entryIdentity = entryState?.[HISTORY_IDENTITY_KEY];
        let previous;
        try { previous = sessionStorage.getItem(IDENTITY_KEY); } catch {}
        const entryChanged = entryIdentity !== undefined && entryIdentity !== identity;
        const changed = entryChanged || (pageIdentity && pageIdentity !== identity) || (previous && previous !== identity);
        if (changed) clearAccountStorage();
        try { sessionStorage.setItem(IDENTITY_KEY, identity); } catch {}
        if (entryChanged || (pageIdentity && pageIdentity !== identity) || !pageRoles[1].includes(role)) {
          // The replacement dashboard must not inherit the previous account's marker.
          const nextState = { ...entryState };
          delete nextState[HISTORY_IDENTITY_KEY];
          history.replaceState(nextState, "");
          location.replace(DASHBOARDS[role] || "/html/auth/login.html");
          return;
        }
        // Navigation metadata only; the server profile and Spring Security decide access.
        history.replaceState({ ...entryState, [HISTORY_IDENTITY_KEY]: identity }, "");
        // Never reveal a restored DOM: its data and pending callbacks belong to the old page.
        if (restored || changed) { location.reload(); return; }
        pageIdentity = identity;
        if (visibility) root.style.setProperty("visibility", visibility, priority);
        else root.style.removeProperty("visibility");
      } catch {
        if (current !== generation) return;
        clearAccountStorage();
        location.replace("/html/auth/login.html");
      }
    };
    window.addEventListener("pagehide", () => { ++generation; hide(); });
    window.addEventListener("pageshow", event => { if (event.persisted) verify(true); });
    verify();
  }

  //Links that more than one role shares, written once so the label and the
  //  address stay the same everywhere
  const supportCases = { label: "Support cases", href: SUPPORT + "/complaints.html" };
  const orderLookup = { label: "Order lookup", href: SUPPORT + "/orders.html" };
  const assignedComplaints = { label: "Assigned Complaints", href: SUPPORT + "/complaints.html" };
  const staffAccounts = { label: "Staff accounts", href: "/html/admin/manager/staff_accounts.html" };

  //The link list of each role, in the order the links are shown.
  //  label: the text of the link
  //  href:  where it goes (always a full path, so it works from any folder)
  //  pages: other pages that belong to the same section. The link is also
  //         highlighted while one of them is open (for example "My Orders" stays
  //         highlighted on the order details page).
  const LINKS = {
    CUSTOMER: [
      { label: "Dashboard", href: DASHBOARDS.CUSTOMER },
      {
        label: "My Orders",
        href: "/html/customer/my_orders.html",
        pages: [
          "/html/customer/order_details.html",
          "/html/customer/upcoming_order_details.html",
          "/html/customer/modify_order.html",
        ],
      },
      {
        label: "Payments",
        href: "/html/customer/payments.html",
        pages: [
          "/html/customer/payment_method.html",
          "/html/customer/payment_checkout.html",
          "/html/customer/receipt.html",
        ],
      },
      { label: "Support", href: "/html/customer/feedback.html" },
    ],
    STAFF: [
      { label: "Dashboard", href: DASHBOARDS.STAFF },
      {
        label: "Processing Board",
        href: "/html/staff/processing_board.html",
        pages: ["/html/staff/order_processing.html"],
      },
      { label: "Receive Items", href: "/html/staff/receive_items.html" },
      { label: "Issue Reports", href: "/html/staff/issue_reports.html" },
      assignedComplaints,
    ],
    RIDER: [
      { label: "Dashboard", href: DASHBOARDS.RIDER },
      { label: "Task List", href: "/html/rider/task_list.html" },
      assignedComplaints,
    ],
    MANAGER: [
      { label: "Dashboard", href: DASHBOARDS.MANAGER },
      orderLookup,
      {
        //A manager reaches the staff pages through here, so all of them count
        //  as part of "Operations"
        label: "Operations",
        href: "/html/staff/processing_board.html",
        pages: [
          "/html/staff/dashboard.html",
          "/html/staff/order_processing.html",
          "/html/staff/receive_items.html",
          "/html/staff/issue_reports.html",
        ],
      },
      {
        label: "Payments & Billing",
        href: "/html/admin/owner/payments_billing.html",
        pages: ["/html/admin/owner/payment_detail.html"],
      },
      { label: "Promotions", href: "/html/admin/owner/promotions.html" },
      { label: "Assign Riders", href: "/html/admin/manager/assign_riders.html" },
      { label: "Service catalogue", href: "/html/admin/manager/service_catalog.html" },
      staffAccounts,
      supportCases,
    ],
    CSM: [{ label: "Dashboard", href: DASHBOARDS.CSM }, supportCases, orderLookup],
    OWNER: [
      {
        label: "Dashboard",
        href: DASHBOARDS.OWNER,
        pages: ["/html/admin/owner/administration_overview.html"],
      },
      supportCases,
      orderLookup,
      {
        label: "Payments & Billing",
        href: "/html/admin/owner/payments_billing.html",
        pages: ["/html/admin/owner/payment_detail.html"],
      },
      { label: "Promotions", href: "/html/admin/owner/promotions.html" },
      { label: "Reports", href: "/html/admin/owner/reports.html" },
      staffAccounts,
      { label: "Roles & permissions", href: "/html/admin/owner/roles_permissions.html" },
    ],
  };
  //Two role names mean the same thing in the database, so they share one list
  LINKS.CUSTOMER_SERVICE_MANAGER = LINKS.CSM;
  LINKS.ADMIN = LINKS.OWNER;

  //Links of the public site. "button" is the extra button class of a link that
  //  is drawn as a button.
  const PUBLIC_LINKS = [
    { label: "Services", href: "/html/public/services.html" },
    { label: "Pricing", href: "/html/public/pricing.html" },
  ];
  const SIGNED_OUT_LINKS = [
    ...PUBLIC_LINKS,
    { label: "Log In", href: "/html/auth/login.html", button: "custom_button_border" },
    { label: "Get Started", href: "/html/auth/register.html", button: "custom_button_bg" },
  ];

  //The role and initials of the signed-in user are remembered for this browser
  //  tab, so the next page can draw the right navigation bar immediately instead
  //  of waiting for the server.
  const STORE_KEY = "laundryLink.nav";

  //Read the remembered user. Returns nothing if there is none, if it cannot be
  //  read, or if the browser blocks sessionStorage.
  const readStoredUser = () => {
    try {
      const stored = JSON.parse(sessionStorage.getItem(STORE_KEY));
      return stored && typeof stored.role === "string" ? stored : undefined;
    } catch {
      return undefined;
    }
  };

  //Remember the user, or forget them when "user" is empty (signed out)
  const storeUser = (user) => {
    try {
      if (user) sessionStorage.setItem(STORE_KEY, JSON.stringify(user));
      else sessionStorage.removeItem(STORE_KEY);
    } catch {}
  };

  //With nothing remembered, guess who is looking at the page from its folder:
  //  a role folder -> that role (initials not known yet)
  //  the shared account folder -> unknown (undefined), wait for the server
  //  anything else (public, auth) -> signed out (null)
  const guessUser = () => {
    const path = location.pathname.toLowerCase();
    const folders = [
      ["/admin/customer-service-manager/", "CSM"],
      ["/admin/manager/", "MANAGER"],
      ["/admin/owner/", "OWNER"],
      ["/customer/", "CUSTOMER"],
      ["/staff/", "STAFF"],
      ["/rider/", "RIDER"],
    ];
    const match = folders.find(([folder]) => path.includes(folder));
    if (match) return { role: match[1], initials: "" };
    return path.includes("/account/") ? undefined : null;
  };

  //First letters of the first two words of a name: "Priya Fernando" -> "PF"
  const initialsOf = (name) =>
    String(name || "User")
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase();

  //Used to tell whether two users would get the same navigation bar
  const userKey = (user) => (user ? `${user.role}|${user.initials}` : String(user));

  function buildNav() {
    const nav = document.querySelector("nav.navbar, nav.dashboard_nav");
    if (!nav) return;
    nav.setAttribute("aria-label", "Main navigation");

    //A page in the middle of an order or a payment asks for the short bar with
    //  data-nav="flow": only the logo, one way out and Log out.
    //    data-exit-label / data-exit-href: text and target of the way out
    //    data-exit-id: an id for that link, when another script needs to find it
    //    data-exit-keep-query: also pass this page's "?..." part to the target
    const flow = nav.dataset.nav === "flow";
    const exitLink = flow && {
      label: nav.dataset.exitLabel || "Back",
      href:
        (nav.dataset.exitHref || "/index.html") +
        ("exitKeepQuery" in nav.dataset ? location.search : ""),
      id: nav.dataset.exitId,
      exit: true,
    };

    //The fixed parts of the bar. Everything that depends on who is signed in
    //  starts hidden or empty and is filled in by show() below.
    nav.innerHTML = `
      <ul class="navigation">
        <li class="logo"><a href="/index.html"><h3>LaundryLink</h3></a></li>
        <li class="nav_right">
          <div class="notification_center" hidden>
            <button class="notification_button" type="button" aria-label="Open notifications" aria-expanded="false">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg>
              <span class="notification_count" hidden>0</span>
            </button>
            <section class="notification_panel" aria-label="Notifications" hidden>
              <header><div><strong>Notifications</strong><small class="notification_summary"></small></div><button class="notification_read_all" type="button">Mark all read</button></header>
              <div class="notification_list"><p class="notification_empty">Loading…</p></div>
            </section>
          </div>
          <div class="nav_links" id="nav-links"></div>
          <a class="profile_badge" id="profile-badge" href="${PROFILE_PAGE}" aria-label="My profile" hidden></a>
          <button class="logout_button" type="button" hidden>Log out</button>
          <button class="nav_menu_button" type="button" aria-expanded="false" aria-controls="nav-links">Menu</button>
        </li>
      </ul>`;
    const navigation = nav.querySelector(".navigation");
    const navRight = nav.querySelector(".nav_right");
    const logoLink = nav.querySelector(".logo a");
    const links = nav.querySelector(".nav_links");
    const profileBadge = nav.querySelector(".profile_badge");
    const logoutButton = nav.querySelector(".logout_button");
    const menuButton = nav.querySelector(".nav_menu_button");
    const shell = nav.querySelector(".notification_center");

    //Open or close the menu that holds the links when they do not fit in the bar
    const setMenu = (open) => {
      links.classList.toggle("open", open);
      menuButton.setAttribute("aria-expanded", String(open));
    };

    //Decide whether the links fit in one row:
    //  Take the "collapsed" mark off, so the links are laid out in the bar
    //  If the logo and the right side together are wider than the bar
    //    put the mark back: the links move into the menu and the Menu button shows
    //  Otherwise make sure a menu left open from a narrower window is closed
    //Measuring instead of using a fixed screen width also covers the long manager
    //  and owner lists on a laptop. (The half pixel allows for rounding.)
    const width = (element) => element.getBoundingClientRect().width;
    const fit = () => {
      navigation.classList.remove("nav_collapsed");
      if (width(logoLink.parentElement) + width(navRight) > width(navigation) + 0.5)
        navigation.classList.add("nav_collapsed");
      else setMenu(false);
    };

    //Build one link of the bar
    const toAnchor = (link) => {
      const anchor = document.createElement("a");
      anchor.href = link.href;
      anchor.textContent = link.label;
      if (link.id) anchor.id = link.id;
      if (link.button) anchor.className = `custom_button ${link.button}`;
      //Mark the link of the page that is open ("page"), or of the section the
      //  open page belongs to ("true"). global.css highlights both.
      if (!link.exit) {
        if (link.href === location.pathname) anchor.setAttribute("aria-current", "page");
        else if (link.pages?.includes(location.pathname)) anchor.setAttribute("aria-current", "true");
      }
      return anchor;
    };

    //The links a user gets. The bar follows the user's role, not the folder of
    //  the page, so a manager who opens a staff page keeps the manager links.
    //    short bar            -> only the way out
    //    not known yet        -> nothing
    //    signed out           -> public links with Log In and Get Started
    //    public or auth page  -> public links and a way back to the dashboard
    //    role page            -> the list of that role
    const linksFor = (user) => {
      if (flow) return [exitLink];
      if (user === undefined) return [];
      if (!user) return SIGNED_OUT_LINKS;
      const dashboard = { label: "Dashboard", href: DASHBOARDS[user.role] || "/html/portal.html" };
      if (/\/(public|auth)\//.test(location.pathname.toLowerCase()))
        return [...PUBLIC_LINKS, dashboard];
      return LINKS[user.role] || [dashboard];
    };

    //Draw the bar for a user ({role, initials}), for a signed-out visitor (null)
    //  or for a visitor we know nothing about yet (undefined)
    let shown;
    const show = (user) => {
      shown = user;
      const signedIn = Boolean(user);
      logoLink.href = signedIn ? DASHBOARDS[user.role] || "/html/portal.html" : "/index.html";
      links.replaceChildren(...linksFor(user).map(toAnchor));
      shell.hidden = flow || !signedIn;
      profileBadge.hidden = flow || !signedIn;
      profileBadge.textContent = signedIn ? user.initials : "";
      logoutButton.hidden = !signedIn;
      fit();
    };

    //Draw immediately from what is remembered (or guessed), so the bar does not
    //  appear empty and then fill in
    const stored = readStoredUser();
    show(stored || guessUser());

    //The room for the links changes with the window, and the text gets wider or
    //  narrower when the web font finishes loading
    window.addEventListener("resize", fit);
    if (document.fonts) document.fonts.ready.then(fit);

    //Menu button: open or close the menu
    menuButton.addEventListener("click", () => setMenu(!links.classList.contains("open")));
    //Close the menu after a link in it is chosen,
    links.addEventListener("click", (event) => {
      if (event.target.closest("a")) setMenu(false);
    });
    //  after a click anywhere outside it,
    document.addEventListener("click", (event) => {
      if (!links.contains(event.target) && !menuButton.contains(event.target)) setMenu(false);
    });
    //  and when Escape is pressed (focus goes back to the button that opened it)
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape" || !links.classList.contains("open")) return;
      setMenu(false);
      menuButton.focus();
    });

    let csrf;

    //When the log out button is clicked:
    //  Disable it so a second click cannot send the request twice
    //  Get the CSRF token (Spring Security rejects a POST /logout without it)
    //  Ask the server to end the session
    //  Forget the remembered user, so the next page starts signed out
    //  Go to the login page, which shows a "signed out" message for ?logout
    //If anything fails, re-enable the button and tell the user
    logoutButton.addEventListener("click", async () => {
      logoutButton.disabled = true;
      try {
        csrf ||= await fetch("/api/auth/csrf").then(r=>r.ok?r.json():Promise.reject(new Error("Unable to verify this action.")));
        const response = await fetch("/logout", {
          method: "POST",
          headers: { [csrf.headerName]: csrf.token },
        });
        //A successful logout redirects to the login page. A refused one (for example
        //  a stale CSRF token) is redirected elsewhere by the access-denied handler,
        //  so the final address is what tells the two apart, not the status code.
        const loginPage = "/html/auth/login.html";
        if (!response.ok || new URL(response.url).pathname !== loginPage)
          throw new Error("Unable to log out. Please try again.");
        clearAccountStorage();
        storeUser(null);
        location.href = loginPage + "?logout=true";
      } catch (error) {
        logoutButton.disabled = false;
        //connectedToast comes from global-post.js, which not every page loads
        if (window.connectedToast) window.connectedToast("Log out failed", error.message);
        else alert(error.message);
      }
    });

    // Shared authenticated notification bell used by every role page.
    const button=shell.querySelector(".notification_button");
    const panel=shell.querySelector(".notification_panel");
    const list=shell.querySelector(".notification_list");
    const badge=shell.querySelector(".notification_count");
    const summary=shell.querySelector(".notification_summary");
    const api=async(path,method="GET")=>{
      const headers={Accept:"application/json"};
      if(method!=="GET") {
        csrf ||= await fetch("/api/auth/csrf").then(r=>r.ok?r.json():Promise.reject(new Error("Unable to verify this action.")));
        headers[csrf.headerName]=csrf.token;
      }
      const response=await fetch("/api/notifications"+path,{method,headers});
      if(!response.ok) throw new Error("Unable to load notifications.");
      return response.status===204?null:response.json();
    };
    const time=value=>{
      const parsed=new Date(value),seconds=Math.max(0,Math.floor((Date.now()-parsed.getTime())/1000));
      if(seconds<60)return "Just now";if(seconds<3600)return Math.floor(seconds/60)+"m ago";if(seconds<86400)return Math.floor(seconds/3600)+"h ago";
      return parsed.toLocaleDateString("en-LK",{day:"numeric",month:"short"});
    };
    const render=data=>{
      shell.hidden=false;badge.textContent=data.unread;badge.hidden=!data.unread;summary.textContent=data.unread?data.unread+" unread":"All caught up";
      list.replaceChildren();
      if(!data.items.length){const empty=document.createElement("p");empty.className="notification_empty";empty.textContent="No notifications yet.";list.append(empty);return;}
      data.items.forEach(item=>{
        const row=document.createElement("button");row.type="button";row.className="notification_item"+(item.isRead?"":" unread");row.dataset.id=item.id;row.dataset.link=item.link;
        const dot=document.createElement("span");dot.className="notification_dot";
        const copy=document.createElement("span");copy.className="notification_copy";
        const title=document.createElement("strong");title.textContent=item.title;
        const message=document.createElement("span");message.textContent=item.message;
        const meta=document.createElement("small");meta.textContent=item.category+" · "+time(item.createdAt);
        copy.append(title,message,meta);row.append(dot,copy);list.append(row);
      });
    };
    const load=()=>api("").then(render).catch(()=>{shell.hidden=true;});
    //Opening the notifications closes the links menu, so the two never overlap
    button.addEventListener("click",async event=>{event.stopPropagation();setMenu(false);panel.hidden=!panel.hidden;button.setAttribute("aria-expanded",String(!panel.hidden));if(!panel.hidden)await load();});
    list.addEventListener("click",async event=>{const row=event.target.closest(".notification_item");if(!row)return;try{if(row.classList.contains("unread"))await api("/"+row.dataset.id+"/read","PATCH");if(row.dataset.link)location.href=row.dataset.link;}catch(error){summary.textContent=error.message;}});
    shell.querySelector(".notification_read_all").addEventListener("click",async()=>{try{await api("/read-all","PATCH");await load();}catch(error){summary.textContent=error.message;}});
    document.addEventListener("click",event=>{if(!shell.contains(event.target)){panel.hidden=true;button.setAttribute("aria-expanded","false");}});

    //Ask the server who is signed in. It answers with the profile as JSON for a
    //  signed-in user; anything else (an error, a redirect to the login page, a
    //  body that is not JSON) means nobody is signed in.
    //Then:
    //  Remember the answer for the next page
    //  Redraw the bar only if it differs from what was drawn above
    //  For a signed-in user, load the notifications and re-check every 30 seconds
    //If the server cannot be reached at all, the bar is left as it is.
    fetch("/api/account/profile", { headers: { Accept: "application/json" } }).then(
      async (response) => {
        const profile =
          response.ok && !response.redirected ? await response.json().catch(() => null) : null;
        const user = profile?.role
          ? { role: String(profile.role).toUpperCase(), initials: initialsOf(profile.name) }
          : null;
        storeUser(user);
        if (userKey(user) !== userKey(shown)) show(user);
        if (user && !flow) {
          load();
          setInterval(load, 30000);
        }
      },
      () => {},
    );
  }

  //Build the bar as soon as the page's HTML has been read. This is earlier than
  //  DOMContentLoaded on purpose: scripts loaded with "defer" run in between, and
  //  some of them look for elements of the bar (payment-flow.js rewrites the
  //  checkout page's Back link).
  if (document.readyState === "loading")
    document.addEventListener("readystatechange", buildNav, { once: true });
  else buildNav();
})();

document.addEventListener("DOMContentLoaded", () => {
  //Get the url path
  const path = location.pathname.toLowerCase();

  //For each "role",
  // check if the browser path includes the role
  //   if it does then add 'v4_[role]' to the document body
  [
    "public",
    "auth",
    "customer",
    "staff",
    "rider",
    "admin",
    "manager",
    "owner",
    "support",
  ].forEach((role) => {
    if (path.includes(`/${role}/`)) document.body.classList.add(`v4_${role}`);
  });

  //Create 2 html tags
  const one = document.createElement("i");
  const two = document.createElement("i");

  //Give them classnames
  one.className = "v4_shape v4_shape_one";
  two.className = "v4_shape v4_shape_two";

  //Add the tags to the end of the html body tag
  document.body.append(one, two);

  //"intersecting" indicates that an entry (html tag) is intersecting with the viewport (is now on screen (visible))
  //the IntersectionObserver is a browser API that lets us watch when an element scrolls into view
  //  If the API is availabe (it is a modern browser)
  //    Create an IntersectionObserver object
  //    For every entry that had it status change
  //      check if it is "intersecting"
  //        if it is add a tag that marks the element as visible
  //        and make sure IntersectionObserver stops tracking that now visible element
  //  Else set to null
  const revealObserver =
    "IntersectionObserver" in window
      ? new IntersectionObserver(
          (entries) =>
            entries.forEach((entry) => {
              if (entry.isIntersecting) {
                entry.target.classList.add("v4_visible");
                revealObserver.unobserve(entry.target);
              }
            }),
          { threshold: 0.08 },
        )
      : null;

  //Select all elements that have one of the following classes and is a direct child of the main class
  //  section, article, .grid, .table_wrap, form
  //For each element that is found
  //  Add a class to it called 'v4_reveal'
  //  Set a delay
  //  if the IntersectionObserver is not null,
  //    make it track this element
  //  else
  //    add a tag that marks the element as visible
  document
    .querySelectorAll(
      "main > section, main > article, main > .grid, main > .table_wrap, main > form",
    )
    .forEach((node, index) => {
      node.classList.add("v4_reveal");
      node.style.transitionDelay = `${Math.min(index * 35, 210)}ms`;
      if (revealObserver) revealObserver.observe(node);
      else node.classList.add("v4_visible");
    });

  //Select all the elements that have the '.status' class
  //For each element found
  //  get the text within the tag
  //  If it is one of the following statues:  delivered, paid, active, complete, resolved, ready, excellent, success
  //    Then add a class to the element indicating success
  //  Else if it is one of the following statues: error, failed, cancel, damage, danger
  //    Then add a class to the element indicating an error
  //  Else if it is one of the following statues: pending, warning, today, priority, await, due, review
  //    Then add a class to the element indicating a warning
  //  Otherwise
  //    Add a class to the element indicating information
  document.querySelectorAll(".status").forEach((badge) => {
    const text = badge.textContent.toLowerCase();
    if (
      /delivered|paid|active|complete|resolved|ready|excellent|success/.test(
        text,
      )
    )
      badge.classList.add("status_success");
    else if (/error|failed|cancel|damage|danger/.test(text))
      badge.classList.add("status_error");
    else if (/pending|warning|today|priority|await|due|review/.test(text))
      badge.classList.add("status_warning");
    else badge.classList.add("status_info");
  });

  //Check if the user has opted to enable a system setting to reduce motions
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  //Select every element with a "data-counter" attribute
  //For each element found:
  //  Read the target number from its "data-counter" attribute (default 0 if missing)
  //  Read an optional suffix string from "data-suffix" (e.g. "%", "+") (default empty)
  //
  //  If the user prefers reduced motion:
  //    Just show the final number + suffix immediately, no animation
  //    Stop here for this element
  //  Otherwise, animate counting up to the target:
  //    Record the start time
  //    Define a "tick" function that runs on each animation frame:
  //      Calculate progress as elapsed time / 1200ms, capped at 1 (100%)
  //      Ease the progress using a "slow down near the end" curve (cubic ease-out)
  //      Calculate the current displayed value based on eased progress
  //      Update the element's text to show current value + suffix
  //      If progress hasn't reached 1 yet, schedule another tick on the next frame
  //  Kick off the animation by scheduling the first tick
  document.querySelectorAll("[data-counter]").forEach((node) => {
    const target = Number(node.dataset.counter || 0);
    const suffix = node.dataset.suffix || "";

    if (reduced) {
      node.textContent = target.toLocaleString() + suffix;
      return;
    }

    const started = performance.now();
    const tick = (now) => {
      const progress = Math.min((now - started) / 1200, 1);
      const value = Math.floor(target * (1 - Math.pow(1 - progress, 3)));
      node.textContent = value.toLocaleString() + suffix;
      if (progress < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });

  //Select every element with class "custom_button"
  //For each button found:
  //  Listen for a click event on it
  //  When the custom_button is clicked:
  //    Create a new empty <span> element to act as the ripple
  //    Get the button's position/size on screen
  //    Style the span inline:
  //      - positioned at the exact click point relative to the button
  //        (click X/Y minus button's offset, minus half the ripple size to center it)
  //      - small 12x12 circle, semi-transparent white
  //      - starts scaled to 0 (invisible)
  //      - animates using the "ripple" CSS keyframes over 0.55s
  //      - ignores further mouse events (so it doesn't block clicks)
  //    Add the ripple span into the button
  //    After 600ms, remove the ripple span from the HTML code (cleanup)
  document.querySelectorAll(".custom_button").forEach((button) =>
    button.addEventListener("click", (event) => {
      const ripple = document.createElement("span");
      const rect = button.getBoundingClientRect();
      ripple.style.cssText = `
        left: ${event.clientX - rect.left - 6}px;
        top: ${event.clientY - rect.top - 6}px;
        position: absolute;
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: rgba(255, 255, 255, .48);
        transform: scale(0);
        animation: ripple .55s ease-out;
        pointer-events: none;
      `;
      button.appendChild(ripple);
      setTimeout(() => ripple.remove(), 600);
    }),
  );
});
