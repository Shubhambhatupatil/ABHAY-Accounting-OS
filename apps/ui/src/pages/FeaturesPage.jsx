import { Banknote, BarChart3, FileCheck2, FileSpreadsheet, FileText, Layers3, ReceiptText, ShieldCheck } from "lucide-react";
import { coreFeatures } from "../data/content.js";
import { FeatureCard } from "../components/ui.jsx";
import { PageShell } from "../components/sections.jsx";

const featureIcons = [Layers3, FileSpreadsheet, ReceiptText, FileText, FileCheck2, BarChart3, Banknote, ShieldCheck];

export function FeaturesPage({ navigate }) {
  return (
    <PageShell
      eyebrow="Features"
      title="Accounting core with an AI-first operating layer"
      copy="ABHAY keeps the familiar accounting foundation while presenting it through a premium, automation-first product experience."
      cta={() => navigate("/dashboard")}
      ctaLabel="Open dashboard"
    >
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {coreFeatures.map(([title, copy], index) => (
          <FeatureCard key={title} title={title} icon={featureIcons[index]} copy={copy} />
        ))}
      </div>
    </PageShell>
  );
}
