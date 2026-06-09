import { publicEnv } from "@/lib/config";

export type Insight = {
  title: string;
  severity: "info" | "warning" | "positive";
  message: string;
  metric_value: string | null;
};

export type FinancialSummary = {
  profit: {
    revenue: string;
    expenses: string;
    profit: string;
    profit_margin: string;
    previous_month_profit: string;
    profit_trend_percent: string | null;
  };
  cashflow: {
    cash_position: string;
    receivables: string;
    payables: string;
    net_cash_flow: string;
    cash_risk_level: string;
    cash_risk_reason: string;
  };
  gst: {
    gst_collected: string;
    gst_input_paid: string;
    current_gst_payable: string;
    estimated_month_end_liability: string;
  };
  insights: Insight[];
};

async function get<T>(path: string, token: string): Promise<T> {
  const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Request failed" }));
    throw new Error(typeof error.detail === "string" ? error.detail : "Request failed");
  }
  return response.json() as Promise<T>;
}

export const financialIntelligenceApi = {
  summary: (companyId: string, token: string, month: string) =>
    get<FinancialSummary>(
      `/companies/${companyId}/financial-intelligence/summary?month=${month}-01`,
      token
    )
};
