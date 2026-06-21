"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  BadgeCheck,
  Building2,
  Calculator,
  Download,
  AlertTriangle,
  FileSearch,
  FileSpreadsheet,
  Loader2,
  LockKeyhole,
  ReceiptText,
  Settings,
  ShieldCheck,
  UploadCloud
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  SubscriptionGate,
  SubscriptionStatusStrip,
  useSubscriptionState
} from "@/components/subscription/subscription-gate";
import { accountingApi, Company, LedgerScrutiny } from "@/lib/api/accounting";
import { bankReconciliationApi } from "@/lib/api/bank-reconciliation";
import { DocumentIntelligenceResult, uploadDocumentForAnalysis } from "@/lib/api/document-intelligence";
import {
  CompanyMember,
  CompanyRole,
  createWorkspaceCompany,
  inviteCompanyMember,
  listCompanyMembers,
  listWorkspaceCompanies,
  updateCompanyMemberRole,
  WorkspaceCompany
} from "@/lib/api/company-workspace";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";

const gstStates = [
  ["27", "Maharashtra"],
  ["24", "Gujarat"],
  ["29", "Karnataka"],
  ["07", "Delhi"],
  ["09", "Uttar Pradesh"],
  ["08", "Rajasthan"],
  ["33", "Tamil Nadu"],
  ["36", "Telangana"],
  ["32", "Kerala"],
  ["19", "West Bengal"],
  ["23", "Madhya Pradesh"],
  ["06", "Haryana"],
  ["03", "Punjab"],
  ["10", "Bihar"],
  ["21", "Odisha"],
  ["18", "Assam"],
  ["30", "Goa"],
  ["04", "Chandigarh"]
] as const;

const roles: CompanyRole[] = ["Owner", "Admin", "Accountant", "Auditor", "Viewer"];
const LAST_COMPANY_KEY = "abhay.lastCompanyId";

const sampleLedgers = [
  ["Cash", "Asset", "₹48,500"],
  ["HDFC Bank", "Asset", "₹2,80,000"],
  ["Sales", "Income", "₹4,25,000"],
  ["Communication Expense", "Expense", "₹4,800"],
  ["Input GST", "Asset", "₹18,250"],
  ["Output GST", "Liability", "₹42,700"]
];

type AnalyticsSummary = {
  totalVisits: number;
  visitsToday: number;
  uniqueVisitors: number;
  topPages: Array<{ path: string; visits: number }>;
  lastVisits: Array<{
    path: string;
    referrer: string | null;
    createdAt: string;
    device: string;
    browser: string;
  }>;
  message: string | null;
};

export function UploadInvoiceWorkspace() {
  const { subscription } = useSubscriptionState();
  const supabase = useMemo(() => createSupabaseBrowserClient(), []);
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [message, setMessage] = useState("Upload a PDF or image for ABHAY Document Intelligence Alpha review.");
  const [result, setResult] = useState<DocumentIntelligenceResult | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  useEffect(() => {
    let active = true;
    async function bootstrap() {
      try {
        const accessToken = await getAccessToken(supabase);
        if (!active) return;
        setToken(accessToken);
        if (!accessToken) {
          setMessage("Please login or continue in Alpha Demo Mode before uploading documents.");
          return;
        }
        const rows = await accountingApi.companies(accessToken);
        if (!active) return;
        setCompanies(rows);
        setCompanyId(rows[0]?.id ?? "");
        setMessage(rows.length ? "Document Intelligence ready. Human approval is required before posting." : "Create or select a company before uploading documents.");
      } catch {
        if (active) {
          setMessage("Unable to load companies. Please retry after checking your session.");
        }
      }
    }
    void bootstrap();
    return () => {
      active = false;
    };
  }, [supabase]);

  async function handleUpload(file: File | undefined) {
    if (!file) return;
    setResult(null);
    if (!token) {
      setMessage("Please login again.");
      return;
    }
    if (!companyId) {
      setMessage("Please select a company first.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setMessage("File too large for Alpha. Upload a document up to 10MB.");
      return;
    }
    if (!["application/pdf", "image/png", "image/jpeg", "image/jpg"].includes(file.type)) {
      setMessage("Upload a PDF, PNG, JPG, or JPEG document.");
      return;
    }
    if (file.type.startsWith("image/")) {
      setMessage("Image/scanned OCR is attempted only when OCR is available. Use text PDF or one-line entry if OCR is unavailable.");
    }
    setIsAnalyzing(true);
    try {
      const analysis = await uploadDocumentForAnalysis(companyId, token, file);
      setResult(analysis);
      setMessage(analysis.extracted_text_available ? "Document analysis ready for human review." : "Scanned/image OCR is coming soon. Use text PDF or one-line entry for now.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Document analysis failed.");
    } finally {
      setIsAnalyzing(false);
    }
  }

  return (
    <PageFrame
      icon={FileSearch}
      badge="Invoice OCR Alpha"
      title="Upload Invoice"
      subtitle="Upload text-based invoice PDFs, review extracted fields, and send clean suggestions to the AI Workbench."
    >
      <SubscriptionStatusStrip subscription={subscription} />
      <SubscriptionGate subscription={subscription} feature="invoice_upload" title="Invoice upload requires trial capacity or a paid plan">
        <section className="grid gap-4 lg:grid-cols-[1fr_360px]">
          <label className="glass-panel flex min-h-72 cursor-pointer flex-col items-center justify-center p-6 text-center transition hover:-translate-y-0.5">
            <UploadCloud className="text-orange-200" size={34} />
            <h2 className="mt-4 text-xl font-semibold text-white">Drop or select invoice, bill, statement, GST or ledger document</h2>
            <p className="mt-2 max-w-xl text-sm leading-6 text-white/60">
              Alpha tries text PDF extraction first, then OCR when available. Scanned OCR may be unavailable on some servers, and ABHAY will not fake extraction.
            </p>
            <input
              className="sr-only"
              type="file"
              accept="application/pdf,image/png,image/jpeg,image/jpg"
              onChange={(event) => void handleUpload(event.target.files?.[0])}
            />
            <span className="mt-5 rounded-2xl border border-orange-300/25 bg-orange-400/10 px-4 py-2 text-sm font-semibold text-orange-100">
              {isAnalyzing ? "Analyzing..." : "Select document"}
            </span>
          </label>
          <div className="space-y-4">
            <div className="glass-card p-4">
              <h2 className="text-sm font-semibold text-white">Company</h2>
              <select className="premium-select mt-3 h-11 w-full" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
                <option value="">{companies.length ? "Select company" : "No company found"}</option>
                {companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}
              </select>
            </div>
            <InfoCard title="Trial Usage" copy={`${subscription?.invoiceUploadsUsed ?? 0}/10 invoice uploads used in Free Trial.`} />
            <InfoCard title="Alpha Limits" copy="Max 10MB. PDFs analyze first 20 pages. Posting still requires human approval." />
            <InfoCard title="Fallback" copy="One-line AI entry remains the fastest working input when OCR is unavailable." />
            <Link className="premium-link w-full" href="/ai-workbench">Open AI Workbench</Link>
          </div>
        </section>
        {isAnalyzing ? (
          <div className="glass-card flex items-center gap-2 p-4 text-sm text-orange-100">
            <Loader2 className="animate-spin" size={18} />
            ABHAY is reading the document and preparing a review suggestion...
          </div>
        ) : null}
        {result ? <DocumentAnalysisPanel result={result} onAction={setMessage} /> : null}
      </SubscriptionGate>
      <p className="empty-state">{message}</p>
    </PageFrame>
  );
}

function DocumentAnalysisPanel({
  result,
  onAction
}: Readonly<{
  result: DocumentIntelligenceResult;
  onAction: (message: string) => void;
}>) {
  const fields = result.fields;
  const suggestion = result.accounting_suggestion;
  const fieldRows = [
    ["Document Type", result.document_type],
    ["Vendor", fields.vendor_name ?? "-"],
    ["Customer", fields.customer_name ?? "-"],
    ["GSTIN", fields.gstin ?? "-"],
    ["Invoice Number", fields.invoice_number ?? "-"],
    ["Invoice Date", fields.invoice_date ?? "-"],
    ["Subtotal", formatMoney(fields.subtotal)],
    ["GST Rate", fields.gst_rate ? `${fields.gst_rate}%` : "-"],
    ["GST Amount", formatMoney(fields.gst_amount)],
    ["Total Amount", formatMoney(fields.total_amount)]
  ];

  return (
    <section className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
      <article className="glass-panel p-5">
        <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <span className="ai-badge mb-2">Document Intelligence Alpha</span>
            <h2 className="text-lg font-semibold text-white">Extracted Fields</h2>
            <p className="mt-1 text-sm text-white/60">Draft-only analysis. Human approval is required before posting.</p>
          </div>
          <span className="rounded-full border border-[#FFD700]/30 bg-[#FFD700]/10 px-3 py-1 text-xs font-semibold text-[#FFE88A]">
            Confidence {Math.round(Number(result.confidence_score) * 100)}%
          </span>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          {fieldRows.map(([label, value]) => (
            <div key={label} className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3 text-sm">
              <p className="text-xs text-white/45">{label}</p>
              <p className="mt-1 font-semibold text-white">{value}</p>
            </div>
          ))}
        </div>
        <div className="mt-4 rounded-xl border border-[#1F2937] bg-[#0F172A]/80 p-3">
          <h3 className="text-sm font-semibold text-white">Extracted Text Summary</h3>
          <p className="mt-2 text-sm leading-6 text-white/60">{result.extracted_text_summary}</p>
        </div>
      </article>

      <aside className="space-y-4">
        <article className="glass-card p-5">
          <h2 className="text-base font-semibold text-white">Accounting Suggestion</h2>
          <div className="mt-4 space-y-2 text-sm">
            <SuggestionRow label="Voucher" value={suggestion.suggested_voucher_type} />
            <SuggestionRow label="Debit" value={suggestion.debit_ledger} />
            <SuggestionRow label="Credit" value={suggestion.credit_ledger} />
            <SuggestionRow label="GST" value={suggestion.gst_treatment} />
          </div>
          <p className="mt-4 rounded-xl border border-[#00E5FF]/20 bg-[#00E5FF]/10 p-3 text-sm leading-6 text-[#B9F7FF]">
            {suggestion.summary}
          </p>
        </article>
        <article className="glass-card p-5">
          <h2 className="text-base font-semibold text-white">Warnings</h2>
          <div className="mt-3 space-y-2">
            {result.warnings.length ? result.warnings.map((warning, index) => (
              <p key={`${index}-${warning}`} className="rounded-xl border border-amber-300/20 bg-amber-300/10 p-3 text-sm text-amber-100">
                {warning}
              </p>
            )) : (
              <p className="rounded-xl border border-[#14B8A6]/20 bg-[#14B8A6]/10 p-3 text-sm text-[#9FF5EA]">
                No major warning detected. Please still verify before posting.
              </p>
            )}
          </div>
        </article>
        <div className="glass-card grid gap-2 p-4 sm:grid-cols-3 xl:grid-cols-1">
          <Button type="button" onClick={() => onAction("Draft invoice prepared for review. Final posting requires approval.")}>
            Create Draft Invoice
          </Button>
          <Button type="button" variant="secondary" onClick={() => onAction("Draft voucher prepared for review. Final posting requires approval.")}>
            Create Draft Voucher
          </Button>
          <Button type="button" variant="ghost" onClick={() => onAction("Document suggestion rejected. No accounting entry was posted.")}>
            Reject
          </Button>
        </div>
      </aside>
    </section>
  );
}

function SuggestionRow({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="flex items-start justify-between gap-3 rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3">
      <span className="text-white/45">{label}</span>
      <span className="text-right font-semibold text-white">{value}</span>
    </div>
  );
}

export function EntriesLedgerWorkspace() {
  const { subscription } = useSubscriptionState();
  return (
    <PageFrame
      icon={ReceiptText}
      badge="Entries / Ledger"
      title="Entries & Ledger"
      subtitle="Review voucher entries, ledger mapping, and sample accounting data before posting through the Accounting Core."
    >
      <SubscriptionStatusStrip subscription={subscription} />
      <SubscriptionGate subscription={subscription} feature="advanced_ledger" title="Advanced ledger workflows need an active paid plan">
        <section className="grid gap-4 lg:grid-cols-[1fr_360px]">
          <div className="glass-card p-5">
            <h2 className="text-base font-semibold text-white">Sample Ledger Data</h2>
            <p className="mt-2 text-sm text-white/60">Visible sample data keeps pilot demos understandable even before a company imports real books.</p>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[560px] text-left text-sm">
                <thead className="text-white/50"><tr><th className="py-2">Ledger</th><th>Nature</th><th>Balance</th></tr></thead>
                <tbody>
                  {sampleLedgers.map(([name, nature, balance]) => (
                    <tr key={name} className="border-t border-white/10">
                      <td className="py-3 font-medium text-white">{name}</td>
                      <td className="text-white/60">{nature}</td>
                      <td className="text-white/80">{balance}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="space-y-4">
            <InfoCard title="Accounting Core" copy="Create real ledgers and vouchers inside Dashboard â†’ Ledgers/Vouchers." />
            <InfoCard title="Approval Control" copy="AI suggestions still require human approval before posting." />
            <Link className="premium-link w-full" href="/dashboard">Open Accounting Core</Link>
          </div>
        </section>
      </SubscriptionGate>
    </PageFrame>
  );
}

export function ReportsWorkspace() {
  const { subscription } = useSubscriptionState();
  const supabase = useMemo(() => createSupabaseBrowserClient(), []);
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [trialBalance, setTrialBalance] = useState<Array<{ ledger_id: string; ledger_name: string; account_nature: string; category: string; debit: string; credit: string }>>([]);
  const [pnl, setPnl] = useState<{ revenue: string; expenses: string; profit: string } | null>(null);
  const [balanceSheet, setBalanceSheet] = useState<{ assets: string; liabilities: string; equity: string; check_difference: string } | null>(null);
  const [cashFlow, setCashFlow] = useState<{ operating_cash_flow: string; investing_cash_flow: string; financing_cash_flow: string; net_cash_flow: string } | null>(null);
  const [gst, setGst] = useState<{ input_gst: string; output_gst: string; net_payable: string } | null>(null);
  const [scrutiny, setScrutiny] = useState<LedgerScrutiny | null>(null);
  const [tds, setTds] = useState({ amount: "100000", rate: "10" });
  const [pf, setPf] = useState({ wage: "15000", employeeRate: "12", employerRate: "12", ceiling: "15000" });
  const [esic, setEsic] = useState({ wage: "21000", employeeRate: "0.75", employerRate: "3.25", limit: "21000" });
  const [calculatorResult, setCalculatorResult] = useState<string>("Run a calculator to preview statutory deductions.");
  const [bankImportStatus, setBankImportStatus] = useState("Upload bank CSV for stored reconciliation import.");
  const [status, setStatus] = useState("Loading Launch Pack reports");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    let active = true;
    getAccessToken(supabase)
      .then(async (accessToken) => {
        if (!active) return;
        setToken(accessToken);
        if (!accessToken) {
          setStatus("Please login or continue in Alpha Demo Mode.");
          return;
        }
        const rows = await accountingApi.companies(accessToken);
        if (!active) return;
        setCompanies(rows);
        setCompanyId(rows[0]?.id ?? "");
        setStatus(rows.length ? "Select a company and refresh reports." : "No company found.");
      })
      .catch((error: Error) => {
        if (active) setStatus(error.message);
      });
    return () => {
      active = false;
    };
  }, [supabase]);

  async function refreshReports(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) return;
    setIsBusy(true);
    try {
      const [tb, pl, bs, cf, gstRow, scrutinyRow] = await Promise.all([
        accountingApi.trialBalance(selectedCompanyId, token),
        accountingApi.profitAndLoss(selectedCompanyId, token),
        accountingApi.balanceSheet(selectedCompanyId, token),
        accountingApi.cashFlow(selectedCompanyId, token),
        accountingApi.gstReport(selectedCompanyId, token),
        accountingApi.ledgerScrutiny(selectedCompanyId, token)
      ]);
      setTrialBalance(tb);
      setPnl(pl);
      setBalanceSheet(bs);
      setCashFlow(cf);
      setGst(gstRow);
      setScrutiny(scrutinyRow);
      setStatus("Launch Pack reports refreshed from stored accounting data.");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to refresh reports.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) void refreshReports(companyId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  async function runTds() {
    if (!token) return;
    const result = await accountingApi.calculateTds(token, { amount: tds.amount, rate_percent: tds.rate });
    setCalculatorResult(`TDS ${formatMoney(result.tds_amount)} | Net payable ${formatMoney(result.net_payable)}`);
  }

  async function runPf() {
    if (!token) return;
    const result = await accountingApi.calculatePf(token, {
      monthly_basic_wage: pf.wage,
      employee_rate_percent: pf.employeeRate,
      employer_rate_percent: pf.employerRate,
      wage_ceiling: pf.ceiling
    });
    setCalculatorResult(`PF employee ${formatMoney(result.employee_contribution)} + employer ${formatMoney(result.employer_contribution)} = ${formatMoney(result.total_contribution)}`);
  }

  async function runEsic() {
    if (!token) return;
    const result = await accountingApi.calculateEsic(token, {
      monthly_gross_wage: esic.wage,
      employee_rate_percent: esic.employeeRate,
      employer_rate_percent: esic.employerRate,
      wage_limit: esic.limit
    });
    setCalculatorResult(`${result.eligible ? "ESIC eligible" : "ESIC not eligible"} | Total ${formatMoney(result.total_contribution)}`);
  }

  async function uploadBankCsv(file: File | undefined) {
    if (!file || !token || !companyId) return;
    setIsBusy(true);
    try {
      const csv = await file.text();
      const result = await bankReconciliationApi.upload(companyId, token, {
        filename: file.name,
        csv_content: csv,
        bank_name: "Primary Bank"
      });
      setBankImportStatus(`Imported ${result.imported_count} bank transactions into reconciliation.`);
      await refreshReports();
    } catch (error) {
      setBankImportStatus(error instanceof Error ? error.message : "Bank CSV import failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function downloadGstrCsv(kind: "gstr1" | "gstr3b") {
    if (!token || !companyId) {
      setStatus("Select a company before downloading GST draft CSV.");
      return;
    }
    setIsBusy(true);
    try {
      const filename = kind === "gstr1" ? "gstr1-draft.csv" : "gstr3b-draft.csv";
      const response = await fetch(`/api/reports/${kind}.csv?company_id=${encodeURIComponent(companyId)}`, {
        method: "GET",
        cache: "no-store"
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({ detail: "CSV download failed." })) as { detail?: string };
        throw new Error(error.detail ?? "CSV download failed.");
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      setStatus(`${filename} downloaded as draft CSV.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "CSV download failed.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <PageFrame
      icon={FileSpreadsheet}
      badge="Launch Pack • 21 June"
      title="ABHAY Launch Reports"
      subtitle="Trial Balance, P&L, Balance Sheet, Cash Flow, GST drafts, payroll calculators, bank import, and ledger scrutiny."
    >
      <SubscriptionStatusStrip subscription={subscription} />
      <SubscriptionGate subscription={subscription} feature="reports" title="Reports require trial or paid access">
        <section className="glass-card grid gap-3 p-4 lg:grid-cols-[1fr_auto_auto_auto]">
          <select className="premium-select h-11 w-full" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
            <option value="">Select company</option>
            {companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}
          </select>
          <Button type="button" onClick={() => refreshReports()} disabled={isBusy || !companyId}>
            <FileSpreadsheet size={17} />
            Refresh Reports
          </Button>
          <Button type="button" variant="secondary" onClick={() => void downloadGstrCsv("gstr1")} disabled={isBusy || !companyId || !token}>
            <Download size={16} />
            GSTR-1 CSV
          </Button>
          <Button type="button" variant="secondary" onClick={() => void downloadGstrCsv("gstr3b")} disabled={isBusy || !companyId || !token}>
            <Download size={16} />
            GSTR-3B CSV
          </Button>
        </section>

        <section className="grid gap-4 lg:grid-cols-4">
          <MetricCard title="Revenue" value={formatMoney(pnl?.revenue)} copy="From stored income ledgers" />
          <MetricCard title="Expenses" value={formatMoney(pnl?.expenses)} copy="From stored expense ledgers" />
          <MetricCard title="Profit / Loss" value={formatMoney(pnl?.profit)} copy="Live P&L summary" />
          <MetricCard title="GST Net Payable" value={formatMoney(gst?.net_payable)} copy="GST assistance only" />
        </section>

        <section className="grid gap-4 xl:grid-cols-3">
          <StatementCard title="Balance Sheet" rows={[
            ["Assets", formatMoney(balanceSheet?.assets)],
            ["Liabilities", formatMoney(balanceSheet?.liabilities)],
            ["Equity", formatMoney(balanceSheet?.equity)],
            ["Check Difference", formatMoney(balanceSheet?.check_difference)]
          ]} />
          <StatementCard title="Cash Flow Summary" rows={[
            ["Operating Cash Flow", formatMoney(cashFlow?.operating_cash_flow)],
            ["Investing Cash Flow", formatMoney(cashFlow?.investing_cash_flow)],
            ["Financing Cash Flow", formatMoney(cashFlow?.financing_cash_flow)],
            ["Net Cash Flow", formatMoney(cashFlow?.net_cash_flow)]
          ]} />
          <StatementCard title="GST Summary Report" rows={[
            ["Output Tax", formatMoney(gst?.output_gst)],
            ["Input Tax", formatMoney(gst?.input_gst)],
            ["Net Payable", formatMoney(gst?.net_payable)],
            ["Filing Status", "Draft only"]
          ]} />
        </section>

        <section className="glass-panel p-5">
          <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-base font-semibold text-white">Trial Balance</h2>
              <p className="mt-1 text-sm text-white/60">Generated from stored vouchers and ledger balances.</p>
            </div>
            <span className="rounded-full border border-[#14B8A6]/20 bg-[#14B8A6]/10 px-3 py-1 text-xs font-semibold text-[#9FF5EA]">
              {trialBalance.length} ledgers
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="text-white/50"><tr><th className="py-2">Ledger</th><th>Nature</th><th>Category</th><th>Debit</th><th>Credit</th></tr></thead>
              <tbody>
                {trialBalance.map((row) => (
                  <tr key={row.ledger_id} className="border-t border-white/10">
                    <td className="py-3 font-medium text-white">{row.ledger_name}</td>
                    <td className="text-white/60">{row.account_nature}</td>
                    <td className="text-white/60">{row.category}</td>
                    <td className="text-white/80">{formatMoney(row.debit)}</td>
                    <td className="text-white/80">{formatMoney(row.credit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
          <div className="glass-panel p-5">
            <div className="mb-4 flex items-center gap-2">
              <Calculator className="text-orange-200" size={20} />
              <h2 className="text-base font-semibold text-white">TDS / PF / ESIC Calculators</h2>
            </div>
            <div className="grid gap-4 md:grid-cols-3">
              <CalculatorBox title="TDS" fields={[
                ["Amount", tds.amount, (value) => setTds({ ...tds, amount: value })],
                ["Rate %", tds.rate, (value) => setTds({ ...tds, rate: value })]
              ]} onRun={runTds} />
              <CalculatorBox title="PF" fields={[
                ["Wage", pf.wage, (value) => setPf({ ...pf, wage: value })],
                ["Employee %", pf.employeeRate, (value) => setPf({ ...pf, employeeRate: value })],
                ["Employer %", pf.employerRate, (value) => setPf({ ...pf, employerRate: value })],
                ["Ceiling", pf.ceiling, (value) => setPf({ ...pf, ceiling: value })]
              ]} onRun={runPf} />
              <CalculatorBox title="ESIC" fields={[
                ["Gross Wage", esic.wage, (value) => setEsic({ ...esic, wage: value })],
                ["Employee %", esic.employeeRate, (value) => setEsic({ ...esic, employeeRate: value })],
                ["Employer %", esic.employerRate, (value) => setEsic({ ...esic, employerRate: value })],
                ["Limit", esic.limit, (value) => setEsic({ ...esic, limit: value })]
              ]} onRun={runEsic} />
            </div>
            <p className="empty-state mt-4">{calculatorResult}</p>
          </div>

          <div className="glass-panel p-5">
            <h2 className="text-base font-semibold text-white">Bank CSV Import</h2>
            <p className="mt-2 text-sm leading-6 text-white/60">Imports into Bank Reconciliation using stored bank transaction tables.</p>
            <Input className="mt-4" type="file" accept=".csv,text/csv" onChange={(event) => void uploadBankCsv(event.target.files?.[0])} disabled={isBusy || !companyId} />
            <p className="empty-state mt-4">{bankImportStatus}</p>
          </div>
        </section>

        <section className="glass-panel p-5">
          <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-base font-semibold text-white">Ledger Scrutiny Dashboard</h2>
              <p className="mt-1 text-sm text-white/60">Flags risky accounting patterns from stored ledgers, vouchers, invoices and bank imports.</p>
            </div>
            <div className="flex flex-wrap gap-2 text-xs font-semibold">
              <span className="rounded-full border border-red-400/20 bg-red-400/10 px-3 py-1 text-red-200">High {scrutiny?.high_risk_count ?? 0}</span>
              <span className="rounded-full border border-amber-300/20 bg-amber-300/10 px-3 py-1 text-amber-100">Warnings {scrutiny?.warning_count ?? 0}</span>
              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-white/70">Total {scrutiny?.issue_count ?? 0}</span>
            </div>
          </div>
          <div className="grid gap-3 lg:grid-cols-2">
            {scrutiny?.issues.length ? scrutiny.issues.map((issue, index) => (
              <div key={`${index}-${issue.title}`} className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-4">
                <div className="flex items-start gap-3">
                  <AlertTriangle className={issue.severity === "high" ? "text-red-300" : "text-amber-200"} size={18} />
                  <div>
                    <p className="font-semibold text-white">{issue.title}</p>
                    <p className="mt-1 text-sm text-white/60">{issue.detail}</p>
                    {issue.amount ? <p className="mt-2 text-xs text-white/50">Amount: {formatMoney(issue.amount)}</p> : null}
                  </div>
                </div>
              </div>
            )) : <p className="empty-state lg:col-span-2">No scrutiny issues detected for current stored data.</p>}
          </div>
        </section>

        <p className="empty-state">
          {status} GST assistance only. GSTR exports are draft CSVs for review, not government filing.
        </p>
      </SubscriptionGate>
    </PageFrame>
  );
}

export function SettingsWorkspace() {
  const router = useRouter();
  const supabase = useMemo(() => createSupabaseBrowserClient(), []);
  const [form, setForm] = useState({
    companyName: "",
    gstin: "",
    industry: "Trading",
    state: "27",
    financialYear: "FY 2025-26"
  });
  const [companies, setCompanies] = useState<WorkspaceCompany[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState("");
  const [members, setMembers] = useState<CompanyMember[]>([]);
  const [inviteUserId, setInviteUserId] = useState("");
  const [inviteRole, setInviteRole] = useState<CompanyRole>("Accountant");
  const [status, setStatus] = useState("Complete onboarding once to prepare ABHAY for pilot use.");
  const [isBusy, setIsBusy] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    let active = true;
    void loadCompanies(() => active);
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supabase]);

  async function loadCompanies(isActive = () => true) {
    setLoadFailed(false);
    setStatus("Loading company workspace...");
    try {
      const {
        data: { user }
      } = await supabase.auth.getUser();
      const rows = await listWorkspaceCompanies(supabase);
      const savedCompanyId = typeof window === "undefined" ? "" : window.localStorage.getItem(LAST_COMPANY_KEY) ?? "";
      const selectedCompany = rows.find((company) => company.id === savedCompanyId) ?? rows[0];
      if (process.env.NODE_ENV === "development") {
        console.log("ABHAY settings company load", {
          userId: user?.id ?? "missing",
          companyCount: rows.length,
          selectedCompanyId: selectedCompany?.id ?? ""
        });
      }
      if (isActive()) {
        setCompanies(rows);
        setSelectedCompanyId(selectedCompany?.id ?? "");
        setStatus(rows.length ? "Company workspace loaded." : "Create your first company workspace.");
      }
    } catch {
      if (isActive()) {
        setLoadFailed(true);
        setCompanies([]);
        setSelectedCompanyId("");
        setStatus("Unable to load companies. Retry");
      }
    }
  }

  useEffect(() => {
    if (!selectedCompanyId) {
      setMembers([]);
      return;
    }
    listCompanyMembers(supabase, selectedCompanyId)
      .then(setMembers)
      .catch((error: Error) => setStatus(error.message));
  }, [selectedCompanyId, supabase]);

  async function save() {
    if (!form.companyName.trim()) return;
    setIsBusy(true);
    try {
      const company = await createWorkspaceCompany(supabase, {
        company_name: form.companyName.trim(),
        gstin: form.gstin.trim() || null,
        industry: form.industry.trim() || null,
        state: form.state,
        financial_year: form.financialYear.trim() || null
      });
      setCompanies((current) => [company, ...current.filter((item) => item.id !== company.id)]);
      setSelectedCompanyId(company.id);
      persistSelectedCompany(company.id);
      setStatus("Company workspace created. You are the Owner. Opening dashboard...");
      router.push("/dashboard");
      router.refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Company workspace could not be saved.");
    } finally {
      setIsBusy(false);
    }
  }

  function continueToDashboard() {
    const targetCompanyId = selectedCompanyId || companies[0]?.id;
    if (!targetCompanyId) {
      setStatus("Create a company first, then continue to Dashboard.");
      return;
    }
    persistSelectedCompany(targetCompanyId);
    if (process.env.NODE_ENV === "development") {
      console.log("ABHAY settings selected company", { selectedCompanyId: targetCompanyId });
    }
    setStatus("Company selected. Opening dashboard...");
    router.push("/dashboard");
    router.refresh();
  }

  async function inviteMember() {
    if (!selectedCompanyId || !inviteUserId.trim()) return;
    setIsBusy(true);
    try {
      const member = await inviteCompanyMember(supabase, selectedCompanyId, inviteUserId.trim(), inviteRole);
      setMembers((current) => [...current.filter((item) => item.id !== member.id), member]);
      setInviteUserId("");
      setStatus("Team member invited into company workspace.");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Invite failed.");
    } finally {
      setIsBusy(false);
    }
  }

  async function changeRole(memberId: string, role: CompanyRole) {
    setIsBusy(true);
    try {
      const member = await updateCompanyMemberRole(supabase, memberId, role);
      setMembers((current) => current.map((item) => (item.id === member.id ? member : item)));
      setStatus("Role updated.");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Role update failed.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <PageFrame
      icon={Settings}
      badge="Settings"
      title="Business Onboarding"
      subtitle="Capture company context for GST, imports, financial year selection, and AI accounting memory."
    >
      <section className="glass-panel p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-white">Select Company</h2>
            <p className="mt-2 text-sm leading-6 text-white/60">
              Choose your active company workspace, then continue to the ABHAY dashboard.
            </p>
          </div>
          {loadFailed ? (
            <Button type="button" variant="secondary" onClick={() => void loadCompanies()} disabled={isBusy}>
              Retry
            </Button>
          ) : null}
        </div>
        <div className="mt-4 grid gap-3 lg:grid-cols-[1fr_auto]">
          <select
            className="premium-select h-11 w-full"
            value={selectedCompanyId}
            onChange={(event) => setSelectedCompanyId(event.target.value)}
            disabled={!companies.length}
          >
            <option value="">{companies.length ? "Select company" : "No company found"}</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>{company.company_name}</option>
            ))}
          </select>
          <Button type="button" onClick={continueToDashboard} disabled={isBusy || !companies.length}>
            Continue to Dashboard
          </Button>
        </div>
      </section>

      <form className="glass-panel grid gap-4 p-5 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <div className="lg:col-span-2">
          <h2 className="text-lg font-semibold text-white">{companies.length ? "Register / Add Company" : "Create Company"}</h2>
          <p className="mt-2 text-sm leading-6 text-white/60">
            {companies.length ? "Add another business workspace when needed." : "No company exists yet. Create your first company to unlock Dashboard, AI Workbench and Reports."}
          </p>
        </div>
        <Field label="Company Name"><Input value={form.companyName} onChange={(event) => setForm({ ...form, companyName: event.target.value })} required /></Field>
        <Field label="GSTIN"><Input value={form.gstin} onChange={(event) => setForm({ ...form, gstin: event.target.value.toUpperCase() })} maxLength={15} placeholder="27ABCDE1234F1Z5" /></Field>
        <Field label="Industry"><Input value={form.industry} onChange={(event) => setForm({ ...form, industry: event.target.value })} /></Field>
        <Field label="GST State">
          <select className="premium-select h-11 w-full" value={form.state} onChange={(event) => setForm({ ...form, state: event.target.value })}>
            {gstStates.map(([code, name]) => <option key={code} value={code}>{code} {name}</option>)}
          </select>
        </Field>
        <Field label="Financial year"><Input value={form.financialYear} onChange={(event) => setForm({ ...form, financialYear: event.target.value })} /></Field>
        <div className="lg:col-span-2"><Button type="submit" disabled={isBusy || !form.companyName.trim()}><BadgeCheck size={17} /> Create company workspace</Button></div>
      </form>

      <section className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <form className="glass-panel p-5" onSubmit={(event) => { event.preventDefault(); void inviteMember(); }}>
          <h2 className="text-base font-semibold text-white">Invite Team Member</h2>
          <p className="mt-2 text-sm leading-6 text-white/60">Invite by Supabase user ID. Roles are isolated per company workspace.</p>
          <div className="mt-4 grid gap-3">
            <Input value={inviteUserId} onChange={(event) => setInviteUserId(event.target.value)} placeholder="Supabase user ID" />
            <select className="premium-select h-11 w-full" value={inviteRole} onChange={(event) => setInviteRole(event.target.value as CompanyRole)}>
              {roles.map((role) => <option key={role} value={role}>{role}</option>)}
            </select>
            <Button type="submit" disabled={isBusy || !selectedCompanyId || !inviteUserId.trim()}>Invite member</Button>
          </div>
        </form>

        <div className="glass-panel p-5">
          <h2 className="text-base font-semibold text-white">Role Management</h2>
          <p className="mt-2 text-sm leading-6 text-white/60">Owner and Admin can manage roles. Dashboard data remains company-scoped.</p>
          <div className="mt-4 space-y-3">
            {members.length ? members.map((member) => (
              <div key={member.id} className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3 text-sm">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="font-semibold text-white">{member.user_id}</p>
                    <p className="text-xs text-muted-foreground">Member since {new Date(member.created_at).toLocaleDateString("en-IN")}</p>
                  </div>
                  <select className="premium-select h-10" value={member.role} onChange={(event) => void changeRole(member.id, event.target.value as CompanyRole)} disabled={isBusy}>
                    {roles.map((role) => <option key={role} value={role}>{role}</option>)}
                  </select>
                </div>
              </div>
            )) : <p className="empty-state">No team members yet. The first company creator becomes Owner automatically.</p>}
          </div>
        </div>
      </section>
      <p className="empty-state">{status}</p>
    </PageFrame>
  );
}

function persistSelectedCompany(companyId: string) {
  window.localStorage.setItem(LAST_COMPANY_KEY, companyId);
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `abhay_selected_company_id=${encodeURIComponent(companyId)}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`;
}

export function AdminWorkspace() {
  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [analyticsStatus, setAnalyticsStatus] = useState("Loading visitor analytics...");

  useEffect(() => {
    let cancelled = false;
    async function loadAnalytics() {
      try {
        const response = await fetch("/api/analytics/summary", { cache: "no-store" });
        if (!response.ok) {
          throw new Error("Analytics unavailable");
        }
        const data = (await response.json()) as AnalyticsSummary;
        if (!cancelled) {
          setAnalytics(data);
          setAnalyticsStatus(data.message ?? "Visitor analytics ready.");
        }
      } catch {
        if (!cancelled) {
          setAnalytics(null);
          setAnalyticsStatus("Analytics is syncing. Visit data will appear after the table is installed.");
        }
      }
    }
    void loadAnalytics();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <PageFrame
      icon={ShieldCheck}
      badge="Admin Alpha"
      title="Admin"
      subtitle="Basic pilot controls for subscription, company access, security posture, and operational readiness."
    >
      <section className="grid gap-4 lg:grid-cols-3">
        <InfoCard title="Subscription Store" copy="Supabase tables store profiles, trials, subscriptions, and payments for pilot access control." />
        <InfoCard title="Access Control" copy="Company data remains scoped through backend membership checks and owner access request approval." />
        <InfoCard title="Security Note" copy="Frontend uses publishable keys only. Razorpay secret is server-side only." />
      </section>
      <section className="mt-4 grid gap-4 lg:grid-cols-3">
        <MetricCard title="Total Visits" value={analytics ? String(analytics.totalVisits) : "0"} copy="Privacy-safe page visits tracked without raw IP storage." />
        <MetricCard title="Visits Today" value={analytics ? String(analytics.visitsToday) : "0"} copy="Based on server-side visit events received today." />
        <MetricCard title="Unique Visitors" value={analytics ? String(analytics.uniqueVisitors) : "0"} copy="Estimated from salted IP hashes only." />
      </section>
      <section className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="glass-card p-5">
          <h2 className="text-base font-semibold text-white">Top pages</h2>
          <p className="mt-2 text-sm leading-6 text-white/60">{analyticsStatus}</p>
          <div className="mt-4 space-y-2">
            {analytics?.topPages.length ? analytics.topPages.map((page) => (
              <div key={page.path} className="flex items-center justify-between gap-3 rounded-xl border border-[#1F2937] bg-[#111827]/80 px-3 py-2 text-sm">
                <span className="truncate text-white/70">{page.path}</span>
                <span className="font-semibold text-white">{page.visits}</span>
              </div>
            )) : <p className="empty-state">No visit data yet.</p>}
          </div>
        </div>
        <div className="glass-card p-5">
          <h2 className="text-base font-semibold text-white">Last 20 visits</h2>
          <div className="mt-4 max-h-80 space-y-2 overflow-y-auto pr-1">
            {analytics?.lastVisits.length ? analytics.lastVisits.map((visit, index) => (
              <div key={`${visit.createdAt}-${index}`} className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <span className="truncate font-semibold text-white">{visit.path}</span>
                  <span className="text-xs text-white/50">{new Date(visit.createdAt).toLocaleString("en-IN")}</span>
                </div>
                <p className="mt-1 text-xs text-white/50">{visit.device} • {visit.browser}</p>
              </div>
            )) : <p className="empty-state">Recent visits will appear here after traffic starts.</p>}
          </div>
        </div>
      </section>
      <div className="glass-card mt-4 p-5">
        <div className="flex items-start gap-3">
          <LockKeyhole className="text-orange-200" size={22} />
          <div>
            <h2 className="text-base font-semibold text-white">Production hardening path</h2>
            <p className="mt-2 text-sm leading-6 text-white/60">
              Before paid launch: persist subscriptions in backend, verify Razorpay signatures server-side, disable Alpha demo token, and enforce plan limits through API middleware.
            </p>
          </div>
        </div>
      </div>
    </PageFrame>
  );
}

function MetricCard({ title, value, copy }: Readonly<{ title: string; value: string; copy: string }>) {
  return (
    <article className="glass-card float-card p-5">
      <p className="text-sm text-white/50">{title}</p>
      <p className="mt-2 text-2xl font-semibold text-white">{value}</p>
      <p className="mt-2 text-xs leading-5 text-white/50">{copy}</p>
    </article>
  );
}

function StatementCard({ title, rows }: Readonly<{ title: string; rows: Array<[string, string]> }>) {
  return (
    <article className="glass-card float-card p-5">
      <h2 className="text-base font-semibold text-white">{title}</h2>
      <div className="mt-4 space-y-3">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between gap-3 rounded-xl border border-[#1F2937] bg-[#111827]/80 px-3 py-2 text-sm">
            <span className="text-white/60">{label}</span>
            <span className="font-semibold text-white">{value}</span>
          </div>
        ))}
      </div>
    </article>
  );
}

function CalculatorBox({
  title,
  fields,
  onRun
}: Readonly<{
  title: string;
  fields: Array<[string, string, (value: string) => void]>;
  onRun: () => Promise<void>;
}>) {
  const [isRunning, setIsRunning] = useState(false);
  return (
    <div className="rounded-2xl border border-[#1F2937] bg-[#111827]/80 p-4">
      <h3 className="font-semibold text-white">{title}</h3>
      <div className="mt-3 grid gap-2">
        {fields.map(([label, value, onChange]) => (
          <label key={label} className="space-y-1 text-xs font-semibold text-white/60">
            <span>{label}</span>
            <Input value={value} inputMode="decimal" onChange={(event) => onChange(event.target.value)} />
          </label>
        ))}
      </div>
      <Button
        className="mt-3 w-full"
        type="button"
        disabled={isRunning}
        onClick={async () => {
          setIsRunning(true);
          try {
            await onRun();
          } finally {
            setIsRunning(false);
          }
        }}
      >
        {isRunning ? "Calculating..." : `Run ${title}`}
      </Button>
    </div>
  );
}

function formatMoney(value: string | number | null | undefined) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(value ?? 0));
}

function PageFrame({
  icon: Icon,
  badge,
  title,
  subtitle,
  children
}: Readonly<{
  icon: LucideIcon;
  badge: string;
  title: string;
  subtitle: string;
  children: React.ReactNode;
}>) {
  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(0,0,0,0.28)] lg:p-6">
          <div className="relative z-10 flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
              <Icon size={22} />
            </span>
            <div>
              <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">{badge}</span>
              <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
              <p className="mt-1 text-sm text-white/70">{subtitle}</p>
            </div>
          </div>
        </header>
        {children}
      </section>
    </main>
  );
}

function InfoCard({ title, copy }: Readonly<{ title: string; copy: string }>) {
  return (
    <article className="glass-card float-card p-5">
      <Building2 className="text-orange-200" size={21} />
      <h2 className="mt-3 text-base font-semibold text-white">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-white/60">{copy}</p>
    </article>
  );
}

function Field({ label, children }: Readonly<{ label: string; children: React.ReactNode }>) {
  return (
    <label className="block space-y-2 text-sm font-semibold text-white">
      <span>{label}</span>
      {children}
    </label>
  );
}
