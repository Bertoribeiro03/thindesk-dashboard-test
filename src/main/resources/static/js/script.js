"use strict";
const sidebar = document.getElementById("sidebar");
const toggle = document.getElementById("sidebar-toggle");
const backdrop = document.getElementById("sidebar-backdrop");
if (sidebar && toggle && backdrop) {
  const setOpen = (open) => {
    sidebar.classList.toggle("is-open", open);
    toggle.setAttribute("aria-expanded", String(open));
    backdrop.hidden = !open;
  };
  toggle.addEventListener("click", () => setOpen(!sidebar.classList.contains("is-open")));
  backdrop.addEventListener("click", () => setOpen(false));
  document.addEventListener("keydown", (event) => { if (event.key === "Escape") setOpen(false); });
}
