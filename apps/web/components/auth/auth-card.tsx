"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { Loader2, LogIn, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { verifyApiSession } from "@/lib/api/auth";
import { isAlphaDemoModeEnabled, startLocalDemoSession } from "@/lib/auth/demo-auth";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";
import { getAuthCallbackUrl } from "@/lib/config";

type AuthMode = "login" | "signup";
const AUTH_NOTICE_KEY = "abhay_auth_notice";
export const SIGNUP_FIELD_LABELS = ["Full Name", "Business Name", "Email", "Password", "Confirm Password"] as const;
export const AUTH_ACTION_LABELS = ["Login", "Create account", "Continue in Alpha Demo Mode"] as const;

export function AuthCard({ mode }: Readonly<{ mode: AuthMode }>) {
  const router = useRouter();
  const supabase = createSupabaseBrowserClient();
  const [fullName, setFullName] = useState("");
  const [businessName, setBusinessName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [canUseLocalDemo, setCanUseLocalDemo] = useState(false);
  const isSignup = mode === "signup";
  const alphaDemoMode = isAlphaDemoModeEnabled();

  useEffect(() => {
    setCanUseLocalDemo(alphaDemoMode);
    const notice = window.sessionStorage.getItem(AUTH_NOTICE_KEY);
    if (notice) {
      setSuccess(notice);
      window.sessionStorage.removeItem(AUTH_NOTICE_KEY);
    }
  }, [alphaDemoMode]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;
    setError(null);
    setSuccess(null);
    setIsSubmitting(true);
    const normalizedEmail = email.trim().toLowerCase();

    try {
      validateAuthForm(normalizedEmail, password, isSignup ? confirmPassword : undefined, isSignup ? fullName : undefined);
      if (isSignup) {
        const result = await supabase.auth.signUp({
          email: normalizedEmail,
          password,
          options: {
            emailRedirectTo: getAuthCallbackUrl(),
            data: {
              full_name: fullName.trim(),
              initial_company_name: businessName.trim() || undefined
            }
          }
        });
        if (result.error) {
          throw result.error;
        }
        if (result.data.user && result.data.user.identities?.length === 0) {
          throw new Error("user already registered");
        }
        const accessToken = result.data.session?.access_token;
        if (!accessToken) {
          setSuccess("Check your email to confirm your ABHAY account.");
          return;
        }
        await verifyBackendSessionIfAvailable(accessToken);
        setSuccess("Account created. Opening company onboarding...");
        router.push("/settings");
        router.refresh();
        return;
      }

      const result = await supabase.auth.signInWithPassword({ email: normalizedEmail, password });
      if (result.error) {
        throw result.error;
      }
      const accessToken = result.data.session?.access_token;
      if (accessToken) {
        await verifyBackendSessionIfAvailable(accessToken);
        const redirectPath = await getPostLoginPath();
        setSuccess(redirectPath === "/dashboard" ? "Login successful. Opening dashboard..." : "Login successful. Opening company setup...");
        router.push(redirectPath);
        router.refresh();
        return;
      }
      throw new Error("email not confirmed");
    } catch (caught) {
      console.error("ABHAY auth error", caught);
      setError(formatAuthError(caught, mode));
    } finally {
      setIsSubmitting(false);
    }
  }

  function continueInDemoMode() {
    if (isSubmitting) return;
    setError(null);
    setSuccess(null);
    if (!alphaDemoMode) {
      setError("Alpha Demo Mode is disabled in this environment.");
      return;
    }
    setIsSubmitting(true);
    startLocalDemoSession();
    setSuccess("Alpha Demo Mode active. Opening dashboard...");
    router.push("/dashboard");
    router.refresh();
    window.setTimeout(() => setIsSubmitting(false), 750);
  }

  async function resetPassword() {
    if (isSubmitting) return;
    setError(null);
    setSuccess(null);
    setIsSubmitting(true);
    try {
      const normalizedEmail = email.trim().toLowerCase();
      validateEmail(normalizedEmail);
      const result = await supabase.auth.resetPasswordForEmail(normalizedEmail, {
        redirectTo: getAuthCallbackUrl()
      });
      if (result.error) {
        throw result.error;
      }
      setSuccess("Password reset email sent if this email is registered.");
    } catch (caught) {
      console.error("ABHAY password reset error", caught);
      setError(formatPasswordResetError(caught));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function resendConfirmationEmail() {
    if (isSubmitting) return;
    setError(null);
    setSuccess(null);
    setIsSubmitting(true);
    try {
      const normalizedEmail = email.trim().toLowerCase();
      validateEmail(normalizedEmail);
      const result = await supabase.auth.resend({
        type: "signup",
        email: normalizedEmail,
        options: {
          emailRedirectTo: getAuthCallbackUrl()
        }
      });
      if (result.error) {
        throw result.error;
      }
      setSuccess("Confirmation email sent. Please check your inbox.");
    } catch (caught) {
      console.error("ABHAY confirmation resend error", caught);
      setError(formatResendConfirmationError(caught));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="glass-panel w-full max-w-sm p-5">
      <div className="mb-5">
        <span className="ai-badge mb-3">ABHAY Alpha v0.1 Auth Stable</span>
        <h2 className="text-xl font-semibold">{isSignup ? "Create your account" : "Sign in"}</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          {isSignup
            ? "Create your Alpha account. Email confirmation may be required by Supabase."
            : "Login with email/password, create an account, or continue in Alpha Demo Mode."}
        </p>
      </div>
      <div className="space-y-4">
        {isSignup ? (
          <>
            <label className="block space-y-2 text-sm font-medium">
              <span>Full Name</span>
              <Input value={fullName} onChange={(event) => setFullName(event.target.value)} autoComplete="name" required />
            </label>
            <label className="block space-y-2 text-sm font-medium">
              <span>Business Name</span>
              <Input value={businessName} onChange={(event) => setBusinessName(event.target.value)} autoComplete="organization" />
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
      {error ? <p className="mt-4 rounded-xl border border-destructive/40 bg-destructive/15 p-3 text-sm text-red-200">{error}</p> : null}
      {success ? <p className="mt-4 rounded-xl border border-emerald-300/30 bg-emerald-400/10 p-3 text-sm text-emerald-200">{success}</p> : null}
      <Button type="submit" className="mt-5 w-full" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="animate-spin" size={18} /> : isSignup ? <UserPlus size={18} /> : <LogIn size={18} />}
        {isSignup ? "Create account" : "Login"}
      </Button>
      {!isSignup ? (
        <Link
          className="mt-3 inline-flex h-10 w-full items-center justify-center rounded-xl border border-white/10 bg-white/[0.08] px-4 text-sm font-semibold text-white shadow-sm backdrop-blur transition duration-300 hover:-translate-y-0.5 hover:bg-white/[0.12] hover:shadow-md"
          href="/signup"
        >
          Create account
        </Link>
      ) : null}
      <Button type="button" className="mt-3 w-full" variant="secondary" disabled={isSubmitting || !canUseLocalDemo} onClick={continueInDemoMode}>
        Continue in Alpha Demo Mode
      </Button>
      {!canUseLocalDemo ? (
        <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
          Alpha Demo Mode is disabled in this environment.
        </p>
      ) : null}
      {alphaDemoMode ? (
        <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
          For Alpha testing, use Alpha Demo Mode. Production login will be hardened before paid launch.
        </p>
      ) : null}
      {!isSignup ? (
        <div className="mt-4 grid gap-2 text-center text-sm font-medium sm:grid-cols-2">
          <button
            type="button"
            className="text-primary"
            disabled={isSubmitting}
            onClick={resetPassword}
          >
            Forgot password?
          </button>
          <button
            type="button"
            className="text-primary"
            disabled={isSubmitting}
            onClick={resendConfirmationEmail}
          >
            Resend confirmation email
          </button>
        </div>
      ) : null}
      <p className="mt-5 text-center text-sm text-muted-foreground">
        {isSignup ? "Already have account?" : "New to ABHAY?"}{" "}
        <Link className="font-medium text-primary" href={isSignup ? "/login" : "/signup"}>
          {isSignup ? "Login" : "Create account"}
        </Link>
      </p>
    </form>
  );
}

function validateEmail(email: string) {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new Error("invalid email");
  }
}

function validateAuthForm(email: string, password: string, confirmPassword?: string, fullName?: string) {
  if (fullName !== undefined && !fullName.trim()) {
    throw new Error("full name required");
  }
  validateEmail(email);
  if (password.length < 8) {
    throw new Error("invalid password");
  }
  if (confirmPassword !== undefined && password !== confirmPassword) {
    throw new Error("passwords do not match");
  }
}

async function verifyBackendSessionIfAvailable(accessToken: string) {
  try {
    await verifyApiSession(accessToken);
  } catch (error) {
    console.warn("ABHAY backend verification unavailable", error);
  }
}

async function getPostLoginPath() {
  try {
    const supabase = createSupabaseBrowserClient();
    const { data, error } = await supabase
      .from("company_members")
      .select("id")
      .limit(1);

    if (error) {
      console.warn("ABHAY company membership check unavailable", error);
      return "/settings";
    }

    return data?.length ? "/dashboard" : "/settings";
  } catch (error) {
    console.warn("ABHAY company membership check unavailable", error);
    return "/settings";
  }
}

function formatAuthError(caught: unknown, mode: AuthMode) {
  const message = caught instanceof Error ? caught.message : "Authentication failed.";
  const normalized = message.toLowerCase();
  if (normalized.includes("rate limit") || normalized.includes("too many") || normalized.includes("email rate limit")) {
    return "Email service is rate limited. Please wait or use Alpha Demo Mode.";
  }
  if (
    normalized.includes("email signup") ||
    normalized.includes("email provider") ||
    normalized.includes("provider is disabled") ||
    normalized.includes("signup disabled") ||
    normalized.includes("signups not allowed")
  ) {
    return "Email signup is not enabled in Supabase.";
  }
  if (normalized.includes("already registered") || normalized.includes("already been registered") || normalized.includes("user already registered")) {
    return "This email may already be registered. Try login or reset password.";
  }
  if (normalized.includes("invalid email")) {
    return "Enter a valid email address.";
  }
  if (normalized.includes("full name required")) {
    return "Full Name is required.";
  }
  if (normalized.includes("passwords do not match")) {
    return "Passwords do not match.";
  }
  if (mode === "login" && normalized.includes("email not confirmed")) {
    return "Please confirm your email before signing in.";
  }
  if (mode === "login" && (normalized.includes("invalid login") || normalized.includes("invalid credentials"))) {
    return "Email or password is incorrect, or your email is not confirmed yet.";
  }
  if (normalized.includes("invalid login") || normalized.includes("invalid credentials") || normalized.includes("email not confirmed")) {
    return "Account created. Please confirm your email, or continue in Alpha Demo Mode for now.";
  }
  if (normalized.includes("password")) {
    return "Invalid password. Use at least 8 characters.";
  }
  return mode === "login"
    ? "Login failed. Check password, email confirmation, or use Alpha Demo Mode."
    : "Signup failed. Try login if this email already exists, or use Alpha Demo Mode.";
}

function formatResendConfirmationError(caught: unknown) {
  const message = caught instanceof Error ? caught.message : "Confirmation email could not be sent.";
  const normalized = message.toLowerCase();
  if (normalized.includes("rate limit") || normalized.includes("too many") || normalized.includes("email rate limit")) {
    return "Email service is rate limited. Please wait or use Alpha Demo Mode.";
  }
  if (normalized.includes("invalid email")) {
    return "Enter a valid email address.";
  }
  return "Confirmation email could not be sent. Check the email address or try Alpha Demo Mode.";
}

function formatPasswordResetError(caught: unknown) {
  const message = caught instanceof Error ? caught.message : "Password reset failed.";
  const normalized = message.toLowerCase();
  if (normalized.includes("rate limit") || normalized.includes("smtp") || normalized.includes("email")) {
    return "Password reset email is temporarily unavailable. Use Alpha Demo Mode or contact ANVRITAI.";
  }
  return "Password reset email is temporarily unavailable. Use Alpha Demo Mode or contact ANVRITAI.";
}
