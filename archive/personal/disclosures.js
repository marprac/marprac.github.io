(() => {
  "use strict";

  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const controllers = [];

  document.querySelectorAll(".research-item, .project-notes").forEach((details) => {
    const summary = details.querySelector("summary");
    const panel = details.querySelector(".research-panel, .project-panel");
    if (!summary || !panel) return;

    const group = details.classList.contains("research-item") ? "research" : "projects";
    const canAnimate = typeof panel.animate === "function";
    let animation = null;
    let targetOpen = details.open;

    function finish(open) {
      details.open = open;
      targetOpen = open;
      delete details.dataset.state;
      if (animation) animation.cancel();
      animation = null;
    }

    function setOpen(opening) {
      if (opening === targetOpen) return;

      if (reducedMotion.matches || !canAnimate) {
        finish(opening);
        return;
      }

      // Capture the current frame so rapid clicks reverse without jumping.
      const startHeight = details.open ? panel.getBoundingClientRect().height : 0;
      const startOpacity = details.open ? window.getComputedStyle(panel).opacity : "0";
      if (animation) animation.cancel();

      // The content must remain rendered until its closing animation finishes.
      details.open = true;
      targetOpen = opening;
      details.dataset.state = opening ? "opening" : "closing";
      const fullHeight = panel.getBoundingClientRect().height;

      // Animate the layout height, leaving text at its natural scale and position.
      animation = panel.animate([
        { height: `${startHeight}px`, opacity: startOpacity },
        { height: opening ? `${fullHeight}px` : "0px", opacity: opening ? 1 : 0 }
      ], {
        duration: opening ? 480 : 340,
        easing: "cubic-bezier(.22, 1, .36, 1)",
        fill: "both"
      });

      const currentAnimation = animation;
      currentAnimation.onfinish = () => {
        if (animation === currentAnimation) finish(opening);
      };
    }

    const controller = { group, close: () => setOpen(false) };
    controllers.push(controller);

    function closeOtherEntries() {
      for (const other of controllers) {
        if (other !== controller && other.group === group) other.close();
      }
    }

    function toggleEntry() {
      const opening = !targetOpen;
      if (opening) closeOtherEntries();
      setOpen(opening);
    }

    summary.addEventListener("click", (event) => {
      event.preventDefault();
      toggleEntry();
    });

    if (group === "projects") {
      const project = details.closest(".project");
      if (project) {
        project.classList.add("project-clickable");
        project.addEventListener("click", (event) => {
          // The native summary already handles its own click and keyboard activation.
          if (event.defaultPrevented || event.button > 0) return;
          if (event.target.closest("summary, a, button, input, select, textarea, label, [contenteditable], [role='button'], [role='link']")) return;

          // Selecting a passage in the notes should not collapse the project.
          const selection = window.getSelection();
          if (selection && !selection.isCollapsed && project.contains(selection.anchorNode)) return;

          summary.focus({ preventScroll: true });
          toggleEntry();
        });
      }
    }

    details.addEventListener("toggle", () => {
      if (!animation) {
        targetOpen = details.open;
        if (targetOpen) closeOtherEntries();
      }
    });

    reducedMotion.addEventListener("change", () => {
      if (reducedMotion.matches && animation) finish(targetOpen);
    });

    // Return to natural height if the layout changes during an animation.
    window.addEventListener("resize", () => {
      if (animation) finish(targetOpen);
    }, { passive: true });
  });
})();
