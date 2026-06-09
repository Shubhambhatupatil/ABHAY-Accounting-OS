"use client";

import { useEffect, useState } from "react";
import { Bot, Loader2, MessageSquareText, RefreshCw, Wand2 } from "lucide-react";
import { accountingApi, Company } from "@/lib/api/accounting";
import { automationApi, AutomationSummary } from "@/lib/api/automation";
import { bankReconciliationApi, BankTransaction } from "@/lib/api/bank-reconciliation";
import { AiSuggestion } from "@/lib/api/ai-accountant";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export function AutomationCenterWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [summary, setSummary] = useState<AutomationSummary | null>(null);
  const [bankTransactions, setBankTransactions] = useState<BankTransaction[]>([]);
  const [message, setMessage] = useState("Diesel 2500 cash");
  const [suggestion, setSuggestion] = useState<AiSuggestion | null>(null);
  const [status, setStatus] = useState("Loading automation center");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      if (!accessToken) return setStatus("Sign in to use automation.");
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
      const [summaryRow, transactions] = await Promise.all([
        automationApi.summary(selectedCompanyId, token),
        bankReconciliationApi.transactions(selectedCompanyId, token)
      ]);
      setSummary(summaryRow);
      setBankTransactions(transactions.filter((item) => item.reconciliation_status !== "matched" && item.reconciliation_status !== "ignored"));
      setStatus("Automation data refreshed");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to load automation.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) void refresh(companyId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  async function parseWhatsApp() {
    if (!token || !companyId) return;
    setIsBusy(true);
    try {
      const result = await automationApi.whatsAppEntry(companyId, token, message);
      setSuggestion(result);
      setStatus("WhatsApp accounting suggestion ready for approval");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Automation failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function createBankSuggestion(transactionId: string) {
    if (!token || !companyId) return;
    setIsBusy(true);
    try {
      const result = await automationApi.bankAutoVoucher(companyId, token, transactionId);
      setSuggestion(result);
      setStatus("Bank transaction voucher suggestion ready");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Bank automation failed.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <Header title="Automation Center" subtitle="Auto vouchers, WhatsApp accounting, bank triggers and categorization" companies={companies} companyId={companyId} setCompanyId={setCompanyId} />
        <p className="glass-card px-3 py-2 text-sm text-muted-foreground">{status}</p>
        {summary ? (
          <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Card label="Business Health" value={`${summary.business_health_score}/100`} />
            <Card label="Open AI Suggestions" value={String(summary.open_ai_suggestions)} />
            <Card label="Unreconciled Bank" value={String(summary.unreconciled_bank_transactions)} />
            <Card label="Active Alerts" value={String(summary.active_alerts)} />
          </section>
        ) : null}
        <section className="grid gap-4 xl:grid-cols-[420px_1fr]">
          <div className="glass-panel p-4">
            <div className="mb-3 flex items-center gap-2"><MessageSquareText size={18} className="text-primary" /><h2 className="font-semibold">WhatsApp Accounting</h2></div>
            <Input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Diesel 2500 cash" />
            <Button className="ai-glow mt-3 w-full" type="button" onClick={parseWhatsApp} disabled={isBusy || !companyId}>
              {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Wand2 size={17} />} Generate voucher suggestion
            </Button>
          </div>
          <div className="glass-card p-4">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold">Bank-triggered Auto Vouchers</h2>
              <Button type="button" variant="secondary" onClick={() => refresh()}><RefreshCw size={17} /> Refresh</Button>
            </div>
            {bankTransactions.length === 0 ? <p className="text-sm text-muted-foreground">No unreconciled bank transactions available.</p> : (
              <div className="space-y-2">
                {bankTransactions.slice(0, 8).map((item) => (
                  <div key={item.id} className="flex flex-col gap-2 rounded-xl border border-white/70 bg-white/70 p-3 shadow-sm transition hover:-translate-y-0.5 hover:bg-orange-50 sm:flex-row sm:items-center sm:justify-between">
                    <div><p className="font-medium">{item.description}</p><p className="text-sm text-muted-foreground">{item.transaction_date} · {formatMoney(Number(item.debit) || Number(item.credit))}</p></div>
                    <Button type="button" variant="secondary" onClick={() => createBankSuggestion(item.id)} disabled={isBusy}><Bot size={17} /> Suggest voucher</Button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
        {suggestion ? <SuggestionPreview suggestion={suggestion} /> : null}
      </section>
    </main>
  );
}

function Header({ title, subtitle, companies, companyId, setCompanyId }: { title: string; subtitle: string; companies: Company[]; companyId: string; setCompanyId: (id: string) => void }) {
  return <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6"><div className="relative z-10 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"><div><span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">AI Active</span><h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1><p className="mt-1 text-sm text-white/80">{subtitle}</p></div><select className="premium-select text-slate-900" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>{companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}</select></div></header>;
}

function Card({ label, value }: { label: string; value: string }) {
  return <div className="glass-card float-card p-4"><p className="text-sm text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">{value}</p></div>;
}

function SuggestionPreview({ suggestion }: { suggestion: AiSuggestion }) {
  return <div className="glass-panel p-4"><h2 className="font-semibold">Latest Suggestion</h2><p className="mt-1 text-sm text-muted-foreground">{suggestion.explanation}</p><div className="mt-3 grid gap-2 sm:grid-cols-2">{suggestion.lines.map((line) => <div key={line.ledger_name} className="rounded-xl border border-white/70 bg-white/70 p-3 text-sm"><p className="font-medium">{line.ledger_name}</p><p>Debit {formatMoney(line.debit)} · Credit {formatMoney(line.credit)}</p></div>)}</div></div>;
}

function formatMoney(value: string | number | undefined) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(value ?? 0));
}
