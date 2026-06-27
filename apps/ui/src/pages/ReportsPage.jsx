import { FileSpreadsheet } from "lucide-react";
import { reportCards } from "../data/content.js";
import { Badge } from "../components/ui.jsx";
import { PageShell } from "../components/sections.jsx";

export function ReportsPage({ navigate }) {
  return (
    <PageShell
      eyebrow="Reports"
      title="Financial statements and GST-ready reporting"
      copy="ABHAY report surfaces are designed for owners, accountants, and CAs who need clarity without waiting for month-end."
      cta={() => navigate("/dashboard")}
      ctaLabel="View dashboard"
    >
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {reportCards.map(([title, copy]) => (
          <div key={title} className="glass-panel rounded-lg p-5 transition hover:-translate-y-1 hover:border-abhay-cyan/40">
            <div className="flex items-center justify-between">
              <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-abhay-cyan/10 text-abhay-cyan">
                <FileSpreadsheet size={20} />
              </span>
              <Badge tone="cyan">Enterprise</Badge>
            </div>
            <h3 className="mt-5 text-lg font-bold">{title}</h3>
            <p className="mt-2 text-sm leading-6 text-abhay-muted">{copy}</p>
          </div>
        ))}
      </div>
    </PageShell>
  );
}
