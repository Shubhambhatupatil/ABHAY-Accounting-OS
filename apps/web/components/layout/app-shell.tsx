"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  Banknote,
  Bot,
  CreditCard,
  FileCog,
  FileText,
  Gauge,
  Upload,
  Landmark,
  LogOut,
  LogIn,
  Menu,
  Sparkles,
  Settings,
  Shield,
  TrendingUp,
  UserPlus,
  X
} from "lucide-react";
import { accountingApi } from "@/lib/api/accounting";
import {
  clearLocalDemoSession,
  getAccessToken,
  getLocalDemoToken,
  isAlphaDemoModeEnabled,
  startLocalDemoSession
} from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type NavItem = { href: string; label: string; icon: typeof Gauge };

const navSections: Array<{ title: string; items: NavItem[] }> = [
  {
    title: "Main",
    items: [
      { href: "/dashboard", label: "Dashboard", icon: Gauge },
      { href: "/ai-workbench", label: "AI Workbench", icon: Bot },
      { href: "/upload-invoice", label: "Upload Invoice", icon: Upload },
      { href: "/entries", label: "Entries / Ledger", icon: FileCog },
      { href: "/reports", label: "Reports", icon: TrendingUp }
    ]
  },
  {
    title: "Operations",
    items: [
      { href: "/import-data", label: "Import Data", icon: Upload },
      { href: "/command-center", label: "Command Center", icon: Sparkles },
      { href: "/automation-center", label: "Automation", icon: Bot },
      { href: "/invoices", label: "Invoices", icon: FileText },
      { href: "/bank-reconciliation", label: "Bank Reco", icon: Banknote }
    ]
  },
  {
    title: "Intelligence",
    items: [
      { href: "/ai-accountant", label: "AI Accountant", icon: Bot },
      { href: "/financial-intelligence", label: "Financial Intel", icon: TrendingUp }
    ]
  },
  {
    title: "Account",
    items: [
      { href: "/subscription", label: "Billing", icon: CreditCard },
      { href: "/settings", label: "Settings", icon: Settings },
      { href: "/admin", label: "Admin", icon: Shield }
    ]
  }
];

const LAST_COMPANY_KEY = "abhay.lastCompanyId";
const AUTH_NOTICE_KEY = "abhay_auth_notice";

export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  const pathname = usePathname();
  const router = useRouter();
  const supabase = useMemo(() => createSupabaseBrowserClient(), []);
  const [isOpen, setIsOpen] = useState(false);
  const [status, setStatus] = useState("");
  const [isBusy, setIsBusy] = useState(false);
  const [authStatus, setAuthStatus] = useState<"checking" | "authenticated" | "missing">("checking");
  const alphaDemoMode = isAlphaDemoModeEnabled();
  const isDemoSession = Boolean(getLocalDemoToken());

  useEffect(() => {
    let active = true;
    const timeoutId = window.setTimeout(() => {
      if (active) setAuthStatus("missing");
    }, 3500);

    getAccessToken(supabase)
      .then((token) => {
        if (!active) return;
        setAuthStatus(token ? "authenticated" : "missing");
      })
      .catch(() => {
        if (active) setAuthStatus("missing");
      })
      .finally(() => window.clearTimeout(timeoutId));

    return () => {
      active = false;
      window.clearTimeout(timeoutId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function createDemoCompany() {
    setIsBusy(true);
    setStatus("");
    try {
      const token = await getAccessToken(supabase);
      if (!token) {
        setStatus("Sign in first.");
        return;
      }
      const demo = await accountingApi.createDemoCompany(token);
      setStatus(
        demo.seeded_ledgers
          ? `Demo ready: ${demo.seeded_ledgers} ledgers, ${demo.seeded_vouchers} vouchers, ${demo.seeded_invoices} invoices.`
          : "Demo company already exists."
      );
      router.push("/dashboard");
      router.refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Demo creation failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function logout() {
    setIsBusy(true);
    setStatus("");
    try {
      await supabase.auth.signOut();
      clearAuthLocalStorage();
      window.sessionStorage.setItem(AUTH_NOTICE_KEY, "Logged out successfully.");
      router.replace("/login");
      router.refresh();
    } finally {
      setIsBusy(false);
    }
  }

  function continueInAlphaDemoMode() {
    startLocalDemoSession();
    setAuthStatus("authenticated");
    setStatus("Alpha Demo Mode active.");
    router.push("/dashboard");
    router.refresh();
  }

  if (authStatus !== "authenticated") {
    return (
      <main className="abhay-shell-bg flex min-h-screen items-center justify-center p-4">
        <section className="glass-panel max-w-md p-6 text-center">
          <span className="ai-badge mb-4">ABHAY Alpha v0.1 Auth Stable</span>
          <h1 className="text-2xl font-semibold">Please login or continue in Alpha Demo Mode</h1>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">
            {authStatus === "checking"
              ? "Checking your secure session..."
              : "For Alpha testing, use Alpha Demo Mode. Production login will be hardened before paid launch."}
          </p>
          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            <Button type="button" variant="secondary" onClick={() => router.push("/login")}>
              <LogIn size={17} />
              Login
            </Button>
            <Button type="button" onClick={continueInAlphaDemoMode}>
              <Sparkles size={17} />
              Alpha Demo Mode
            </Button>
          </div>
          <Link className="mt-4 inline-flex items-center justify-center gap-2 text-sm font-semibold text-primary" href="/signup">
            <UserPlus size={16} />
            Create account
          </Link>
        </section>
      </main>
    );
  }

  return (
    <div className="abhay-shell-bg min-h-screen overflow-x-hidden lg:grid lg:grid-cols-[292px_1fr]">
      <header className="sticky top-0 z-30 m-3 flex items-center justify-between rounded-2xl border border-[#1F2937] bg-[#050816]/95 px-3 py-3 shadow-[0_18px_60px_rgba(0,0,0,0.35)] backdrop-blur-xl lg:hidden">
        <Brand />
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" onClick={logout} title="Logout" disabled={isBusy}>
            <LogOut size={18} />
          </Button>
          <Button type="button" variant="secondary" onClick={() => setIsOpen(true)} title="Open navigation">
            <Menu size={18} />
          </Button>
        </div>
      </header>
      {isOpen ? (
        <button
          aria-label="Close navigation overlay"
          className="fixed inset-0 z-30 bg-black/70 backdrop-blur-sm lg:hidden"
          type="button"
          onClick={() => setIsOpen(false)}
        />
      ) : null}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 w-[min(88vw,320px)] p-3 transition-transform duration-300 lg:sticky lg:top-0 lg:h-screen lg:w-auto lg:translate-x-0 lg:p-4",
          isOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex h-full min-h-0 flex-col rounded-3xl border border-[#1F2937] bg-[#050816]/96 p-4 shadow-[0_30px_100px_rgba(0,0,0,0.45)] backdrop-blur-2xl">
          <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
            <Brand />
            <Button className="lg:hidden" type="button" variant="ghost" onClick={() => setIsOpen(false)} title="Close navigation">
              <X size={18} />
            </Button>
          </div>
          <span className="ai-badge mb-4 w-fit shrink-0 border-[#FFD700]/25 bg-[#FFD700]/10 text-[#FFD700]">AI Accounting Alpha</span>
          {alphaDemoMode || isDemoSession ? (
            <p className="mb-4 shrink-0 rounded-2xl border border-[#FFD700]/25 bg-[#FFD700]/10 px-3 py-2 text-xs font-semibold text-[#FFE88A]">
              Alpha Demo Mode {isDemoSession ? "active" : "available"}
            </p>
          ) : null}
          <nav className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain pr-1">
            {navSections.map((section) => (
              <div key={section.title} className="space-y-2">
                <p className="px-3 text-[11px] font-bold uppercase tracking-[0.18em] text-[#A1A1AA]">{section.title}</p>
                <div className="space-y-1.5">
                  {section.items.map((item) => (
                    <NavLinkItem
                      key={item.href}
                      item={item}
                      active={isActiveRoute(pathname, item.href)}
                      onNavigate={() => setIsOpen(false)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </nav>
          <div className="mt-4 shrink-0 rounded-2xl border border-[#FFD700]/20 bg-gradient-to-br from-[#FFD700]/10 via-[#FF6B00]/10 to-[#050816] p-3 shadow-inner">
            <p className="text-sm font-semibold text-white">Demo Mode</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              Seed a complete demo company with ledgers, vouchers, invoices, GST and bank data.
            </p>
            <Button className="mt-3 w-full border border-[#FFD700]/30 bg-[#FFD700]/15 text-[#FFE88A] shadow-[0_0_28px_rgba(255,215,0,0.12)] hover:bg-[#FFD700]/20" type="button" onClick={createDemoCompany} disabled={isBusy}>
              <Sparkles size={17} />
              Create demo
            </Button>
            {status ? <p className="mt-2 text-xs text-muted-foreground">{status}</p> : null}
            <Button className="mt-3 w-full" type="button" variant="secondary" onClick={logout} disabled={isBusy}>
              <LogOut size={17} />
              Logout
            </Button>
          </div>
        </div>
      </aside>
      <div className="min-w-0 overflow-x-hidden">{children}</div>
    </div>
  );
}

function clearAuthLocalStorage() {
  clearLocalDemoSession();
  window.localStorage.removeItem(LAST_COMPANY_KEY);
  for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
    const key = window.localStorage.key(index);
    if (!key) continue;
    if (key.startsWith("sb-") || key.startsWith("abhay_") || key.startsWith("abhay.")) {
      window.localStorage.removeItem(key);
    }
  }
}

function NavLinkItem({
  item,
  active,
  onNavigate
}: Readonly<{
  item: NavItem;
  active: boolean;
  onNavigate: () => void;
}>) {
  const Icon = item.icon;
  return (
    <Link
      href={item.href as never}
      onClick={onNavigate}
      className={cn(
        "group flex min-h-12 items-center gap-3 rounded-2xl border border-transparent px-3 text-sm font-semibold text-[#A1A1AA] transition duration-300 hover:-translate-y-0.5 hover:border-[#FFD700]/20 hover:bg-[#FFD700]/10 hover:text-[#F8FAFC] hover:shadow-[0_0_28px_rgba(255,215,0,0.08)]",
        active &&
          "border-[#FFD700]/45 bg-gradient-to-r from-[#FFD700]/24 via-[#FACC15]/14 to-[#FF6B00]/10 text-[#F8FAFC] shadow-[0_16px_38px_rgba(255,215,0,0.14)]"
      )}
    >
      <span
        className={cn(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-[#1F2937] bg-[#111827] text-[#A1A1AA] transition group-hover:border-[#FFD700]/30 group-hover:text-[#FFD700]",
          active && "border-[#FFD700]/40 bg-[#FFD700]/15 text-[#FFD700]"
        )}
      >
        <Icon size={16} />
      </span>
      <span className="min-w-0 flex-1 truncate">{item.label}</span>
    </Link>
  );
}

function isActiveRoute(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

function Brand() {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-[#FFD700]/30 bg-gradient-to-br from-[#FFD700] via-[#FACC15] to-[#FF6B00] text-[#050816] shadow-[0_12px_30px_rgba(255,215,0,0.2)]">
        <Landmark size={19} />
      </span>
      <div>
        <p className="text-sm font-bold text-white">ABHAY Accounting OS</p>
        <p className="text-xs font-medium text-muted-foreground">by ANVRITAI</p>
      </div>
    </div>
  );
}
