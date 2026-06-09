"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Banknote, CheckCircle2, FileUp, Loader2, RefreshCw, SearchCheck, XCircle } from "lucide-react";
import { accountingApi, Company } from "@/lib/api/accounting";
import {
  bankReconciliationApi,
  BankTransaction,
  ReconciliationSummary,
  SuggestedMatch
} from "@/lib/api/bank-reconciliation";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export function BankReconciliationWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [transactions, setTransactions] = useState<BankTransaction[]>([]);
  const [suggestions, setSuggestions] = useState<SuggestedMatch[]>([]);
  const [summary, setSummary] = useState<ReconciliationSummary | null>(null);
  const [csvContent, setCsvContent] = useState("");
  const [filename, setFilename] = useState("");
  const [status, setStatus] = useState("Loading bank reconciliation");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      if (!accessToken) {
        setStatus("Sign in to reconcile bank statements.");
        return;
      }
      accountingApi
        .companies(accessToken)
        .then((items) => {
          setCompanies(items);
          setCompanyId(items[0]?.id ?? "");
          setStatus(items.length ? "Upload a bank CSV to begin" : "No company membership found.");
        })
        .catch((error: Error) => setStatus(error.message));
    });
  }, [supabase]);

  async function refresh(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) return;
    setIsBusy(true);
    try {
      const [transactionRows, summaryRow] = await Promise.all([
        bankReconciliationApi.transactions(selectedCompanyId, token),
        bankReconciliationApi.summary(selectedCompanyId, token)
      ]);
      setTransactions(transactionRows);
      setSummary(summaryRow);
      setStatus("Reconciliation data refreshed");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to refresh reconciliation data.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) void refresh(companyId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  async function upload() {
    if (!token || !companyId || !csvContent) return;
    setIsBusy(true);
    try {
      const result = await bankReconciliationApi.upload(companyId, token, {
        filename: filename || "bank-statement.csv",
        csv_content: csvContent,
        bank_name: "Primary Bank"
      });
      setStatus(`Imported ${result.imported_count} bank transactions`);
      setCsvContent("");
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Upload failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function suggestMatches() {
    if (!token || !companyId) return;
    setIsBusy(true);
    try {
      const result = await bankReconciliationApi.suggestMatches(companyId, token);
      setSuggestions(result);
      setStatus(result.length ? `${result.length} suggested matches found` : "No confident matches found");
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Matching failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function confirm(match: SuggestedMatch) {
    if (!token || !companyId) return;
    setIsBusy(true);
    try {
      await bankReconciliationApi.confirmMatch(companyId, token, {
        bank_transaction_id: match.bank_transaction_id,
        journal_entry_id: match.journal_entry_id,
        confidence: match.confidence
      });
      setSuggestions((items) => items.filter((item) => item.bank_transaction_id !== match.bank_transaction_id));
      setStatus(`Matched bank transaction to ${match.voucher_number}`);
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Confirm match failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function ignore(transactionId: string) {
    if (!token || !companyId) return;
    setIsBusy(true);
    try {
      await bankReconciliationApi.ignore(companyId, token, transactionId);
      setStatus("Transaction ignored");
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Ignore failed.");
    } finally {
      setIsBusy(false);
    }
  }

  const suggestionsByTransaction = useMemo(
    () => new Map(suggestions.map((item) => [item.bank_transaction_id, item])),
    [suggestions]
  );

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
              <Banknote size={22} aria-hidden="true" />
            </span>
            <div>
              <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">AI Matching Active</span>
              <h1 className="text-2xl font-semibold sm:text-3xl">Bank Reconciliation</h1>
              <p className="mt-1 text-sm text-white/80">Upload statements, review matches, and approve reconciliation</p>
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

        <section className="glass-card grid gap-3 p-4 lg:grid-cols-[1fr_180px_180px]">
          <Input
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (!file) return;
              setFilename(file.name);
              file.text().then(setCsvContent).catch(() => setStatus("Unable to read CSV file."));
            }}
          />
          <Button type="button" onClick={upload} disabled={isBusy || !csvContent || !companyId}>
            {isBusy ? <Loader2 className="animate-spin" size={17} /> : <FileUp size={17} />}
            Upload CSV
          </Button>
          <Button type="button" variant="secondary" onClick={suggestMatches} disabled={isBusy || !companyId}>
            <SearchCheck size={17} />
            Auto Match
          </Button>
        </section>

        <p className="glass-card px-3 py-2 text-sm text-muted-foreground">{status}</p>

        {summary ? <SummaryCards summary={summary} /> : null}

        <section className="glass-panel p-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold">Bank Transactions</h2>
            <Button type="button" variant="secondary" onClick={() => refresh()} disabled={isBusy}>
              <RefreshCw size={17} />
              Refresh
            </Button>
          </div>
          {transactions.length === 0 ? (
            <p className="empty-state">No bank transactions uploaded yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[980px] text-left text-sm">
                <thead className="text-muted-foreground">
                  <tr>
                    <th className="py-2">Date</th>
                    <th>Description</th>
                    <th>Debit</th>
                    <th>Credit</th>
                    <th>Balance</th>
                    <th>Status</th>
                    <th>Suggested Match</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((transaction) => {
                    const suggestion = suggestionsByTransaction.get(transaction.id);
                    return (
                      <tr key={transaction.id} className="border-t">
                        <td className="py-2">{transaction.transaction_date}</td>
                        <td>
                          <p className="font-medium">{transaction.description}</p>
                          <p className="text-xs text-muted-foreground">{transaction.reference_number ?? "No reference"}</p>
                        </td>
                        <td>{formatMoney(transaction.debit)}</td>
                        <td>{formatMoney(transaction.credit)}</td>
                        <td>{transaction.balance ? formatMoney(transaction.balance) : "-"}</td>
                        <td>{statusLabel(transaction.reconciliation_status)}</td>
                        <td>
                          {suggestion ? (
                            <div>
                              <p className="font-medium">{suggestion.voucher_number}</p>
                              <p className="text-xs text-muted-foreground">{suggestion.confidence}% · {suggestion.reason}</p>
                            </div>
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </td>
                        <td className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button type="button" variant="secondary" disabled={!suggestion || isBusy} onClick={() => suggestion && confirm(suggestion)} title="Confirm match">
                              <CheckCircle2 size={16} />
                            </Button>
                            <Button type="button" variant="ghost" disabled={transaction.reconciliation_status === "matched" || isBusy} onClick={() => ignore(transaction.id)} title="Ignore transaction">
                              <XCircle size={16} />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </section>
    </main>
  );
}

function SummaryCards({ summary }: { summary: ReconciliationSummary }) {
  const cards = [
    ["Transactions", String(summary.total_transactions)],
    ["Matched", String(summary.matched)],
    ["Suggested", String(summary.suggested_match)],
    ["Unmatched", String(summary.unmatched)],
    ["Ignored", String(summary.ignored)],
    ["Matched Amount", formatMoney(summary.matched_amount)],
    ["Unreconciled", formatMoney(summary.unreconciled_amount)]
  ];
  return (
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {cards.map(([label, value]) => (
        <div key={label} className="glass-card float-card p-4">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="mt-2 text-xl font-semibold">{value}</p>
        </div>
      ))}
    </section>
  );
}

function statusLabel(value: BankTransaction["reconciliation_status"]) {
  return value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatMoney(value: string | number | undefined) {
  const amount = Number(value ?? 0);
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount);
}
