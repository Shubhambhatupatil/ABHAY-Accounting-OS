import { Check, Sparkles } from "lucide-react";

export function Button({ children, variant = "primary", size = "md", className = "", ...props }) {
  const styles = {
    primary: "border-abhay-orange/70 bg-abhay-orange text-white shadow-orange hover:bg-orange-500",
    secondary: "border-abhay-cyan/35 bg-abhay-cyan/10 text-abhay-text hover:border-abhay-cyan hover:bg-abhay-cyan/15",
    ghost: "border-white/10 bg-white/5 text-abhay-text hover:border-abhay-orange/50 hover:bg-abhay-orange/10"
  };
  const sizes = {
    md: "h-11 px-4 text-sm",
    lg: "h-12 px-5 text-base"
  };

  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg border font-semibold leading-none transition disabled:cursor-not-allowed disabled:opacity-60 ${styles[variant]} ${sizes[size]} ${className}`}
      type="button"
      {...props}
    >
      {children}
    </button>
  );
}

export function NavButton({ active, children, onClick }) {
  return (
    <button
      className={`inline-flex h-10 items-center justify-start rounded-lg px-3 text-sm font-semibold leading-none transition xl:justify-center ${
        active
          ? "border border-abhay-gold/50 bg-abhay-gold/10 text-abhay-gold shadow-[0_0_24px_rgba(250,204,21,0.12)]"
          : "text-abhay-muted hover:bg-white/5 hover:text-abhay-text"
      }`}
      type="button"
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export function Badge({ children, tone = "orange" }) {
  const toneClass = {
    orange: "border-abhay-orange/35 bg-abhay-orange/10 text-abhay-orange",
    cyan: "border-abhay-cyan/35 bg-abhay-cyan/10 text-abhay-cyan",
    gold: "border-abhay-gold/35 bg-abhay-gold/10 text-abhay-gold",
    teal: "border-abhay-teal/35 bg-abhay-teal/10 text-abhay-teal"
  };
  return (
    <span className={`inline-flex h-8 items-center justify-center rounded-lg border px-3 text-xs font-bold leading-none ${toneClass[tone]}`}>
      {children}
    </span>
  );
}

export function CheckLine({ children }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-white/10 bg-white/[0.03] p-3 text-sm text-abhay-muted">
      <Check className="shrink-0 text-abhay-teal" size={17} />
      <span>{children}</span>
    </div>
  );
}

export function TrustLine({ icon: Icon, children }) {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/5 text-abhay-cyan">
        <Icon size={17} />
      </span>
      <span>{children}</span>
    </div>
  );
}

export function StatusMessage({ children }) {
  return (
    <p className="mt-4 rounded-lg border border-abhay-cyan/25 bg-abhay-cyan/10 p-3 text-sm leading-6 text-abhay-muted">
      {children}
    </p>
  );
}

export function BrandMark() {
  return (
    <span className="flex h-10 w-10 items-center justify-center rounded-lg border border-abhay-orange/40 bg-abhay-orange/15 text-abhay-orange shadow-orange">
      <Sparkles size={20} />
    </span>
  );
}

export function ValueCard({ title, copy, icon: Icon }) {
  return (
    <div className="glass-panel rounded-lg p-5">
      <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-abhay-cyan/10 text-abhay-cyan">
        <Icon size={20} />
      </span>
      <h2 className="mt-5 font-bold">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-abhay-muted">{copy}</p>
    </div>
  );
}

export function FeatureCard({ title, copy, icon: Icon }) {
  return (
    <article className="glass-panel rounded-lg p-5 transition hover:-translate-y-1 hover:border-abhay-orange/45">
      <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-abhay-cyan/10 text-abhay-cyan">
        <Icon size={20} />
      </span>
      <h3 className="mt-5 font-bold">{title}</h3>
      <p className="mt-2 text-sm leading-6 text-abhay-muted">{copy}</p>
    </article>
  );
}

export function GlassPanel({ title, icon: Icon, children }) {
  return (
    <section className="glass-panel rounded-lg p-5 sm:p-6">
      <div className="mb-5 flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-abhay-orange/10 text-abhay-orange">
          <Icon size={19} />
        </span>
        <h3 className="text-lg font-bold">{title}</h3>
      </div>
      {children}
    </section>
  );
}

export function Metric({ label, value, tone, elevated = false }) {
  const tones = {
    orange: "text-abhay-orange",
    cyan: "text-abhay-cyan",
    teal: "text-abhay-teal",
    gold: "text-abhay-gold"
  };

  return (
    <div className={`rounded-lg border border-white/10 bg-white/[0.03] p-4 ${elevated ? "premium-sheen shadow-glow" : ""}`}>
      <p className="text-xs uppercase tracking-wide text-abhay-muted">{label}</p>
      <p className={`mt-2 text-2xl font-black ${tones[tone]}`}>{value}</p>
    </div>
  );
}

export function Field({ label, value, onChange, type = "text" }) {
  return (
    <label className="grid gap-2 text-sm font-medium text-abhay-muted">
      {label}
      <input
        className="premium-input h-12 px-4"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required
      />
    </label>
  );
}

export function toneText(tone) {
  const tones = {
    orange: "text-abhay-orange",
    cyan: "text-abhay-cyan",
    teal: "text-abhay-teal",
    gold: "text-abhay-gold"
  };
  return tones[tone] || tones.cyan;
}

export function toneBar(tone) {
  const tones = {
    orange: "bg-abhay-orange",
    cyan: "bg-abhay-cyan",
    teal: "bg-abhay-teal",
    gold: "bg-abhay-gold"
  };
  return tones[tone] || tones.cyan;
}
