import { AuthCard } from "@/components/auth/auth-card";
import { AuthShell } from "@/components/auth/auth-shell";

export default function SignupPage() {
  return (
    <AuthShell
      title="Create the accounting command center for your company."
      subtitle="Set up secure Supabase authentication, company ownership, and role-ready access for your accounting team."
    >
      <AuthCard mode="signup" />
    </AuthShell>
  );
}

