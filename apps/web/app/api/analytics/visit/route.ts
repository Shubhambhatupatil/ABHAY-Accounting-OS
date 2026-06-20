import { createHash } from "node:crypto";
import { createClient } from "@supabase/supabase-js";
import { NextRequest, NextResponse } from "next/server";
import { publicEnv } from "@/lib/config";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  const body = await request.json().catch(() => ({}));
  const path = normalizeText(typeof body.path === "string" ? body.path : request.nextUrl.pathname, 300);
  const referrer = normalizeText(request.headers.get("referer") ?? "", 500);
  const userAgent = normalizeText(request.headers.get("user-agent") ?? "", 500);
  const ipHash = hashIp(getClientIp(request));

  if (isPlaceholderSupabase()) {
    return NextResponse.json({ ok: false, disabled: true });
  }

  try {
    const supabase = createClient(publicEnv.NEXT_PUBLIC_SUPABASE_URL, publicEnv.NEXT_PUBLIC_SUPABASE_ANON_KEY, {
      auth: { persistSession: false }
    });
    const { error } = await supabase.from("site_visits").insert({
      path,
      referrer: referrer || null,
      user_agent: userAgent || null,
      ip_hash: ipHash
    });
    if (error) {
      return NextResponse.json({ ok: false });
    }
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ ok: false });
  }
}

function getClientIp(request: NextRequest) {
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) {
    return forwardedFor.split(",")[0]?.trim() || "unknown";
  }
  return request.headers.get("x-real-ip") ?? "unknown";
}

function hashIp(ip: string) {
  const salt = process.env.ANALYTICS_SALT || process.env.NEXT_PUBLIC_APP_URL || "abhay-alpha-analytics";
  return createHash("sha256").update(`${salt}:${ip}`).digest("hex");
}

function normalizeText(value: string, maxLength: number) {
  return value.replace(/[\u0000-\u001F\u007F]/g, "").slice(0, maxLength);
}

function isPlaceholderSupabase() {
  return (
    publicEnv.NEXT_PUBLIC_SUPABASE_URL.includes("placeholder") ||
    publicEnv.NEXT_PUBLIC_SUPABASE_ANON_KEY.includes("placeholder")
  );
}
