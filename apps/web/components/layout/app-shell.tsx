"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Banknote,
  Bot,
  CreditCard,
  FileText,
  Gauge,
  Upload,
  Landmark,
  LogOut,
  LogIn,
  Menu,
  Sparkles,
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

const navItems: Array<{ href: string; label: string; icon: typeof Gauge }> = [
  { href: "/dashboard", label: "Dashboard", icon: Gauge },
  { href: "/ai-workbench", label: "AI Workbench", icon: Bot },
  { href: "/import-data", label: "Import Data", icon: Upload },
  { href: "/subscription", label: "Subscription", icon: CreditCard },
  { href: "/command-center", label: "Command Center", icon: Sparkles },
  { href: "/automation-center", label: "Automation", icon: Bot },
  { href: "/invoices", label: "Invoices", icon: FileText },
  { href: "/ai-accountant", label: "AI Accountant", icon: Bot },
  { href: "/financial-intelligence", label: "Financial Intel", icon: TrendingUp },
  { href: "/bank-reconciliation", label: "Bank Reco", icon: Banknote }
];

const LAST_COMPANY_KEY = "abhay.lastCompanyId";
const AUTH_NOTICE_KEY = "abhay_auth_notice";

export function AppShell({ children }: Readonly<{ children: React.ReactNode }>) {
  const pathname = usePathname();
  const router = useRouter();
  const supabase = createSupabaseBrowserClient();
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
    <div className="abhay-shell-bg min-h-screen lg:grid lg:grid-cols-[292px_1fr]">
      <header className="sticky top-0 z-30 m-3 flex items-center justify-between rounded-2xl border border-white/70 bg-white/80 px-3 py-3 shadow-lg backdrop-blur-xl lg:hidden">
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
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 w-[280px] p-3 transition-transform lg:sticky lg:top-0 lg:h-screen lg:translate-x-0 lg:p-4",
          isOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex h-full flex-col rounded-3xl border border-white/75 bg-white/80 p-4 shadow-[0_24px_70px_rgba(15,23,42,0.14)] backdrop-blur-2xl">
          <div className="mb-5 flex items-center justify-between gap-3">
            <Brand />
            <Button className="lg:hidden" type="button" variant="ghost" onClick={() => setIsOpen(false)} title="Close navigation">
              <X size={18} />
            </Button>
          </div>
          <span className="ai-badge mb-5 w-fit">AI Accounting Alpha</span>
          {alphaDemoMode || isDemoSession ? (
            <p className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800">
              Alpha Demo Mode {isDemoSession ? "active" : "available"}
            </p>
          ) : null}
          <nav className="space-y-1.5">
            {navItems.map((item) => {
              const Icon = item.icon;
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href as never}
                  onClick={() => setIsOpen(false)}
                  className={cn(
                    "flex h-11 items-center gap-3 rounded-2xl px-3 text-sm font-semibold text-muted-foreground transition duration-300 hover:-translate-y-0.5 hover:bg-orange-50 hover:text-foreground",
                    active &&
                      "bg-gradient-to-r from-orange-500 to-amber-400 text-white shadow-[0_16px_38px_rgba(249,115,22,0.3)] hover:from-orange-500 hover:to-amber-400 hover:text-white"
                  )}
                >
                  <Icon size={17} />
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <div className="mt-auto rounded-2xl border border-orange-100 bg-gradient-to-br from-orange-50 to-white p-3 shadow-inner">
            <p className="text-sm font-semibold text-slate-900">Demo Mode</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              Seed a complete demo company with ledgers, vouchers, invoices, GST and bank data.
            </p>
            <Button className="mt-3 w-full ai-glow" type="button" onClick={createDemoCompany} disabled={isBusy}>
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
      <div className="min-w-0">{children}</div>
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

function Brand() {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-orange-500 to-amber-400 text-primary-foreground shadow-[0_12px_30px_rgba(249,115,22,0.28)]">
        <Landmark size={19} />
      </span>
      <div>
        <p className="text-sm font-bold text-slate-950">ABHAY Accounting OS</p>
        <p className="text-xs font-medium text-muted-foreground">by ANVRITAI</p>
      </div>
    </div>
  );
}
