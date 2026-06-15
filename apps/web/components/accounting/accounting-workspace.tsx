"use client";

import {
  Banknote,
  BookOpen,
  Check,
  Copy,
  FileDown,
  FileText,
  Landmark,
  Loader2,
  Plus,
  ReceiptIndianRupee,
  RefreshCw,
  Search,
  Trash2
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  accountingApi,
  AccountNature,
  AccessRequest,
  Company,
  DashboardMetrics,
  GstReport,
  Ledger,
  LedgerCategory,
  LedgerGroup,
  TrialBalanceRow,
  VoucherType
} from "@/lib/api/accounting";
import { getAccessToken, isAlphaDemoModeEnabled, isLocalDevelopmentApi, tokenSourceFor } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { publicEnv } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

type Tab = "dashboard" | "ledgers" | "vouchers" | "invoices" | "reports" | "gst";
export const LAST_COMPANY_KEY = "abhay.lastCompanyId";

const natures: AccountNature[] = ["asset", "liability", "income", "expense", "equity"];
const categories: LedgerCategory[] = [
  "cash",
  "bank",
  "sundry_debtor",
  "sundry_creditor",
  "sales",
  "purchase",
  "direct_expense",
  "indirect_expense",
  "input_gst",
  "output_gst",
  "capital",
  "other"
];
const voucherTypes: VoucherType[] = ["receipt", "payment", "contra", "journal", "purchase", "sales"];
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
const gstRates = ["0%", "5%", "12%", "18%", "28%"];

export function AccountingWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [groups, setGroups] = useState<LedgerGroup[]>([]);
  const [ledgers, setLedgers] = useState<Ledger[]>([]);
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [trialBalance, setTrialBalance] = useState<TrialBalanceRow[]>([]);
  const [pnl, setPnl] = useState<{ revenue: string; expenses: string; profit: string } | null>(null);
  const [balanceSheet, setBalanceSheet] = useState<{
    assets: string;
    liabilities: string;
    equity: string;
    check_difference: string;
  } | null>(null);
  const [cashFlow, setCashFlow] = useState<{ net_cash_flow: string; operating_cash_flow: string } | null>(null);
  const [gstReport, setGstReport] = useState<GstReport | null>(null);
  const [invoices, setInvoices] = useState<Array<{ id: string; invoice_number: string; total_amount: string }>>([]);
  const [tab, setTab] = useState<Tab>("dashboard");
  const [search, setSearch] = useState("");
  const [natureFilter, setNatureFilter] = useState("");
  const [status, setStatus] = useState("Loading accounting workspace");
  const [isBusy, setIsBusy] = useState(false);
  const [newCompanyLegalName, setNewCompanyLegalName] = useState("");
  const [newCompanyTradeName, setNewCompanyTradeName] = useState("");
  const [newCompanyGstin, setNewCompanyGstin] = useState("");
  const [newCompanyStateCode, setNewCompanyStateCode] = useState("");
  const [accessRequests, setAccessRequests] = useState<AccessRequest[]>([]);
  const [requestCompanyId, setRequestCompanyId] = useState("");
  const [requestRole, setRequestRole] = useState<"accountant" | "viewer">("accountant");
  const [tokenSource, setTokenSource] = useState<"supabase" | "demo" | "missing">("missing");
  const [backendConnected, setBackendConnected] = useState(false);
  const [companyIdCopied, setCompanyIdCopied] = useState(false);
  const showAlphaDebug = isAlphaDemoModeEnabled() || isLocalDevelopmentApi();

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), 3000);

    fetch(`${publicEnv.NEXT_PUBLIC_API_URL}/health`, { cache: "no-store", signal: controller.signal })
      .then((response) => {
        if (active) setBackendConnected(response.ok);
      })
      .catch(() => {
        if (active) setBackendConnected(false);
      })
      .finally(() => window.clearTimeout(timeoutId));

    return () => {
      active = false;
      window.clearTimeout(timeoutId);
      controller.abort();
    };
  }, []);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      setTokenSource(tokenSourceFor(accessToken));
      if (!accessToken) {
        setStatus("Please login or continue in Alpha Demo Mode.");
        return;
      }
      loadCompanies(accessToken);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supabase]);

  async function loadCompanies(accessToken = token) {
    if (!accessToken) return;
    try {
      const items = await accountingApi.companies(accessToken);
      const rows = items.length
        ? items
        : [await accountingApi.createCompany(accessToken, { legal_name: "ANVRITAI Demo Company" })];
      setCompanies(rows);
      const savedCompanyId = typeof window === "undefined" ? "" : window.localStorage.getItem(LAST_COMPANY_KEY) ?? "";
      const selectedCompany = rows.find((company) => company.id === savedCompanyId) ?? rows[0];
      setCompanyId(selectedCompany.id);
      setStatus("Workspace ready");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to load companies.");
    }
  }

  async function registerCompany() {
    if (!token || !newCompanyLegalName.trim()) return;
    setIsBusy(true);
    try {
      const company = await accountingApi.createCompany(token, {
        legal_name: newCompanyLegalName.trim(),
        trade_name: newCompanyTradeName.trim() || null,
        gstin: newCompanyGstin.trim() || null,
        state_code: newCompanyStateCode.trim() || null
      });
      setCompanies((current) => [...current.filter((item) => item.id !== company.id), company]);
      setCompanyId(company.id);
      window.localStorage.setItem(LAST_COMPANY_KEY, company.id);
      setNewCompanyLegalName("");
      setNewCompanyTradeName("");
      setNewCompanyGstin("");
      setNewCompanyStateCode("");
      setStatus(`Company created: ${company.legal_name}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to create company.");
    } finally {
      setIsBusy(false);
    }
  }

  async function refresh(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) {
      return;
    }
    setIsBusy(true);
    try {
      const query = new URLSearchParams();
      if (search) query.set("search", search);
      if (natureFilter) query.set("nature", natureFilter);
      const queryString = query.toString() ? `?${query}` : "";
      const [groupRows, ledgerRows, metricRows, tbRows, pnlRows, bsRows, cfRows, invoiceRows, gstRows] =
        await Promise.all([
          accountingApi.groups(selectedCompanyId, token),
          accountingApi.ledgers(selectedCompanyId, token, queryString),
          accountingApi.dashboard(selectedCompanyId, token),
          accountingApi.trialBalance(selectedCompanyId, token),
          accountingApi.profitAndLoss(selectedCompanyId, token),
          accountingApi.balanceSheet(selectedCompanyId, token),
          accountingApi.cashFlow(selectedCompanyId, token),
          accountingApi.invoices(selectedCompanyId, token),
          accountingApi.gstReport(selectedCompanyId, token)
        ]);
      setGroups(groupRows);
      setLedgers(ledgerRows);
      setMetrics(metricRows);
      setTrialBalance(tbRows);
      setPnl(pnlRows);
      setBalanceSheet(bsRows);
      setCashFlow(cfRows);
      setInvoices(invoiceRows);
      setGstReport(gstRows);
      void loadAccessRequests(selectedCompanyId);
      setStatus("Accounting data refreshed");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to refresh accounting data.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) {
      window.localStorage.setItem(LAST_COMPANY_KEY, companyId);
      void refresh(companyId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  const companyName = companies.find((company) => company.id === companyId)?.legal_name ?? "Company";

  async function loadAccessRequests(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) return;
    try {
      setAccessRequests(await accountingApi.accessRequests(selectedCompanyId, token));
    } catch {
      setAccessRequests([]);
    }
  }

  async function submitAccessRequest() {
    if (!token || !requestCompanyId.trim()) return;
    setIsBusy(true);
    try {
      const request = await accountingApi.requestAccess(requestCompanyId.trim(), token, requestRole);
      setStatus(`Access request sent to ${request.company_legal_name}.`);
      setRequestCompanyId("");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to request access.");
    } finally {
      setIsBusy(false);
    }
  }

  async function decideAccessRequest(requestId: string, decision: "approve" | "reject", role: "accountant" | "viewer") {
    if (!token || !companyId) return;
    setIsBusy(true);
    try {
      await accountingApi.decideAccessRequest(companyId, requestId, token, decision, role);
      setStatus(`Access request ${decision === "approve" ? "approved" : "rejected"}.`);
      await loadAccessRequests(companyId);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to update access request.");
    } finally {
      setIsBusy(false);
    }
  }

  async function copyCompanyId() {
    if (!companyId) return;
    try {
      await window.navigator.clipboard.writeText(companyId);
      setCompanyIdCopied(true);
      setStatus("Company ID copied.");
      window.setTimeout(() => setCompanyIdCopied(false), 1600);
    } catch {
      setStatus(`Company ID: ${companyId}`);
    }
  }

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
              <Landmark size={21} aria-hidden="true" />
            </span>
            <div>
              <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">AI Accounting Alpha</span>
              <h1 className="text-2xl font-semibold sm:text-3xl">ABHAY Accounting OS by ANVRITAI</h1>
              <p className="mt-1 text-sm text-white/80">Ledger, vouchers, GST, invoices, and live AI reports</p>
              {showAlphaDebug ? (
                <p className="mt-3 rounded-xl border border-white/15 bg-white/10 px-3 py-2 text-xs text-white/75">
                  API URL: {publicEnv.NEXT_PUBLIC_API_URL} | token source: {tokenSource} | backend status:{" "}
                  {backendConnected ? "connected" : "offline"}
                </p>
              ) : null}
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <select
              className="premium-select text-slate-900"
              value={companyId}
              onChange={(event) => setCompanyId(event.target.value)}
            >
              {companies.map((company) => (
                <option key={company.id} value={company.id}>
                  {company.legal_name}
                </option>
              ))}
            </select>
            <Button type="button" variant="secondary" onClick={() => refresh()} disabled={isBusy || !companyId}>
              {isBusy ? <Loader2 className="animate-spin" size={17} /> : <RefreshCw size={17} />}
              Refresh
            </Button>
          </div>
          </div>
        </header>

        <nav className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
          {(["dashboard", "ledgers", "vouchers", "invoices", "reports", "gst"] as Tab[]).map((item) => (
            <Button
              key={item}
              type="button"
              variant={tab === item ? "primary" : "secondary"}
              onClick={() => setTab(item)}
              className="capitalize"
            >
              {item}
            </Button>
          ))}
        </nav>

        <p className="glass-card px-3 py-2 text-sm text-muted-foreground">{status}</p>

        <section className="grid gap-4 xl:grid-cols-[1fr_1fr]">
          <form
            className="glass-card p-4"
            onSubmit={(event) => {
              event.preventDefault();
              void registerCompany();
            }}
          >
            <h2 className="text-base font-semibold">Register / Add Company</h2>
            <p className="mt-1 text-sm text-muted-foreground">Create a persisted company and assign yourself as owner.</p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <Input value={newCompanyLegalName} onChange={(event) => setNewCompanyLegalName(event.target.value)} placeholder="Company legal name" required />
              <Input value={newCompanyTradeName} onChange={(event) => setNewCompanyTradeName(event.target.value)} placeholder="Trade name" />
              <Input value={newCompanyGstin} onChange={(event) => setNewCompanyGstin(event.target.value.toUpperCase())} placeholder="GSTIN optional" minLength={15} maxLength={15} />
              <select className="premium-select h-11" value={newCompanyStateCode} onChange={(event) => setNewCompanyStateCode(event.target.value)}>
                <option value="">GST state code optional</option>
                {gstStates.map(([code, name]) => <option key={code} value={code}>{code} {name}</option>)}
              </select>
            </div>
            <Button className="mt-3" type="submit" disabled={isBusy || !newCompanyLegalName.trim()}>
              {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Plus size={17} />}
              Register company
            </Button>
          </form>
          <AccessRequestsPanel
            accessRequests={accessRequests}
            currentCompanyId={companyId}
            currentCompanyName={companyName}
            companyIdCopied={companyIdCopied}
            requestCompanyId={requestCompanyId}
            requestRole={requestRole}
            setRequestCompanyId={setRequestCompanyId}
            setRequestRole={setRequestRole}
            onRequest={submitAccessRequest}
            onDecision={decideAccessRequest}
            onCopyCompanyId={copyCompanyId}
            isBusy={isBusy}
          />
        </section>

        {tab === "dashboard" ? <DashboardPanel metrics={metrics} companyName={companyName} /> : null}
        {tab === "ledgers" ? (
          <LedgersPanel
            groups={groups}
            ledgers={ledgers}
            search={search}
            natureFilter={natureFilter}
            setSearch={setSearch}
            setNatureFilter={setNatureFilter}
            onRefresh={() => refresh()}
            onCreateGroup={(payload) =>
              token ? accountingApi.createGroup(companyId, token, payload).then(() => refresh()) : null
            }
            onCreateLedger={(payload) =>
              token ? accountingApi.createLedger(companyId, token, payload).then(() => refresh()) : null
            }
            onDeleteLedger={(ledgerId) =>
              token ? accountingApi.deleteLedger(companyId, ledgerId, token).then(() => refresh()) : null
            }
          />
        ) : null}
        {tab === "vouchers" ? (
          <VouchersPanel
            ledgers={ledgers}
            onPost={(payload) =>
              token ? accountingApi.createVoucher(companyId, token, payload).then(() => refresh()) : null
            }
          />
        ) : null}
        {tab === "invoices" ? (
          <InvoicesPanel
            ledgers={ledgers}
            invoices={invoices}
            companyId={companyId}
            token={token}
            onCreate={(payload) =>
              token ? accountingApi.createInvoice(companyId, token, payload).then(() => refresh()) : null
            }
          />
        ) : null}
        {tab === "reports" ? (
          <ReportsPanel trialBalance={trialBalance} pnl={pnl} balanceSheet={balanceSheet} cashFlow={cashFlow} />
        ) : null}
        {tab === "gst" ? <GstPanel gstReport={gstReport} /> : null}
      </section>
    </main>
  );
}

function DashboardPanel({ metrics, companyName }: { metrics: DashboardMetrics | null; companyName: string }) {
  const cards: Array<[string, string | undefined, LucideIcon]> = [
    ["Revenue", metrics?.revenue, ReceiptIndianRupee],
    ["Expenses", metrics?.expenses, FileText],
    ["Profit", metrics?.profit, BookOpen],
    ["Cash Position", metrics?.cash_position, Banknote],
    ["Receivables", metrics?.receivables, Landmark],
    ["Payables", metrics?.payables, FileText]
  ];
  return (
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <div className="sm:col-span-2 xl:col-span-3">
        <h2 className="text-lg font-semibold">{companyName}</h2>
      </div>
      {cards.map(([label, value, Icon]) => (
        <div key={String(label)} className="glass-card float-card p-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">{label}</p>
            <span className="rounded-xl bg-orange-50 p-2 text-primary"><Icon size={18} aria-hidden="true" /></span>
          </div>
          <p className="mt-3 text-2xl font-semibold">{formatMoney(value)}</p>
        </div>
      ))}
    </section>
  );
}

function LedgersPanel(props: {
  groups: LedgerGroup[];
  ledgers: Ledger[];
  search: string;
  natureFilter: string;
  setSearch: (value: string) => void;
  setNatureFilter: (value: string) => void;
  onRefresh: () => void;
  onCreateGroup: (payload: { name: string; account_nature: AccountNature }) => Promise<unknown> | false | null;
  onCreateLedger: (payload: unknown) => Promise<unknown> | false | null;
  onDeleteLedger: (ledgerId: string) => Promise<unknown> | false | null;
}) {
  const [groupName, setGroupName] = useState("");
  const [groupNature, setGroupNature] = useState<AccountNature>("asset");
  const [ledgerName, setLedgerName] = useState("");
  const [groupId, setGroupId] = useState("");
  const [category, setCategory] = useState<LedgerCategory>("other");
  const [nature, setNature] = useState<AccountNature>("asset");

  const defaultGroupId = groupId || props.groups[0]?.id || "";

  return (
    <section className="grid gap-4 xl:grid-cols-[360px_1fr]">
      <div className="space-y-4">
        <form
          className="glass-card p-4"
          onSubmit={(event) => {
            event.preventDefault();
            void props.onCreateGroup({ name: groupName, account_nature: groupNature });
            setGroupName("");
          }}
        >
          <h2 className="mb-3 text-base font-semibold">Chart of Accounts</h2>
          <Input value={groupName} onChange={(event) => setGroupName(event.target.value)} placeholder="Group name" required />
          <select className="premium-select mt-3 w-full" value={groupNature} onChange={(event) => setGroupNature(event.target.value as AccountNature)}>
            {natures.map((item) => <option key={item} value={item}>{title(item)}</option>)}
          </select>
          <Button className="mt-3 w-full" type="submit"><Plus size={17} /> Add group</Button>
        </form>
        <form
          className="glass-card p-4"
          onSubmit={(event) => {
            event.preventDefault();
            void props.onCreateLedger({
              name: ledgerName,
              ledger_group_id: defaultGroupId,
              category,
              account_nature: nature,
              opening_balance: "0.00",
              opening_balance_type: "dr"
            });
            setLedgerName("");
          }}
        >
          <h2 className="mb-3 text-base font-semibold">Create Ledger</h2>
          <Input value={ledgerName} onChange={(event) => setLedgerName(event.target.value)} placeholder="Ledger name" required />
          <select className="premium-select mt-3 w-full" value={defaultGroupId} onChange={(event) => setGroupId(event.target.value)} required>
            {props.groups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
          </select>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <select className="premium-select" value={nature} onChange={(event) => setNature(event.target.value as AccountNature)}>
              {natures.map((item) => <option key={item} value={item}>{title(item)}</option>)}
            </select>
            <select className="premium-select" value={category} onChange={(event) => setCategory(event.target.value as LedgerCategory)}>
              {categories.map((item) => <option key={item} value={item}>{title(item)}</option>)}
            </select>
          </div>
          <Button className="mt-3 w-full" type="submit"><Plus size={17} /> Create ledger</Button>
        </form>
      </div>
      <div className="glass-card p-4">
        <div className="mb-3 grid gap-2 sm:grid-cols-[1fr_180px_100px]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-3 text-muted-foreground" size={17} />
            <Input className="pl-9" value={props.search} onChange={(event) => props.setSearch(event.target.value)} placeholder="Search ledgers" />
          </div>
          <select className="premium-select h-11" value={props.natureFilter} onChange={(event) => props.setNatureFilter(event.target.value)}>
            <option value="">All natures</option>
            {natures.map((item) => <option key={item} value={item}>{title(item)}</option>)}
          </select>
          <Button type="button" variant="secondary" onClick={props.onRefresh}>Apply</Button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="text-muted-foreground">
              <tr><th className="py-2">Ledger</th><th>Group</th><th>Nature</th><th>Category</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {props.ledgers.map((ledger) => (
                <tr key={ledger.id} className="border-t">
                  <td className="py-2 font-medium">{ledger.name}</td>
                  <td>{ledger.group_name}</td>
                  <td>{title(ledger.account_nature)}</td>
                  <td>{title(ledger.category)}</td>
                  <td>{ledger.is_active ? "Active" : "Inactive"}</td>
                  <td className="text-right">
                    <Button type="button" variant="ghost" disabled={ledger.is_system} onClick={() => void props.onDeleteLedger(ledger.id)} title="Delete ledger">
                      <Trash2 size={16} />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function VouchersPanel({ ledgers, onPost }: { ledgers: Ledger[]; onPost: (payload: unknown) => Promise<unknown> | false | null }) {
  const [voucherType, setVoucherType] = useState<VoucherType>("journal");
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [debitLedger, setDebitLedger] = useState("");
  const [creditLedger, setCreditLedger] = useState("");
  const [amount, setAmount] = useState("");
  const balanced = Number(amount) > 0 && debitLedger !== creditLedger;
  return (
    <form className="glass-card p-4" onSubmit={(event) => {
      event.preventDefault();
      void onPost({
        voucher_type: voucherType,
        voucher_date: date,
        narration: `${title(voucherType)} voucher`,
        lines: [
          { ledger_id: debitLedger, debit: amount, credit: "0.00" },
          { ledger_id: creditLedger, debit: "0.00", credit: amount }
        ]
      });
    }}>
      <h2 className="mb-3 text-base font-semibold">Voucher Engine</h2>
      <div className="grid gap-3 md:grid-cols-5">
        <select className="premium-select h-11" value={voucherType} onChange={(event) => setVoucherType(event.target.value as VoucherType)}>
          {voucherTypes.map((item) => <option key={item} value={item}>{title(item)}</option>)}
        </select>
        <Input type="date" value={date} onChange={(event) => setDate(event.target.value)} required />
        <select className="premium-select h-11" value={debitLedger} onChange={(event) => setDebitLedger(event.target.value)} required>
          <option value="">Debit ledger</option>
          {ledgers.map((ledger) => <option key={ledger.id} value={ledger.id}>{ledger.name}</option>)}
        </select>
        <select className="premium-select h-11" value={creditLedger} onChange={(event) => setCreditLedger(event.target.value)} required>
          <option value="">Credit ledger</option>
          {ledgers.map((ledger) => <option key={ledger.id} value={ledger.id}>{ledger.name}</option>)}
        </select>
        <Input type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="Amount" required />
      </div>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className={cn("text-sm", balanced ? "text-primary" : "text-destructive")}>
          Debit total and credit total must match before posting.
        </p>
        <Button type="submit" disabled={!balanced}><ReceiptIndianRupee size={17} /> Post voucher</Button>
      </div>
    </form>
  );
}

function InvoicesPanel(props: {
  ledgers: Ledger[];
  invoices: Array<{ id: string; invoice_number: string; total_amount: string }>;
  companyId: string;
  token: string | null;
  onCreate: (payload: unknown) => Promise<unknown> | false | null;
}) {
  const [partyLedgerId, setPartyLedgerId] = useState("");
  const [amount, setAmount] = useState("");
  const invoiceNumber = useMemo(() => `INV-${Date.now()}`, []);
  return (
    <section className="grid gap-4 xl:grid-cols-[360px_1fr]">
      <form className="glass-card p-4" onSubmit={(event) => {
        event.preventDefault();
        void props.onCreate({
          invoice_type: "sales",
          invoice_number: invoiceNumber,
          invoice_date: new Date().toISOString().slice(0, 10),
          party_ledger_id: partyLedgerId,
          gst_supply_type: "intra_state",
          lines: [{ description: "Accounting invoice line", quantity: "1", unit: "NOS", unit_price: amount, gst_rate: "18.00" }]
        });
      }}>
        <h2 className="mb-3 text-base font-semibold">Sales Invoice</h2>
        <select className="premium-select h-11 w-full" value={partyLedgerId} onChange={(event) => setPartyLedgerId(event.target.value)} required>
          <option value="">Party ledger</option>
          {props.ledgers.map((ledger) => <option key={ledger.id} value={ledger.id}>{ledger.name}</option>)}
        </select>
        <Input className="mt-3" type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="Taxable amount" required />
        <Button className="mt-3 w-full" type="submit"><Plus size={17} /> Create invoice</Button>
      </form>
      <div className="glass-card p-4">
        <h2 className="mb-3 text-base font-semibold">Invoices</h2>
        <div className="space-y-2">
          {props.invoices.map((invoice) => (
            <div key={invoice.id} className="flex items-center justify-between rounded-xl border border-white/70 bg-white/70 p-3 shadow-sm">
              <div>
                <p className="font-medium">{invoice.invoice_number}</p>
                <p className="text-sm text-muted-foreground">{formatMoney(invoice.total_amount)}</p>
              </div>
              <a className="premium-link gap-2" href={accountingApi.invoicePdfUrl(props.companyId, invoice.id)}>
                <FileDown size={16} /> PDF
              </a>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function ReportsPanel(props: {
  trialBalance: TrialBalanceRow[];
  pnl: { revenue: string; expenses: string; profit: string } | null;
  balanceSheet: { assets: string; liabilities: string; equity: string; check_difference: string } | null;
  cashFlow: { net_cash_flow: string; operating_cash_flow: string } | null;
}) {
  return (
    <section className="grid gap-4 xl:grid-cols-3">
      <Statement title="Profit & Loss" rows={[["Revenue", props.pnl?.revenue], ["Expenses", props.pnl?.expenses], ["Profit", props.pnl?.profit]]} />
      <Statement title="Balance Sheet" rows={[["Assets", props.balanceSheet?.assets], ["Liabilities", props.balanceSheet?.liabilities], ["Equity", props.balanceSheet?.equity], ["Difference", props.balanceSheet?.check_difference]]} />
      <Statement title="Cash Flow" rows={[["Operating", props.cashFlow?.operating_cash_flow], ["Net Cash Flow", props.cashFlow?.net_cash_flow]]} />
      <div className="glass-card p-4 xl:col-span-3">
        <h2 className="mb-3 text-base font-semibold">Trial Balance</h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[620px] text-left text-sm">
            <thead className="text-muted-foreground"><tr><th className="py-2">Ledger</th><th>Nature</th><th>Debit</th><th>Credit</th></tr></thead>
            <tbody>{props.trialBalance.map((row) => <tr key={row.ledger_id} className="border-t"><td className="py-2">{row.ledger_name}</td><td>{title(row.account_nature)}</td><td>{formatMoney(row.debit)}</td><td>{formatMoney(row.credit)}</td></tr>)}</tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function GstPanel({ gstReport }: { gstReport: GstReport | null }) {
  return (
    <section className="space-y-3">
      <p className="empty-state">ABHAY provides GST assistance. Please verify before filing.</p>
      <div className="glass-card p-4">
        <h2 className="mb-3 text-base font-semibold">GST category/rate structure</h2>
        <div className="flex flex-wrap gap-2">
          {gstRates.map((rate) => (
            <span key={rate} className="rounded-full border border-orange-100 bg-orange-50 px-3 py-1 text-sm font-semibold text-orange-700">
              GST {rate}
            </span>
          ))}
        </div>
        <p className="mt-3 text-sm text-muted-foreground">Rate mapping is an Alpha assistance layer. Verify with CA before filing.</p>
      </div>
      <Statement
        title="GST-ready insights"
        rows={[
          ["Input GST", gstReport?.input_gst],
          ["Output GST", gstReport?.output_gst],
          ["Net Payable", gstReport?.net_payable]
        ]}
      />
    </section>
  );
}

function AccessRequestsPanel(props: {
  accessRequests: AccessRequest[];
  currentCompanyId: string;
  currentCompanyName: string;
  companyIdCopied: boolean;
  requestCompanyId: string;
  requestRole: "accountant" | "viewer";
  setRequestCompanyId: (value: string) => void;
  setRequestRole: (value: "accountant" | "viewer") => void;
  onRequest: () => Promise<void>;
  onDecision: (requestId: string, decision: "approve" | "reject", role: "accountant" | "viewer") => Promise<void>;
  onCopyCompanyId: () => Promise<void>;
  isBusy: boolean;
}) {
  return (
    <section className="glass-card p-4">
      <h2 className="text-base font-semibold">Access Requests</h2>
      <p className="mt-1 text-sm text-muted-foreground">Request access using Company ID. Owner can approve/reject.</p>
      <div className="mt-4 rounded-2xl border border-white/70 bg-white/70 p-3">
        <p className="text-xs text-muted-foreground">Selected company</p>
        <p className="mt-1 font-semibold">{props.currentCompanyName}</p>
        <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <code className="rounded-xl bg-slate-950 px-3 py-2 text-xs text-white">{props.currentCompanyId || "No company selected"}</code>
          <Button type="button" variant="secondary" onClick={() => void props.onCopyCompanyId()} disabled={!props.currentCompanyId || props.isBusy}>
            {props.companyIdCopied ? <Check size={16} /> : <Copy size={16} />}
            {props.companyIdCopied ? "Copied" : "Copy ID"}
          </Button>
        </div>
      </div>
      <form
        className="mt-4 grid gap-3 sm:grid-cols-[1fr_150px_140px]"
        onSubmit={(event) => {
          event.preventDefault();
          void props.onRequest();
        }}
      >
        <Input value={props.requestCompanyId} onChange={(event) => props.setRequestCompanyId(event.target.value)} placeholder="Existing company ID" />
        <select className="premium-select h-11" value={props.requestRole} onChange={(event) => props.setRequestRole(event.target.value as "accountant" | "viewer")}>
          <option value="accountant">Accountant</option>
          <option value="viewer">Viewer</option>
        </select>
        <Button type="submit" variant="secondary" disabled={props.isBusy || !props.requestCompanyId.trim()}>
          Request access
        </Button>
      </form>
      <div className="mt-4 space-y-2">
        {props.accessRequests.length ? props.accessRequests.map((request) => (
          <div key={request.id} className="rounded-xl border border-white/70 bg-white/70 p-3 text-sm">
            <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <p className="font-medium">{request.requester_email ?? request.requester_profile_id}</p>
                <p className="text-muted-foreground">{title(request.requested_role)} - {title(request.status)}</p>
              </div>
              {request.status === "pending" ? (
                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="secondary" onClick={() => void props.onDecision(request.id, "reject", request.requested_role)} disabled={props.isBusy}>
                    Reject
                  </Button>
                  <Button type="button" onClick={() => void props.onDecision(request.id, "approve", request.requested_role)} disabled={props.isBusy}>
                    Approve
                  </Button>
                </div>
              ) : null}
            </div>
          </div>
        )) : <p className="empty-state">No pending owner access requests for the selected company.</p>}
      </div>
    </section>
  );
}

function Statement({ title: heading, rows }: { title: string; rows: Array<[string, string | undefined]> }) {
  return (
    <div className="glass-card float-card p-4">
      <h2 className="mb-3 text-base font-semibold">{heading}</h2>
      <div className="space-y-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between border-b py-2 text-sm last:border-b-0">
            <span className="text-muted-foreground">{label}</span>
            <span className="font-medium">{formatMoney(value)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function formatMoney(value: string | number | undefined) {
  const amount = Number(value ?? 0);
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount);
}

function title(value: string) {
  return value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}
