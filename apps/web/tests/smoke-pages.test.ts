import DashboardPage from "@/app/(app)/dashboard/page";
import LoginPage from "@/app/(auth)/login/page";
import SignupPage from "@/app/(auth)/signup/page";
import AiAccountantPage from "@/app/(app)/ai-accountant/page";
import BankReconciliationPage from "@/app/(app)/bank-reconciliation/page";
import FinancialIntelligencePage from "@/app/(app)/financial-intelligence/page";
import InvoicesPage from "@/app/(app)/invoices/page";
import AutomationCenterPage from "@/app/(app)/automation-center/page";
import CommandCenterPage from "@/app/(app)/command-center/page";
import ImportDataPage from "@/app/(app)/import-data/page";
import SubscriptionPage from "@/app/(app)/subscription/page";
import UploadInvoicePage from "@/app/(app)/upload-invoice/page";
import EntriesPage from "@/app/(app)/entries/page";
import ReportsPage from "@/app/(app)/reports/page";
import SettingsPage from "@/app/(app)/settings/page";
import AdminPage from "@/app/(app)/admin/page";
import LandingPage from "@/app/page";
import { LAST_COMPANY_KEY } from "@/components/accounting/accounting-workspace";
import { AUTH_ACTION_LABELS, AuthCard, SIGNUP_FIELD_LABELS } from "@/components/auth/auth-card";
import { ALPHA_DEMO_MODE_STORAGE_KEY, LOCAL_DEMO_STORAGE_KEY, LOCAL_DEMO_TOKEN } from "@/lib/auth/demo-auth";

const pages = [
  LoginPage,
  SignupPage,
  DashboardPage,
  AiAccountantPage,
  InvoicesPage,
  BankReconciliationPage,
  FinancialIntelligencePage,
  AutomationCenterPage,
  CommandCenterPage,
  ImportDataPage,
  SubscriptionPage,
  UploadInvoicePage,
  EntriesPage,
  ReportsPage,
  SettingsPage,
  AdminPage,
  LandingPage
];

pages.forEach((Page) => {
if (typeof Page !== "function") {
    throw new Error("Smoke page import failed");
  }
});

if (LAST_COMPANY_KEY !== "abhay.lastCompanyId") {
  throw new Error("Last selected company persistence key changed");
}

if (typeof AuthCard !== "function") {
  throw new Error("Auth card import failed");
}

if (ALPHA_DEMO_MODE_STORAGE_KEY !== "abhay_alpha_demo") {
  throw new Error("Alpha demo flag key changed");
}

if (LOCAL_DEMO_STORAGE_KEY !== "abhay_demo_token") {
  throw new Error("Alpha demo token key changed");
}

if (LOCAL_DEMO_TOKEN !== "abhay-local-demo-token") {
  throw new Error("Alpha demo token value changed");
}

if (!SIGNUP_FIELD_LABELS.includes("Full Name")) {
  throw new Error("Signup Full Name field missing");
}

if (!SIGNUP_FIELD_LABELS.includes("Business Name")) {
  throw new Error("Signup Business Name field missing");
}

if (!AUTH_ACTION_LABELS.includes("Client Demo Mode")) {
  throw new Error("Client demo action missing");
}
