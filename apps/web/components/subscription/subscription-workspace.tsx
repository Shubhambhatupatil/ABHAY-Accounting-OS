"use client";

import { CheckCircle2, CreditCard, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { publicEnv } from "@/lib/config";

const plans = [
  {
    name: "Free Trial",
    price: "\u20b90 / 14 days",
    audience: "Alpha evaluation for owners and accounting teams",
    features: ["AI Workbench preview", "Demo company", "Import Data UI", "GST assistance"]
  },
  {
    name: "Starter",
    price: "\u20b9999/month",
    audience: "Small traders and early MSMEs",
    features: ["AI Workbench", "Demo company", "Core reports", "GST assistance"]
  },
  {
    name: "Professional",
    price: "\u20b91,499/month",
    audience: "Growing businesses with recurring accounting work",
    features: ["Everything in Starter", "Bank reconciliation", "Invoices", "Financial intelligence"]
  },
  {
    name: "Business",
    price: "\u20b92,999/month",
    audience: "Multi-company businesses and accounting teams",
    features: ["Multi-company workflows", "Access requests", "Automation center", "Review-ready AI suggestions"]
  }
];

const statuses = ["Active", "Trial", "Expired"];
const paymentStatuses = ["Pending", "Paid", "Failed"];

export function SubscriptionWorkspace() {
  const razorpayConfigured = Boolean(publicEnv.NEXT_PUBLIC_RAZORPAY_KEY_ID);

  return (
    <main className="abhay-page">
      <section className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="hero-grid rounded-3xl p-5 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:p-6">
          <div className="relative z-10 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-3">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
                <CreditCard size={22} aria-hidden="true" />
              </span>
              <div>
                <span className="ai-badge mb-2 border-white/20 bg-white/10 text-white">Alpha Billing Preview</span>
                <h1 className="text-2xl font-semibold sm:text-3xl">Subscription</h1>
                <p className="mt-1 text-sm text-white/80">Plans, access status, and payment readiness for ABHAY Alpha.</p>
              </div>
            </div>
            <div className="rounded-2xl border border-white/15 bg-white/10 px-4 py-3 text-sm text-white/80 backdrop-blur">
              Payment integration coming soon.
            </div>
          </div>
        </header>

        <section className="grid gap-4 lg:grid-cols-4">
          {plans.map((plan) => (
            <article key={plan.name} className="glass-card float-card flex flex-col p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold">{plan.name}</h2>
                  <p className="mt-1 text-sm text-muted-foreground">{plan.audience}</p>
                </div>
                <ShieldCheck className="text-primary" size={22} />
              </div>
              <p className="mt-5 text-2xl font-semibold text-slate-950">{plan.price}</p>
              <div className="mt-4 space-y-2">
                {plan.features.map((feature) => (
                  <p key={feature} className="flex items-center gap-2 text-sm text-muted-foreground">
                    <CheckCircle2 className="text-emerald-600" size={16} />
                    {feature}
                  </p>
                ))}
              </div>
              <Button className="mt-5" type="button" disabled>
                <CreditCard size={17} />
                Payment coming soon
              </Button>
            </article>
          ))}
        </section>

        <section className="grid gap-4 lg:grid-cols-2">
          <div className="glass-card p-5">
            <h2 className="text-base font-semibold">Subscription Status</h2>
            <div className="mt-4 flex flex-wrap gap-2">
              {statuses.map((status) => (
                <span key={status} className="rounded-full border border-orange-100 bg-orange-50 px-3 py-1 text-sm font-semibold text-orange-700">
                  {status}
                </span>
              ))}
            </div>
          </div>
          <div className="glass-card p-5">
            <h2 className="text-base font-semibold">Payment Status</h2>
            <div className="mt-4 flex flex-wrap gap-2">
              {paymentStatuses.map((status) => (
                <span key={status} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-sm font-semibold text-slate-700">
                  {status}
                </span>
              ))}
            </div>
          </div>
        </section>

        <p className="empty-state">
          Payment integration coming soon. Razorpay will use NEXT_PUBLIC_RAZORPAY_KEY_ID on the frontend; never expose a Razorpay secret key in the web app.
          {razorpayConfigured ? " Razorpay publishable key is configured." : " Razorpay publishable key is not configured yet."}
        </p>
      </section>
    </main>
  );
}
