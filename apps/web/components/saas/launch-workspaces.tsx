"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  BadgeCheck,
  Building2,
  FileSearch,
  FileSpreadsheet,
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

const sampleLedgers = [
  ["Cash", "Asset", "₹48,500"],
  ["HDFC Bank", "Asset", "₹2,80,000"],
  ["Sales", "Income", "₹4,25,000"],
  ["Communication Expense", "Expense", "₹4,800"],
  ["Input GST", "Asset", "₹18,250"],
  ["Output GST", "Liability", "₹42,700"]
];

export function UploadInvoiceWorkspace() {
  const { subscription } = useSubscriptionState();
  const [message, setMessage] = useState("Upload text PDFs for Alpha extraction review. Scanned/image OCR is coming soon.");

  function handleUpload(file: File | undefined) {
    if (!file) return;
    if (file.type.startsWith("image/")) {
      setMessage("Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.");
      return;
    }
    if (file.type !== "application/pdf") {
      setMessage("Only text PDF bills are supported in Alpha. Use one-line AI entry for other formats.");
      return;
    }
    setMessage(`${file.name} selected. Text PDF extraction runs through AI Workbench in this Alpha build.`);
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
            <h2 className="mt-4 text-xl font-semibold text-white">Drop or select text PDF invoice</h2>
            <p className="mt-2 max-w-xl text-sm leading-6 text-white/60">
              Alpha supports text-based PDFs only. Scanned PDF and image OCR is coming soon, and ABHAY will not fake extraction.
            </p>
            <input className="sr-only" type="file" accept="application/pdf,image/*" onChange={(event) => handleUpload(event.target.files?.[0])} />
            <span className="mt-5 rounded-2xl border border-orange-300/25 bg-orange-400/10 px-4 py-2 text-sm font-semibold text-orange-100">
              Select bill
            </span>
          </label>
          <div className="space-y-4">
            <InfoCard title="Trial Usage" copy={`${subscription?.invoiceUploadsUsed ?? 0}/10 invoice uploads used in Free Trial.`} />
            <InfoCard title="Fallback" copy="One-line AI entry remains the fastest working input for handwritten or image bills." />
            <Link className="premium-link w-full" href="/ai-workbench">Open AI Workbench</Link>
          </div>
        </section>
      </SubscriptionGate>
      <p className="empty-state">{message}</p>
    </PageFrame>
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
  return (
    <PageFrame
      icon={FileSpreadsheet}
      badge="Reports"
      title="Finance Reports"
      subtitle="Basic reports are available during trial. Exports and advanced drill-downs unlock on paid plans."
    >
      <SubscriptionStatusStrip subscription={subscription} />
      <SubscriptionGate subscription={subscription} feature="reports" title="Reports require trial or paid access">
        <section className="grid gap-4 lg:grid-cols-4">
          {[
            ["Trial Balance", "Debit/credit equality and ledger balances."],
            ["Profit & Loss", "Revenue, expenses, and current profit."],
            ["Balance Sheet", "Assets, liabilities, equity, and difference check."],
            ["GST Assistance", "Output tax, input tax, and net payable for CA review."]
          ].map(([title, copy]) => <InfoCard key={title} title={title} copy={copy} />)}
        </section>
        <p className="empty-state mt-4">GST assistance only. Verify with CA before filing. Open Dashboard â†’ Reports for live database-backed reports.</p>
      </SubscriptionGate>
    </PageFrame>
  );
}

export function SettingsWorkspace() {
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

  useEffect(() => {
    let active = true;
    listWorkspaceCompanies(supabase)
      .then((rows) => {
        if (!active) return;
        setCompanies(rows);
        setSelectedCompanyId(rows[0]?.id ?? "");
        setStatus(rows.length ? "Company workspace loaded." : "Create your first company workspace.");
      })
      .catch((error: Error) => {
        if (active) setStatus(error.message);
      });
    return () => {
      active = false;
    };
  }, [supabase]);

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
      setStatus("Company workspace created. You are the Owner.");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Company workspace could not be saved.");
    } finally {
      setIsBusy(false);
    }
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
      <form className="glass-panel grid gap-4 p-5 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <Field label="Company Name"><Input value={form.companyName} onChange={(event) => setForm({ ...form, companyName: event.target.value })} required /></Field>
        <Field label="GSTIN"><Input value={form.gstin} onChange={(event) => setForm({ ...form, gstin: event.target.value.toUpperCase() })} maxLength={15} placeholder="27ABCDE1234F1Z5" /></Field>
        <Field label="Industry"><Input value={form.industry} onChange={(event) => setForm({ ...form, industry: event.target.value })} /></Field>
        <Field label="GST State">
          <select className="premium-select h-11 w-full" value={form.state} onChange={(event) => setForm({ ...form, state: event.target.value })}>
            {gstStates.map(([code, name]) => <option key={code} value={code}>{code} {name}</option>)}
          </select>
        </Field>
        <Field label="Financial year"><Input value={form.financialYear} onChange={(event) => setForm({ ...form, financialYear: event.target.value })} /></Field>
        <Field label="Active company workspace">
          <select className="premium-select h-11 w-full" value={selectedCompanyId} onChange={(event) => setSelectedCompanyId(event.target.value)}>
            <option value="">No company selected</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>{company.company_name}</option>
            ))}
          </select>
        </Field>
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

export function AdminWorkspace() {
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
