const menuButton = document.querySelector(".menu-button");
const mobileMenu = document.querySelector("#mobileMenu");
const mobileLinks = document.querySelectorAll("#mobileMenu a");
const header = document.querySelector(".site-header");
const forms = document.querySelectorAll("form");

function setMenuOpen(isOpen) {
  if (!mobileMenu || !menuButton) return;
  mobileMenu.classList.toggle("open", isOpen);
  menuButton.setAttribute("aria-expanded", String(isOpen));
}

if (menuButton) {
  menuButton.addEventListener("click", () => {
    setMenuOpen(!mobileMenu.classList.contains("open"));
  });
}

mobileLinks.forEach((link) => {
  link.addEventListener("click", () => setMenuOpen(false));
});

forms.forEach((form) => {
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const button = form.querySelector("button[type='submit']");
    if (!button) return;
    const originalText = button.textContent;
    button.textContent = "Request received";
    button.disabled = true;
    window.setTimeout(() => {
      button.textContent = originalText;
      button.disabled = false;
    }, 1800);
  });
});

window.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    setMenuOpen(false);
  }
});

window.addEventListener("scroll", () => {
  if (header) {
    header.classList.toggle("is-scrolled", window.scrollY > 8);
  }
});
