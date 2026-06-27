import { useState } from "react";
import { Building2, Lock, ShieldCheck } from "lucide-react";
import { apiClient as client } from "../lib/apiClient.js";
import { Badge, BrandMark, Button, Field, StatusMessage, TrustLine } from "../components/ui.jsx";

export function LoginPage({ navigate }) {
  return (
    <AuthPage
      mode="login"
      title="Sign in to ABHAY"
      subtitle="Access your multi-company finance operating system."
      cta="Sign in"
      switchText="New to ABHAY?"
      switchAction="Start 14-day trial"
      onSwitch={() => navigate("/signup")}
    />
  );
}

export function SignupPage({ navigate }) {
  return (
    <AuthPage
      mode="signup"
      title="Start your 14-day trial"
      subtitle="Create your ABHAY workspace for AI-powered accounting operations."
      cta="Start 14-day trial"
      switchText="Already have an account?"
      switchAction="Login"
      onSwitch={() => navigate("/login")}
    />
  );
}

function AuthPage({ mode, title, subtitle, cta, switchText, switchAction, onSwitch }) {
  const [form, setForm] = useState({
    name: "",
    email: "",
    password: "",
    companyName: "",
    remember: true
  });
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setLoading(true);
    const result = mode === "login" ? await client.login(form) : await client.signup(form);
    setLoading(false);
    setMessage(result.ok ? "Welcome to ABHAY." : result.message);
  }

  return (
    <section className="mx-auto grid min-h-[calc(100vh-160px)] max-w-7xl items-center gap-10 px-4 py-12 sm:px-6 lg:grid-cols-[0.95fr_1.05fr] lg:px-8">
      <div>
        <Badge tone="cyan">Enterprise access</Badge>
        <h1 className="mt-5 text-4xl font-black tracking-tight sm:text-5xl">{title}</h1>
        <p className="mt-4 max-w-xl text-lg leading-8 text-abhay-muted">{subtitle}</p>
        <div className="mt-8 grid gap-3 text-sm text-abhay-muted">
          <TrustLine icon={Lock}>Secure workspace access for finance teams.</TrustLine>
          <TrustLine icon={Building2}>Built for multi-company accounting operations.</TrustLine>
          <TrustLine icon={ShieldCheck}>Designed for audit-ready, human-verified automation.</TrustLine>
        </div>
      </div>

      <form className="glass-panel rounded-lg p-5 sm:p-8" onSubmit={submit}>
        <div className="flex items-center gap-3">
          <BrandMark />
          <div>
            <h2 className="text-2xl font-bold">{mode === "login" ? "Login" : "Signup"}</h2>
            <p className="text-sm text-abhay-muted">ABHAY Accounting OS by ANVRITAI</p>
          </div>
        </div>
        <div className="mt-6 grid gap-4">
          {mode === "signup" ? (
            <>
              <Field label="Name" value={form.name} onChange={(name) => setForm({ ...form, name })} />
              <Field label="Company name" value={form.companyName} onChange={(companyName) => setForm({ ...form, companyName })} />
            </>
          ) : null}
          <Field label="Email" type="email" value={form.email} onChange={(email) => setForm({ ...form, email })} />
          <Field label="Password" type="password" value={form.password} onChange={(password) => setForm({ ...form, password })} />
        </div>
        {mode === "login" ? (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-abhay-muted">
            <label className="inline-flex cursor-pointer items-center gap-2">
              <input
                checked={form.remember}
                className="h-4 w-4 rounded border-white/20 accent-abhay-orange"
                type="checkbox"
                onChange={(event) => setForm({ ...form, remember: event.target.checked })}
              />
              Remember me
            </label>
            <button className="text-abhay-cyan transition hover:text-abhay-orange" type="button">Forgot password?</button>
          </div>
        ) : null}
        <Button className="mt-6 w-full" type="submit" disabled={loading}>
          {loading ? "Please wait..." : cta}
        </Button>
        {message ? <StatusMessage>{message}</StatusMessage> : null}
        <div className="mt-6 flex flex-wrap items-center justify-center gap-2 text-sm text-abhay-muted">
          <span>{switchText}</span>
          <button className="font-semibold text-abhay-cyan transition hover:text-abhay-orange" type="button" onClick={onSwitch}>
            {switchAction}
          </button>
        </div>
      </form>
    </section>
  );
}
