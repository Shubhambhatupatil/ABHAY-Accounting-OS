"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Bot, CheckCircle2, FileText, Loader2, RefreshCw, Send, Upload, XCircle } from "lucide-react";
import { accountingApi, Company } from "@/lib/api/accounting";
import {
  aiEntryApi,
  AiAccuracyDashboard,
  AiCorrection,
  AiEntryWorkbenchResponse,
  AiMonthEndReadiness,
  AiOwnerReport
} from "@/lib/api/ai-entry";
import type { ConfirmAiPostingResponse } from "@/lib/api/ai-accountant";
import { getAccessToken, isAlphaDemoModeEnabled, isLocalDevelopmentApi, tokenSourceFor } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { publicEnv } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

const testEntry = "400 recharge smartphone back office 30 people";
const examples = [
  testEntry,
  "Purchase bill 10000 with GST from supplier",
  "Paid diesel expense 2500 cash"
];

export function AiWorkbench() {
  const supabase = useMemo(() => createSupabaseBrowserClient(), []);
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [entryText, setEntryText] = useState(examples[0]);
  const [sourceType, setSourceType] = useState("one_line_text");
  const [suggestion, setSuggestion] = useState<AiEntryWorkbenchResponse | null>(null);
  const [posted, setPosted] = useState<ConfirmAiPostingResponse | null>(null);
  const [accuracy, setAccuracy] = useState<AiAccuracyDashboard | null>(null);
  const [readiness, setReadiness] = useState<AiMonthEndReadiness | null>(null);
  const [ownerReport, setOwnerReport] = useState<AiOwnerReport | null>(null);
  const [corrections, setCorrections] = useState<AiCorrection[]>([]);
  const [status, setStatus] = useState("Loading AI Workbench");
  const [statusTone, setStatusTone] = useState<"loading" | "success" | "error" | "info">("loading");
  const [backendConnected, setBackendConnected] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [tokenSource, setTokenSource] = useState<"supabase" | "demo" | "missing">("missing");
  const showAlphaDebug = isAlphaDemoModeEnabled() || isLocalDevelopmentApi();

  const loadCompanies = useCallback(async (accessToken: string) => {
    setStatusTone("loading");
    const items = await accountingApi.companies(accessToken);
    const rows = items.length
      ? items
      : [await accountingApi.createCompany(accessToken, { legal_name: "ANVRITAI Demo Company" })];
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

    async function checkBackend() {
      try {
        const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}/health`, { cache: "no-store" });
        if (active) setBackendConnected(response.ok);
      } catch {
        if (active) setBackendConnected(false);
      }
    }

    void checkBackend();
    return () => {
      active = false;
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
        setTokenSource(tokenSourceFor(accessToken));
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

  const refreshReviewData = useCallback(async (selectedCompanyId = companyId, accessToken = token, reportError = true) => {
    if (!selectedCompanyId || !accessToken) return;
    try {
      const [accuracyRow, correctionRows, readinessRow, ownerReportRow] = await Promise.all([
        aiEntryApi.accuracy(selectedCompanyId, accessToken),
        aiEntryApi.corrections(selectedCompanyId, accessToken),
        aiEntryApi.readiness(selectedCompanyId, accessToken),
        aiEntryApi.ownerReport(selectedCompanyId, accessToken)
      ]);
      setAccuracy(accuracyRow);
      setCorrections(correctionRows);
      setReadiness(readinessRow);
      setOwnerReport(ownerReportRow);
    } catch (error) {
      if (reportError) {
        setStatus(error instanceof Error ? error.message : "Unable to load AI review data.");
        setStatusTone("error");
      }
    }
  }, [companyId, token]);

  useEffect(() => {
    if (token && companyId) void refreshReviewData(companyId, token);
  }, [companyId, refreshReviewData, token]);

  async function parseText(textOverride?: string) {
    const text = textOverride ?? entryText.trim();
    if (!token || !companyId || !text.trim()) {
      setStatus("AI Engine needs a token, company, and transaction text before parsing.");
      setStatusTone("error");
      return;
    }
    setIsBusy(true);
    setPosted(null);
    setSuggestion(null);
    setStatus("AI Engine is parsing the accounting entry...");
    setStatusTone("loading");
    try {
      const result = await aiEntryApi.inbox(companyId, token, text.trim(), sourceType);
      setSuggestion(result);
      setStatus(statusForWorkflow(result.workflow_state));
      setStatusTone("success");
      await refreshReviewData(companyId, token, false);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "AI parse failed.");
      setStatusTone("error");
    } finally {
      setIsBusy(false);
    }
  }

  async function runTestEntry() {
    setEntryText(testEntry);
    await parseText(testEntry);
  }

  async function uploadPdf(file: File | null) {
    if (!file || !token || !companyId) return;
    if (file.type.startsWith("image/")) {
      setSuggestion(null);
      setPosted(null);
      setStatus("Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.");
      setStatusTone("info");
      return;
    }
    if (file.type !== "application/pdf") {
      setStatus("Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.");
      setStatusTone("error");
      return;
    }
    setIsBusy(true);
    setPosted(null);
    try {
      const fileBase64 = await readFileBase64(file);
      const result = await aiEntryApi.uploadPdf(companyId, token, file.name, fileBase64);
      setSuggestion(result);
      setStatus("PDF bill extracted and suggestion created");
      setStatusTone("success");
      await refreshReviewData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "PDF extraction failed.");
      setStatusTone("error");
    } finally {
      setIsBusy(false);
    }
  }

  async function approve() {
    if (!token || !companyId || !suggestion) return;
    setIsBusy(true);
    try {
      const result = await aiEntryApi.approve(companyId, token, suggestion.suggestion.suggestion_id);
      setPosted(result);
      setStatus(`Posted voucher ${result.voucher.voucher_number}`);
      setStatusTone("success");
      await refreshReviewData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Approval failed.");
      setStatusTone("error");
    } finally {
      setIsBusy(false);
    }
  }

  async function reject() {
    if (!token || !companyId || !suggestion) return;
    setIsBusy(true);
    try {
      const result = await aiEntryApi.reject(companyId, token, suggestion.suggestion.suggestion_id, "Rejected during workbench review");
      setSuggestion(result);
      setStatus("Suggestion rejected");
      setStatusTone("info");
      await refreshReviewData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Reject failed.");
      setStatusTone("error");
    } finally {
      setIsBusy(false);
    }
  }

  const aiEngineReady = Boolean(token && companyId);
  const canParse = Boolean(aiEngineReady && entryText.trim());
  const gstPreview = useMemo(() => suggestion?.suggestion.lines.filter((line) => line.ledger_name.toLowerCase().includes("gst")) ?? [], [suggestion]);

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
              <p className="mt-1 text-sm text-white/80">Command console for input, review, approve, and post</p>
              <div className="mt-3 flex flex-wrap gap-2">
                <ReadinessBadge ready={backendConnected} readyText="Backend connected" pendingText="Backend offline" />
                <ReadinessBadge ready={aiEngineReady} readyText="AI Engine ready" pendingText="AI Engine waiting" />
              </div>
              {showAlphaDebug ? (
                <p className="mt-3 rounded-xl border border-white/15 bg-white/10 px-3 py-2 text-xs text-white/75">
                  API URL: {publicEnv.NEXT_PUBLIC_API_URL} | token source: {tokenSource} | backend status:{" "}
                  {backendConnected ? "connected" : "offline"}
                </p>
              ) : null}
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <select className="premium-select text-slate-900" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
              {!companies.length ? <option value="">No company found</option> : null}
              {companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}
            </select>
            <Button type="button" variant="secondary" onClick={() => refreshReviewData()} disabled={isBusy}>
              <RefreshCw size={17} />
              Refresh
            </Button>
          </div>
          </div>
        </header>

        <section className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-4">
            <form className="glass-panel p-4 ring-1 ring-orange-100/70" onSubmit={(event) => { event.preventDefault(); void parseText(); }}>
              <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <span className="ai-badge mb-2">AI Active</span>
                  <h2 className="text-base font-semibold">Accounting Autopilot Inbox</h2>
                </div>
                <select className="premium-select" value={sourceType} onChange={(event) => setSourceType(event.target.value)}>
                  <option value="one_line_text">One-line text</option>
                  <option value="whatsapp_message">WhatsApp-style message</option>
                  <option value="bank_transaction">Bank transaction</option>
                </select>
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
                  Parse is enabled when token, company, and transaction text are ready.
                </p>
                <Button type="button" variant="secondary" onClick={runTestEntry} disabled={isBusy || !aiEngineReady}>
                  {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Bot size={17} />}
                  Test AI Entry
                </Button>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {examples.map((example) => (
                  <button key={example} type="button" className="rounded-xl border border-white/70 bg-white/70 px-3 py-2 text-left text-xs text-muted-foreground shadow-sm transition hover:-translate-y-0.5 hover:bg-orange-50" onClick={() => setEntryText(example)}>
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
              <label className="flex min-h-28 cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed border-orange-200 bg-orange-50/40 p-4 text-center text-sm text-muted-foreground transition hover:-translate-y-0.5 hover:bg-orange-50">
                <Upload className="mb-2 text-primary" size={22} />
                <span>Upload text PDF bill or invoice</span>
                <span className="mt-1 text-xs">Use one-line AI entry as fallback.</span>
                <input className="hidden" type="file" accept="application/pdf,image/*" onChange={(event) => void uploadPdf(event.target.files?.[0] ?? null)} />
              </label>
            </div>
          </div>

          <aside className="space-y-4">
            <StatusPanel status={status} tone={statusTone} />
            {accuracy ? <AccuracyPanel accuracy={accuracy} /> : null}
            {readiness ? <ReadinessPanel readiness={readiness} /> : null}
            {ownerReport ? <OwnerReportPanel report={ownerReport} /> : null}
            {posted ? (
              <div className="glass-card border-primary/30 bg-primary/10 p-4">
                <div className="flex items-center gap-2 font-semibold text-primary"><CheckCircle2 size={18} /> Posted</div>
                <p className="mt-1 text-sm text-muted-foreground">{posted.voucher.voucher_number}</p>
              </div>
            ) : null}
          </aside>
        </section>

        {suggestion ? (
          <section className="glass-panel p-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <h2 className="text-lg font-semibold">Suggested {title(suggestion.suggestion.voucher_type)} Voucher</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Confidence {Number(suggestion.suggestion.confidence).toFixed(2)} · {title(suggestion.confidence_band)} · {title(suggestion.workflow_state)} · {suggestion.suggestion.model_name}
                </p>
              </div>
              <div className="flex gap-2">
                <Button type="button" variant="secondary" onClick={reject} disabled={isBusy}>
                  <XCircle size={17} />
                  Reject
                </Button>
                <Button type="button" onClick={approve} disabled={isBusy || !suggestion.suggestion.can_post}>
                  <CheckCircle2 size={17} />
                  Approve
                </Button>
              </div>
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-3">
              <PreviewPanel title="Debit/Credit Preview" rows={suggestion.suggestion.lines} />
              <div className="rounded-xl border border-white/70 bg-white/70 p-3">
                <h3 className="mb-2 font-medium">GST Preview</h3>
                {gstPreview.length ? gstPreview.map((line, index) => <LineRow key={`${index}-${line.ledger_name}`} line={line} />) : <p className="text-sm text-muted-foreground">No GST line detected.</p>}
              </div>
              <div className="rounded-xl border border-white/70 bg-white/70 p-3">
                <h3 className="mb-2 font-medium">Review Notes</h3>
                <p className="text-sm text-muted-foreground">{suggestion.suggestion.explanation}</p>
                {suggestion.clarification_questions.map((question, index) => <p key={`${index}-${question}`} className="mt-2 rounded-md bg-muted p-2 text-sm">{question}</p>)}
                {suggestion.suggestion.validation_errors.map((error, index) => <p key={`${index}-${error}`} className="mt-2 rounded-md bg-destructive/10 p-2 text-sm text-destructive">{error}</p>)}
              </div>
            </div>

            <section className="mt-4 grid gap-3 lg:grid-cols-3">
              <RiskPanel title="AI Voucher Doctor" items={suggestion.doctor_findings} empty="No doctor findings." />
              <RiskPanel title="GST Risk Detector" items={suggestion.gst_risks} empty="No GST risks detected." />
              <RiskPanel title="Duplicate Bill Detector" items={suggestion.duplicate_warnings} empty="No duplicate signal." />
            </section>

            {suggestion.extracted_invoice ? <ExtractedInvoicePanel fields={suggestion.extracted_invoice} /> : null}
          </section>
        ) : null}

        <section className="glass-card p-4">
          <h2 className="mb-3 text-base font-semibold">Correction History</h2>
          {corrections.length ? (
            <div className="grid gap-2 lg:grid-cols-2">
              {corrections.map((item) => (
                <div key={item.id} className="rounded-xl border border-white/70 bg-white/70 p-3 text-sm">
                  <p className="font-medium">{new Date(item.created_at).toLocaleString("en-IN")}</p>
                  <p className="mt-1 truncate text-muted-foreground">{JSON.stringify(item.corrected_payload)}</p>
                </div>
              ))}
            </div>
          ) : <p className="text-sm text-muted-foreground">No corrections recorded.</p>}
        </section>
      </section>
    </main>
  );
}

function ReadinessBadge({ ready, readyText, pendingText }: { ready: boolean; readyText: string; pendingText: string }) {
  return (
    <span className={cn(
      "inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold backdrop-blur",
      ready
        ? "border-emerald-200 bg-emerald-50/90 text-emerald-700"
        : "border-amber-200 bg-amber-50/90 text-amber-700"
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
        tone === "success" && "text-emerald-700",
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

function AccuracyPanel({ accuracy }: { accuracy: AiAccuracyDashboard }) {
  const stats = [
    ["Suggestions", accuracy.total_suggestions],
    ["No-edit Approvals", accuracy.approved_without_edit],
    ["Approved", accuracy.approved_suggestions],
    ["Rejected", accuracy.rejected_suggestions],
    ["Corrections", accuracy.corrected_suggestions],
    ["Est. Accuracy", `${Number(accuracy.estimated_accuracy).toFixed(1)}%`],
    ["Avg Confidence", Number(accuracy.average_confidence).toFixed(2)]
  ];
  return (
    <div className="glass-card float-card p-4">
      <h2 className="mb-3 text-base font-semibold">Accuracy Tracking</h2>
      <div className="grid grid-cols-2 gap-2">
        {stats.map(([label, value]) => (
          <div key={label} className="rounded-xl border border-white/70 bg-white/70 p-3">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="mt-1 font-semibold">{value}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function ReadinessPanel({ readiness }: { readiness: AiMonthEndReadiness }) {
  return (
    <div className="glass-card float-card p-4">
      <h2 className="mb-3 text-base font-semibold">Month-End Readiness</h2>
      <div className="grid grid-cols-2 gap-2">
        <Metric label="Score" value={`${Number(readiness.readiness_score).toFixed(0)}%`} />
        <Metric label="Completion" value={`${Number(readiness.books_completion_percent).toFixed(0)}%`} />
        <Metric label="Pending" value={readiness.pending_vouchers} />
        <Metric label="Unreconciled" value={readiness.unreconciled_bank_entries} />
        <Metric label="GST Risks" value={readiness.gst_risk_count} />
        <Metric label="Missing Bills" value={readiness.missing_bill_count} />
      </div>
    </div>
  );
}

function OwnerReportPanel({ report }: { report: AiOwnerReport }) {
  return (
    <div className="glass-card float-card p-4">
      <h2 className="mb-2 text-base font-semibold">Owner Plain-English Report</h2>
      <p className="text-sm leading-6 text-muted-foreground">{report.summary}</p>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-xl border border-white/70 bg-white/70 p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 font-semibold">{value}</p>
    </div>
  );
}

function RiskPanel({ title: panelTitle, items, empty }: { title: string; items: string[]; empty: string }) {
  return (
    <div className="rounded-xl border border-white/70 bg-white/70 p-3">
      <h3 className="mb-2 font-medium">{panelTitle}</h3>
      {items.length ? (
        <div className="space-y-2">
          {items.map((item, index) => (
            <p key={`${index}-${item}`} className="rounded-md bg-muted p-2 text-sm text-muted-foreground">{item}</p>
          ))}
        </div>
      ) : <p className="text-sm text-muted-foreground">{empty}</p>}
    </div>
  );
}

function PreviewPanel({ title: panelTitle, rows }: { title: string; rows: AiEntryWorkbenchResponse["suggestion"]["lines"] }) {
  return (
    <div className="rounded-xl border border-white/70 bg-white/70 p-3">
      <h3 className="mb-2 font-medium">{panelTitle}</h3>
      <div className="space-y-2">{rows.map((line, index) => <LineRow key={`${index}-${line.ledger_name}-${line.debit}-${line.credit}`} line={line} />)}</div>
    </div>
  );
}

function LineRow({ line }: { line: AiEntryWorkbenchResponse["suggestion"]["lines"][number] }) {
  return (
    <div className={cn("rounded-xl border border-white/70 p-2 text-sm", line.ledger_id ? "bg-white/70" : "bg-destructive/10")}>
      <p className="font-medium">{line.ledger_name}</p>
      <div className="mt-1 grid grid-cols-2 gap-2 text-muted-foreground">
        <span>Dr {formatMoney(line.debit)}</span>
        <span>Cr {formatMoney(line.credit)}</span>
      </div>
    </div>
  );
}

function ExtractedInvoicePanel({ fields }: { fields: NonNullable<AiEntryWorkbenchResponse["extracted_invoice"]> }) {
  const rows = [
    ["Party", fields.vendor_or_customer_name],
    ["Invoice", fields.invoice_number],
    ["Date", fields.invoice_date],
    ["GSTIN", fields.gstin],
    ["Taxable", fields.taxable_amount ? formatMoney(fields.taxable_amount) : null],
    ["CGST", fields.cgst_amount ? formatMoney(fields.cgst_amount) : null],
    ["SGST", fields.sgst_amount ? formatMoney(fields.sgst_amount) : null],
    ["IGST", fields.igst_amount ? formatMoney(fields.igst_amount) : null],
    ["Total", fields.total_amount ? formatMoney(fields.total_amount) : null]
  ];
  return (
    <div className="mt-4 rounded-xl border border-white/70 bg-white/70 p-3">
      <div className="mb-2 flex items-center gap-2"><FileText size={17} className="text-primary" /><h3 className="font-medium">Extracted Invoice</h3></div>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {rows.map(([label, value]) => <p key={label} className="text-sm"><span className="text-muted-foreground">{label}: </span>{value ?? "-"}</p>)}
      </div>
    </div>
  );
}

function readFileBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const value = String(reader.result ?? "");
      resolve(value.includes(",") ? value.split(",")[1] : value);
    };
    reader.onerror = () => reject(new Error("Unable to read PDF file."));
    reader.readAsDataURL(file);
  });
}

function formatMoney(value: string | number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(value));
}

function title(value: string) {
  return value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function statusForWorkflow(workflowState: string) {
  if (workflowState === "ready_for_approval") return "90%+ confidence: ready for approval";
  if (workflowState === "needs_review") return "70-89% confidence: review required";
  return "Below 70% confidence: clarification required";
}
