"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Download, FileText, Loader2, Plus, RefreshCw } from "lucide-react";
import { accountingApi, Company, Invoice, InvoiceGstSummaryRow, Ledger } from "@/lib/api/accounting";
import { getAccessToken } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type DraftLine = {
  description: string;
  hsn_sac: string;
  quantity: string;
  unit: string;
  unit_price: string;
  gst_rate: string;
};

const emptyLine: DraftLine = {
  description: "",
  hsn_sac: "",
  quantity: "1",
  unit: "NOS",
  unit_price: "",
  gst_rate: "18.00"
};

export function InvoicesWorkspace() {
  const supabase = createSupabaseBrowserClient();
  const [token, setToken] = useState<string | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [ledgers, setLedgers] = useState<Ledger[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [gstRows, setGstRows] = useState<InvoiceGstSummaryRow[]>([]);
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [status, setStatus] = useState("Loading invoices");
  const [isBusy, setIsBusy] = useState(false);
  const [invoiceType, setInvoiceType] = useState<"sales" | "purchase">("sales");
  const [invoiceNumber, setInvoiceNumber] = useState(() => `INV-${Date.now()}`);
  const [invoiceDate, setInvoiceDate] = useState(new Date().toISOString().slice(0, 10));
  const [dueDate, setDueDate] = useState("");
  const [partyLedgerId, setPartyLedgerId] = useState("");
  const [supplyType, setSupplyType] = useState("intra_state");
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<DraftLine[]>([{ ...emptyLine }]);

  useEffect(() => {
    getAccessToken(supabase).then((accessToken) => {
      setToken(accessToken);
      if (!accessToken) {
        setStatus("Sign in to manage invoices.");
        return;
      }
      accountingApi
        .companies(accessToken)
        .then((items) => {
          setCompanies(items);
          setCompanyId(items[0]?.id ?? "");
          setStatus(items.length ? "Invoice workspace ready" : "No company membership found.");
        })
        .catch((error: Error) => setStatus(error.message));
    });
  }, [supabase]);

  async function refresh(selectedCompanyId = companyId) {
    if (!token || !selectedCompanyId) return;
    setIsBusy(true);
    try {
      const [ledgerRows, invoiceRows, gstSummaryRows] = await Promise.all([
        accountingApi.ledgers(selectedCompanyId, token),
        accountingApi.invoices(selectedCompanyId, token),
        accountingApi.invoiceGstSummary(selectedCompanyId, token)
      ]);
      setLedgers(ledgerRows);
      setInvoices(invoiceRows);
      setGstRows(gstSummaryRows);
      setSelectedInvoice(invoiceRows[0] ?? null);
      setStatus("Invoices refreshed");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unable to refresh invoices.");
    } finally {
      setIsBusy(false);
    }
  }

  useEffect(() => {
    if (companyId) void refresh(companyId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId]);

  const partyLedgers = useMemo(
    () =>
      ledgers.filter((ledger) =>
        invoiceType === "sales" ? ledger.category === "sundry_debtor" : ledger.category === "sundry_creditor"
      ),
    [invoiceType, ledgers]
  );

  async function createInvoice() {
    if (!token || !companyId || !partyLedgerId) return;
    setIsBusy(true);
    try {
      const invoice = await accountingApi.createInvoice(companyId, token, {
        invoice_type: invoiceType,
        invoice_number: invoiceNumber,
        invoice_date: invoiceDate,
        due_date: dueDate || null,
        party_ledger_id: partyLedgerId,
        gst_supply_type: supplyType,
        notes,
        lines: lines.map((line) => ({
          description: line.description,
          hsn_sac: line.hsn_sac || null,
          quantity: line.quantity,
          unit: line.unit,
          unit_price: line.unit_price,
          gst_rate: line.gst_rate
        }))
      });
      setSelectedInvoice(invoice);
      setInvoiceNumber(`INV-${Date.now()}`);
      setLines([{ ...emptyLine }]);
      setStatus(`Invoice ${invoice.invoice_number} created and posted`);
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Invoice creation failed.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
              <FileText size={22} aria-hidden="true" />
            </span>
            <div>
              <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">AI Accounting Alpha</span>
              <h1 className="text-2xl font-semibold sm:text-3xl">GST Invoices</h1>
              <p className="mt-1 text-sm text-white/80">Create GST-ready invoices, auto-post vouchers, and download PDFs</p>
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <select className="premium-select" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
              {companies.map((company) => <option key={company.id} value={company.id}>{company.legal_name}</option>)}
            </select>
            <Link className="premium-link" href="/dashboard">Accounting</Link>
          </div>
          </div>
        </header>

        <p className="glass-card px-3 py-2 text-sm text-muted-foreground">{status}</p>

        <section className="grid gap-4 xl:grid-cols-[430px_1fr]">
          <form className="glass-panel p-4" onSubmit={(event) => { event.preventDefault(); void createInvoice(); }}>
            <h2 className="mb-3 text-base font-semibold">Create Invoice</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              <select className="premium-select h-11" value={invoiceType} onChange={(event) => setInvoiceType(event.target.value as "sales" | "purchase")}>
                <option value="sales">Sales Invoice</option>
                <option value="purchase">Purchase Invoice</option>
              </select>
              <Input value={invoiceNumber} onChange={(event) => setInvoiceNumber(event.target.value)} placeholder="Invoice number" required />
              <Input type="date" value={invoiceDate} onChange={(event) => setInvoiceDate(event.target.value)} required />
              <Input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
              <select className="premium-select h-11 sm:col-span-2" value={partyLedgerId} onChange={(event) => setPartyLedgerId(event.target.value)} required>
                <option value="">Customer / vendor ledger</option>
                {partyLedgers.map((ledger) => <option key={ledger.id} value={ledger.id}>{ledger.name}{ledger.gstin ? ` · ${ledger.gstin}` : ""}</option>)}
              </select>
              <select className="premium-select h-11 sm:col-span-2" value={supplyType} onChange={(event) => setSupplyType(event.target.value)}>
                <option value="intra_state">Intra-state CGST + SGST</option>
                <option value="inter_state">Inter-state IGST</option>
              </select>
            </div>
            <div className="mt-4 space-y-3">
              {lines.map((line, index) => (
                <div key={index} className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3 shadow-sm">
                  <Input value={line.description} onChange={(event) => updateLine(index, "description", event.target.value, lines, setLines)} placeholder="Item description" required />
                  <div className="mt-2 grid gap-2 sm:grid-cols-3">
                    <Input value={line.hsn_sac} onChange={(event) => updateLine(index, "hsn_sac", event.target.value, lines, setLines)} placeholder="HSN/SAC" />
                    <Input type="number" min="0.001" step="0.001" value={line.quantity} onChange={(event) => updateLine(index, "quantity", event.target.value, lines, setLines)} placeholder="Qty" required />
                    <Input value={line.unit} onChange={(event) => updateLine(index, "unit", event.target.value, lines, setLines)} placeholder="Unit" required />
                    <Input type="number" min="0" step="0.01" value={line.unit_price} onChange={(event) => updateLine(index, "unit_price", event.target.value, lines, setLines)} placeholder="Rate" required />
                    <Input type="number" min="0" step="0.01" value={line.gst_rate} onChange={(event) => updateLine(index, "gst_rate", event.target.value, lines, setLines)} placeholder="GST %" required />
                  </div>
                </div>
              ))}
              <Button type="button" variant="secondary" onClick={() => setLines([...lines, { ...emptyLine }])}>
                <Plus size={17} /> Add line
              </Button>
            </div>
            <Input className="mt-3" value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Notes" />
            <Button className="mt-3 w-full" type="submit" disabled={isBusy || !partyLedgerId}>
              {isBusy ? <Loader2 className="animate-spin" size={17} /> : <Plus size={17} />}
              Create and post invoice
            </Button>
          </form>

          <div className="space-y-4">
            <div className="glass-card p-4">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-base font-semibold">Invoice List</h2>
                <Button type="button" variant="secondary" onClick={() => refresh()} disabled={isBusy}><RefreshCw size={17} /> Refresh</Button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-left text-sm">
                  <thead className="text-muted-foreground"><tr><th className="py-2">Invoice</th><th>Party</th><th>Date</th><th>GST</th><th>Total</th><th></th></tr></thead>
                  <tbody>
                    {invoices.map((invoice) => (
                      <tr key={invoice.id} className="border-t">
                        <td className="py-2 font-medium">{invoice.invoice_number}</td>
                        <td>{invoice.party_ledger_name ?? invoice.party_ledger_id}</td>
                        <td>{invoice.invoice_date}</td>
                        <td>{formatMoney(Number(invoice.cgst_amount) + Number(invoice.sgst_amount) + Number(invoice.igst_amount))}</td>
                        <td>{formatMoney(invoice.total_amount)}</td>
                        <td className="text-right"><Button type="button" variant="ghost" onClick={() => setSelectedInvoice(invoice)}>View</Button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            {selectedInvoice ? <InvoiceDetail invoice={selectedInvoice} companyId={companyId} /> : null}
            <GstSummary rows={gstRows} />
          </div>
        </section>
      </section>
    </main>
  );
}

function InvoiceDetail({ invoice, companyId }: { invoice: Invoice; companyId: string }) {
  return (
    <div className="glass-panel p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs font-semibold text-muted-foreground">ANVRITAI / ABHAY GST-READY INVOICE</p>
          <h2 className="text-lg font-semibold">{invoice.invoice_number}</h2>
          <p className="text-sm text-muted-foreground">{invoice.party_ledger_name ?? "Linked party ledger"} · Voucher {invoice.voucher_id ?? "-"}</p>
        </div>
        <a className="premium-link gap-2" href={accountingApi.invoicePdfUrl(companyId, invoice.id)}>
          <Download size={17} /> Download PDF
        </a>
      </div>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead className="text-muted-foreground"><tr><th className="py-2">Item</th><th>HSN/SAC</th><th>Qty</th><th>Rate</th><th>GST</th><th>Total</th></tr></thead>
          <tbody>
            {invoice.lines.map((line) => (
              <tr key={line.id} className="border-t">
                <td className="py-2">{line.description}</td>
                <td>{line.hsn_sac ?? "-"}</td>
                <td>{line.quantity} {line.unit}</td>
                <td>{formatMoney(line.unit_price)}</td>
                <td>CGST {formatMoney(line.cgst_amount)} · SGST {formatMoney(line.sgst_amount)} · IGST {formatMoney(line.igst_amount)}</td>
                <td>{formatMoney(line.total_amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-4 grid gap-2 sm:grid-cols-4">
        <Total label="Taxable" value={invoice.taxable_value} />
        <Total label="CGST" value={invoice.cgst_amount} />
        <Total label="SGST" value={invoice.sgst_amount} />
        <Total label="IGST" value={invoice.igst_amount} />
        <Total label="Total" value={invoice.total_amount} />
      </div>
    </div>
  );
}

function GstSummary({ rows }: { rows: InvoiceGstSummaryRow[] }) {
  return (
    <div className="glass-card p-4">
      <h2 className="mb-3 text-base font-semibold">Invoice-wise GST Assistance</h2>
      <p className="mb-3 text-sm text-muted-foreground">ABHAY provides GST assistance. Please verify before filing.</p>
      {rows.length === 0 ? <p className="empty-state">No GST invoices posted yet.</p> : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="text-muted-foreground"><tr><th className="py-2">Invoice</th><th>Type</th><th>Party</th><th>Output/Input</th><th>Total</th></tr></thead>
            <tbody>{rows.map((row) => <tr key={row.invoice_id} className="border-t"><td className="py-2">{row.invoice_number}</td><td>{row.invoice_type}</td><td>{row.party_ledger_name}</td><td>CGST {formatMoney(row.cgst_amount)} · SGST {formatMoney(row.sgst_amount)} · IGST {formatMoney(row.igst_amount)}</td><td>{formatMoney(row.total_amount)}</td></tr>)}</tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Total({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl border border-[#1F2937] bg-[#111827]/80 p-3 shadow-sm"><p className="text-xs text-muted-foreground">{label}</p><p className="font-semibold">{formatMoney(value)}</p></div>;
}

function updateLine(index: number, key: keyof DraftLine, value: string, lines: DraftLine[], setLines: (lines: DraftLine[]) => void) {
  setLines(lines.map((line, lineIndex) => lineIndex === index ? { ...line, [key]: value } : line));
}

function formatMoney(value: string | number | undefined) {
  const amount = Number(value ?? 0);
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount);
}
