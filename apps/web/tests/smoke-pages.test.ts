import DashboardPage from "@/app/(app)/dashboard/page";
import AiAccountantPage from "@/app/(app)/ai-accountant/page";
import BankReconciliationPage from "@/app/(app)/bank-reconciliation/page";
import FinancialIntelligencePage from "@/app/(app)/financial-intelligence/page";
import InvoicesPage from "@/app/(app)/invoices/page";
import AutomationCenterPage from "@/app/(app)/automation-center/page";
import CommandCenterPage from "@/app/(app)/command-center/page";
import { LAST_COMPANY_KEY } from "@/components/accounting/accounting-workspace";

const pages = [
  DashboardPage,
  AiAccountantPage,
  InvoicesPage,
  BankReconciliationPage,
  FinancialIntelligencePage,
  AutomationCenterPage,
  CommandCenterPage
];

pages.forEach((Page) => {
if (typeof Page !== "function") {
    throw new Error("Smoke page import failed");
  }
});

if (LAST_COMPANY_KEY !== "abhay.lastCompanyId") {
  throw new Error("Last selected company persistence key changed");
}
