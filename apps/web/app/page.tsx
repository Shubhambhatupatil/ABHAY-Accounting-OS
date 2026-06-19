import Link from "next/link";
import {
  ArrowRight,
  BadgeCheck,
  BrainCircuit,
  FileSearch,
  Landmark,
  Layers3,
  LockKeyhole,
  ReceiptText,
  ShieldCheck,
  Sparkles,
  Workflow
} from "lucide-react";
import { Button } from "@/components/ui/button";

const capabilities = [
  { title: "Invoice OCR", copy: "Text PDF bill reading with honest scanned OCR roadmap.", icon: FileSearch },
  { title: "GST Intelligence", copy: "GST-ready assistance, state codes, and CA review disclaimers.", icon: ReceiptText },
  { title: "Ledger Mapping", copy: "AI suggests ledgers, voucher type, and debit/credit preview.", icon: Layers3 },
  { title: "Tally/Zoho Import Ready", copy: "Keep Tally. Add ABHAY intelligence gradually.", icon: Workflow },
  { title: "Approval Workflow", copy: "AI prepares entries. Human approves before posting.", icon: ShieldCheck },
  { title: "AI Accounting Memory", copy: "Correction patterns improve ledger suggestions over time.", icon: BrainCircuit },
  { title: "Audit Trail", copy: "Recent activity foundation for who changed what.", icon: LockKeyhole },
  { title: "Human Verified Finance Automation", copy: "Fast automation with accountant control.", icon: BadgeCheck }
];

export default function HomePage() {
  return (
    <main className="min-h-screen bg-[#050816] text-[#F8FAFC]">
      <section className="ai-luxury-bg relative overflow-hidden">
        <nav className="mx-auto flex max-w-7xl items-center justify-between px-4 py-5 sm:px-6">
          <Link className="flex items-center gap-3" href="/">
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FF6B00] to-[#FDBA74] text-white shadow-[0_16px_42px_rgba(255,107,0,0.34)]">
              <Landmark size={20} />
            </span>
            <span>
              <span className="block text-sm font-bold">ABHAY Accounting OS</span>
              <span className="block text-xs text-[#A1A1AA]">by ANVRITAI</span>
            </span>
          </Link>
          <div className="hidden items-center gap-3 sm:flex">
            <Link className="premium-dark-link" href="/login">Login</Link>
            <Link className="premium-dark-link border-[#FF6B00]/30 text-[#FDBA74]" href="/signup">Start Trial</Link>
          </div>
        </nav>

        <div className="mx-auto grid min-h-[calc(100vh-84px)] max-w-7xl gap-8 px-4 pb-12 pt-6 sm:px-6 lg:grid-cols-[1fr_520px] lg:items-center">
          <div className="relative z-10">
            <span className="inline-flex min-h-8 items-center justify-center rounded-full border border-[#FF6B00]/30 bg-[#FF6B00]/10 px-3 py-1 text-xs font-semibold leading-none text-[#FDBA74] shadow-[0_0_42px_rgba(255,107,0,0.18)]">
              Made in Bharat &bull; Built by ANVRITAI
            </span>
            <h1 className="mt-6 max-w-4xl text-5xl font-semibold tracking-normal text-white sm:text-6xl lg:text-7xl">
              ABHAY Accounting OS
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-[#F8FAFC]/75">
              AI-powered Accounting Automation Layer for Indian Businesses.
            </p>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-[#A1A1AA]">
              Replace manual accounting effort with AI suggestions, ledger mapping, GST assistance, document memory,
              approval workflows, and real-time finance intelligence built by ANVRITAI.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
              <Link className="w-full sm:w-auto" href="/signup">
                <Button className="h-12 w-full rounded-2xl px-5 sm:w-auto" type="button">
                  Start 14-Day Free Trial
                  <ArrowRight size={18} />
                </Button>
              </Link>
              <Link className="premium-dark-link h-12 w-full px-5 sm:w-auto" href="mailto:contact@anvritai.com?subject=Book%20ABHAY%20Demo">
                Book Demo
              </Link>
            </div>
            <div className="mt-8 grid max-w-2xl gap-3 sm:grid-cols-3">
              {["AI ledger mapping", "GST assistance", "Approval-first posting"].map((item) => (
                <div key={item} className="rounded-2xl border border-[#1F2937] bg-[#0F172A]/80 p-3 text-sm text-[#A1A1AA] backdrop-blur">
                  {item}
                </div>
              ))}
            </div>
          </div>

          <div className="relative z-10">
            <div className="ai-console rounded-[2rem] border border-[#1F2937] bg-[#0F172A]/90 p-4 shadow-[0_30px_100px_rgba(0,0,0,0.45),0_0_80px_rgba(0,229,255,0.08)] backdrop-blur-2xl">
              <div className="flex items-center justify-between border-b border-[#1F2937] pb-3">
                <div>
                  <p className="text-sm font-semibold">AI Accounting Layer</p>
                  <p className="text-xs text-[#A1A1AA]">Input â†’ Suggestion â†’ Approval â†’ Books</p>
                </div>
                <span className="inline-flex min-h-7 items-center justify-center rounded-full border border-[#00E5FF]/25 bg-[#00E5FF]/10 px-3 py-1 text-xs font-semibold leading-none text-[#BFF7FF]">
                  AI Active
                </span>
              </div>
              <div className="mt-4 space-y-3">
                {[
                  ["Input", "400 recharge smartphone back office 30 people"],
                  ["Suggested Ledger", "Communication Expense"],
                  ["Voucher", "Payment · Dr Expense / Cr Cash"],
                  ["GST Risk", "No GST claim without valid bill"],
                  ["Status", "Ready for human approval"]
                ].map(([label, value]) => (
                  <div key={label} className="rounded-2xl border border-[#1F2937] bg-[#111827]/80 p-4">
                    <p className="text-xs uppercase tracking-[0.16em] text-[#00E5FF]/75">{label}</p>
                    <p className="mt-2 text-sm font-semibold text-white">{value}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
        <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <span className="text-sm font-semibold text-[#FDBA74]">Built by ANVRITAI</span>
            <h2 className="mt-2 text-3xl font-semibold">Premium AI finance automation for pilot clients</h2>
          </div>
          <p className="max-w-xl text-sm leading-6 text-[#A1A1AA]">
            ABHAY is not a CRM or ERP. It focuses on accounting, finance, tax assistance, ledger controls, reports,
            and AI-assisted review workflows.
          </p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {capabilities.map((item) => {
            const Icon = item.icon;
            return (
              <article key={item.title} className="float-card rounded-3xl border border-[#1F2937] bg-[#0F172A]/90 p-5 backdrop-blur hover:border-[#00E5FF]/25">
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#FF6B00]/10 text-[#FDBA74]">
                  <Icon size={21} />
                </span>
                <h3 className="mt-4 text-base font-semibold">{item.title}</h3>
                <p className="mt-2 text-sm leading-6 text-[#A1A1AA]">{item.copy}</p>
              </article>
            );
          })}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-12 sm:px-6">
        <div className="rounded-[2rem] border border-[#FF6B00]/20 bg-gradient-to-br from-[#FF6B00]/10 via-[#0F172A] to-[#00E5FF]/10 p-6 backdrop-blur lg:p-8">
          <div className="grid gap-6 lg:grid-cols-[1fr_360px] lg:items-center">
            <div>
              <Sparkles className="text-[#00E5FF]" size={24} />
              <h2 className="mt-3 text-2xl font-semibold">Keep Tally. Add ABHAY intelligence.</h2>
              <p className="mt-3 text-sm leading-7 text-[#A1A1AA]">
                Existing accounting records can be imported gradually from Excel, CSV, Tally exports, or Zoho exports
                as connectors mature. ABHAY adds automation, document memory, and owner intelligence without forcing
                a sudden migration.
              </p>
            </div>
            <Link href="/import-data">
              <Button className="h-12 w-full rounded-2xl" type="button">
                Explore Import Data
                <ArrowRight size={18} />
              </Button>
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
