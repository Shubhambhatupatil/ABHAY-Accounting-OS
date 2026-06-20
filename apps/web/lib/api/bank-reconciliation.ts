import { publicEnv } from "@/lib/config";
import { safeApiErrorMessage } from "@/lib/api/safe-error";

export type BankTransaction = {
  id: string;
  transaction_date: string;
  description: string;
  reference_number: string | null;
  debit: string;
  credit: string;
  balance: string | null;
  reconciliation_status: "matched" | "unmatched" | "suggested_match" | "ignored";
};

export type SuggestedMatch = {
  bank_transaction_id: string;
  voucher_id: string;
  journal_entry_id: string;
  voucher_number: string;
  confidence: string;
  reason: string;
};

export type ReconciliationSummary = {
  total_transactions: number;
  matched: number;
  unmatched: number;
  suggested_match: number;
  ignored: number;
  matched_amount: string;
  unreconciled_amount: string;
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
    throw new Error(safeApiErrorMessage(error.detail, "Bank reconciliation request could not be completed. Please try again."));
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const bankReconciliationApi = {
  upload: (companyId: string, token: string, body: { filename: string; csv_content: string; bank_name: string }) =>
    request<{ statement_id: string; imported_count: number }>(
      `/companies/${companyId}/bank-reconciliation/upload`,
      token,
      { method: "POST", body }
    ),
  transactions: (companyId: string, token: string) =>
    request<BankTransaction[]>(`/companies/${companyId}/bank-reconciliation/transactions`, token),
  suggestMatches: (companyId: string, token: string) =>
    request<SuggestedMatch[]>(`/companies/${companyId}/bank-reconciliation/suggest-matches`, token),
  confirmMatch: (companyId: string, token: string, body: { bank_transaction_id: string; journal_entry_id: string; confidence: string }) =>
    request<void>(`/companies/${companyId}/bank-reconciliation/confirm-match`, token, {
      method: "POST",
      body
    }),
  ignore: (companyId: string, token: string, bankTransactionId: string) =>
    request<void>(`/companies/${companyId}/bank-reconciliation/ignore`, token, {
      method: "POST",
      body: { bank_transaction_id: bankTransactionId }
    }),
  summary: (companyId: string, token: string) =>
    request<ReconciliationSummary>(`/companies/${companyId}/bank-reconciliation/summary`, token)
};
