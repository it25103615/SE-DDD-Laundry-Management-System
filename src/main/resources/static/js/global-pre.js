document.addEventListener("DOMContentLoaded", () => {
  const path = location.pathname.toLowerCase();
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
  const one = document.createElement("i"),
    two = document.createElement("i");
  one.className = "v4_shape v4_shape_one";
  two.className = "v4_shape v4_shape_two";
  document.body.append(one, two);
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
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
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
  document.querySelectorAll(".custom_button").forEach((button) =>
    button.addEventListener("click", (event) => {
      const ripple = document.createElement("span");
      const rect = button.getBoundingClientRect();
      ripple.style.cssText = `position:absolute;width:12px;height:12px;border-radius:50%;background:rgba(255,255,255,.48);left:${event.clientX - rect.left - 6}px;top:${event.clientY - rect.top - 6}px;transform:scale(0);animation:ripple .55s ease-out;pointer-events:none`;
      button.appendChild(ripple);
      setTimeout(() => ripple.remove(), 600);
    }),
  );
});
const style = document.createElement("style");
style.textContent = "@keyframes ripple{to{transform:scale(18);opacity:0}}";
document.head.appendChild(style);
