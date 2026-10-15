(() => {
  const intro = document.querySelector(".laundry-intro");
  const skip = document.querySelector(".laundry-replay");
  if (!intro || !skip) return;
  const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  // Move the intro out of the hero so it covers the entire viewport.
  intro.removeAttribute("aria-hidden");
  intro.setAttribute("aria-label", "LaundryLink opening animation");
  intro.querySelector(".intro-washer").setAttribute("aria-hidden", "true");
  skip.textContent = "Skip intro →";
  skip.setAttribute("aria-label", "Skip opening animation");
  intro.appendChild(skip);
  document.body.prepend(intro);
  if (motion.matches) return;
  const content = document.createElement("div");
  content.className = "landing-content";
  const sections = [...document.body.children].filter((node) =>
    node.matches(".home-topbar, .home-nav, main, .home-footer"),
  );
  if (!sections.length) return;
  sections[0].before(content);
  sections.forEach((node) => content.appendChild(node));
  content.inert = true;
  let finishTimer;
  const finish = () => {
    window.clearTimeout(finishTimer);
    document.body.classList.remove("wash-playing");
    content.inert = false;
    if (content.isConnected) content.replaceWith(...content.childNodes);
  };
  skip.addEventListener("click", () => {
    finish();
    document.querySelector(".home-primary")?.focus({ preventScroll: true });
  });
  motion.addEventListener("change", finish);
  window.addEventListener("pagehide", finish);
  document.body.classList.add("wash-playing");
  finishTimer = window.setTimeout(finish, 5300);
})();
