import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { createServerClient } from "@supabase/ssr";
import { publicEnv } from "@/lib/config";

const fallbackApiUrl = "https://abhay-api-x027.onrender.com";

export async function GET(request: NextRequest) {
  const companyId = request.nextUrl.searchParams.get("company_id") ?? request.nextUrl.searchParams.get("companyId");
  const invoiceId = request.nextUrl.searchParams.get("invoice_id") ?? request.nextUrl.searchParams.get("invoiceId");

  if (!companyId || !invoiceId) {
    return NextResponse.json({ detail: "company_id and invoice_id are required." }, { status: 400 });
  }

  const accessToken = await getSupabaseAccessToken();
  if (!accessToken) {
    return NextResponse.json({ detail: "Please sign in to download invoice PDF." }, { status: 401 });
  }

  const apiUrl = (process.env.ABHAY_API_URL || process.env.NEXT_PUBLIC_API_URL || fallbackApiUrl).replace(/\/$/, "");
  try {
    const response = await fetch(`${apiUrl}/companies/${companyId}/invoices/${invoiceId}/pdf`, {
      method: "GET",
      cache: "no-store",
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    });

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        return NextResponse.json({ detail: "You do not have access to this invoice PDF." }, { status: response.status });
      }
      return NextResponse.json({ detail: "Invoice PDF could not be generated." }, { status: response.status });
    }

    const filename = response.headers.get("content-disposition") ?? 'attachment; filename="abhay-invoice.pdf"';
    return new NextResponse(await response.arrayBuffer(), {
      status: 200,
      headers: {
        "Content-Type": response.headers.get("content-type") ?? "application/pdf",
        "Content-Disposition": filename
      }
    });
  } catch {
    return NextResponse.json({ detail: "ABHAY invoice PDF is temporarily unavailable." }, { status: 502 });
  }
}

async function getSupabaseAccessToken() {
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
    data: { session }
  } = await supabase.auth.getSession();
  if (session?.access_token) {
    return session.access_token;
  }
  if (
    cookieStore.get("abhay_client_demo")?.value === "true" ||
    (
      process.env.NEXT_PUBLIC_ALPHA_DEMO_MODE === "true" &&
      cookieStore.get("abhay_alpha_demo")?.value === "true"
    )
  ) {
    return "abhay-local-demo-token";
  }
  return null;
}
