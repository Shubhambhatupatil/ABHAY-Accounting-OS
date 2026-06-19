"use client";

import Link from "next/link";
import { LockKeyhole, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  canUseAdvancedFeature,
  daysRemaining,
  isSubscriptionActive,
  SubscriptionState
} from "@/lib/subscription";
import { useEffect, useState } from "react";
import { getOrCreateSubscription } from "@/lib/api/subscriptions";
import { createSupabaseBrowserClient } from "@/lib/auth/supabase-browser";

type FeatureKey = "invoice_upload" | "ai_extraction" | "reports" | "export" | "advanced_ledger";

export function useSubscriptionState() {
  const [subscription, setSubscription] = useState<SubscriptionState | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const supabase = createSupabaseBrowserClient();
    getOrCreateSubscription(supabase)
      .then((row) => {
        if (active) setSubscription(row);
      })
      .catch((caught: unknown) => {
        if (!active) return;
        setError(caught instanceof Error ? caught.message : "Subscription access is syncing.");
      });
    return () => {
      active = false;
    };
  }, []);

  return { subscription, setSubscription, error };
}

export function SubscriptionStatusStrip({ subscription }: Readonly<{ subscription: SubscriptionState | null }>) {
  const active = isSubscriptionActive(subscription);
  return (
    <div className="glass-card flex flex-col gap-2 px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-2">
        <ShieldCheck className={active ? "text-[#14B8A6]" : "text-[#FFD700]"} size={17} />
        <span className="font-semibold text-white">
          {subscription?.status === "active" ? "Subscription active" : `Free Trial: ${daysRemaining(subscription)} days left`}
        </span>
        <span className="text-white/60">Plan: {subscription?.plan ?? "trial"}</span>
      </div>
      {!active ? (
        <Link className="text-sm font-semibold text-[#FDBA74]" href="/subscription">
          Choose plan
        </Link>
      ) : null}
    </div>
  );
}

export function SubscriptionGate({
  subscription,
  feature,
  title,
  children
}: Readonly<{
  subscription: SubscriptionState | null;
  feature: FeatureKey;
  title: string;
  children: React.ReactNode;
}>) {
  if (canUseAdvancedFeature(subscription, feature)) {
    return <>{children}</>;
  }

  return (
    <div className="glass-panel p-5 text-center">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-[#FF6B00]/20 text-[#FDBA74]">
        <LockKeyhole size={22} />
      </span>
      <h2 className="mt-4 text-lg font-semibold text-white">{title}</h2>
      <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-white/60">
        Your Free Trial includes basic dashboard, basic reports, and 10 invoice uploads. Upgrade to use AI extraction,
        exports, advanced ledger workflows, and higher limits for client-ready operations.
      </p>
      <Button className="mt-4" type="button" onClick={() => window.location.assign("/subscription")}>
        View Subscription Plans
      </Button>
    </div>
  );
}
