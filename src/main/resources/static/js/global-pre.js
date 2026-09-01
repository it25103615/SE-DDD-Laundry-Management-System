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
