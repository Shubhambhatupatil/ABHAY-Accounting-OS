"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Brain, FileText, Loader2, RefreshCw } from "lucide-react";
import { accountingApi, Company } from "@/lib/api/accounting";
import { automationApi, AiCfoDashboard, MonthEndClosePack } from "@/lib/api/automation";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";

export function CommandCenterWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [cfo, setCfo] = useState<AiCfoDashboard | null>(null);
  const [pack, setPack] = useState<MonthEndClosePack | null>(null);
  const [status, setStatus] = useState("Loading command center");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      if (!accessToken) return setStatus("Sign in to use command center.");
      accountingApi.companies(accessToken).then((items) => {
        setCompanies(items);
        setCompanyId(items[0]?.id ?? "");
      }).catch((error: Error) => setStatus(error.message));
    });
  }, [supabase]);

  async function refresh(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) return;
    setIsBusy(true);
    try {
      const [cfoRow, closePack] = await Promise.all([
        automationApi.cfo(selectedCompanyId, token),
        automationApi.monthEnd(selectedCompanyId, token)
      ]);
      setCfo(cfoRow);
      setPack(closePack);
      setStatus("Command center updated from live accounting data");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to load command center.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) void refresh(companyId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  return (
    <main className="min-h-screen bg-background p-3 sm:p-5">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="flex flex-col gap-3 border-b pb-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3"><Brain className="text-primary" /><div><h1 className="text-xl font-semibold">AI Command Center</h1><p className="text-sm text-muted-foreground">Profit, GST-ready insights, cash flow, health and alerts without waiting for month end</p></div></div>
          <div className="flex gap-2"><select className="h-10 rounded-md border bg-card px-3 text-sm" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>{companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}</select><Button type="button" onClick={() => refresh()} disabled={isBusy}>{isBusy ? <Loader2 className="animate-spin" size={17} /> : <RefreshCw size={17} />} Refresh</Button></div>
        </header>
        <p className="rounded-md border bg-card px-3 py-2 text-sm text-muted-foreground">{status}</p>
        {cfo ? <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"><Card label="Health Score" value={`${cfo.business_health_score}/100`} /><Card label="Profit Forecast" value={formatMoney(cfo.profit_forecast)} /><Card label="Cash Runway" value={cfo.cash_runway_days === null ? "Stable" : `${cfo.cash_runway_days} days`} /><Card label="GST Risk" value={cfo.gst_risk.toUpperCase()} /></section> : null}
        {cfo ? <section className="rounded-md border bg-card p-4"><h2 className="mb-3 font-semibold">AI Alerts</h2>{cfo.alerts.length === 0 ? <p className="text-sm text-muted-foreground">No active alerts.</p> : <div className="grid gap-2 lg:grid-cols-2">{cfo.alerts.map((alert) => <div key={alert.title} className="rounded-md border p-3"><div className="flex items-center gap-2"><AlertTriangle size={17} className="text-primary" /><p className="font-medium">{alert.title}</p></div><p className="mt-1 text-sm text-muted-foreground">{alert.message}</p></div>)}</div>}</section> : null}
        {pack ? <section className="rounded-md border bg-card p-4"><div className="mb-3 flex items-center gap-2"><FileText size={18} className="text-primary" /><h2 className="font-semibold">Smart Month-End Closing Pack</h2></div><p className="mb-3 text-sm text-muted-foreground">ABHAY provides GST assistance. Please verify before filing.</p><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5"><Card label="Trial Balance Rows" value={String(pack.trial_balance.length)} /><Card label="P&L Profit" value={formatMoney(pack.profit_and_loss.profit)} /><Card label="Balance Diff" value={formatMoney(pack.balance_sheet.check_difference)} /><Card label="Net Cash Flow" value={formatMoney(pack.cash_flow.net_cash_flow)} /><Card label="GST Assistance" value={formatMoney(pack.gst_summary.net_payable)} /></div></section> : null}
      </section>
    </main>
  );
}

function Card({ label, value }: { label: string; value: string }) {
  return <div className="rounded-md border bg-card p-4"><p className="text-sm text-muted-foreground">{label}</p><p className="mt-2 text-xl font-semibold">{value}</p></div>;
}

function formatMoney(value: string | number | undefined) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(value ?? 0));
}
