"use client";

import { useState } from "react";
import { ArrowRightLeft, CheckCircle2, FileSpreadsheet, FileText, ShieldAlert, UploadCloud } from "lucide-react";
import { Button } from "@/components/ui/button";
import { accountingApi } from "@/lib/api/accounting";
import { startClientDemoSession } from "@/lib/auth/demo-auth";

const gstStates = [
  { code: "27", name: "Maharashtra" },
  { code: "24", name: "Gujarat" },
  { code: "29", name: "Karnataka" },
  { code: "07", name: "Delhi" },
  { code: "09", name: "Uttar Pradesh" },
  { code: "08", name: "Rajasthan" },
  { code: "33", name: "Tamil Nadu" },
  { code: "36", name: "Telangana" },
  { code: "32", name: "Kerala" },
  { code: "19", name: "West Bengal" },
  { code: "23", name: "Madhya Pradesh" },
  { code: "06", name: "Haryana" },
  { code: "03", name: "Punjab" },
  { code: "10", name: "Bihar" },
  { code: "21", name: "Odisha" },
  { code: "18", name: "Assam" },
  { code: "30", name: "Goa" },
  { code: "04", name: "Chandigarh" }
];

const gstRates = ["0%", "5%", "12%", "18%", "28%"];

const importOptions = [
  {
    title: "Import from Excel",
    description: "Upload ledgers, opening balances, invoices, and voucher sheets for Alpha review.",
    status: "Alpha Ready UI",
    badge: "Alpha",
    accept: ".xlsx,.xls",
    icon: FileSpreadsheet
  },
  {
    title: "Import from CSV",
    description: "Bring clean CSV exports into ABHAY for gradual accounting intelligence adoption.",
    status: "Alpha Ready UI",
    badge: "Alpha",
    accept: ".csv",
    icon: FileText
  },
  {
    title: "Import from Tally Export",
    description: "Tally XML and export mapping will be added after Alpha validation.",
    status: "Connector planned",
    badge: "Coming Soon",
    icon: ArrowRightLeft
  },
  {
    title: "Import from Zoho Export",
    description: "Zoho Books import mapping is planned for businesses moving gradually.",
    status: "Connector planned",
    badge: "Coming Soon",
    icon: ArrowRightLeft
  }
];

export function ImportDataWorkspace() {
  const [selectedState, setSelectedState] = useState("27");
  const [message, setMessage] = useState("Choose Excel or CSV to prepare an Alpha import review.");
  const [isPreparingDemo, setIsPreparingDemo] = useState(false);

  async function openClientDemoMode() {
    setIsPreparingDemo(true);
    try {
      const demo = await accountingApi.clientDemoWorkspace();
      startClientDemoSession();
      window.localStorage.setItem("abhay.lastCompanyId", demo.company_id);
      setMessage("Client Demo Workspace ready. You can now open Dashboard, Reports, AI Workbench, or continue reviewing import workflows.");
    } catch {
      setMessage("Client Demo Workspace could not be prepared. Please retry.");
    } finally {
      setIsPreparingDemo(false);
    }
  }

  function handleFileChange(file: File | undefined, title: string) {
    if (!file) {
      return;
    }
    if (file.type.startsWith("image/")) {
      setMessage("Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.");
      return;
    }
    setMessage(`${title}: ${file.name} selected for Alpha import review.`);
  }

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <span className="ai-badge mb-3 border-white/20 bg-white/10 text-white">Import Intelligence</span>
              <h1 className="text-2xl font-semibold sm:text-3xl">Keep Tally. Add ABHAY intelligence.</h1>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-white/80">
                Tally = accounting records. ABHAY = accounting intelligence, automation and document memory.
              </p>
            </div>
            <div className="rounded-2xl border border-white/20 bg-white/10 px-4 py-3 text-sm leading-6 text-white/80 backdrop-blur">
              ABHAY does not force you to leave Tally. Import/export data gradually.
            </div>
          </div>
        </header>

        <section className="glass-card flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-semibold text-white">Client Demo Mode</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Open a clearly labelled sample workspace without email confirmation before reviewing import workflows.
            </p>
          </div>
          <Button type="button" onClick={() => void openClientDemoMode()} disabled={isPreparingDemo}>
            Client Demo Mode
          </Button>
        </section>

        <section className="grid gap-4 lg:grid-cols-4">
          {importOptions.map((option) => {
            const Icon = option.icon;
            const enabled = Boolean(option.accept);
            return (
              <article key={option.title} className="glass-card float-card flex flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#FF6B00]/10 text-[#FDBA74]">
                    <Icon size={21} />
                  </span>
                  <StatusBadge label={option.badge} />
                </div>
                <h2 className="mt-4 text-base font-semibold text-white">{option.title}</h2>
                <p className="mt-2 min-h-16 text-sm leading-6 text-muted-foreground">{option.description}</p>
                <p className="mt-3 text-xs font-semibold uppercase tracking-[0.14em] text-[#A1A1AA]">{option.status}</p>
                {enabled ? (
                  <label className="mt-4 inline-flex h-11 cursor-pointer items-center justify-center gap-2 rounded-xl border border-[#FF6B00]/25 bg-[#FF6B00]/10 px-4 text-sm font-semibold leading-none text-[#FDBA74] shadow-sm transition hover:-translate-y-0.5 hover:border-[#00E5FF]/30 hover:shadow-md">
                    <UploadCloud size={17} />
                    Select file
                    <input
                      className="sr-only"
                      type="file"
                      accept={option.accept}
                      onChange={(event) => handleFileChange(event.target.files?.[0], option.title)}
                    />
                  </label>
                ) : (
                  <Button className="mt-4" type="button" disabled>
                    Coming soon
                  </Button>
                )}
              </article>
            );
          })}
        </section>

        <section className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="glass-card p-5">
            <div className="flex items-start gap-3">
              <ShieldAlert className="mt-0.5 text-primary" size={22} />
              <div>
                <h2 className="text-base font-semibold">PDF and Image Honesty</h2>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  Text PDF supported. Image/scanned bill OCR coming soon. If an image is uploaded, ABHAY will not fake extraction.
                </p>
                <p className="mt-3 rounded-2xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800">
                  Image/scanned OCR is coming soon. Use text PDF or one-line entry for now.
                </p>
              </div>
            </div>
          </div>

          <div className="glass-card p-5">
            <h2 className="text-base font-semibold">GST Setup</h2>
            <label className="mt-4 block text-sm font-semibold text-white" htmlFor="gst-state">
              GST State Code
            </label>
            <select
              id="gst-state"
              className="premium-select mt-2 w-full"
              value={selectedState}
              onChange={(event) => setSelectedState(event.target.value)}
            >
              {gstStates.map((state) => (
                <option key={state.code} value={state.code}>
                  {state.code} {state.name}
                </option>
              ))}
            </select>
            <div className="mt-4 flex flex-wrap gap-2">
              {gstRates.map((rate) => (
                <span key={rate} className="inline-flex h-8 items-center rounded-full border border-[#FF6B00]/25 bg-[#FF6B00]/10 px-3 text-sm font-semibold leading-none text-[#FDBA74]">
                  GST {rate}
                </span>
              ))}
            </div>
            <p className="mt-4 text-sm font-semibold text-amber-700">GST assistance only. Verify with CA before filing.</p>
          </div>
        </section>

        <section className="glass-card p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-base font-semibold">Gradual Migration Positioning</h2>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                Import records when ready, keep familiar accounting workflows, and use ABHAY for AI review, GST assistance, document memory, and owner intelligence.
              </p>
            </div>
            <div className="flex items-center gap-2 rounded-2xl border border-[#14B8A6]/25 bg-[#14B8A6]/10 px-3 py-2 text-sm font-semibold text-[#9FF5EA]">
              <CheckCircle2 size={17} />
              Tally-friendly Alpha
            </div>
          </div>
        </section>

        <p className="empty-state">{message}</p>
      </section>
    </main>
  );
}

function StatusBadge({ label }: Readonly<{ label: string }>) {
  const className =
    label === "Ready"
      ? "border-[#14B8A6]/25 bg-[#14B8A6]/10 text-[#9FF5EA]"
      : label === "Alpha"
        ? "border-[#FF6B00]/25 bg-[#FF6B00]/10 text-[#FDBA74]"
        : "border-[#1F2937] bg-[#111827] text-[#A1A1AA]";

  return <span className={`rounded-full border px-2.5 py-1 text-xs font-semibold ${className}`}>{label}</span>;
}
