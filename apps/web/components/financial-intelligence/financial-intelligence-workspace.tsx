"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Banknote, Bot, Calendar, FileText, Loader2, RefreshCw, TrendingUp } from "lucide-react";
import { accountingApi, Company } from "@/lib/api/accounting";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import {
  financialIntelligenceApi,
  FinancialSummary,
  Insight
} from "@/lib/api/financial-intelligence";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export function FinancialIntelligenceWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [summary, setSummary] = useState<FinancialSummary | null>(null);
  const [status, setStatus] = useState("Loading financial intelligence");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      if (!accessToken) {
        setStatus("Sign in to load financial intelligence.");
        return;
      }
      accountingApi
        .companies(accessToken)
        .then((items) => {
          setCompanies(items);
          setCompanyId(items[0]?.id ?? "");
          setStatus(items.length ? "Select a month to analyze" : "No company membership found.");
        })
        .catch((error: Error) => setStatus(error.message));
    });
  }, [supabase]);

  async function refresh(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) return;
    setIsBusy(true);
    try {
      const result = await financialIntelligenceApi.summary(selectedCompanyId, token, month);
      setSummary(result);
      setStatus("Financial intelligence refreshed from posted accounting data.");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to load financial intelligence.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) void refresh(companyId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  const hasData = useMemo(() => {
    if (!summary) return false;
    return [
      summary.profit.revenue,
      summary.profit.expenses,
      summary.cashflow.cash_position,
      summary.gst.gst_collected,
      summary.gst.gst_input_paid
    ].some((value) => Number(value) !== 0);
  }, [summary]);

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
              <TrendingUp size={22} aria-hidden="true" />
            </span>
            <div>
              <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">AI Active</span>
              <h1 className="text-2xl font-semibold sm:text-3xl">Real-Time Financial Intelligence</h1>
              <p className="mt-1 text-sm text-white/80">Profit, cash flow, GST-ready insights, and owner insights from posted entries</p>
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <select className="premium-select text-slate-900" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
              {companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}
            </select>
            <Link className="premium-link text-slate-900" href="/dashboard">Accounting</Link>
          </div>
          </div>
        </header>

        <div className="glass-card grid gap-3 p-4 md:grid-cols-[220px_140px_1fr]">
          <div className="relative">
            <Calendar className="pointer-events-none absolute left-3 top-3 text-muted-foreground" size={17} />
            <Input className="pl-9" type="month" value={month} onChange={(event) => setMonth(event.target.value)} />
          </div>
          <Button type="button" onClick={() => refresh()} disabled={isBusy || !companyId}>
            {isBusy ? <Loader2 className="animate-spin" size={17} /> : <RefreshCw size={17} />}
            Refresh
          </Button>
          <p className="flex items-center text-sm text-muted-foreground">{status}</p>
        </div>

        {summary && !hasData ? (
          <div className="empty-state">
            No posted accounting data exists for the selected month. Financial intelligence will appear after vouchers are posted.
          </div>
        ) : null}

        {summary ? (
          <>
            <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <Kpi title="Revenue" value={summary.profit.revenue} icon="profit" />
              <Kpi title="Expenses" value={summary.profit.expenses} icon="expense" />
              <Kpi title="Profit" value={summary.profit.profit} icon="profit" />
              <Kpi title="Profit Margin" value={`${summary.profit.profit_margin}%`} plain />
              <Kpi title="Cash Position" value={summary.cashflow.cash_position} icon="cash" />
              <Kpi title="Receivables" value={summary.cashflow.receivables} icon="cash" />
              <Kpi title="Payables" value={summary.cashflow.payables} icon="expense" />
              <Kpi title="GST Assistance" value={summary.gst.current_gst_payable} icon="gst" />
            </section>

            <section className="grid gap-3 lg:grid-cols-3">
              <TrendCard title="Profit Trend" value={summary.profit.profit_trend_percent ? `${summary.profit.profit_trend_percent}%` : "Unavailable"} detail={`Previous month profit ${formatMoney(summary.profit.previous_month_profit)}`} />
              <TrendCard title="Cash Risk" value={summary.cashflow.cash_risk_level.toUpperCase()} detail={summary.cashflow.cash_risk_reason} warning={summary.cashflow.cash_risk_level !== "low"} />
              <TrendCard title="GST-ready Estimate" value={formatMoney(summary.gst.estimated_month_end_liability)} detail={`Collected ${formatMoney(summary.gst.gst_collected)} · Input ${formatMoney(summary.gst.gst_input_paid)} · Needs CA review before filing`} />
            </section>

            <p className="empty-state">ABHAY provides GST assistance. Please verify before filing.</p>

            <section className="glass-panel p-4">
              <div className="mb-3 flex items-center gap-2">
                <Bot size={18} className="text-primary" />
                <h2 className="text-base font-semibold">Owner Insight Feed</h2>
              </div>
              <div className="grid gap-3 lg:grid-cols-2">
                {summary.insights.map((insight) => <InsightCard key={insight.title} insight={insight} />)}
              </div>
            </section>
          </>
        ) : null}
      </section>
    </main>
  );
}

function Kpi({ title, value, icon, plain = false }: { title: string; value: string; icon?: string; plain?: boolean }) {
  const Icon = icon === "cash" ? Banknote : icon === "gst" ? FileText : TrendingUp;
  return (
    <div className="glass-card float-card p-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">{title}</p>
        <span className="rounded-xl bg-orange-50 p-2 text-primary"><Icon size={18} /></span>
      </div>
      <p className="mt-3 text-2xl font-semibold">{plain ? value : formatMoney(value)}</p>
    </div>
  );
}

function TrendCard({ title, value, detail, warning = false }: { title: string; value: string; detail: string; warning?: boolean }) {
  return (
    <div className={cn("glass-card float-card p-4", warning && "border-destructive/40 bg-destructive/10")}>
      <p className="text-sm text-muted-foreground">{title}</p>
      <p className="mt-2 text-xl font-semibold">{value}</p>
      <p className="mt-2 text-sm text-muted-foreground">{detail}</p>
    </div>
  );
}

function InsightCard({ insight }: { insight: Insight }) {
  const warning = insight.severity === "warning";
  return (
    <div className={cn("rounded-xl border border-white/70 p-3 shadow-sm", warning ? "border-destructive/40 bg-destructive/10" : "bg-white/70")}>
      <div className="flex items-center gap-2">
        {warning ? <AlertTriangle size={17} className="text-destructive" /> : <TrendingUp size={17} className="text-primary" />}
        <p className="font-medium">{insight.title}</p>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">{insight.message}</p>
    </div>
  );
}

function formatMoney(value: string | number | undefined) {
  const amount = Number(value ?? 0);
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount);
}
