"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Bot, CheckCircle2, Loader2, RefreshCw, Send, Upload, XCircle } from "lucide-react";
import { AiCommandResponse, runAiCommand } from "@/lib/api/ai-command";
import { AbhayHealthStatus, checkAbhayHealth } from "@/lib/api/health";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

const testEntry = "30 people x 400 recharge with GST 9%";
const examples = [
  testEntry,
  "400 recharge smartphone back office 30 people",
  "Calculate 10000 + 2500 - 500 with GST 18%",
  "Paid diesel expense 2500 cash"
];

type BackendStatus = "checking" | "online" | "offline";

type WorkbenchCompany = {
  id: string;
  legal_name: string;
};

export function AiWorkbench() {
  const supabase = useMemo(() => createSupabaseBrowserClient(), []);
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<WorkbenchCompany[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [commandText, setCommandText] = useState(testEntry);
  const [commandResult, setCommandResult] = useState<AiCommandResponse | null>(null);
  const [entryText, setEntryText] = useState(examples[1]);
  const [status, setStatus] = useState("Loading AI Workbench");
  const [statusTone, setStatusTone] = useState<"loading" | "success" | "error" | "info">("loading");
  const [backendStatus, setBackendStatus] = useState<BackendStatus>("checking");
  const [healthStatus, setHealthStatus] = useState<AbhayHealthStatus>({
    backendOnline: false,
    aiReady: false,
    message: "Checking ABHAY Intelligence",
    source: "/api/abhay-health",
    lastCheck: "checking"
  });
  const [isBusy, setIsBusy] = useState(false);

  const loadCompanies = useCallback(async (accessToken: string) => {
    setStatusTone("loading");
    const items = await fetchWorkbenchCompanies(accessToken);
    const rows = items.length
      ? items
      : [await createWorkbenchCompany(accessToken, { legal_name: "ANVRITAI Demo Company" })];
    setCompanies(rows);
    setCompanyId((currentCompanyId) =>
      rows.some((company) => company.id === currentCompanyId) ? currentCompanyId : rows[0].id
    );
    setStatus("AI Workbench ready");
    setStatusTone("success");
    return rows;
  }, []);

  useEffect(() => {
    let active = true;

    async function runHealthCheck() {
      setBackendStatus("checking");
      const health = await checkAbhayHealth();
      if (!active) return;
      setHealthStatus(health);
      setBackendStatus(health.backendOnline && health.aiReady ? "online" : "offline");
    }

    void runHealthCheck();
    const intervalId = window.setInterval(() => {
      void runHealthCheck();
    }, 30_000);
    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      setStatus("Loading AI Workbench");
      setStatusTone("loading");
      try {
        const accessToken = await getAccessToken(supabase);
        if (!active) return;
        setToken(accessToken);
        if (!accessToken) {
          setCompanies([]);
          setCompanyId("");
          setStatus("Please login or continue in Alpha Demo Mode.");
          setStatusTone("error");
          return;
        }
        await loadCompanies(accessToken);
      } catch (error) {
        if (active) {
          setStatus(error instanceof Error ? error.message : "Unable to load AI Workbench.");
          setStatusTone("error");
        }
      }
    }

    void bootstrap();
    return () => {
      active = false;
    };
  }, [loadCompanies, supabase]);

  async function analyzeCommand(commandOverride?: string) {
    const command = (commandOverride ?? commandText).trim();
    if (!command) {
      setStatus("Enter an AI command to analyze.");
      setStatusTone("error");
      return;
    }
    setIsBusy(true);
    setCommandResult(null);
    setStatus("ABHAY AI is calculating and analyzing...");
    setStatusTone("loading");
    try {
      const result = await runAiCommand(command, {
        companyId,
        source: "ai-workbench",
        hasToken: Boolean(token)
      });
      setCommandResult(result);
      setStatus(result.base_amount != null ? "Accounting calculation ready." : "ABHAY AI command analyzed.");
      setStatusTone("success");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "AI command failed.");
      setStatusTone("error");
    } finally {
      setIsBusy(false);
    }
  }

  async function parseText(textOverride?: string) {
    const text = (textOverride ?? entryText).trim();
    if (!text) {
      setStatus("Enter transaction text before parsing.");
      setStatusTone("error");
      return;
    }
    setCommandText(text);
    await analyzeCommand(text);
  }

  async function runTestEntry() {
    setEntryText(testEntry);
    setCommandText(testEntry);
    await analyzeCommand(testEntry);
  }

  function uploadPdf(file: File | null) {
    if (!file) return;
    if (file.type.startsWith("image/")) {
      setStatus("Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.");
      setStatusTone("info");
      return;
    }
    if (file.type !== "application/pdf") {
      setStatus("Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.");
      setStatusTone("error");
      return;
    }
    setStatus("Alpha supports text PDFs only. For calculation now, use one-line AI entry.");
    setStatusTone("info");
  }

  async function recheckAiEngine() {
    setBackendStatus("checking");
    const health = await checkAbhayHealth();
    setHealthStatus(health);
    setBackendStatus(health.backendOnline && health.aiReady ? "online" : "offline");
  }

  const backendConnected = healthStatus.backendOnline;
  const aiReady = healthStatus.aiReady;
  const canParse = Boolean(entryText.trim());

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-3">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
                <Bot size={22} aria-hidden="true" />
              </span>
              <div>
                <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">AI Active</span>
                <h1 className="text-2xl font-semibold sm:text-3xl">AI Workbench</h1>
                <p className="mt-1 text-sm text-white/80">Real-time AI accounting calculations and review guidance</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <ReadinessBadge
                    ready={backendConnected}
                    readyText="Backend online"
                    pendingText={backendStatus === "checking" ? "Checking backend" : "Backend offline"}
                  />
                  <ReadinessBadge
                    ready={aiReady}
                    readyText="AI Engine ready"
                    pendingText={backendStatus === "checking" ? "Checking AI Engine" : "AI Engine offline"}
                  />
                </div>
              </div>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <select className="premium-select" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
                {!companies.length ? <option value="">No company found</option> : null}
                {companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}
              </select>
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  if (token) void loadCompanies(token);
                }}
                disabled={isBusy || !token}
              >
                <RefreshCw size={17} />
                Refresh
              </Button>
              <Button type="button" variant="secondary" onClick={recheckAiEngine} disabled={isBusy}>
                <RefreshCw size={17} />
                Recheck AI Engine
              </Button>
            </div>
          </div>
        </header>

        <section className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
          <div className="space-y-4">
            <form className="glass-panel p-4 ring-1 ring-[#FFD700]/10" onSubmit={(event) => { event.preventDefault(); void analyzeCommand(); }}>
              <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <span className="ai-badge mb-2">AI Command</span>
                  <h2 className="text-base font-semibold">Run / Analyze</h2>
                </div>
                {commandResult ? <ConfidenceBadge confidence={commandResult.confidence} /> : null}
              </div>
              <div className="grid gap-3 md:grid-cols-[1fr_150px]">
                <Input value={commandText} onChange={(event) => setCommandText(event.target.value)} required />
                <Button className="ai-glow" type="submit" disabled={isBusy || !commandText.trim()}>
                  {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Bot size={17} />}
                  Analyze
                </Button>
              </div>
              {commandResult ? <CommandResultCard result={commandResult} /> : null}
            </form>

            <form className="glass-panel p-4 ring-1 ring-[#00E5FF]/10" onSubmit={(event) => { event.preventDefault(); void parseText(); }}>
              <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <span className="ai-badge mb-2">AI Calculation</span>
                  <h2 className="text-base font-semibold">One-Line Accounting Entry</h2>
                </div>
                <span className="rounded-full border border-[#00E5FF]/20 bg-[#00E5FF]/10 px-3 py-1 text-xs font-semibold text-[#B9F7FF]">
                  Proxy secured
                </span>
              </div>
              <div className="grid gap-3 md:grid-cols-[1fr_140px]">
                <Input value={entryText} onChange={(event) => setEntryText(event.target.value)} required />
                <Button className="ai-glow" type="submit" disabled={isBusy || !canParse}>
                  {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Send size={17} />}
                  Parse
                </Button>
              </div>
              <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-xs text-muted-foreground">
                  Parse now uses the same `/api/ai-command` calculation engine as Analyze.
                </p>
                <Button type="button" variant="secondary" onClick={runTestEntry} disabled={isBusy}>
                  {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Bot size={17} />}
                  Test AI Entry
                </Button>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {examples.map((example) => (
                  <button
                    key={example}
                    type="button"
                    className="rounded-xl border border-[#1F2937] bg-[#111827]/80 px-3 py-2 text-left text-xs text-muted-foreground shadow-sm transition hover:-translate-y-0.5 hover:border-[#00E5FF]/30 hover:bg-[#0F172A]"
                    onClick={() => {
                      setEntryText(example);
                      setCommandText(example);
                    }}
                  >
                    {example}
                  </button>
                ))}
              </div>
            </form>

            <div className="glass-card float-card p-4">
              <h2 className="mb-3 text-base font-semibold">PDF Bill Reader</h2>
              <p className="mb-3 text-sm text-muted-foreground">
                Alpha supports text PDFs only. Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.
              </p>
              <label className="flex min-h-28 cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed border-[#00E5FF]/25 bg-[#00E5FF]/10 p-4 text-center text-sm text-muted-foreground transition hover:-translate-y-0.5 hover:border-[#FF6B00]/30 hover:bg-[#111827]">
                <Upload className="mb-2 text-[#00E5FF]" size={22} />
                <span>Upload text PDF bill or invoice</span>
                <span className="mt-1 text-xs">OCR is not faked. Use one-line AI entry for calculations now.</span>
                <input className="hidden" type="file" accept="application/pdf,image/*" onChange={(event) => uploadPdf(event.target.files?.[0] ?? null)} />
              </label>
            </div>
          </div>

          <aside className="space-y-4">
            <StatusPanel status={status} tone={statusTone} />
            <div className="glass-card float-card p-4">
              <h2 className="mb-2 text-base font-semibold">AI Calculation Scope</h2>
              <div className="grid gap-2">
                {["Addition, subtraction, multiplication and division", "Percentage and GST calculations", "Human review before accounting posting", "Ledger/GST workflow suggestions retained"].map((item, index) => (
                  <p key={`${index}-${item}`} className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3 text-sm text-muted-foreground">
                    {item}
                  </p>
                ))}
              </div>
            </div>
            <div className="glass-card float-card p-4">
              <h2 className="mb-2 text-base font-semibold">CORS-Safe Routing</h2>
              <p className="text-sm leading-6 text-muted-foreground">
                This Workbench calls same-origin Next.js API routes only. The browser no longer calls Render AI endpoints directly.
              </p>
            </div>
          </aside>
        </section>
      </section>
    </main>
  );
}

function CommandResultCard({ result }: { result: AiCommandResponse }) {
  return (
    <div className="mt-4 rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <p className="text-sm font-semibold text-[#F8FAFC]">{result.summary}</p>
        <ConfidenceBadge confidence={result.confidence} />
      </div>
      {result.base_amount != null ? <CommandCalculationPanel result={result} /> : null}
      <div className="mt-3 grid gap-2">
        {result.actions.map((action, index) => (
          <p key={`${index}-${action}`} className="rounded-lg border border-[#1F2937] bg-[#0F172A] p-2 text-sm text-muted-foreground">
            {index + 1}. {action}
          </p>
        ))}
      </div>
    </div>
  );
}

function CommandCalculationPanel({ result }: { result: AiCommandResponse }) {
  const rows = [
    ["Base Amount", result.base_amount != null ? formatMoney(result.base_amount) : "-"],
    [
      "GST Amount",
      result.gst_amount != null
        ? `${formatMoney(result.gst_amount)}${result.gst_rate != null ? ` (${formatRate(result.gst_rate)}%)` : ""}`
        : "GST rate needed"
    ],
    ["Total Amount", result.total != null ? formatMoney(result.total) : "Pending GST rate"]
  ];

  return (
    <div className="mt-3 rounded-xl border border-[#00E5FF]/20 bg-[#00E5FF]/10 p-3">
      <div className="mb-2 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <h3 className="text-sm font-semibold text-[#B9F7FF]">Accounting Calculation</h3>
        {result.calculation ? <span className="text-xs text-muted-foreground">{result.calculation}</span> : null}
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-lg border border-[#1F2937] bg-[#0F172A]/90 p-3">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="mt-1 text-sm font-semibold text-[#F8FAFC]">{value}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function ConfidenceBadge({ confidence }: { confidence: number }) {
  return (
    <span className="inline-flex min-h-7 shrink-0 items-center justify-center rounded-full border border-[#FFD700]/30 bg-[#FFD700]/10 px-3 text-xs font-semibold text-[#FFE88A]">
      Confidence {Math.round(confidence * 100)}%
    </span>
  );
}

function ReadinessBadge({ ready, readyText, pendingText }: { ready: boolean; readyText: string; pendingText: string }) {
  return (
    <span className={cn(
      "inline-flex min-h-7 items-center justify-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold leading-none backdrop-blur",
      ready
        ? "border-[#14B8A6]/30 bg-[#14B8A6]/10 text-[#9FF5EA]"
        : "border-[#FF6B00]/30 bg-[#FF6B00]/10 text-[#FDBA74]"
    )}>
      {ready ? <CheckCircle2 size={14} /> : <Loader2 className="animate-spin" size={14} />}
      {ready ? readyText : pendingText}
    </span>
  );
}

function StatusPanel({ status, tone }: { status: string; tone: "loading" | "success" | "error" | "info" }) {
  return (
    <div
      className={cn(
        "glass-card flex items-center gap-2 px-3 py-2 text-sm",
        tone === "success" && "text-[#9FF5EA]",
        tone === "error" && "border-destructive/30 bg-destructive/10 text-destructive",
        tone === "loading" && "text-amber-700",
        tone === "info" && "text-muted-foreground"
      )}
    >
      {tone === "success" ? <CheckCircle2 size={17} /> : null}
      {tone === "error" ? <XCircle size={17} /> : null}
      {tone === "loading" ? <Loader2 className="animate-spin" size={17} /> : null}
      <span>{status}</span>
    </div>
  );
}

async function fetchWorkbenchCompanies(token: string) {
  const response = await fetch("/api/companies", {
    method: "GET",
    cache: "no-store",
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Unable to load companies." })) as { detail?: string };
    throw new Error(error.detail ?? "Unable to load companies.");
  }
  return response.json() as Promise<WorkbenchCompany[]>;
}

async function createWorkbenchCompany(token: string, body: { legal_name: string }) {
  const response = await fetch("/api/companies", {
    method: "POST",
    cache: "no-store",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Unable to create company." })) as { detail?: string };
    throw new Error(error.detail ?? "Unable to create company.");
  }
  return response.json() as Promise<WorkbenchCompany>;
}

function formatMoney(value: string | number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(value));
}

function formatRate(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
}
