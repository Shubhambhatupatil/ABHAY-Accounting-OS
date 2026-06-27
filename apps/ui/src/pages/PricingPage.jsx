import { Activity, BrainCircuit, Building2, Check } from "lucide-react";
import { plans } from "../data/content.js";
import { Badge, Button, CheckLine, ValueCard } from "../components/ui.jsx";

export function PricingPage({ navigate }) {
  return (
    <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
      <div className="mx-auto max-w-4xl text-center">
        <Badge tone="gold">Production-grade subscription system</Badge>
        <h1 className="mt-5 text-4xl font-black tracking-tight sm:text-6xl">
          Pricing for every business that wants AI-powered accounting memory
        </h1>
        <p className="mt-5 text-base leading-8 text-abhay-muted sm:text-lg">
          Every ABHAY plan includes AI Memory OS. Scale by company count, AI Memory Actions, document volume, team controls, reporting depth, and support level.
        </p>
      </div>

      <div className="mt-8 grid gap-4 md:grid-cols-3">
        <ValueCard title="AI Memory OS in every plan" copy="The core USP stays available from Free Forever to Enterprise." icon={BrainCircuit} />
        <ValueCard title="Built for mobile and desktop" copy="Owners, accountants, and CA firms can work wherever finance decisions happen." icon={Activity} />
        <ValueCard title="Multi-company accounting OS" copy="Start lean and scale into teams, branches, groups, and global companies." icon={Building2} />
      </div>

      <div className="mt-8 grid gap-4 xl:grid-cols-5">
        {plans.map((plan) => (
          <PricingCard key={plan.name} plan={plan} navigate={navigate} />
        ))}
      </div>

      <div className="mt-8 glass-panel rounded-lg p-6 sm:p-8">
        <div className="grid gap-6 lg:grid-cols-[1fr_1fr] lg:items-center">
          <div>
            <Badge tone="cyan">Built for scale</Badge>
            <h2 className="mt-4 text-3xl font-black">One operating system for accounting, documents, intelligence, and compliance automation</h2>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {["Accounting Engine", "AI Accounting Assistant", "Business Memory OS", "Document Intelligence", "OCR", "GST Intelligence", "Financial Reporting", "Bank Reconciliation", "Compliance Automation", "Multi-company Controls"].map((item) => (
              <CheckLine key={item}>{item}</CheckLine>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function PricingCard({ plan, navigate }) {
  const cardStyle = plan.featured
    ? "border-abhay-orange/70 shadow-orange"
    : plan.premium || plan.enterprise
      ? "border-abhay-gold/45"
      : "";

  return (
    <article className={`glass-panel flex min-h-[680px] flex-col rounded-lg p-5 transition hover:-translate-y-1 hover:border-abhay-cyan/45 ${cardStyle}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-black">{plan.name}</h2>
          <p className="mt-2 text-sm leading-6 text-abhay-muted">{plan.audience.join(", ")}</p>
        </div>
        {plan.featured ? <Badge>Popular</Badge> : plan.enterprise ? <Badge tone="gold">Custom</Badge> : <Badge tone="cyan">AI Memory</Badge>}
      </div>

      <div className="mt-6 rounded-lg border border-white/10 bg-white/[0.03] p-4">
        <p className="text-xs uppercase tracking-wide text-abhay-muted">India</p>
        <div className="mt-2 flex items-end gap-2">
          <span className="text-3xl font-black">{plan.india}</span>
          <span className="pb-1 text-sm text-abhay-muted">/ {plan.period}</span>
        </div>
        <p className="mt-4 text-xs uppercase tracking-wide text-abhay-muted">Global</p>
        <div className="mt-2 flex items-end gap-2">
          <span className="text-2xl font-black text-abhay-cyan">{plan.global}</span>
          <span className="pb-1 text-sm text-abhay-muted">/ {plan.period}</span>
        </div>
      </div>

      <div className="mt-5 grid gap-2">
        {plan.features.map((item) => (
          <div key={item} className="flex gap-2 text-sm leading-5 text-abhay-muted">
            <Check className="mt-0.5 shrink-0 text-abhay-teal" size={16} />
            <span>{item}</span>
          </div>
        ))}
      </div>

      <Button
        className="mt-auto w-full"
        variant={plan.enterprise ? "secondary" : "primary"}
        onClick={() => navigate(plan.enterprise ? "/pricing" : "/signup")}
      >
        {plan.enterprise ? "Contact Sales" : "Choose Plan"}
      </Button>
    </article>
  );
}
