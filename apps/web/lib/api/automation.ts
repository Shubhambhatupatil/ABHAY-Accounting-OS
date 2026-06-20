import { publicEnv } from "@/lib/config";
import { safeApiErrorMessage } from "@/lib/api/safe-error";
import type { AiSuggestion } from "@/lib/api/ai-accountant";

export type AiCfoDashboard = {
  profit_forecast: string;
  cash_runway_days: number | null;
  expense_warnings: string[];
  receivable_risk: string;
  gst_risk: string;
  business_health_score: number;
  alerts: Array<{ title: string; severity: string; message: string; metric_value: string | null }>;
};

export type AutomationSummary = {
  business_health_score: number;
  open_ai_suggestions: number;
  unreconciled_bank_transactions: number;
  active_alerts: number;
  cfo: AiCfoDashboard;
};

export type MonthEndClosePack = {
  trial_balance: unknown[];
  profit_and_loss: { revenue: string; expenses: string; profit: string };
  balance_sheet: { assets: string; liabilities: string; equity: string; check_difference: string };
  cash_flow: { net_cash_flow: string };
  gst_summary: { input_gst: string; output_gst: string; net_payable: string };
};

async function request<T>(path: string, token: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}${path}`, {
    method: options.method ?? "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Request failed" }));
    throw new Error(safeApiErrorMessage(error.detail, "Automation request could not be completed. Please try again."));
  }
  return response.json() as Promise<T>;
}

export const automationApi = {
  summary: (companyId: string, token: string) =>
    request<AutomationSummary>(`/companies/${companyId}/automation/summary`, token),
  cfo: (companyId: string, token: string) =>
    request<AiCfoDashboard>(`/companies/${companyId}/automation/cfo-dashboard`, token),
  monthEnd: (companyId: string, token: string) =>
    request<MonthEndClosePack>(`/companies/${companyId}/automation/month-end-close`, token),
  whatsAppEntry: (companyId: string, token: string, message: string) =>
    request<AiSuggestion>(`/companies/${companyId}/automation/whatsapp-entry`, token, {
      method: "POST",
      body: { message }
    }),
  bankAutoVoucher: (companyId: string, token: string, bankTransactionId: string) =>
    request<AiSuggestion>(`/companies/${companyId}/automation/bank-auto-voucher`, token, {
      method: "POST",
      body: { bank_transaction_id: bankTransactionId }
    })
};
