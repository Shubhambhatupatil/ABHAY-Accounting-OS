import { NextResponse } from "next/server";

const planAmounts: Record<string, number> = {
  starter: 99900,
  business: 299900,
  pro: 499900
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

  const body = (await request.json().catch(() => ({}))) as { plan?: string };
  const plan = body.plan ?? "";
  const amount = planAmounts[plan];

  if (!amount) {
    return NextResponse.json({ detail: "Unsupported subscription plan." }, { status: 400 });
  }

  const response = await fetch("https://api.razorpay.com/v1/orders", {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`${keyId}:${keySecret}`).toString("base64")}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      amount,
      currency: "INR",
      receipt: `abhay_${plan}_${Date.now()}`,
      notes: {
        product: "ABHAY Accounting OS",
        plan
      }
    })
  });

  if (!response.ok) {
    return NextResponse.json(
      { detail: "Unable to create Razorpay order." },
      { status: response.status }
    );
  }

  return NextResponse.json(await response.json());
}
