import { BrainCircuit } from "lucide-react";
import { ARROW, memoryCards } from "../data/content.js";
import { CheckLine, GlassPanel } from "../components/ui.jsx";
import { PageShell } from "../components/sections.jsx";

export function AIMemoryPage({ navigate }) {
  return (
    <PageShell
      eyebrow="AI Memory OS"
      title="Finance memory that improves with every verified decision"
      copy="ABHAY remembers the way each company accounts for vendors, vouchers, taxes, documents, and CA decisions."
      cta={() => navigate("/signup")}
      ctaLabel="Start memory workspace"
    >
      <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
        <GlassPanel title="Memory OS model" icon={BrainCircuit}>
          <p className="text-sm leading-7 text-abhay-muted">
            Traditional accounting software stores records. ABHAY adds a finance memory layer above those records so repeated work becomes review-first instead of entry-first.
          </p>
          <div className="mt-5 grid gap-3">
            <CheckLine>Input {ARROW} AI parse {ARROW} review {ARROW} correction {ARROW} future suggestion</CheckLine>
            <CheckLine>Stores why an entry was approved or rejected</CheckLine>
            <CheckLine>Creates decision memory without removing human control</CheckLine>
          </div>
        </GlassPanel>
        <div className="grid gap-4">
          {memoryCards.map(([title, copy], index) => (
            <div key={title} className="glass-panel rounded-lg p-5 transition hover:-translate-y-1 hover:border-abhay-cyan/40">
              <div className="flex items-start gap-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-abhay-cyan/30 bg-abhay-cyan/10 text-sm font-bold text-abhay-cyan">
                  {index + 1}
                </span>
                <div>
                  <h3 className="font-bold">{title}</h3>
                  <p className="mt-2 text-sm leading-6 text-abhay-muted">{copy}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </PageShell>
  );
}
