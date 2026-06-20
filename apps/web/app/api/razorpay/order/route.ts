import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { createServerClient } from "@supabase/ssr";
import { publicEnv } from "@/lib/config";

const planConfig: Record<string, { amount: number; name: string }> = {
  starter: { amount: 99900, name: "Starter" },
  business: { amount: 299900, name: "Business" },
  pro: { amount: 499900, name: "Pro" }
};

export async function POST(request: Request) {
  const keyId = process.env.NEXT_PUBLIC_RAZORPAY_KEY_ID;
  const keySecret = process.env.RAZORPAY_KEY_SECRET;

  if (!keyId || !keySecret) {
    return NextResponse.json(
      { detail: "Razorpay is not configured for this environment." },
      { status: 503 }
    );
  }

  const user = await getAuthenticatedUser();
  if (!user) {
    return NextResponse.json({ detail: "Please sign in before starting payment." }, { status: 401 });
  }

  const body = (await request.json().catch(() => ({}))) as { plan?: string; plan_name?: string };
  const plan = normalizePlan(body.plan_name ?? body.plan ?? "");
  const selectedPlan = planConfig[plan];

  if (!selectedPlan) {
    return NextResponse.json({ detail: "Unsupported subscription plan." }, { status: 400 });
  }

  try {
    const response = await fetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: {
        Authorization: `Basic ${Buffer.from(`${keyId}:${keySecret}`).toString("base64")}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        amount: selectedPlan.amount,
        currency: "INR",
        receipt: `abhay_${plan}_${Date.now()}`,
        notes: {
          product: "ABHAY Accounting OS",
          plan,
          user_id: user.id
        }
      })
    });

    if (!response.ok) {
      return NextResponse.json(
        { detail: "Unable to create Razorpay order." },
        { status: response.status }
      );
    }

    const order = await response.json();
    return NextResponse.json({
      id: order.id,
      amount: order.amount,
      currency: order.currency,
      plan_name: selectedPlan.name
    });
  } catch {
    return NextResponse.json({ detail: "Payment gateway is temporarily unavailable." }, { status: 502 });
  }
}

function normalizePlan(planName: string) {
  const normalized = planName.trim().toLowerCase();
  if (normalized.includes("starter")) return "starter";
  if (normalized.includes("business")) return "business";
  if (normalized.includes("pro")) return "pro";
  return normalized;
}

async function getAuthenticatedUser() {
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
  return user;
}
