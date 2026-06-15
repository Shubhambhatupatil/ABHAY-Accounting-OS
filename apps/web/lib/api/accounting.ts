import { publicEnv } from "@/lib/config";

export type Company = {
  id: string;
  legal_name: string;
  trade_name: string | null;
  gstin: string | null;
  state_code: string | null;
};

export type DemoCompanyResponse = {
  company_id: string;
  legal_name: string;
  seeded_ledgers: number;
  seeded_vouchers: number;
  seeded_invoices: number;
  seeded_bank_transactions: number;
};

export type CompanyCreate = {
  legal_name: string;
  trade_name?: string | null;
  gstin?: string | null;
  state_code?: string | null;
};

export type AccessRequest = {
  id: string;
  company_id: string;
  company_legal_name: string;
  requester_profile_id: string;
  requester_email: string | null;
  requested_role: "accountant" | "viewer";
  status: "pending" | "approved" | "rejected";
  created_at: string;
  decided_at: string | null;
};

export type AccountNature = "asset" | "liability" | "equity" | "income" | "expense";
export type LedgerCategory =
  | "cash"
  | "bank"
  | "sundry_debtor"
  | "sundry_creditor"
  | "sales"
  | "purchase"
  | "direct_expense"
  | "indirect_expense"
  | "direct_income"
  | "indirect_income"
  | "input_gst"
  | "output_gst"
  | "round_off"
  | "capital"
  | "loan"
  | "other";

export type LedgerGroup = {
  id: string;
  name: string;
  account_nature: AccountNature;
  parent_id: string | null;
  is_system: boolean;
};

export type Ledger = {
  id: string;
  name: string;
  ledger_group_id: string;
  group_name: string;
  category: LedgerCategory;
  account_nature: AccountNature;
  opening_balance: string;
  opening_balance_type: "dr" | "cr";
  gstin: string | null;
  state_code: string | null;
  is_system: boolean;
  is_active: boolean;
};

export type VoucherType = "receipt" | "payment" | "contra" | "journal" | "purchase" | "sales";

export type Voucher = {
  id: string;
  voucher_number: string;
  voucher_type: VoucherType;
  voucher_date: string;
  status: string;
  narration: string | null;
  posted_at: string | null;
  lines: Array<{
    id: string;
    ledger_id: string;
    ledger_name: string;
    debit: string;
    credit: string;
    narration: string | null;
  }>;
};

export type DashboardMetrics = {
  revenue: string;
  expenses: string;
  profit: string;
  cash_position: string;
  receivables: string;
  payables: string;
};

export type TrialBalanceRow = {
  ledger_id: string;
  ledger_name: string;
  account_nature: AccountNature;
  category: LedgerCategory;
  debit: string;
  credit: string;
};

export type Invoice = {
  id: string;
  invoice_type: "sales" | "purchase";
  invoice_number: string;
  invoice_date: string;
  due_date: string | null;
  party_ledger_id: string;
  party_ledger_name: string | null;
  voucher_id: string | null;
  taxable_value: string;
  cgst_amount: string;
  sgst_amount: string;
  igst_amount: string;
  total_amount: string;
  notes: string | null;
  lines: Array<{
    id: string;
    description: string;
    hsn_sac: string | null;
    quantity: string;
    unit: string;
    unit_price: string;
    gst_rate: string;
    taxable_value: string;
    cgst_amount: string;
    sgst_amount: string;
    igst_amount: string;
    total_amount: string;
  }>;
};

export type InvoiceGstSummaryRow = {
  invoice_id: string;
  invoice_number: string;
  invoice_type: "sales" | "purchase";
  invoice_date: string;
  party_ledger_name: string;
  taxable_value: string;
  cgst_amount: string;
  sgst_amount: string;
  igst_amount: string;
  total_amount: string;
};

export type GstReport = {
  input_gst: string;
  output_gst: string;
  net_payable: string;
};

type ApiOptions = {
  token: string;
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
};

async function api<T>(path: string, options: ApiOptions): Promise<T> {
  const url = `${publicEnv.NEXT_PUBLIC_API_URL}${path}`;
  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? "GET",
      headers: {
        Authorization: `Bearer ${options.token}`,
        "Content-Type": "application/json"
      },
      body: options.body ? JSON.stringify(options.body) : undefined
    });
  } catch {
    throw new Error(`API not reachable. Check NEXT_PUBLIC_API_URL. Current API: ${publicEnv.NEXT_PUBLIC_API_URL}`);
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Request failed" }));
    throw new Error(typeof error.detail === "string" ? error.detail : `API request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const accountingApi = {
  companies: (token: string) => api<Company[]>("/companies", { token }),
  createCompany: (token: string, body: CompanyCreate) =>
    api<Company>("/companies", { token, method: "POST", body }),
  requestAccess: (companyId: string, token: string, requestedRole: "accountant" | "viewer") =>
    api<AccessRequest>(`/companies/${companyId}/access-requests`, {
      token,
      method: "POST",
      body: { requested_role: requestedRole }
    }),
  accessRequests: (companyId: string, token: string) =>
    api<AccessRequest[]>(`/companies/${companyId}/access-requests`, { token }),
  decideAccessRequest: (
    companyId: string,
    requestId: string,
    token: string,
    decision: "approve" | "reject",
    role: "accountant" | "viewer"
  ) =>
    api<AccessRequest>(`/companies/${companyId}/access-requests/${requestId}`, {
      token,
      method: "PATCH",
      body: { decision, role }
    }),
  createDemoCompany: (token: string) =>
    api<DemoCompanyResponse>("/demo/company", { token, method: "POST" }),
  groups: (companyId: string, token: string) =>
    api<LedgerGroup[]>(`/companies/${companyId}/ledger-groups`, { token }),
  createGroup: (companyId: string, token: string, body: { name: string; account_nature: AccountNature }) =>
    api<LedgerGroup>(`/companies/${companyId}/ledger-groups`, { token, method: "POST", body }),
  ledgers: (companyId: string, token: string, query = "") =>
    api<Ledger[]>(`/companies/${companyId}/ledgers${query}`, { token }),
  createLedger: (companyId: string, token: string, body: unknown) =>
    api<Ledger>(`/companies/${companyId}/ledgers`, { token, method: "POST", body }),
  updateLedger: (companyId: string, ledgerId: string, token: string, body: unknown) =>
    api<Ledger>(`/companies/${companyId}/ledgers/${ledgerId}`, { token, method: "PATCH", body }),
  deleteLedger: (companyId: string, ledgerId: string, token: string) =>
    api<void>(`/companies/${companyId}/ledgers/${ledgerId}`, { token, method: "DELETE" }),
  vouchers: (companyId: string, token: string) =>
    api<Voucher[]>(`/companies/${companyId}/vouchers`, { token }),
  createVoucher: (companyId: string, token: string, body: unknown) =>
    api<Voucher>(`/companies/${companyId}/vouchers`, { token, method: "POST", body }),
  dashboard: (companyId: string, token: string) =>
    api<DashboardMetrics>(`/companies/${companyId}/reports/dashboard`, { token }),
  trialBalance: (companyId: string, token: string) =>
    api<TrialBalanceRow[]>(`/companies/${companyId}/reports/trial-balance`, { token }),
  profitAndLoss: (companyId: string, token: string) =>
    api<{ revenue: string; expenses: string; profit: string }>(
      `/companies/${companyId}/reports/profit-and-loss`,
      { token }
    ),
  balanceSheet: (companyId: string, token: string) =>
    api<{ assets: string; liabilities: string; equity: string; check_difference: string }>(
      `/companies/${companyId}/reports/balance-sheet`,
      { token }
    ),
  cashFlow: (companyId: string, token: string) =>
    api<{
      operating_cash_flow: string;
      investing_cash_flow: string;
      financing_cash_flow: string;
      net_cash_flow: string;
    }>(`/companies/${companyId}/reports/cash-flow`, { token }),
  invoices: (companyId: string, token: string) =>
    api<Invoice[]>(`/companies/${companyId}/invoices`, { token }),
  invoice: (companyId: string, invoiceId: string, token: string) =>
    api<Invoice>(`/companies/${companyId}/invoices/${invoiceId}`, { token }),
  createInvoice: (companyId: string, token: string, body: unknown) =>
    api<Invoice>(`/companies/${companyId}/invoices`, { token, method: "POST", body }),
  gstReport: (companyId: string, token: string) =>
    api<GstReport>(`/companies/${companyId}/reports/gst`, { token }),
  invoiceGstSummary: (companyId: string, token: string) =>
    api<InvoiceGstSummaryRow[]>(`/companies/${companyId}/reports/gst/invoices`, { token }),
  invoicePdfUrl: (companyId: string, invoiceId: string) =>
    `${publicEnv.NEXT_PUBLIC_API_URL}/companies/${companyId}/invoices/${invoiceId}/pdf`
};
