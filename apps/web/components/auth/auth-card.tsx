"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { Building2, Loader2, LogIn, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { accountingApi } from "@/lib/api/accounting";
import { verifyApiSession } from "@/lib/api/auth";
import { ensureLocalDemoSession, isAlphaDemoFallbackAllowed, isAlphaDemoModeEnabled } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";

type AuthMode = "login" | "signup";

export function AuthCard({ mode }: Readonly<{ mode: AuthMode }>) {
  const router = useRouter();
  const supabase = createSupabaseBrowserClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [canUseLocalDemo, setCanUseLocalDemo] = useState(false);
  const isSignup = mode === "signup";
  const alphaDemoMode = isAlphaDemoModeEnabled();

  useEffect(() => {
    setCanUseLocalDemo(isAlphaDemoFallbackAllowed());
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      if (isSignup && password !== confirmPassword) {
        throw new Error("Passwords do not match.");
      }
      const result = isSignup
        ? await supabase.auth.signUp({
            email,
            password,
            options: {
              data: {
                full_name: fullName,
                initial_company_name: companyName
              }
            }
          })
        : await supabase.auth.signInWithPassword({ email, password });

      if (result.error) {
        throw result.error;
      }
      if (isSignup && result.data.user && result.data.user.identities?.length === 0) {
        throw new Error("Email already registered. Please sign in instead.");
      }

      let accessToken = result.data.session?.access_token;
      if (isSignup && !accessToken) {
        const loginResult = await supabase.auth.signInWithPassword({ email, password });
        if (loginResult.error) {
          throw loginResult.error;
        }
        accessToken = loginResult.data.session?.access_token;
      }
      if (accessToken) {
        await verifyApiSession(accessToken);
        if (isSignup && companyName.trim()) {
          await accountingApi.createCompany(accessToken, { legal_name: companyName.trim() });
        }
      }

      router.push("/dashboard");
      router.refresh();
    } catch (caught) {
      setError(formatAuthError(caught));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function continueInDemoMode() {
    setError(null);
    setIsSubmitting(true);
    try {
      const token = ensureLocalDemoSession();
      if (!token) {
        throw new Error("Alpha Demo Mode is not enabled for this deployment.");
      }
      await verifyApiSession(token);
      router.push("/dashboard");
      router.refresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Alpha Demo Mode is unavailable.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="glass-panel w-full max-w-sm p-5">
      <div className="mb-5">
        <span className="ai-badge mb-3">AI Accounting Alpha</span>
        <h2 className="text-xl font-semibold">{isSignup ? "Create your account" : "Sign in"}</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          {isSignup
            ? "Start with your company account and invite your accounting team later."
            : "Access your companies, roles, and accounting workspace."}
        </p>
      </div>
      <div className="space-y-4">
        {isSignup ? (
          <>
            <label className="block space-y-2 text-sm font-medium">
              <span>Full name</span>
              <Input value={fullName} onChange={(event) => setFullName(event.target.value)} required />
            </label>
            <label className="block space-y-2 text-sm font-medium">
              <span>Company name</span>
              <div className="relative">
                <Building2 className="pointer-events-none absolute left-3 top-3 text-muted-foreground" size={18} />
                <Input className="pl-10" value={companyName} onChange={(event) => setCompanyName(event.target.value)} required />
              </div>
            </label>
          </>
        ) : null}
        <label className="block space-y-2 text-sm font-medium">
          <span>Email</span>
          <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required />
        </label>
        <label className="block space-y-2 text-sm font-medium">
          <span>Password</span>
          <Input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={isSignup ? "new-password" : "current-password"}
            minLength={8}
            required
          />
        </label>
        {isSignup ? (
          <label className="block space-y-2 text-sm font-medium">
            <span>Confirm password</span>
            <Input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </label>
        ) : null}
      </div>
      {error ? <p className="mt-4 rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{error}</p> : null}
      <Button type="submit" className="mt-5 w-full" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="animate-spin" size={18} /> : isSignup ? <UserPlus size={18} /> : <LogIn size={18} />}
        {isSignup ? "Create account" : "Sign in"}
      </Button>
      {canUseLocalDemo ? (
        <Button type="button" className="mt-3 w-full" variant="secondary" disabled={isSubmitting} onClick={continueInDemoMode}>
          Continue in Alpha Demo Mode
        </Button>
      ) : null}
      {alphaDemoMode ? (
        <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
          Alpha Demo Mode is enabled for this live demo. Real Supabase login remains available.
        </p>
      ) : null}
      {!isSignup ? (
        <button
          type="button"
          className="mt-4 w-full text-center text-sm font-medium text-primary"
          onClick={() => setError("Password reset link is coming soon for Alpha. Use Alpha Demo Mode for the live walkthrough.")}
        >
          Forgot password?
        </button>
      ) : null}
      <p className="mt-5 text-center text-sm text-muted-foreground">
        {isSignup ? "Already have an account?" : "New to ABHAY?"}{" "}
        <Link className="font-medium text-primary" href={isSignup ? "/login" : "/signup"}>
          {isSignup ? "Sign in" : "Create account"}
        </Link>
      </p>
    </form>
  );
}

function formatAuthError(caught: unknown) {
  const message = caught instanceof Error ? caught.message : "Authentication failed.";
  const normalized = message.toLowerCase();
  if (normalized.includes("already registered") || normalized.includes("already been registered") || normalized.includes("user already registered")) {
    return "Email already registered. Please sign in instead.";
  }
  if (normalized.includes("invalid login") || normalized.includes("invalid credentials")) {
    return "Invalid email or password.";
  }
  if (normalized.includes("password")) {
    return "Invalid password. Use at least 8 characters.";
  }
  if (normalized.includes("email not confirmed") || normalized.includes("confirm")) {
    return "Email not confirmed. Please confirm your email or use Alpha Demo Mode.";
  }
  return message;
}
