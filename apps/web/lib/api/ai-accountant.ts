import { publicEnv } from "@/lib/config";
import { safeApiErrorMessage } from "@/lib/api/safe-error";
import type { LedgerCategory, VoucherType, Voucher } from "@/lib/api/accounting";

export type AiSuggestion = {
  suggestion_id: string;
  input_text: string;
  voucher_type: VoucherType;
  voucher_date: string;
  amount: string;
  confidence: string;
  gst_applicable: boolean;
  suggested_gst_rate: string | null;
  suggested_ledgers: Array<{
    ledger_id: string | null;
    ledger_name: string;
    category: LedgerCategory;
    reason: string;
    should_create: boolean;
  }>;
  lines: Array<{
    ledger_id: string | null;
    ledger_name: string;
    debit: string;
    credit: string;
    reason: string;
  }>;
  explanation: string;
  validation_errors: string[];
  can_post: boolean;
  model_name: string;
};

export type ConfirmAiPostingResponse = {
  suggestion_id: string;
  voucher: Voucher;
};

async function api<T>(path: string, token: string, body: unknown): Promise<T> {
  const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Request failed" }));
    throw new Error(safeApiErrorMessage(error.detail, "AI accountant request could not be completed. Please try again."));
  }

  return response.json() as Promise<T>;
}

export const aiAccountantApi = {
  parse: (companyId: string, token: string, text: string) =>
    api<AiSuggestion>(`/companies/${companyId}/ai-accountant/parse`, token, { text }),
  suggestVoucher: (companyId: string, token: string, text: string) =>
    api<AiSuggestion>(`/companies/${companyId}/ai-accountant/suggest-voucher`, token, { text }),
  confirm: (companyId: string, token: string, suggestionId: string) =>
    api<ConfirmAiPostingResponse>(`/companies/${companyId}/ai-accountant/confirm`, token, {
      suggestion_id: suggestionId
    }),
  reject: (companyId: string, token: string, suggestionId: string, reason: string) =>
    api<AiSuggestion>(`/companies/${companyId}/ai-accountant/suggestions/${suggestionId}/reject`, token, {
      reason
    })
};
