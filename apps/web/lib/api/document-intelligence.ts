export type DocumentAiFields = {
  document_type: string;
  vendor_name: string | null;
  customer_name: string | null;
  gstin: string | null;
  invoice_number: string | null;
  invoice_date: string | null;
  subtotal: string | null;
  gst_rate: string | null;
  gst_amount: string | null;
  total_amount: string | null;
  line_items: Array<{
    description: string;
    hsn_sac: string | null;
    quantity: string | null;
    unit_price: string | null;
    amount: string | null;
  }>;
  confidence_score: string;
};

export type DocumentAiSuggestion = {
  suggested_voucher_type: string;
  debit_ledger: string;
  credit_ledger: string;
  gst_treatment: string;
  summary: string;
  warnings: string[];
};

export type DocumentIntelligenceResult = {
  id: string;
  file_name: string;
  document_type: string;
  extracted_text_summary: string;
  extracted_text_available: boolean;
  fields: DocumentAiFields;
  accounting_suggestion: DocumentAiSuggestion;
  confidence_score: string;
  warnings: string[];
  human_approval_required: boolean;
  draft_only: boolean;
};

export async function uploadDocumentForAnalysis(companyId: string, token: string, file: File) {
  const response = await fetch(`/api/document-intelligence/upload?company_id=${encodeURIComponent(companyId)}`, {
    method: "POST",
    cache: "no-store",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": file.type || "application/octet-stream",
      "X-File-Name": file.name
    },
    body: file
  });
  const data = await response.json().catch(() => ({ detail: "Document analysis failed." }));
  if (!response.ok) {
    throw new Error(typeof data.detail === "string" ? data.detail : "Document analysis failed.");
  }
  return data as DocumentIntelligenceResult;
}
