"use client";

import type { SupabaseClient } from "@supabase/supabase-js";
import { getLocalDemoToken, LOCAL_DEMO_TOKEN } from "@/lib/auth/demo-auth";
import {
  createDemoSubscriptionState,
  mapPlanIdToName,
  mapSubscriptionRow,
  SubscriptionPlan,
  SubscriptionRow,
  SubscriptionState
} from "@/lib/subscription";

type AuthCapableSupabase = SupabaseClient & {
  auth: SupabaseClient["auth"];
};

export async function getOrCreateSubscription(supabase: AuthCapableSupabase): Promise<SubscriptionState | null> {
  if (getLocalDemoToken() === LOCAL_DEMO_TOKEN) {
    return createDemoSubscriptionState();
  }

  const {
    data: { user },
    error: userError
  } = await supabase.auth.getUser();
  if (userError || !user) return null;

  await supabase
    .from("profiles")
    .upsert({ id: user.id, email: user.email ?? "" }, { onConflict: "id" });

  const { data: existing, error: selectError } = await supabase
    .from("subscriptions")
    .select("id,user_id,plan_name,trial_start,trial_end,status,active,created_at")
    .eq("user_id", user.id)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle<SubscriptionRow>();

  if (selectError) {
    throw new Error("ABHAY subscription access is syncing. Please refresh in a moment.");
  }

  if (!existing) {
    return createTrialSubscription(supabase, user.id);
  }

  const mapped = mapSubscriptionRow(existing);
  if (mapped.status === "trialing" && new Date(mapped.trialEnd).getTime() < Date.now()) {
    const { data: expired, error: updateError } = await supabase
      .from("subscriptions")
      .update({ status: "expired", active: false })
      .eq("id", existing.id)
      .select("id,user_id,plan_name,trial_start,trial_end,status,active,created_at")
      .single<SubscriptionRow>();

    if (updateError) {
      throw new Error("ABHAY subscription access is syncing. Please refresh in a moment.");
    }
    return mapSubscriptionRow(expired);
  }

  return mapped;
}

export async function activateSubscriptionPlan(
  supabase: AuthCapableSupabase,
  plan: SubscriptionPlan,
  razorpayPaymentId?: string,
  amount = 0
) {
  const user = await requireUser(supabase);
  const planName = mapPlanIdToName(plan);
  const periodStart = new Date().toISOString();
  const current = await getOrCreateSubscription(supabase);

  if (plan === "trial" && current) {
    if (current.status === "expired" || new Date(current.trialEnd).getTime() < Date.now()) {
      return current;
    }
    if (current.status === "trialing") {
      return current;
    }
  }

  const periodEnd = new Date(Date.now() + (plan === "trial" ? 14 : 30) * 86_400_000).toISOString();

  if (razorpayPaymentId) {
    const { error: paymentError } = await supabase.from("payments").insert({
      user_id: user.id,
      razorpay_payment_id: razorpayPaymentId,
      amount,
      status: "paid"
    });
    if (paymentError) {
      throw new Error("Payment captured, but subscription sync failed. Contact ANVRITAI support.");
    }
  }

  const query = current?.id
    ? supabase
        .from("subscriptions")
        .update({
          plan_name: planName,
          status: plan === "trial" ? "trialing" : "active",
          active: true,
          trial_start: plan === "trial" ? periodStart : current.trialStartedAt,
          trial_end: periodEnd
        })
        .eq("id", current.id)
    : supabase
        .from("subscriptions")
        .insert({
          user_id: user.id,
          plan_name: planName,
          trial_start: periodStart,
          trial_end: periodEnd,
          status: plan === "trial" ? "trialing" : "active",
          active: true
        });

  const { data, error } = await query
    .select("id,user_id,plan_name,trial_start,trial_end,status,active,created_at")
    .single<SubscriptionRow>();

  if (error) {
    throw new Error("Subscription could not be updated. Please try again.");
  }

  return mapSubscriptionRow(data);
}

export async function markSubscriptionPaymentPending(supabase: AuthCapableSupabase, plan: SubscriptionPlan) {
  const user = await requireUser(supabase);
  const current = await getOrCreateSubscription(supabase);
  const planName = mapPlanIdToName(plan);
  const end = new Date(Date.now() + 30 * 86_400_000).toISOString();
  const query = current?.id
    ? supabase
        .from("subscriptions")
        .update({ plan_name: planName, status: "payment_pending", active: false })
        .eq("id", current.id)
    : supabase
        .from("subscriptions")
        .insert({
          user_id: user.id,
          plan_name: planName,
          trial_start: new Date().toISOString(),
          trial_end: end,
          status: "payment_pending",
          active: false
        });

  const { data, error } = await query
    .select("id,user_id,plan_name,trial_start,trial_end,status,active,created_at")
    .single<SubscriptionRow>();

  if (error) {
    throw new Error("Payment pending status could not be saved.");
  }
  return mapSubscriptionRow(data);
}

async function createTrialSubscription(supabase: AuthCapableSupabase, userId: string) {
  const trialStart = new Date();
  const trialEnd = new Date(trialStart.getTime() + 14 * 86_400_000);
  const { data, error } = await supabase
    .from("subscriptions")
    .insert({
      user_id: userId,
      plan_name: "Free Trial",
      trial_start: trialStart.toISOString(),
      trial_end: trialEnd.toISOString(),
      status: "trialing",
      active: true
    })
    .select("id,user_id,plan_name,trial_start,trial_end,status,active,created_at")
    .single<SubscriptionRow>();

  if (error) {
    throw new Error("Free Trial could not be created. Please try again.");
  }

  return mapSubscriptionRow(data);
}

async function requireUser(supabase: AuthCapableSupabase) {
  const {
    data: { user },
    error
  } = await supabase.auth.getUser();
  if (error || !user) {
    throw new Error("Please login to manage your subscription.");
  }
  return user;
}
