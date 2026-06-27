"use client";

import { useMemo, useState } from "react";
import { Download, Printer, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Invoice, Ledger, TrialBalanceRow, Voucher, VoucherType } from "@/lib/api/accounting";

type MoneyValue = string | number | undefined | null;

type DetailedReportGridsProps = {
  ledgers: Ledger[];
  vouchers: Voucher[];
  invoices: Invoice[];
  trialBalance: TrialBalanceRow[];
  pnl: { revenue: string; expenses: string; profit: string } | null;
  balanceSheet: { assets: string; liabilities: string; equity: string; check_difference: string } | null;
  cashFlow: {
    operating_cash_flow: string;
    investing_cash_flow?: string;
    financing_cash_flow?: string;
    net_cash_flow: string;
  } | null;
};

type GridColumn<T> = {
  key: keyof T | string;
  label: string;
  align?: "left" | "right";
  render?: (row: T) => string;
};

type TrialGridRow = {
  ledgerId: string;
  ledgerName: string;
  group: string;
  openingBalance: number;
  debit: number;
  credit: number;
  closingBalance: number;
  nature: string;
};

type StatementGridRow = {
  section: string;
  group: string;
  ledgerId: string;
  ledgerName: string;
  closingBalance: number;
  nature: string;
};

type LedgerStatementRow = {
  voucherId: string;
  date: string;
  voucherNo: string;
  voucherType: string;
  particulars: string;
  debit: number;
  credit: number;
  balance: number;
};

type DayBookRow = {
  voucherId: string;
  date: string;
  voucherNo: string;
  type: string;
  party: string;
  narration: string;
  debit: number;
  credit: number;
  amount: number;
};

type RegisterRow = {
  invoiceId: string;
  voucherId: string | null;
  date: string;
  invoiceNo: string;
  party: string;
  gstin: string;
  taxable: number;
  cgst: number;
  sgst: number;
  igst: number;
  total: number;
  status: string;
};

const voucherTypes: Array<VoucherType | ""> = ["", "receipt", "payment", "contra", "journal", "purchase", "sales", "debit_note", "credit_note"];

export function DetailedReportGrids({
  ledgers,
  vouchers,
  invoices,
  trialBalance,
  pnl,
  balanceSheet,
  cashFlow
}: DetailedReportGridsProps) {
  const [search, setSearch] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [voucherType, setVoucherType] = useState<VoucherType | "">("");
  const [ledgerId, setLedgerId] = useState("");
  const [groupId, setGroupId] = useState("");
  const [selectedVoucher, setSelectedVoucher] = useState<Voucher | null>(null);

  const ledgerMap = useMemo(() => new Map(ledgers.map((ledger) => [ledger.id, ledger])), [ledgers]);

  const filteredVouchers = useMemo(
    () => vouchers.filter((voucher) => {
      if (voucherType && voucher.voucher_type !== voucherType) return false;
      if (!isDateInRange(voucher.voucher_date, fromDate, toDate)) return false;
      if (ledgerId && !voucher.lines.some((line) => line.ledger_id === ledgerId)) return false;
      if (groupId && !voucher.lines.some((line) => ledgerMap.get(line.ledger_id)?.ledger_group_id === groupId)) return false;
      if (!search.trim()) return true;
      const term = search.trim().toLowerCase();
      return [
        voucher.voucher_number,
        voucher.voucher_type,
        voucher.narration ?? "",
        ...voucher.lines.flatMap((line) => [line.ledger_name, line.narration ?? ""])
      ].some((value) => value.toLowerCase().includes(term));
    }),
    [fromDate, groupId, ledgerId, ledgerMap, search, toDate, voucherType, vouchers]
  );

  const filteredInvoices = useMemo(
    () => invoices.filter((invoice) => {
      if (!isDateInRange(invoice.invoice_date, fromDate, toDate)) return false;
      if (ledgerId && invoice.party_ledger_id !== ledgerId) return false;
      if (groupId && ledgerMap.get(invoice.party_ledger_id)?.ledger_group_id !== groupId) return false;
      if (!search.trim()) return true;
      const term = search.trim().toLowerCase();
      return [
        invoice.invoice_number,
        invoice.invoice_type,
        invoice.party_ledger_name ?? "",
        invoice.notes ?? "",
        ...invoice.lines.flatMap((line) => [line.description, line.hsn_sac ?? ""])
      ].some((value) => value.toLowerCase().includes(term));
    }),
    [fromDate, groupId, invoices, ledgerId, ledgerMap, search, toDate]
  );

  const ledgerMovements = useMemo(() => movementsByLedger(filteredVouchers), [filteredVouchers]);
  const trialRows = useMemo(
    () => buildTrialRows(ledgers, trialBalance, ledgerMovements).filter((row) => matchesLedgerFilters(row.ledgerId, row.ledgerName, ledgerMap, search, ledgerId, groupId)),
    [groupId, ledgerId, ledgerMap, ledgerMovements, ledgers, search, trialBalance]
  );
  const statementRows = useMemo(() => buildStatementRows(trialRows, ledgerMap), [ledgerMap, trialRows]);
  const dayBookRows = useMemo(() => buildDayBookRows(filteredVouchers), [filteredVouchers]);
  const selectedLedgerStatement = useMemo(
    () => buildLedgerStatementRows(filteredVouchers, ledgerId || trialRows[0]?.ledgerId || ""),
    [filteredVouchers, ledgerId, trialRows]
  );
  const salesRegisterRows = useMemo(
    () => buildRegisterRows(filteredInvoices.filter((invoice) => invoice.invoice_type === "sales"), ledgerMap),
    [filteredInvoices, ledgerMap]
  );
  const purchaseRegisterRows = useMemo(
    () => buildRegisterRows(filteredInvoices.filter((invoice) => invoice.invoice_type === "purchase"), ledgerMap),
    [filteredInvoices, ledgerMap]
  );

  const groups = useMemo(() => {
    const seen = new Map<string, string>();
    ledgers.forEach((ledger) => seen.set(ledger.ledger_group_id, ledger.group_name));
    return Array.from(seen, ([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  }, [ledgers]);

  return (
    <section className="space-y-4">
      <div className="glass-card grid gap-3 p-4 lg:grid-cols-[1fr_150px_150px_170px_190px_190px]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-3 text-muted-foreground" size={17} />
          <Input className="pl-9" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search ledger, voucher, invoice" />
        </div>
        <Input type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} title="From date" />
        <Input type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} title="To date" />
        <select className="premium-select h-11" value={voucherType} onChange={(event) => setVoucherType(event.target.value as VoucherType | "")}>
          {voucherTypes.map((type) => <option key={type || "all"} value={type}>{type ? title(type) : "All voucher types"}</option>)}
        </select>
        <select className="premium-select h-11" value={ledgerId} onChange={(event) => setLedgerId(event.target.value)}>
          <option value="">All ledgers</option>
          {ledgers.map((ledger) => <option key={ledger.id} value={ledger.id}>{ledger.name}</option>)}
        </select>
        <select className="premium-select h-11" value={groupId} onChange={(event) => setGroupId(event.target.value)}>
          <option value="">All groups</option>
          {groups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
        </select>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <SummaryBox title="Balance Sheet Check" rows={[
          ["Assets", formatMoney(balanceSheet?.assets)],
          ["Liabilities", formatMoney(balanceSheet?.liabilities)],
          ["Equity", formatMoney(balanceSheet?.equity)],
          ["Difference", formatMoney(balanceSheet?.check_difference)]
        ]} />
        <SummaryBox title="Profit & Loss" rows={[
          ["Revenue", formatMoney(pnl?.revenue)],
          ["Expenses", formatMoney(pnl?.expenses)],
          ["Net Profit", formatMoney(pnl?.profit)]
        ]} />
        <SummaryBox title="Cash Flow" rows={[
          ["Operating", formatMoney(cashFlow?.operating_cash_flow)],
          ["Investing", formatMoney(cashFlow?.investing_cash_flow)],
          ["Financing", formatMoney(cashFlow?.financing_cash_flow)],
          ["Net Cash Flow", formatMoney(cashFlow?.net_cash_flow)]
        ]} />
      </div>

      <ReportGrid
        title="Balance Sheet Detailed View"
        subtitle="Assets, liabilities and equity grouped from real ledger balances."
        columns={[
          { key: "section", label: "Section" },
          { key: "group", label: "Group" },
          { key: "ledgerName", label: "Ledger" },
          { key: "closingBalance", label: "Closing Balance", align: "right", render: (row) => formatMoney(row.closingBalance) },
          { key: "nature", label: "Debit/Credit Nature" }
        ]}
        rows={statementRows.filter((row) => ["Assets", "Liabilities", "Equity"].includes(row.section))}
        totals={{ label: "Balance Sheet Total", amount: balanceSheetTotal(statementRows) }}
        fileName="abhay-balance-sheet-detail"
      />

      <ReportGrid
        title="Profit & Loss Detailed View"
        subtitle={`Gross Profit ${formatMoney(grossProfit(statementRows))} | Net Profit ${formatMoney(pnl?.profit)}`}
        columns={[
          { key: "section", label: "Section" },
          { key: "group", label: "Group" },
          { key: "ledgerName", label: "Ledger" },
          { key: "closingBalance", label: "Ledger-wise Total", align: "right", render: (row) => formatMoney(row.closingBalance) },
          { key: "nature", label: "Debit/Credit Nature" }
        ]}
        rows={statementRows.filter((row) => ["Income", "Direct Expenses", "Indirect Expenses"].includes(row.section))}
        totals={{ label: "Net Profit", amount: Number(pnl?.profit ?? 0) }}
        fileName="abhay-profit-and-loss-detail"
      />

      <ReportGrid
        title="Trial Balance Full Grid"
        subtitle="Opening balance, debit movement, credit movement and closing balance by ledger."
        columns={[
          { key: "ledgerName", label: "Ledger Name" },
          { key: "group", label: "Group" },
          { key: "openingBalance", label: "Opening Balance", align: "right", render: (row) => formatMoney(row.openingBalance) },
          { key: "debit", label: "Debit", align: "right", render: (row) => formatMoney(row.debit) },
          { key: "credit", label: "Credit", align: "right", render: (row) => formatMoney(row.credit) },
          { key: "closingBalance", label: "Closing Balance", align: "right", render: (row) => formatMoney(row.closingBalance) }
        ]}
        rows={trialRows}
        totals={{
          label: "Trial Balance Totals",
          debit: sum(trialRows.map((row) => row.debit)),
          credit: sum(trialRows.map((row) => row.credit)),
          amount: sum(trialRows.map((row) => Math.abs(row.closingBalance)))
        }}
        fileName="abhay-trial-balance"
      />

      <ReportGrid
        title="Ledger Statement"
        subtitle={ledgerId ? `Selected ledger: ${ledgerMap.get(ledgerId)?.name ?? "Ledger"}` : "Select a ledger above to drill into its statement."}
        columns={[
          { key: "date", label: "Date" },
          { key: "voucherNo", label: "Voucher No" },
          { key: "voucherType", label: "Voucher Type" },
          { key: "particulars", label: "Particulars" },
          { key: "debit", label: "Debit", align: "right", render: (row) => formatMoney(row.debit) },
          { key: "credit", label: "Credit", align: "right", render: (row) => formatMoney(row.credit) },
          { key: "balance", label: "Balance", align: "right", render: (row) => formatMoney(row.balance) }
        ]}
        rows={selectedLedgerStatement}
        totals={{
          label: "Ledger Movement",
          debit: sum(selectedLedgerStatement.map((row) => row.debit)),
          credit: sum(selectedLedgerStatement.map((row) => row.credit)),
          amount: selectedLedgerStatement.at(-1)?.balance ?? 0
        }}
        fileName="abhay-ledger-statement"
        onRowClick={(row) => setSelectedVoucher(vouchers.find((voucher) => voucher.id === row.voucherId) ?? null)}
      />

      <ReportGrid
        title="Day Book"
        subtitle="Daily voucher book from posted vouchers."
        columns={[
          { key: "date", label: "Date" },
          { key: "voucherNo", label: "Voucher No" },
          { key: "type", label: "Type" },
          { key: "party", label: "Party" },
          { key: "narration", label: "Narration" },
          { key: "debit", label: "Debit", align: "right", render: (row) => formatMoney(row.debit) },
          { key: "credit", label: "Credit", align: "right", render: (row) => formatMoney(row.credit) },
          { key: "amount", label: "Amount", align: "right", render: (row) => formatMoney(row.amount) }
        ]}
        rows={dayBookRows}
        totals={{
          label: "Day Book Totals",
          debit: sum(dayBookRows.map((row) => row.debit)),
          credit: sum(dayBookRows.map((row) => row.credit)),
          amount: sum(dayBookRows.map((row) => row.amount))
        }}
        fileName="abhay-day-book"
        onRowClick={(row) => setSelectedVoucher(vouchers.find((voucher) => voucher.id === row.voucherId) ?? null)}
      />

      <ReportGrid
        title="Sales Register"
        subtitle="Invoice-wise sales register with GST split."
        columns={registerColumns("Customer")}
        rows={salesRegisterRows}
        totals={registerTotals(salesRegisterRows)}
        fileName="abhay-sales-register"
      />

      <ReportGrid
        title="Purchase Register"
        subtitle="Bill-wise purchase register with GST split."
        columns={registerColumns("Vendor")}
        rows={purchaseRegisterRows}
        totals={registerTotals(purchaseRegisterRows)}
        fileName="abhay-purchase-register"
      />

      {selectedVoucher ? (
        <div className="glass-card p-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold">Voucher Detail: {selectedVoucher.voucher_number}</h2>
              <p className="text-sm text-muted-foreground">{selectedVoucher.voucher_date} · {title(selectedVoucher.voucher_type)} · {selectedVoucher.status}</p>
            </div>
            <Button type="button" variant="ghost" onClick={() => setSelectedVoucher(null)}>Close</Button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="sticky top-0 bg-[#0F172A] text-muted-foreground">
                <tr><th className="py-2">Ledger</th><th>Narration</th><th className="text-right">Debit</th><th className="text-right">Credit</th></tr>
              </thead>
              <tbody>
                {selectedVoucher.lines.map((line) => (
                  <tr key={line.id} className="border-t border-[#1F2937]">
                    <td className="py-2 font-medium">{line.ledger_name}</td>
                    <td>{line.narration ?? selectedVoucher.narration ?? "-"}</td>
                    <td className="text-right">{formatMoney(line.debit)}</td>
                    <td className="text-right">{formatMoney(line.credit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function ReportGrid<T extends Record<string, unknown>>({
  title: heading,
  subtitle,
  columns,
  rows,
  totals,
  fileName,
  onRowClick
}: {
  title: string;
  subtitle: string;
  columns: GridColumn<T>[];
  rows: T[];
  totals?: { label: string; debit?: number; credit?: number; amount?: number };
  fileName: string;
  onRowClick?: (row: T) => void;
}) {
  return (
    <div className="glass-card p-4">
      <div className="mb-3 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="text-base font-semibold">{heading}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="secondary" onClick={() => exportCsv(fileName, columns, rows)}>
            <Download size={16} />
            Excel/CSV
          </Button>
          <Button type="button" variant="secondary" onClick={() => printReport(heading, columns, rows, totals)}>
            <Printer size={16} />
            PDF / Print
          </Button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[960px] border-separate border-spacing-0 text-left text-sm">
          <thead className="sticky top-0 z-10 bg-[#0F172A] text-muted-foreground">
            <tr>
              {columns.map((column) => (
                <th key={String(column.key)} className={`border-b border-[#1F2937] px-3 py-3 ${column.align === "right" ? "text-right" : ""}`}>
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr
                key={rowKey(row, index)}
                className={onRowClick ? "cursor-pointer border-t hover:bg-[#F97316]/10" : "border-t"}
                onClick={() => onRowClick?.(row)}
              >
                {columns.map((column) => (
                  <td key={String(column.key)} className={`border-b border-[#1F2937]/70 px-3 py-3 ${column.align === "right" ? "text-right" : ""}`}>
                    {column.render ? column.render(row) : String(row[column.key] ?? "-")}
                  </td>
                ))}
              </tr>
            ))}
            {!rows.length ? (
              <tr>
                <td className="px-3 py-6 text-center text-muted-foreground" colSpan={columns.length}>No rows found for the selected filters.</td>
              </tr>
            ) : null}
          </tbody>
          {totals ? (
            <tfoot className="sticky bottom-0 bg-[#111827] font-semibold">
              <tr>
                <td className="border-t border-[#1F2937] px-3 py-3" colSpan={Math.max(columns.length - 3, 1)}>{totals.label}</td>
                <td className="border-t border-[#1F2937] px-3 py-3 text-right">{totals.debit !== undefined ? formatMoney(totals.debit) : ""}</td>
                <td className="border-t border-[#1F2937] px-3 py-3 text-right">{totals.credit !== undefined ? formatMoney(totals.credit) : ""}</td>
                <td className="border-t border-[#1F2937] px-3 py-3 text-right">{totals.amount !== undefined ? formatMoney(totals.amount) : ""}</td>
              </tr>
            </tfoot>
          ) : null}
        </table>
      </div>
    </div>
  );
}

function SummaryBox({ title: heading, rows }: { title: string; rows: Array<[string, string]> }) {
  return (
    <div className="glass-card p-4">
      <h2 className="mb-3 text-base font-semibold">{heading}</h2>
      <div className="space-y-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between border-b border-[#1F2937] py-2 text-sm last:border-b-0">
            <span className="text-muted-foreground">{label}</span>
            <span className="font-medium">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function buildTrialRows(ledgers: Ledger[], trialBalance: TrialBalanceRow[], movements: Map<string, { debit: number; credit: number }>): TrialGridRow[] {
  const tbMap = new Map(trialBalance.map((row) => [row.ledger_id, row]));
  return ledgers.map((ledger) => {
    const movement = movements.get(ledger.id) ?? { debit: 0, credit: 0 };
    const opening = signedOpeningBalance(ledger);
    const closingSigned = opening + movement.debit - movement.credit;
    const tbRow = tbMap.get(ledger.id);
    const fallbackClosing = Number(tbRow?.debit ?? 0) - Number(tbRow?.credit ?? 0);
    const closing = movement.debit || movement.credit || opening ? closingSigned : fallbackClosing;
    return {
      ledgerId: ledger.id,
      ledgerName: ledger.name,
      group: ledger.group_name,
      openingBalance: opening,
      debit: movement.debit,
      credit: movement.credit,
      closingBalance: closing,
      nature: closing >= 0 ? "Debit" : "Credit"
    };
  }).sort((a, b) => a.group.localeCompare(b.group) || a.ledgerName.localeCompare(b.ledgerName));
}

function buildStatementRows(rows: TrialGridRow[], ledgerMap: Map<string, Ledger>): StatementGridRow[] {
  return rows.map((row) => {
    const ledger = ledgerMap.get(row.ledgerId);
    return {
      section: statementSection(ledger),
      group: row.group,
      ledgerId: row.ledgerId,
      ledgerName: row.ledgerName,
      closingBalance: Math.abs(row.closingBalance),
      nature: row.nature
    };
  });
}

function buildLedgerStatementRows(vouchers: Voucher[], selectedLedgerId: string): LedgerStatementRow[] {
  if (!selectedLedgerId) return [];
  let balance = 0;
  return vouchers
    .flatMap((voucher) => voucher.lines
      .filter((line) => line.ledger_id === selectedLedgerId)
      .map((line) => {
        balance += Number(line.debit) - Number(line.credit);
        return {
          voucherId: voucher.id,
          date: voucher.voucher_date,
          voucherNo: voucher.voucher_number,
          voucherType: title(voucher.voucher_type),
          particulars: voucher.lines.filter((item) => item.ledger_id !== selectedLedgerId).map((item) => item.ledger_name).join(", ") || voucher.narration || "-",
          debit: Number(line.debit),
          credit: Number(line.credit),
          balance
        };
      }))
    .sort((a, b) => a.date.localeCompare(b.date) || a.voucherNo.localeCompare(b.voucherNo));
}

function buildDayBookRows(vouchers: Voucher[]): DayBookRow[] {
  return vouchers.map((voucher) => {
    const debit = sum(voucher.lines.map((line) => Number(line.debit)));
    const credit = sum(voucher.lines.map((line) => Number(line.credit)));
    const partyLine = voucher.lines.find((line) => !["Cash", "Bank"].includes(line.ledger_name)) ?? voucher.lines[0];
    return {
      voucherId: voucher.id,
      date: voucher.voucher_date,
      voucherNo: voucher.voucher_number,
      type: title(voucher.voucher_type),
      party: partyLine?.ledger_name ?? "-",
      narration: voucher.narration ?? "-",
      debit,
      credit,
      amount: Math.max(debit, credit)
    };
  }).sort((a, b) => b.date.localeCompare(a.date) || b.voucherNo.localeCompare(a.voucherNo));
}

function buildRegisterRows(invoices: Invoice[], ledgerMap: Map<string, Ledger>): RegisterRow[] {
  return invoices.map((invoice) => {
    const party = ledgerMap.get(invoice.party_ledger_id);
    return {
      invoiceId: invoice.id,
      voucherId: invoice.voucher_id,
      date: invoice.invoice_date,
      invoiceNo: invoice.invoice_number,
      party: invoice.party_ledger_name ?? party?.name ?? "-",
      gstin: party?.gstin ?? "",
      taxable: Number(invoice.taxable_value),
      cgst: Number(invoice.cgst_amount),
      sgst: Number(invoice.sgst_amount),
      igst: Number(invoice.igst_amount),
      total: Number(invoice.total_amount),
      status: invoice.voucher_id ? "Posted" : "Draft"
    };
  }).sort((a, b) => b.date.localeCompare(a.date) || b.invoiceNo.localeCompare(a.invoiceNo));
}

function movementsByLedger(vouchers: Voucher[]) {
  const map = new Map<string, { debit: number; credit: number }>();
  vouchers.forEach((voucher) => {
    voucher.lines.forEach((line) => {
      const current = map.get(line.ledger_id) ?? { debit: 0, credit: 0 };
      current.debit += Number(line.debit);
      current.credit += Number(line.credit);
      map.set(line.ledger_id, current);
    });
  });
  return map;
}

function registerColumns(label: "Customer" | "Vendor"): GridColumn<RegisterRow>[] {
  return [
    { key: "date", label: "Date" },
    { key: "invoiceNo", label: label === "Customer" ? "Invoice No" : "Bill No" },
    { key: "party", label },
    { key: "gstin", label: "GSTIN" },
    { key: "taxable", label: "Taxable", align: "right", render: (row) => formatMoney(row.taxable) },
    { key: "cgst", label: "CGST", align: "right", render: (row) => formatMoney(row.cgst) },
    { key: "sgst", label: "SGST", align: "right", render: (row) => formatMoney(row.sgst) },
    { key: "igst", label: "IGST", align: "right", render: (row) => formatMoney(row.igst) },
    { key: "total", label: "Total", align: "right", render: (row) => formatMoney(row.total) },
    { key: "status", label: "Status" }
  ];
}

function registerTotals(rows: RegisterRow[]) {
  return {
    label: "Register Totals",
    debit: sum(rows.map((row) => row.taxable)),
    credit: sum(rows.map((row) => row.cgst + row.sgst + row.igst)),
    amount: sum(rows.map((row) => row.total))
  };
}

function matchesLedgerFilters(
  ledgerIdValue: string,
  ledgerName: string,
  ledgerMap: Map<string, Ledger>,
  search: string,
  selectedLedgerId: string,
  selectedGroupId: string
) {
  if (selectedLedgerId && ledgerIdValue !== selectedLedgerId) return false;
  if (selectedGroupId && ledgerMap.get(ledgerIdValue)?.ledger_group_id !== selectedGroupId) return false;
  return !search.trim() || ledgerName.toLowerCase().includes(search.trim().toLowerCase());
}

function statementSection(ledger: Ledger | undefined) {
  if (!ledger) return "Other";
  if (ledger.account_nature === "asset") return "Assets";
  if (ledger.account_nature === "liability") return "Liabilities";
  if (ledger.account_nature === "equity") return "Equity";
  if (ledger.account_nature === "income") return "Income";
  if (ledger.category === "direct_expense" || ledger.category === "purchase") return "Direct Expenses";
  if (ledger.account_nature === "expense") return "Indirect Expenses";
  return "Other";
}

function signedOpeningBalance(ledger: Ledger) {
  const value = Number(ledger.opening_balance);
  return ledger.opening_balance_type === "cr" ? -value : value;
}

function isDateInRange(value: string, fromDate: string, toDate: string) {
  if (fromDate && value < fromDate) return false;
  if (toDate && value > toDate) return false;
  return true;
}

function balanceSheetTotal(rows: StatementGridRow[]) {
  return sum(rows.filter((row) => ["Assets", "Liabilities", "Equity"].includes(row.section)).map((row) => row.closingBalance));
}

function grossProfit(rows: StatementGridRow[]) {
  const income = sum(rows.filter((row) => row.section === "Income").map((row) => row.closingBalance));
  const directExpenses = sum(rows.filter((row) => row.section === "Direct Expenses").map((row) => row.closingBalance));
  return income - directExpenses;
}

function exportCsv<T extends Record<string, unknown>>(fileName: string, columns: GridColumn<T>[], rows: T[]) {
  const csv = [
    columns.map((column) => csvCell(column.label)).join(","),
    ...rows.map((row) => columns.map((column) => csvCell(column.render ? column.render(row) : String(row[column.key] ?? ""))).join(","))
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${fileName}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

function printReport<T extends Record<string, unknown>>(heading: string, columns: GridColumn<T>[], rows: T[], totals?: { label: string; debit?: number; credit?: number; amount?: number }) {
  const printable = window.open("", "_blank", "noopener,noreferrer,width=1100,height=800");
  if (!printable) return;
  const tableRows = rows.map((row) => `<tr>${columns.map((column) => `<td>${escapeHtml(column.render ? column.render(row) : String(row[column.key] ?? ""))}</td>`).join("")}</tr>`).join("");
  const totalRow = totals ? `<tr class="total"><td colspan="${Math.max(columns.length - 3, 1)}">${escapeHtml(totals.label)}</td><td>${totals.debit !== undefined ? formatMoney(totals.debit) : ""}</td><td>${totals.credit !== undefined ? formatMoney(totals.credit) : ""}</td><td>${totals.amount !== undefined ? formatMoney(totals.amount) : ""}</td></tr>` : "";
  printable.document.write(`
    <html>
      <head>
        <title>${escapeHtml(heading)}</title>
        <style>
          body { font-family: Arial, sans-serif; color: #111827; padding: 24px; }
          h1 { font-size: 20px; margin-bottom: 16px; }
          table { border-collapse: collapse; width: 100%; font-size: 12px; }
          th, td { border: 1px solid #d1d5db; padding: 8px; text-align: left; }
          th { background: #f3f4f6; }
          .total td { font-weight: 700; background: #f9fafb; }
        </style>
      </head>
      <body>
        <h1>${escapeHtml(heading)} - ABHAY Accounting OS</h1>
        <table>
          <thead><tr>${columns.map((column) => `<th>${escapeHtml(column.label)}</th>`).join("")}</tr></thead>
          <tbody>${tableRows || `<tr><td colspan="${columns.length}">No rows found.</td></tr>`}</tbody>
          ${totalRow ? `<tfoot>${totalRow}</tfoot>` : ""}
        </table>
      </body>
    </html>
  `);
  printable.document.close();
  printable.focus();
  printable.print();
}

function csvCell(value: string) {
  return `"${value.replace(/"/g, '""')}"`;
}

function rowKey(row: Record<string, unknown>, index: number) {
  return String(row.id ?? row.ledgerId ?? row.voucherId ?? row.invoiceId ?? `${index}`);
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + (Number.isFinite(value) ? value : 0), 0);
}

function formatMoney(value: MoneyValue) {
  const amount = Number(value ?? 0);
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number.isFinite(amount) ? amount : 0);
}

function title(value: string) {
  return value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}
