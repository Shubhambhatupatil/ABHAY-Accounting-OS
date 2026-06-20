export type SubscriptionPlan = "trial" | "starter" | "business" | "pro" | "enterprise";
export type SubscriptionStatus = "trialing" | "active" | "expired" | "payment_pending";

export type SubscriptionState = {
  id?: string;
  userId?: string;
  plan: SubscriptionPlan;
  planName: string;
  status: SubscriptionStatus;
  active: boolean;
  trialStartedAt: string;
  trialEnd: string;
  currentPeriodStart?: string | null;
  currentPeriodEnd?: string | null;
  invoiceUploadsUsed: number;
  razorpayPaymentId?: string;
};

export type SubscriptionRow = {
  id: string;
  user_id: string;
  plan_name: string;
  trial_start: string;
  trial_end: string;
  status: string;
  active: boolean;
  current_period_start?: string | null;
  current_period_end?: string | null;
  created_at: string;
};

export const TRIAL_INVOICE_LIMIT = 10;

export const subscriptionPlans = [
  {
    id: "trial",
    name: "Free Trial",
    price: "₹0",
    cadence: "14 days",
    summary: "For pilot owners evaluating ABHAY with limited usage.",
    invoiceLimit: 10,
    features: ["Basic dashboard", "Basic reports", "10 invoice uploads", "AI Workbench preview"]
  },
  {
    id: "starter",
    name: "Starter",
    price: "₹999",
    cadence: "month",
    summary: "For small businesses starting AI-assisted books.",
    invoiceLimit: 100,
    features: ["AI extraction", "GST assistance", "Ledger mapping", "Exports"]
  },
  {
    id: "business",
    name: "Business",
    price: "₹2999",
    cadence: "month",
    summary: "For growing teams with approval workflows.",
    invoiceLimit: 500,
    features: ["Approval workflow", "Bank reconciliation", "Audit trail", "Financial intelligence"]
  },
  {
    id: "pro",
    name: "Pro",
    price: "₹4999",
    cadence: "month",
    summary: "For CA-led teams and multi-company operators.",
    invoiceLimit: 1500,
    features: ["Advanced ledgers", "AI memory", "Bulk import", "Priority onboarding"]
  },
  {
    id: "enterprise",
    name: "Enterprise",
    price: "Custom",
    cadence: "Contact Sales",
    summary: "For firms needing bespoke onboarding and controls.",
    invoiceLimit: null,
    features: ["Custom limits", "Dedicated support", "Security review", "Migration planning"]
  }
] as const;

export function mapPlanNameToId(planName: string): SubscriptionPlan {
  const normalized = planName.toLowerCase();
  if (normalized.includes("starter")) return "starter";
  if (normalized.includes("business")) return "business";
  if (normalized.includes("pro")) return "pro";
  if (normalized.includes("enterprise")) return "enterprise";
  return "trial";
}

export function mapPlanIdToName(plan: SubscriptionPlan) {
  if (plan === "trial") return "Free Trial";
  if (plan === "starter") return "Starter";
  if (plan === "business") return "Business";
  if (plan === "pro") return "Pro";
  return "Enterprise";
}

export function mapSubscriptionRow(row: SubscriptionRow): SubscriptionState {
  const status = normalizeStatus(row.status);
  return {
    id: row.id,
    userId: row.user_id,
    plan: mapPlanNameToId(row.plan_name),
    planName: row.plan_name,
    status,
    active: row.active && status !== "expired" && new Date(row.current_period_end ?? row.trial_end).getTime() >= Date.now(),
    trialStartedAt: row.trial_start,
    trialEnd: row.trial_end,
    currentPeriodStart: row.current_period_start ?? null,
    currentPeriodEnd: row.current_period_end ?? null,
    invoiceUploadsUsed: 0
  };
}

export function createDemoSubscriptionState(): SubscriptionState {
  const started = new Date();
  const ends = new Date(started.getTime() + 14 * 86_400_000);
  return {
    plan: "trial",
    planName: "Free Trial",
    status: "trialing",
    active: true,
    trialStartedAt: started.toISOString(),
    trialEnd: ends.toISOString(),
    currentPeriodStart: started.toISOString(),
    currentPeriodEnd: ends.toISOString(),
    invoiceUploadsUsed: 0
  };
}

export function daysRemaining(state: SubscriptionState | null) {
  if (!state) return 0;
  const end = new Date(state.currentPeriodEnd ?? state.trialEnd).getTime();
  if (Number.isNaN(end)) return 0;
  const remaining = Math.ceil((end - Date.now()) / 86_400_000);
  return Math.max(0, remaining);
}

export function isSubscriptionActive(state: SubscriptionState | null) {
  if (!state) return false;
  if (state.status === "active") return state.active;
  if (state.status === "trialing") return state.active && daysRemaining(state) > 0;
  return false;
}

export function canUseAdvancedFeature(
  state: SubscriptionState | null,
  feature: "invoice_upload" | "ai_extraction" | "reports" | "export" | "advanced_ledger"
) {
  if (!isSubscriptionActive(state)) return false;
  if (!state) return false;
  if (state.status === "active") return true;
  if (feature === "invoice_upload") return state.invoiceUploadsUsed < TRIAL_INVOICE_LIMIT;
  return feature === "reports";
}

function normalizeStatus(status: string): SubscriptionStatus {
  if (status === "active" || status === "expired" || status === "payment_pending") return status;
  return "trialing";
}
