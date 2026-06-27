import { createHmac, timingSafeEqual } from "node:crypto";
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { publicEnv } from "@/lib/config";

const planConfig: Record<string, { amount: number; name: string }> = {
  starter: { amount: 99900, name: "Starter" },
  business: { amount: 299900, name: "Business" },
  pro: { amount: 499900, name: "Pro" }
};

type VerifyBody = {
  plan?: string;
  plan_name?: string;
  razorpay_order_id?: string;
  razorpay_payment_id?: string;
  razorpay_signature?: string;
};

export async function POST(request: Request) {
  const keySecret = process.env.RAZORPAY_KEY_SECRET;
  if (!keySecret) {
    return NextResponse.json({ detail: "Razorpay verification is not configured." }, { status: 503 });
  }

  const { supabase, user } = await getSupabaseUser();
  if (!user) {
    return NextResponse.json({ detail: "Please sign in before verifying payment." }, { status: 401 });
  }

  const body = (await request.json().catch(() => ({}))) as VerifyBody;
  const plan = normalizePlan(body.plan_name ?? body.plan ?? "");
  const selectedPlan = planConfig[plan];
  if (!selectedPlan) {
    return NextResponse.json({ detail: "Unsupported subscription plan." }, { status: 400 });
  }

  const orderId = body.razorpay_order_id ?? "";
  const paymentId = body.razorpay_payment_id ?? "";
  const signature = body.razorpay_signature ?? "";
  if (!orderId || !paymentId || !signature) {
    return NextResponse.json({ detail: "Payment verification data is incomplete." }, { status: 400 });
  }

  if (!isValidSignature(orderId, paymentId, signature, keySecret)) {
    return NextResponse.json({ detail: "Payment verification failed. Please retry checkout." }, { status: 400 });
  }

  const { data: existingPayment, error: duplicateCheckError } = await supabase
    .from("payments")
    .select("id,status")
    .eq("razorpay_payment_id", paymentId)
    .maybeSingle<{ id: string; status: string }>();
  if (duplicateCheckError) {
    if (process.env.NODE_ENV === "development") {
      console.warn("ABHAY duplicate payment check failed", duplicateCheckError);
    }
    return NextResponse.json({ detail: "Payment verification is temporarily unavailable." }, { status: 503 });
  }
  if (existingPayment) {
    return NextResponse.json({ detail: "This payment has already been processed." }, { status: 409 });
  }

  const now = new Date();
  const periodEnd = new Date(now.getTime() + 30 * 86_400_000);

  try {
    const { error: profileError } = await supabase
      .from("profiles")
      .upsert({ id: user.id, email: user.email ?? "" }, { onConflict: "id" });
    if (profileError) {
      throw profileError;
    }

    const { error: paymentError } = await supabase.from("payments").insert({
      user_id: user.id,
      razorpay_payment_id: paymentId,
      amount: selectedPlan.amount / 100,
      status: "paid"
    });
    if (paymentError) {
      throw paymentError;
    }

    const { data: current } = await supabase
      .from("subscriptions")
      .select("id")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle<{ id: string }>();

    const payload = {
      user_id: user.id,
      plan_name: selectedPlan.name,
      status: "active",
      active: true,
      trial_start: now.toISOString(),
      trial_end: periodEnd.toISOString(),
      current_period_start: now.toISOString(),
      current_period_end: periodEnd.toISOString()
    };

    const query = current?.id
      ? supabase.from("subscriptions").update(payload).eq("id", current.id)
      : supabase.from("subscriptions").insert(payload);

    const { error: subscriptionError } = await query;
    if (subscriptionError) {
      throw subscriptionError;
    }

    await supabase.from("audit_logs").insert({
      company_id: null,
      actor_id: user.id,
      action_type: "payment.success",
      entity_type: "payment",
      event_payload: {
        plan: selectedPlan.name,
        razorpay_order_id: orderId,
        razorpay_payment_id: paymentId,
        amount: selectedPlan.amount / 100
      }
    });

    return NextResponse.json({
      ok: true,
      plan_name: selectedPlan.name,
      status: "active",
      active: true,
      current_period_start: now.toISOString(),
      current_period_end: periodEnd.toISOString()
    });
  } catch (error) {
    if (process.env.NODE_ENV === "development") {
      console.warn("ABHAY payment sync failed", error);
    }
    return NextResponse.json(
      { detail: "Payment verified, but subscription could not be updated. Contact ANVRITAI support." },
      { status: 500 }
    );
  }
}

function isValidSignature(orderId: string, paymentId: string, signature: string, secret: string) {
  const expected = createHmac("sha256", secret).update(`${orderId}|${paymentId}`).digest("hex");
  const actualBuffer = Buffer.from(signature, "hex");
  const expectedBuffer = Buffer.from(expected, "hex");
  if (actualBuffer.length !== expectedBuffer.length) return false;
  return timingSafeEqual(actualBuffer, expectedBuffer);
}

function normalizePlan(planName: string) {
  const normalized = planName.trim().toLowerCase();
  if (normalized.includes("starter")) return "starter";
  if (normalized.includes("business")) return "business";
  if (normalized.includes("pro")) return "pro";
  return normalized;
}

async function getSupabaseUser() {
  const cookieStore = await cookies();
  const supabase = createServerClient(
    publicEnv.NEXT_PUBLIC_SUPABASE_URL,
    publicEnv.NEXT_PUBLIC_SUPABASE_ANON_KEY,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll(cookiesToSet: Array<{ name: string; value: string; options?: Parameters<typeof cookieStore.set>[2] }>) {
          cookiesToSet.forEach(({ name, value, options }) => {
            cookieStore.set(name, value, options);
          });
        }
      }
    }
  );
  const {
    data: { user }
  } = await supabase.auth.getUser();
  return { supabase, user };
}
