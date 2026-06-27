export const INR = "\u20B9";
export const MULTIPLY = "\u00D7";
export const DOT = "\u00B7";
export const ARROW = "\u2192";

export const navItems = [
  { path: "/", label: "Home" },
  { path: "/features", label: "Features" },
  { path: "/ai-memory", label: "AI Memory" },
  { path: "/ocr", label: "OCR" },
  { path: "/reports", label: "Reports" },
  { path: "/pricing", label: "Pricing" },
  { path: "/dashboard", label: "Dashboard" }
];

export const coreFeatures = [
  ["Accounting Core", "Multi-company accounting foundation with ledgers, vouchers, invoices, GST assistance, and audit-ready records."],
  ["Ledgers", "Structured ledger groups, account nature, GSTIN fields, search, and finance-grade organization."],
  ["Vouchers", "Sales, purchase, payment, receipt, contra, journal, and expense workflows with double-entry discipline."],
  ["Invoices", "Professional GST invoice surfaces with line items, tax views, customer/vendor linking, and approval control."],
  ["GST", "GST assistance for rates, invoice-wise tax summaries, and CA review-ready reporting."],
  ["Reports", "Trial Balance, P&L, Balance Sheet, Cash Flow, GST Summary, and export-ready financial views."],
  ["Bank Reconciliation", "Amount, date, narration, and ledger matching experiences for faster reconciliation review."],
  ["Audit Logs", "Transparent activity history across company, ledger, voucher, invoice, and AI-assisted actions."]
];

export const memoryCards = [
  ["Vendor memory", "Remembers recurring vendor behavior, ledger mapping, tax treatment, and payment context."],
  ["Voucher memory", "Learns repeated narrations and preferred accounting treatment across teams and companies."],
  ["CA decision memory", "Preserves accountant decisions so similar future entries require less manual review."],
  ["Audit memory", "Maintains a clear trail of AI suggestions, corrections, approvals, and rejections."],
  ["Business context memory", "Adapts to company-specific language, departments, expense habits, and finance rules."]
];

export const documentFlows = [
  ["PDF invoice OCR", "Extract vendor, invoice number, date, GSTIN, taxable amount, GST, and total."],
  ["Image bill reading", "Turn photographed bills and scanned documents into structured review-ready data."],
  ["GST document parsing", "Bring tax evidence and summaries into a unified finance intelligence layer."],
  ["Draft voucher and invoice", "Transform document fields into accounting-ready drafts for human approval."],
  ["Human approval", "Every AI-assisted posting remains controlled by the owner, accountant, or CA."]
];

export const reportCards = [
  ["Trial Balance", "Ledger-wise debit and credit visibility."],
  ["Profit & Loss", "Revenue, expense, and profit intelligence."],
  ["Balance Sheet", "Assets, liabilities, and equity position."],
  ["Cash Flow", "Cash movement and liquidity readiness."],
  ["GST Summary", "Input and output tax assistance with review flags."],
  ["GSTR Draft Exports", "Structured draft exports for professional review."]
];

export const signatureSignals = [
  ["Enterprise Ready", "Finance-grade controls"],
  ["AI-Powered", "Human-verified automation"],
  ["Built for Scale", "Multi-company growth"],
  ["Made in Bharat", "ANVRITAI product family"]
];

export const operatingSignals = [
  ["Business Health", "92/100", "teal"],
  ["AI Memory Actions", "18,420", "cyan"],
  ["Companies", "5 Active", "orange"],
  ["Cash Risk", "Low", "gold"]
];

export const commandPillars = [
  ["Capture", "Documents, vouchers, invoices, bank entries, and GST evidence enter one controlled workspace."],
  ["Understand", "AI Memory OS reads context, identifies patterns, and prepares accounting suggestions."],
  ["Verify", "Owners, accountants, and CAs approve the final finance treatment before posting."],
  ["Remember", "Every correction strengthens future ledger mapping, tax treatment, and audit memory."]
];

export const memoryGraph = [
  ["Vendor Ledger", "91%", "cyan"],
  ["GST Treatment", "88%", "orange"],
  ["Voucher Pattern", "94%", "teal"],
  ["Audit Trail", "100%", "gold"]
];

export const plans = [
  {
    name: "Free Forever",
    india: `${INR}0`,
    global: "$0",
    period: "Month",
    audience: ["Freelancers", "Students", "Micro Businesses", "Startups"],
    features: [
      "AI Memory OS",
      "1 Company",
      "500 AI Memory Actions / Month",
      "100 Vouchers / Month",
      "25 Invoices / Month",
      "Trial Balance",
      "Profit & Loss",
      "Balance Sheet",
      "Cash Flow",
      "GST Summary",
      "OCR Reading (10 Documents / Month)",
      "Financial Dashboard",
      "Mobile App Access",
      "Desktop Access"
    ]
  },
  {
    name: "Starter",
    india: `${INR}999`,
    global: "$19",
    period: "Month",
    audience: ["Retail Stores", "Service Businesses", "Small Companies"],
    features: [
      "Everything in Free",
      "Unlimited Vouchers",
      "Unlimited Invoices",
      "5,000 AI Memory Actions / Month",
      "OCR Processing (100 Documents / Month)",
      "GST Reports",
      "GSTR-1 Draft Export",
      "GSTR-3B Draft Export",
      "AI Voucher Suggestions",
      "AI Ledger Suggestions",
      "Bank Reconciliation",
      "Email Support"
    ]
  },
  {
    name: "Business",
    india: `${INR}2,999`,
    global: "$59",
    period: "Month",
    audience: ["SMEs", "Manufacturing Companies", "Trading Businesses"],
    featured: true,
    features: [
      "Everything in Starter",
      "Up to 5 Companies",
      "25,000 AI Memory Actions / Month",
      "OCR Processing (1,000 Documents / Month)",
      "Advanced Financial Intelligence",
      "Revenue Analytics",
      "Expense Analytics",
      "Cash Flow Analytics",
      "Team Access Controls",
      "Ledger Scrutiny",
      "TDS Calculator",
      "PF Calculator",
      "ESIC Calculator",
      "Priority Support"
    ]
  },
  {
    name: "Pro",
    india: `${INR}4,999`,
    global: "$149",
    period: "Month",
    audience: ["CA Firms", "Accounting Firms", "Finance Teams", "Multi-Branch Businesses"],
    premium: true,
    features: [
      "Everything in Business",
      "Unlimited Companies",
      "Unlimited Team Members",
      "100,000 AI Memory Actions / Month",
      "OCR Processing (5,000 Documents / Month)",
      "AI Memory Learning",
      "Vendor Intelligence",
      "Audit Intelligence",
      "Compliance Intelligence",
      "Year-End Closing Assistant",
      "CFO Dashboard",
      "Advanced Reporting Suite",
      "Dedicated Onboarding",
      "High Priority Support"
    ]
  },
  {
    name: "Enterprise",
    india: `${INR}15,000+`,
    global: "$499+",
    period: "Month",
    audience: ["Large Enterprises", "Groups", "International Businesses"],
    enterprise: true,
    features: [
      "Unlimited Everything",
      "Unlimited Companies",
      "Unlimited Users",
      "Dedicated Infrastructure",
      "White Label Deployment",
      "Custom AI Workflows",
      "ERP Integrations",
      "API Integrations",
      "Dedicated Success Manager",
      "SLA Support",
      "Enterprise Security Controls",
      "Custom Compliance Modules"
    ]
  }
];
