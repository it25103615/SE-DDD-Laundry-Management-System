// Authentication and API calls require Spring Boot. If a project HTML file is
// opened directly from Explorer, move it to the equivalent local server URL.
if (location.protocol === "file:") {
  const normalizedPath = decodeURIComponent(location.pathname).replaceAll("\\", "/");
  const htmlPath = normalizedPath.match(/\/(html\/.*)$/i);
  const targetPath = htmlPath ? `/${htmlPath[1]}` : "/";
  location.replace(`http://localhost:8080${targetPath}`);
}

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

  // Shared authenticated notification bell used by every role page.
  const navRight = document.querySelector(".nav_right");
  if (navRight) {
    const shell = document.createElement("div");
    shell.className = "notification_center";
    shell.hidden = true;
    shell.innerHTML = `
      <button class="notification_button" type="button" aria-label="Open notifications" aria-expanded="false">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg>
        <span class="notification_count" hidden>0</span>
      </button>
      <section class="notification_panel" aria-label="Notifications" hidden>
        <header><div><strong>Notifications</strong><small class="notification_summary"></small></div><button class="notification_read_all" type="button">Mark all read</button></header>
        <div class="notification_list"><p class="notification_empty">Loading…</p></div>
      </section>`;
    navRight.prepend(shell);
    const button=shell.querySelector(".notification_button");
    const panel=shell.querySelector(".notification_panel");
    const list=shell.querySelector(".notification_list");
    const badge=shell.querySelector(".notification_count");
    const summary=shell.querySelector(".notification_summary");
    let csrf;
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
    button.addEventListener("click",async event=>{event.stopPropagation();panel.hidden=!panel.hidden;button.setAttribute("aria-expanded",String(!panel.hidden));if(!panel.hidden)await load();});
    list.addEventListener("click",async event=>{const row=event.target.closest(".notification_item");if(!row)return;try{if(row.classList.contains("unread"))await api("/"+row.dataset.id+"/read","PATCH");if(row.dataset.link)location.href=row.dataset.link;}catch(error){summary.textContent=error.message;}});
    shell.querySelector(".notification_read_all").addEventListener("click",async()=>{try{await api("/read-all","PATCH");await load();}catch(error){summary.textContent=error.message;}});
    document.addEventListener("click",event=>{if(!shell.contains(event.target)){panel.hidden=true;button.setAttribute("aria-expanded","false");}});
    load();setInterval(load,30000);
  }

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
