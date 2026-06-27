import { UploadCloud } from "lucide-react";
import { documentFlows } from "../data/content.js";
import { GlassPanel } from "../components/ui.jsx";
import { PageShell } from "../components/sections.jsx";

export function DocumentIntelligencePage({ navigate }) {
  return (
    <PageShell
      eyebrow="Document Intelligence"
      title="OCR-powered accounting intelligence for every finance document"
      copy="A polished document intelligence workflow for invoices, bills, GST documents, and statements. Human approval stays central."
      cta={() => navigate("/signup")}
      ctaLabel="Start document workspace"
    >
      <div className="grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
        <GlassPanel title="Secure document intake" icon={UploadCloud}>
          <label className="flex min-h-72 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-abhay-cyan/35 bg-abhay-cyan/5 p-6 text-center transition hover:border-abhay-orange hover:bg-abhay-orange/5">
            <UploadCloud className="text-abhay-cyan" size={44} />
            <span className="mt-4 text-lg font-bold">Upload finance documents</span>
            <span className="mt-2 max-w-sm text-sm text-abhay-muted">
              Bring invoices, bills, statements, and GST documents into one intelligence layer.
            </span>
            <input className="sr-only" type="file" accept=".pdf,.png,.jpg,.jpeg" />
          </label>
        </GlassPanel>
        <div className="grid gap-4 sm:grid-cols-2">
          {documentFlows.map(([title, copy]) => (
            <div key={title} className="glass-panel rounded-lg p-5 transition hover:-translate-y-1 hover:border-abhay-orange/45">
              <h3 className="font-bold">{title}</h3>
              <p className="mt-2 text-sm leading-6 text-abhay-muted">{copy}</p>
            </div>
          ))}
        </div>
      </div>
    </PageShell>
  );
}
