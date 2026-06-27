import { Mail, Menu, X } from "lucide-react";
import { DOT, navItems } from "../data/content.js";
import { BrandMark, Button, NavButton } from "../components/ui.jsx";

export function AppLayout({ children, path, navigate, mobileOpen, setMobileOpen }) {
  return (
    <div className="min-h-screen overflow-hidden bg-abhay-background text-abhay-text">
      <BackgroundSystem />
      <Header path={path} navigate={navigate} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen} />
      <main className="route-fade relative z-10">{children}</main>
      <Footer navigate={navigate} />
    </div>
  );
}

function BackgroundSystem() {
  return (
    <div aria-hidden="true" className="fixed inset-0 z-0 overflow-hidden">
      <div className="absolute inset-0 bg-ai-grid bg-[length:44px_44px] opacity-35" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_6%,rgba(249,115,22,0.2),transparent_30%),radial-gradient(circle_at_80%_12%,rgba(34,211,238,0.18),transparent_30%),radial-gradient(circle_at_62%_86%,rgba(20,184,166,0.12),transparent_30%),linear-gradient(180deg,rgba(5,8,22,0.2),#050816_80%)]" />
      <div className="orb left-[-120px] top-[120px] h-72 w-72 rounded-full bg-abhay-orange" />
      <div className="orb right-[-120px] top-[360px] h-80 w-80 rounded-full bg-abhay-cyan" />
    </div>
  );
}

function Header({ path, navigate, mobileOpen, setMobileOpen }) {
  const activePath = path === "/signin" ? "/login" : path;

  return (
    <header className="sticky top-0 z-40 border-b border-white/10 bg-abhay-background/80 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
        <button className="inline-flex shrink-0 items-center gap-3 text-left" type="button" onClick={() => navigate("/")}>
          <BrandMark />
          <span>
            <span className="block text-sm font-bold tracking-wide sm:text-base">ABHAY Accounting OS</span>
            <span className="block text-xs text-abhay-muted">by ANVRITAI</span>
          </span>
        </button>

        <nav className="hidden min-w-0 flex-1 items-center justify-center gap-1 xl:flex">
          {navItems.map((item) => (
            <NavButton key={item.path} active={activePath === item.path} onClick={() => navigate(item.path)}>
              {item.label}
            </NavButton>
          ))}
        </nav>

        <div className="hidden shrink-0 items-center gap-3 md:flex">
          <Button variant="ghost" onClick={() => navigate("/login")}>Login</Button>
          <Button onClick={() => navigate("/signup")}>Start Trial</Button>
        </div>

        <button
          className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/5 xl:hidden"
          type="button"
          onClick={() => setMobileOpen(true)}
          aria-label="Open menu"
        >
          <Menu size={20} />
        </button>
      </div>

      {mobileOpen ? (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm xl:hidden">
          <div className="ml-auto flex h-full w-[min(90vw,390px)] flex-col border-l border-white/10 bg-abhay-background p-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <div className="inline-flex items-center gap-3">
                <BrandMark />
                <div>
                  <p className="font-bold">ABHAY</p>
                  <p className="text-xs text-abhay-muted">Accounting OS</p>
                </div>
              </div>
              <button
                className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-white/10 bg-white/5"
                type="button"
                onClick={() => setMobileOpen(false)}
                aria-label="Close menu"
              >
                <X size={19} />
              </button>
            </div>
            <div className="mt-6 grid flex-1 content-start gap-2 overflow-y-auto">
              {navItems.map((item) => (
                <NavButton
                  key={item.path}
                  active={activePath === item.path}
                  onClick={() => {
                    navigate(item.path);
                    setMobileOpen(false);
                  }}
                >
                  {item.label}
                </NavButton>
              ))}
              <NavButton
                active={activePath === "/login"}
                onClick={() => {
                  navigate("/login");
                  setMobileOpen(false);
                }}
              >
                Login
              </NavButton>
            </div>
            <Button onClick={() => navigate("/signup")}>Start Trial</Button>
          </div>
        </div>
      ) : null}
    </header>
  );
}

function Footer({ navigate }) {
  return (
    <footer className="relative z-10 border-t border-white/10 px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto grid max-w-7xl gap-6 text-sm text-abhay-muted lg:grid-cols-[1fr_auto] lg:items-center">
        <p>ABHAY Accounting OS by ANVRITAI {DOT} AI Memory OS for Finance & Accounting {DOT} Made in Bharat</p>
        <div className="flex flex-wrap gap-4">
          {[
            ["Home", "/"],
            ["Features", "/features"],
            ["Pricing", "/pricing"],
            ["Login", "/login"],
            ["Signup", "/signup"]
          ].map(([label, nextPath]) => (
            <button key={label} className="hover:text-abhay-orange" type="button" onClick={() => navigate(nextPath)}>
              {label}
            </button>
          ))}
          <a className="inline-flex items-center gap-1 hover:text-abhay-orange" href="mailto:founder@anvritai.com">
            <Mail size={14} /> Contact
          </a>
        </div>
      </div>
    </footer>
  );
}
