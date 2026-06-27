import { Activity, BarChart3, Bot, BrainCircuit, ScanLine, ShieldCheck } from "lucide-react";
import { ARROW, INR, MULTIPLY, memoryGraph, operatingSignals } from "../data/content.js";
import { Badge, BrandMark, Button, CheckLine, GlassPanel, Metric, toneBar, toneText } from "../components/ui.jsx";

export function DashboardPage({ navigate }) {
  const sidebar = ["Dashboard", "AI Workbench", "Document Inbox", "Reports", "Activity"];

  return (
    <section className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
        <div>
          <Badge tone="gold">Production platform</Badge>
          <h1 className="mt-5 text-3xl font-black tracking-tight sm:text-5xl">ABHAY operating dashboard</h1>
          <p className="mt-4 max-w-3xl text-base leading-7 text-abhay-muted">
            Unified KPI cards, AI Workbench, document intelligence, reports, and activity memory for scaled finance teams.
          </p>
        </div>
        <Button onClick={() => navigate("/signup")}>Start Trial</Button>
      </div>
      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {operatingSignals.map(([label, value, tone]) => (
          <Metric key={label} label={label} value={value} tone={tone} elevated />
        ))}
      </div>
      <div className="glass-panel grid min-h-[720px] overflow-hidden rounded-lg lg:grid-cols-[250px_1fr]">
        <aside className="border-b border-white/10 p-4 lg:border-b-0 lg:border-r">
          <div className="flex items-center gap-3">
            <BrandMark />
            <div>
              <p className="font-bold">ABHAY OS</p>
              <p className="text-xs text-abhay-muted">Finance command center</p>
            </div>
          </div>
          <nav className="mt-6 grid gap-2">
            {sidebar.map((item, index) => (
              <button
                key={item}
                className={`inline-flex h-11 items-center rounded-lg px-3 text-sm font-semibold transition ${
                  index === 0 ? "bg-abhay-orange/15 text-abhay-orange" : "text-abhay-muted hover:bg-white/5 hover:text-abhay-text"
                }`}
                type="button"
              >
                {item}
              </button>
            ))}
          </nav>
        </aside>
        <div className="p-4 sm:p-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Metric label="Revenue" value={`${INR}18.4L`} tone="cyan" />
            <Metric label="Expenses" value={`${INR}12.1L`} tone="orange" />
            <Metric label="Profit" value={`${INR}6.3L`} tone="teal" />
            <Metric label="GST assist" value={`${INR}72K`} tone="gold" />
          </div>
          <div className="mt-6 grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
            <GlassPanel title="Finance Memory Graph" icon={BrainCircuit}>
              <div className="grid gap-3">
                {memoryGraph.map(([label, value, tone]) => (
                  <div key={label} className="rounded-lg border border-white/10 bg-white/[0.03] p-3">
                    <div className="flex items-center justify-between gap-4 text-sm">
                      <span className="font-semibold">{label}</span>
                      <span className={`font-black ${toneText(tone)}`}>{value}</span>
                    </div>
                    <div className="mt-3 h-2 overflow-hidden rounded-full bg-white/10">
                      <div className={`h-full rounded-full ${toneBar(tone)}`} style={{ width: value }} />
                    </div>
                  </div>
                ))}
              </div>
            </GlassPanel>
            <GlassPanel title="Executive Controls" icon={ShieldCheck}>
              <div className="grid gap-3">
                <CheckLine>Human approval remains mandatory for final posting</CheckLine>
                <CheckLine>Company, user, role, and activity context stay connected</CheckLine>
                <CheckLine>GST assistance stays review-ready for CA validation</CheckLine>
              </div>
            </GlassPanel>
          </div>
          <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_0.85fr]">
            <GlassPanel title="AI Workbench" icon={Bot}>
              <p className="text-sm leading-7 text-abhay-muted">One-line accounting entries become confidence-scored suggestions for human approval.</p>
              <div className="mt-4 rounded-lg border border-abhay-cyan/30 bg-abhay-cyan/10 p-4 text-sm text-abhay-muted">
                30 people {MULTIPLY} 400 recharge with GST 9% {ARROW} {INR}13,080 total
              </div>
            </GlassPanel>
            <GlassPanel title="Document Intelligence" icon={ScanLine}>
              <p className="text-sm leading-7 text-abhay-muted">Upload invoice, extract fields, create draft invoice/voucher, approve later.</p>
              <Button className="mt-4" variant="secondary">Upload Document</Button>
            </GlassPanel>
          </div>
          <div className="mt-6 grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
            <GlassPanel title="Reports" icon={BarChart3}>
              <div className="grid gap-2">
                {["Trial Balance", "Profit & Loss", "Balance Sheet", "GST Summary"].map((item) => (
                  <div key={item} className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.03] p-3 text-sm">
                    <span>{item}</span>
                    <span className="text-abhay-teal">Available</span>
                  </div>
                ))}
              </div>
            </GlassPanel>
            <GlassPanel title="Recent activity" icon={Activity}>
              <div className="grid gap-3">
                {["AI suggested Communication Expense", "Document intelligence extracted invoice BOS-118", "GST summary refreshed", "Report export generated"].map((item) => (
                  <CheckLine key={item}>{item}</CheckLine>
                ))}
              </div>
            </GlassPanel>
          </div>
        </div>
      </div>
    </section>
  );
}
