"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Bot, CheckCircle2, Loader2, Send, ShieldCheck, XCircle } from "lucide-react";
import { accountingApi, Company } from "@/lib/api/accounting";
import { aiAccountantApi, AiSuggestion, ConfirmAiPostingResponse } from "@/lib/api/ai-accountant";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const examples = [
  "Paid diesel expense ₹2500 cash",
  "Aaj office rent 12000 cash diya",
  "Received 60000 from customer in bank",
  "Purchase goods 10000 plus GST from supplier"
];

export function AiAccountantWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [input, setInput] = useState(examples[0]);
  const [suggestion, setSuggestion] = useState<AiSuggestion | null>(null);
  const [posted, setPosted] = useState<ConfirmAiPostingResponse | null>(null);
  const [status, setStatus] = useState("Loading ABHAY AI Accountant");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      if (!accessToken) {
        setStatus("Sign in to use ABHAY AI Accountant.");
        return;
      }
      accountingApi
        .companies(accessToken)
        .then((items) => {
          setCompanies(items);
          setCompanyId(items[0]?.id ?? "");
          setStatus(items.length ? "Ready for accounting command" : "No company membership found.");
        })
        .catch((error: Error) => setStatus(error.message));
    });
  }, [supabase]);

  async function parseCommand() {
    if (!token || !companyId || !input.trim()) {
      return;
    }
    setIsBusy(true);
    setPosted(null);
    try {
      const result = await aiAccountantApi.parse(companyId, token, input.trim());
      setSuggestion(result);
      setStatus(result.can_post ? "Suggestion ready for approval" : "Suggestion needs ledger review");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "AI parsing failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function confirmPosting() {
    if (!token || !companyId || !suggestion) {
      return;
    }
    setIsBusy(true);
    try {
      const result = await aiAccountantApi.confirm(companyId, token, suggestion.suggestion_id);
      setPosted(result);
      setStatus(`Posted voucher ${result.voucher.voucher_number}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Posting failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function rejectSuggestion() {
    if (!token || !companyId || !suggestion) {
      return;
    }
    setIsBusy(true);
    try {
      const result = await aiAccountantApi.reject(companyId, token, suggestion.suggestion_id, "User rejected");
      setSuggestion(result);
      setStatus("Suggestion rejected");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Reject failed.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="min-h-screen bg-background p-3 sm:p-5">
      <section className="mx-auto flex max-w-6xl flex-col gap-4">
        <header className="flex flex-col gap-3 border-b pb-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-md bg-primary text-primary-foreground">
              <Bot size={22} aria-hidden="true" />
            </span>
            <div>
              <h1 className="text-xl font-semibold">ABHAY AI Accountant</h1>
              <p className="text-sm text-muted-foreground">
                Natural language accounting suggestions, posted only after approval
              </p>
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <select
              className="h-10 rounded-md border bg-card px-3 text-sm"
              value={companyId}
              onChange={(event) => setCompanyId(event.target.value)}
            >
              {companies.map((company) => (
                <option key={company.id} value={company.id}>
                  {company.legal_name}
                </option>
              ))}
            </select>
            <Link className="inline-flex h-10 items-center justify-center rounded-md border px-3 text-sm" href="/dashboard">
              Accounting
            </Link>
          </div>
        </header>

        <div className="rounded-md border bg-card p-4">
          <div className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
            <ShieldCheck size={17} className="text-primary" />
            AI cannot post directly. Every suggestion requires confirmation and double-entry validation.
          </div>
          <form
            className="grid gap-3 md:grid-cols-[1fr_140px]"
            onSubmit={(event) => {
              event.preventDefault();
              void parseCommand();
            }}
          >
            <Input
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Paid diesel expense ₹2500 cash"
              required
            />
            <Button type="submit" disabled={isBusy || !companyId}>
              {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Send size={17} />}
              Parse
            </Button>
          </form>
          <div className="mt-3 flex flex-wrap gap-2">
            {examples.map((example) => (
              <button
                key={example}
                type="button"
                className="rounded-md border px-3 py-2 text-left text-xs text-muted-foreground hover:bg-muted"
                onClick={() => setInput(example)}
              >
                {example}
              </button>
            ))}
          </div>
        </div>

        <p className="rounded-md border bg-card px-3 py-2 text-sm text-muted-foreground">{status}</p>

        {suggestion ? (
          <SuggestionCard
            suggestion={suggestion}
            isBusy={isBusy}
            onConfirm={confirmPosting}
            onReject={rejectSuggestion}
          />
        ) : null}

        {posted ? (
          <div className="rounded-md border border-primary/30 bg-primary/10 p-4">
            <div className="flex items-center gap-2 font-semibold text-primary">
              <CheckCircle2 size={19} />
              Posting complete
            </div>
            <p className="mt-2 text-sm text-muted-foreground">
              Voucher {posted.voucher.voucher_number} was posted with {posted.voucher.lines.length} journal lines.
            </p>
          </div>
        ) : null}
      </section>
    </main>
  );
}

function SuggestionCard({
  suggestion,
  isBusy,
  onConfirm,
  onReject
}: {
  suggestion: AiSuggestion;
  isBusy: boolean;
  onConfirm: () => void;
  onReject: () => void;
}) {
  return (
    <section className="rounded-md border bg-card p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Suggested {title(suggestion.voucher_type)} Voucher</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Confidence {Number(suggestion.confidence).toFixed(2)} · {suggestion.model_name}
          </p>
        </div>
        <div className="text-right">
          <p className="text-sm text-muted-foreground">Amount</p>
          <p className="text-xl font-semibold">{formatMoney(suggestion.amount)}</p>
        </div>
      </div>

      <div className="mt-4 grid gap-3 lg:grid-cols-2">
        {suggestion.lines.map((line) => (
          <div key={`${line.ledger_name}-${line.debit}-${line.credit}`} className="rounded-md border p-3">
            <p className="font-medium">{line.ledger_name}</p>
            <p className="mt-1 text-sm text-muted-foreground">{line.reason}</p>
            <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
              <span>Debit: {formatMoney(line.debit)}</span>
              <span>Credit: {formatMoney(line.credit)}</span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-4 rounded-md border bg-muted p-3 text-sm">
        <p className="font-medium">Explanation</p>
        <p className="mt-1 text-muted-foreground">{suggestion.explanation}</p>
        <p className="mt-2 text-muted-foreground">
          GST: {suggestion.gst_applicable ? `Applicable at ${suggestion.suggested_gst_rate ?? "suggested"}%` : "Not detected"}
        </p>
      </div>

      {suggestion.validation_errors.length ? (
        <div className="mt-4 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          {suggestion.validation_errors.map((error) => (
            <p key={error}>{error}</p>
          ))}
        </div>
      ) : null}

      {suggestion.suggested_ledgers.some((ledger) => ledger.should_create) ? (
        <div className="mt-4 rounded-md border p-3 text-sm text-muted-foreground">
          <p className="font-medium text-foreground">Suggested ledger creation</p>
          {suggestion.suggested_ledgers
            .filter((ledger) => ledger.should_create)
            .map((ledger) => (
              <p key={ledger.ledger_name}>
                Create {ledger.ledger_name} under {title(ledger.category)}.
              </p>
            ))}
        </div>
      ) : null}

      <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Button type="button" variant="secondary" onClick={onReject} disabled={isBusy}>
          <XCircle size={17} />
          Reject/Edit
        </Button>
        <Button type="button" onClick={onConfirm} disabled={isBusy || !suggestion.can_post}>
          <CheckCircle2 size={17} />
          Confirm Posting
        </Button>
      </div>
    </section>
  );
}

function formatMoney(value: string | number | undefined) {
  const amount = Number(value ?? 0);
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount);
}

function title(value: string) {
  return value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}
