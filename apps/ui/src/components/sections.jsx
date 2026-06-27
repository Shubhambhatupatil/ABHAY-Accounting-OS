import { INR, signatureSignals } from "../data/content.js";
import { Badge, Button, Metric } from "./ui.jsx";

export function PageShell({ eyebrow, title, copy, children, cta, ctaLabel }) {
  return (
    <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
      <div className="mb-8 flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div className="max-w-3xl">
          <Badge>{eyebrow}</Badge>
          <h1 className="mt-5 text-3xl font-black tracking-tight sm:text-5xl">{title}</h1>
          <p className="mt-4 text-base leading-7 text-abhay-muted sm:text-lg">{copy}</p>
        </div>
        {cta ? <Button onClick={cta}>{ctaLabel}</Button> : null}
      </div>
      <SignatureStrip />
      {children}
    </section>
  );
}

export function Section({ eyebrow, title, children }) {
  return (
    <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
      <div className="mb-8 max-w-3xl">
        <Badge>{eyebrow}</Badge>
        <h2 className="mt-5 text-3xl font-black tracking-tight sm:text-5xl">{title}</h2>
      </div>
      {children}
    </section>
  );
}

export function TrustBar() {
  return (
    <div className="mt-8 grid max-w-2xl grid-cols-2 gap-3 sm:grid-cols-4">
      {["AI Memory", "OCR", "GST", "Reports"].map((item) => (
        <div key={item} className="glass-panel rounded-lg px-4 py-3 text-center text-sm font-semibold text-abhay-text">
          {item}
        </div>
      ))}
    </div>
  );
}

export function SignatureStrip() {
  return (
    <div className="mb-8 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {signatureSignals.map(([label, copy]) => (
        <div key={label} className="premium-sheen rounded-lg border border-white/10 bg-white/[0.035] p-4">
          <p className="text-xs uppercase tracking-wide text-abhay-muted">{label}</p>
          <p className="mt-2 text-sm font-semibold text-abhay-text">{copy}</p>
        </div>
      ))}
    </div>
  );
}

export function CTASection({ navigate }) {
  return (
    <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
      <div className="glass-panel grid gap-6 rounded-lg p-6 lg:grid-cols-[1fr_0.6fr] lg:p-8">
        <div>
          <Badge tone="gold">Built for scale</Badge>
          <h2 className="mt-4 text-3xl font-black">Bring AI-powered finance operations into one platform</h2>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-abhay-muted">
            ABHAY unifies accounting automation, document intelligence, GST assistance, reports, and audit memory for modern Indian businesses.
          </p>
        </div>
        <div className="flex flex-col justify-center gap-3 sm:flex-row lg:flex-col">
          <Button onClick={() => navigate("/signup")}>Start Trial</Button>
          <Button variant="secondary" onClick={() => navigate("/pricing")}>See Pricing</Button>
        </div>
      </div>
    </section>
  );
}

export function DashboardMockup({ navigate }) {
  return (
    <div className="glass-panel overflow-hidden rounded-lg p-4 shadow-glow sm:p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <p className="text-sm text-abhay-muted">Enterprise finance command center</p>
          <h2 className="text-xl font-bold">ABHAY Operating Dashboard</h2>
        </div>
        <Badge tone="cyan">AI Ready</Badge>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Metric label="Revenue" value={`${INR}18.4L`} tone="cyan" />
        <Metric label="Profit" value={`${INR}6.3L`} tone="teal" />
        <Metric label="GST assist" value={`${INR}72K`} tone="orange" />
        <Metric label="AI confidence" value="91%" tone="gold" />
      </div>
      <div className="mt-4 rounded-lg border border-abhay-cyan/30 bg-abhay-cyan/10 p-4 text-sm leading-6 text-abhay-muted">
        AI Workbench, document intelligence, report cards, and finance memory work together in one commercial-grade operating system.
      </div>
      <Button className="mt-4 w-full" variant="secondary" onClick={() => navigate("/dashboard")}>
        Open Dashboard
      </Button>
    </div>
  );
}
