import { publicEnv } from "@/lib/config";
import type { AiSuggestion, ConfirmAiPostingResponse } from "@/lib/api/ai-accountant";

export type AiEntryWorkbenchResponse = {
  suggestion: AiSuggestion;
  workflow_state: string;
  confidence_band: "high" | "medium" | "low";
  clarification_questions: string[];
  extracted_invoice: {
    vendor_or_customer_name: string | null;
    invoice_number: string | null;
    invoice_date: string | null;
    gstin: string | null;
    taxable_amount: string | null;
    cgst_amount: string | null;
    sgst_amount: string | null;
    igst_amount: string | null;
    total_amount: string | null;
    source_text_available: boolean;
    extraction_warning: string | null;
  } | null;
  doctor_findings: string[];
  gst_risks: string[];
  duplicate_warnings: string[];
};

export type AiAccuracyDashboard = {
  total_suggestions: number;
  approved_suggestions: number;
  approved_without_edit: number;
  rejected_suggestions: number;
  corrected_suggestions: number;
  approval_rate: string;
  correction_rate: string;
  estimated_accuracy: string;
  average_confidence: string;
};

export type AiMonthEndReadiness = {
  books_completion_percent: string;
  pending_vouchers: number;
  unreconciled_bank_entries: number;
  gst_risk_count: number;
  missing_bill_count: number;
  readiness_score: string;
};

export type AiOwnerReport = {
  month: string;
  summary: string;
  profit: string;
  cash_position: string;
  gst_payable: string;
};

export type AiCorrection = {
  id: string;
  suggestion_id: string | null;
  corrected_payload: Record<string, unknown>;
  created_at: string;
};

async function api<T>(path: string, token: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}${path}`, {
      method: options.method ?? "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
  } catch {
    throw new Error(`API not reachable. Check NEXT_PUBLIC_API_URL. Current API: ${publicEnv.NEXT_PUBLIC_API_URL}`);
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Request failed" }));
    throw new Error(typeof error.detail === "string" ? error.detail : "Request failed");
  }

  return response.json() as Promise<T>;
}

export const aiEntryApi = {
  inbox: (companyId: string, token: string, text: string, sourceType: string) =>
    api<AiEntryWorkbenchResponse>(`/companies/${companyId}/ai-entry/inbox`, token, {
      method: "POST",
      body: { text, source_type: sourceType }
    }),
  parseText: (companyId: string, token: string, text: string) =>
    api<AiEntryWorkbenchResponse>(`/companies/${companyId}/ai-entry/parse-text`, token, {
      method: "POST",
      body: { text, source_type: "one_line_text" }
    }),
  uploadPdf: (companyId: string, token: string, filename: string, fileBase64: string) =>
    api<AiEntryWorkbenchResponse>(`/companies/${companyId}/ai-entry/upload-pdf`, token, {
      method: "POST",
      body: { filename, file_base64: fileBase64 }
    }),
  approve: (companyId: string, token: string, suggestionId: string) =>
    api<ConfirmAiPostingResponse>(`/companies/${companyId}/ai-entry/approve`, token, {
      method: "POST",
      body: { suggestion_id: suggestionId }
    }),
  reject: (companyId: string, token: string, suggestionId: string, reason: string) =>
    api<AiEntryWorkbenchResponse>(`/companies/${companyId}/ai-entry/reject`, token, {
      method: "POST",
      body: { suggestion_id: suggestionId, reason }
    }),
  accuracy: (companyId: string, token: string) =>
    api<AiAccuracyDashboard>(`/companies/${companyId}/ai-entry/accuracy-dashboard`, token),
  corrections: (companyId: string, token: string) =>
    api<AiCorrection[]>(`/companies/${companyId}/ai-entry/corrections`, token),
  readiness: (companyId: string, token: string) =>
    api<AiMonthEndReadiness>(`/companies/${companyId}/ai-entry/month-end-readiness`, token),
  ownerReport: (companyId: string, token: string) =>
    api<AiOwnerReport>(`/companies/${companyId}/ai-entry/owner-report`, token)
};
