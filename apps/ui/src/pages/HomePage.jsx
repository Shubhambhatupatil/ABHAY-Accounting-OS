import { ArrowRight, BarChart3, Bot, ChevronRight, ClipboardCheck, Database, Layers3, Sparkles } from "lucide-react";
import { ARROW, DOT, INR, MULTIPLY, commandPillars } from "../data/content.js";
import { Badge, Button, CheckLine, FeatureCard, GlassPanel, Metric } from "../components/ui.jsx";
import { CTASection, DashboardMockup, Section, TrustBar } from "../components/sections.jsx";

export function HomePage({ navigate }) {
  return (
    <>
      <section className="mx-auto grid max-w-7xl gap-10 px-4 py-16 sm:px-6 lg:grid-cols-[1fr_1fr] lg:px-8 lg:py-24">
        <div className="flex flex-col justify-center">
          <Badge>Made in Bharat {DOT} Built by ANVRITAI</Badge>
          <h1 className="mt-6 max-w-4xl text-4xl font-black leading-tight tracking-tight sm:text-6xl lg:text-7xl">
            Enterprise-ready AI Accounting OS
          </h1>
          <p className="mt-5 max-w-2xl text-lg leading-8 text-abhay-muted sm:text-xl">
            ABHAY Accounting OS is an AI-powered, multi-company accounting operating system for finance teams that need automation, accuracy, and scale.
          </p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Button size="lg" onClick={() => navigate("/signup")}>
              Start Free Trial <ArrowRight size={18} />
            </Button>
            <Button size="lg" variant="secondary" onClick={() => navigate("/login")}>
              Sign In <ChevronRight size={18} />
            </Button>
          </div>
          <TrustBar />
        </div>
        <DashboardMockup navigate={navigate} />
      </section>

      <Section eyebrow="Product platform" title="Built for scaled accounting operations">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {[
            ["Accounting Automation", Layers3, "Ledgers, vouchers, invoices, GST, bank reconciliation, and audit logs."],
            ["AI Finance Command", Bot, "One-line accounting, confidence scoring, and human approval patterns."],
            ["Document Memory", Database, "Bills, PDFs, image invoices, extracted fields, and source evidence."],
            ["Owner Reports", BarChart3, "Dashboard KPIs, reports, GST summaries, and export-ready surfaces."]
          ].map(([title, Icon, copy]) => (
            <FeatureCard key={title} title={title} icon={Icon} copy={copy} />
          ))}
        </div>
      </Section>

      <Section eyebrow="AI command layer" title="Finance decisions move from entry to approval">
        <div className="grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
          <GlassPanel title="Accounting instruction" icon={Sparkles}>
            <div className="rounded-lg border border-abhay-cyan/30 bg-abhay-cyan/10 p-4 text-sm leading-7 text-abhay-muted">
              30 people {MULTIPLY} 400 recharge with GST 9%
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <Metric label="Base amount" value={`${INR}12,000`} tone="cyan" />
              <Metric label="GST" value={`${INR}1,080`} tone="orange" />
              <Metric label="Total" value={`${INR}13,080`} tone="gold" />
            </div>
          </GlassPanel>
          <GlassPanel title="Human-verified accounting" icon={ClipboardCheck}>
            <CheckLine>Debit Communication Expense</CheckLine>
            <CheckLine>Credit Cash / Bank</CheckLine>
            <CheckLine>Post only after authorized approval</CheckLine>
          </GlassPanel>
        </div>
      </Section>

      <Section eyebrow="Operating model" title="A finance memory layer over every accounting workflow">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {commandPillars.map(([title, copy], index) => (
            <article key={title} className="premium-sheen glass-panel rounded-lg p-5 transition hover:-translate-y-1 hover:border-abhay-cyan/45">
              <div className="flex items-center justify-between">
                <span className="flex h-11 w-11 items-center justify-center rounded-lg border border-abhay-orange/30 bg-abhay-orange/10 text-sm font-black text-abhay-orange">
                  0{index + 1}
                </span>
                <span className="h-2 w-2 rounded-full bg-abhay-cyan shadow-[0_0_18px_rgba(34,211,238,0.7)]" />
              </div>
              <h3 className="mt-5 text-lg font-bold">{title}</h3>
              <p className="mt-2 text-sm leading-6 text-abhay-muted">{copy}</p>
            </article>
          ))}
        </div>
      </Section>

      <CTASection navigate={navigate} />
    </>
  );
}
