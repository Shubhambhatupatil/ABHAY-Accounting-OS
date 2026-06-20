import { createServerClient } from "@supabase/ssr";
import { NextRequest, NextResponse } from "next/server";

const protectedPaths = ["/dashboard", "/upload-invoice", "/reports", "/ai-workbench", "/admin"];

export async function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname;
  if (!protectedPaths.some((path) => pathname === path || pathname.startsWith(`${path}/`))) {
    return NextResponse.next();
  }

  if (
    process.env.NEXT_PUBLIC_ALPHA_DEMO_MODE === "true" &&
    request.cookies.get("abhay_alpha_demo")?.value === "true" &&
    !isAdminPath(pathname)
  ) {
    return NextResponse.next();
  }

  const response = NextResponse.next({ request });
  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL ?? "https://placeholder.supabase.co",
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "placeholder_key",
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet: Array<{ name: string; value: string; options?: Parameters<typeof response.cookies.set>[2] }>) {
          cookiesToSet.forEach(({ name, value, options }) => {
            request.cookies.set(name, value);
            response.cookies.set(name, value, options);
          });
        }
      }
    }
  );

  const {
    data: { user }
  } = await supabase.auth.getUser();

  if (!user) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  const subscription = await getOrCreateServerSubscription(supabase, user.id, user.email ?? "");
  if (!subscription.active) {
    const subscriptionUrl = new URL("/subscription", request.url);
    subscriptionUrl.searchParams.set("reason", subscription.reason);
    return NextResponse.redirect(subscriptionUrl);
  }

  const hasCompany = await userHasCompanyMembership(supabase, user.id);
  if (!hasCompany) {
    const settingsUrl = new URL("/settings", request.url);
    settingsUrl.searchParams.set("onboarding", "company");
    return NextResponse.redirect(settingsUrl);
  }

  return response;
}

export const config = {
  matcher: ["/dashboard/:path*", "/upload-invoice/:path*", "/reports/:path*", "/ai-workbench/:path*", "/admin/:path*"]
};

function isAdminPath(pathname: string) {
  return pathname === "/admin" || pathname.startsWith("/admin/");
}

async function getOrCreateServerSubscription(
  supabase: ReturnType<typeof createServerClient>,
  userId: string,
  email: string
) {
  await supabase.from("profiles").upsert({ id: userId, email }, { onConflict: "id" });

  const { data: existing, error } = await supabase
    .from("subscriptions")
    .select("id,user_id,plan_name,trial_start,trial_end,status,active,created_at")
    .eq("user_id", userId)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle<{
      id: string;
      user_id: string;
      plan_name: string;
      trial_start: string;
      trial_end: string;
      status: string;
      active: boolean;
      created_at: string;
    }>();

  if (error) {
    return { active: false, reason: "syncing" };
  }

  if (!existing) {
    const trialStart = new Date();
    const trialEnd = new Date(trialStart.getTime() + 14 * 86_400_000);
    const { data: created, error: createError } = await supabase
      .from("subscriptions")
      .insert({
        user_id: userId,
        plan_name: "Free Trial",
        trial_start: trialStart.toISOString(),
        trial_end: trialEnd.toISOString(),
        status: "trialing",
        active: true
      })
      .select("id,trial_end,status,active")
      .single<{ id: string; trial_end: string; status: string; active: boolean }>();

    if (createError || !created) {
      return { active: false, reason: "syncing" };
    }
    return { active: true, reason: "trial" };
  }

  const trialEnded = new Date(existing.trial_end).getTime() < Date.now();
  if (existing.status === "trialing" && trialEnded) {
    await supabase
      .from("subscriptions")
      .update({ status: "expired", active: false })
      .eq("id", existing.id);
    return { active: false, reason: "trial_expired" };
  }

  if (existing.status === "active" && existing.active) {
    return { active: true, reason: "active" };
  }

  if (existing.status === "trialing" && existing.active && !trialEnded) {
    return { active: true, reason: "trial" };
  }

  return { active: false, reason: "inactive" };
}

async function userHasCompanyMembership(supabase: ReturnType<typeof createServerClient>, userId: string) {
  const { data, error } = await supabase
    .from("company_members")
    .select("id")
    .eq("user_id", userId)
    .limit(1);

  if (!error && data?.length) return true;

  const { data: profileData, error: profileError } = await supabase
    .from("company_members")
    .select("id")
    .eq("profile_id", userId)
    .limit(1);

  if (profileError) return false;
  return Boolean(profileData?.length);
}
