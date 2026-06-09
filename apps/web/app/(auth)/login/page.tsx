import { AuthCard } from "@/components/auth/auth-card";
import { AuthShell } from "@/components/auth/auth-shell";

export default function LoginPage() {
  return (
    <AuthShell
      title="Accounting work, verified before it posts."
      subtitle="Sign in to your ABHAY workspace and keep every ledger, voucher, GST liability, and report under company-level access control."
    >
      <AuthCard mode="login" />
    </AuthShell>
  );
}

