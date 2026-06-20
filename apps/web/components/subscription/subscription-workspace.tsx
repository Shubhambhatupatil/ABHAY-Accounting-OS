"use client";

import { CheckCircle2, CreditCard, Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { publicEnv } from "@/lib/config";
import {
  daysRemaining,
  subscriptionPlans,
  SubscriptionPlan
} from "@/lib/subscription";
import { SubscriptionStatusStrip, useSubscriptionState } from "@/components/subscription/subscription-gate";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { activateSubscriptionPlan, markSubscriptionPaymentPending } from "@/lib/api/subscriptions";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";

type RazorpayInstance = {
  open: () => void;
};

type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayInstance;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

export function SubscriptionWorkspace() {
  const { subscription, setSubscription } = useSubscriptionState();
  const supabase = createSupabaseBrowserClient();
  const [status, setStatus] = useState("Choose a plan to activate ABHAY for pilot usage.");
  const [isBusy, setIsBusy] = useState(false);
  const razorpayConfigured = Boolean(publicEnv.NEXT_PUBLIC_RAZORPAY_KEY_ID);

  async function startCheckout(plan: SubscriptionPlan) {
    if (plan === "trial") {
      const next = await activateSubscriptionPlan(supabase, "trial");
      setSubscription(next);
      if (next.status === "expired") {
        setStatus("Your Free Trial has expired. Choose a paid plan to continue.");
      } else {
        setStatus(`Free Trial active: ${daysRemaining(next)} days left.`);
      }
      return;
    }

    if (plan === "enterprise") {
      window.location.href = "mailto:contact@anvritai.com?subject=ABHAY%20Enterprise%20Plan";
      return;
    }

    setIsBusy(true);
    try {
      if (!razorpayConfigured) {
        setSubscription(await markSubscriptionPaymentPending(supabase, plan));
        setStatus("Razorpay publishable key is missing. Payment marked pending until gateway is configured.");
        return;
      }

      await loadRazorpayScript();
      const orderResponse = await fetch("/api/razorpay/order", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ plan })
      });

      if (!orderResponse.ok) {
        const error = await orderResponse.json().catch(() => ({ detail: "Payment order failed." }));
        setSubscription(await markSubscriptionPaymentPending(supabase, plan));
        setStatus(typeof error.detail === "string" ? error.detail : "Payment order failed.");
        return;
      }

      const order = (await orderResponse.json()) as { id: string; amount: number; currency: string };
      const checkout = new window.Razorpay!({
        key: publicEnv.NEXT_PUBLIC_RAZORPAY_KEY_ID,
        amount: order.amount,
        currency: order.currency,
        name: "ABHAY Accounting OS",
        description: `ABHAY ${plan} subscription`,
        order_id: order.id,
        theme: { color: "#f97316" },
        handler: (response: { razorpay_payment_id?: string }) => {
          void activateSubscriptionPlan(supabase, plan, response.razorpay_payment_id, order.amount).then((next) => {
            setSubscription(next);
            setStatus("Payment captured. Subscription activated.");
          }).catch((error: unknown) => {
            setStatus(error instanceof Error ? error.message : "Payment captured, but subscription sync failed.");
          });
        },
        modal: {
          ondismiss: () => {
            void markSubscriptionPaymentPending(supabase, plan).then(setSubscription).catch(() => undefined);
            setStatus("Payment pending. You can retry checkout anytime.");
          }
        }
      });
      checkout.open();
    } catch (error) {
      await markSubscriptionPaymentPending(supabase, plan).then(setSubscription).catch(() => undefined);
      setStatus(error instanceof Error ? error.message : "Payment could not start.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(0,0,0,0.28)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-3">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
                <CreditCard size={22} aria-hidden="true" />
              </span>
              <div>
                <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">Subscription / Billing</span>
                <h1 className="text-2xl font-semibold sm:text-3xl">Pilot-ready access control</h1>
                <p className="mt-1 text-sm text-white/70">
                  New users start on a 14-day trial. Paid plans unlock AI extraction, exports, invoice scale, and advanced ledger work.
                </p>
              </div>
            </div>
            <div className="rounded-2xl border border-white/20 bg-white/10 px-4 py-3 text-sm text-white/75 backdrop-blur">
              Razorpay key: {razorpayConfigured ? "Configured" : "Missing"}
            </div>
          </div>
        </header>

        <SubscriptionStatusStrip subscription={subscription} />

        <section className="grid gap-4 lg:grid-cols-5">
          {subscriptionPlans.map((plan) => (
            <article
              key={plan.id}
              className={cn(
                "glass-card float-card relative flex min-h-[430px] flex-col p-5",
                plan.id === "business" && "border-[#FF6B00]/60 shadow-[0_24px_80px_rgba(0,229,255,0.10)]",
                plan.id === "pro" && "border-[#FFD700]/40",
                plan.id === "enterprise" && "border-[#FFD700]/40 bg-gradient-to-br from-[#111827] via-[#0F172A] to-[#332a08]/50"
              )}
            >
              {plan.id === "business" ? (
                <span className="absolute right-4 top-4 inline-flex h-7 items-center justify-center rounded-full border border-[#00E5FF]/30 bg-[#00E5FF]/10 px-3 text-xs font-semibold leading-none text-[#BFF7FF]">
                  Most Popular
                </span>
              ) : null}
              {plan.id === "pro" || plan.id === "enterprise" ? (
                <span className="absolute right-4 top-4 inline-flex h-7 items-center justify-center rounded-full border border-[#FFD700]/30 bg-[#FFD700]/10 px-3 text-xs font-semibold leading-none text-[#FFD700]">
                  Premium
                </span>
              ) : null}
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold text-white">{plan.name}</h2>
                  <p className="mt-1 min-h-12 pr-16 text-sm leading-6 text-[#A1A1AA]">{plan.summary}</p>
                </div>
                <ShieldCheck className={cn("text-[#FF6B00]", (plan.id === "pro" || plan.id === "enterprise") && "text-[#FFD700]", plan.id === "business" && "text-[#00E5FF]")} size={22} />
              </div>
              <p className="mt-5 text-2xl font-semibold text-white">{plan.price}</p>
              <p className="text-sm text-[#A1A1AA]">{plan.cadence}</p>
              <div className="mt-4 space-y-2">
                {plan.features.map((feature) => (
                  <p key={feature} className="flex items-center gap-2 text-sm text-[#A1A1AA]">
                    <CheckCircle2 className="text-[#14B8A6]" size={16} />
                    {feature}
                  </p>
                ))}
              </div>
              <Button
                className={cn(
                  "mt-auto h-11 w-full",
                  (plan.id === "pro" || plan.id === "enterprise") && "from-[#FFD700] to-[#FDBA74] text-[#050816] shadow-[0_14px_34px_rgba(255,215,0,0.18)]"
                )}
                type="button"
                onClick={() => void startCheckout(plan.id)}
                disabled={isBusy}
              >
                {isBusy ? <Loader2 className="animate-spin" size={17} /> : <CreditCard size={17} />}
                {plan.id === "enterprise" ? "Contact Sales" : plan.id === "trial" ? "Start Trial" : "Pay with Razorpay"}
              </Button>
            </article>
          ))}
        </section>

        <section className="grid gap-4 lg:grid-cols-3">
          <div className="glass-card p-5">
            <h2 className="text-base font-semibold text-white">Current Access</h2>
            <p className="mt-3 text-sm leading-6 text-white/60">
              Status: {subscription?.status ?? "trialing"} · Plan: {subscription?.plan ?? "trial"} · Trial days left: {daysRemaining(subscription)}
            </p>
          </div>
          <div className="glass-card p-5">
            <h2 className="text-base font-semibold text-white">Trial Limits</h2>
            <p className="mt-3 text-sm leading-6 text-white/60">
              Free Trial allows 10 invoice uploads, basic dashboard, and basic reports. Paid plans unlock AI extraction, exports, and advanced ledger features.
            </p>
          </div>
          <div className="glass-card p-5">
            <h2 className="text-base font-semibold text-white">Gateway Security</h2>
            <p className="mt-3 text-sm leading-6 text-white/60">
              Frontend uses only NEXT_PUBLIC_RAZORPAY_KEY_ID. RAZORPAY_KEY_SECRET is read only by the secure server order route.
            </p>
          </div>
        </section>

        <p className="empty-state">{status}</p>
      </section>
    </main>
  );
}

function loadRazorpayScript() {
  return new Promise<void>((resolve, reject) => {
    if (window.Razorpay) {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Razorpay checkout script could not load."));
    document.body.appendChild(script);
  });
}
