import { NextRequest, NextResponse } from "next/server";

const fallbackApiUrl = "https://abhay-api-x027.onrender.com";

export async function GET(request: NextRequest) {
  const companyId = request.nextUrl.searchParams.get("companyId");
  if (!companyId) {
    return NextResponse.json({ detail: "companyId is required." }, { status: 400 });
  }

  const apiUrl = (process.env.ABHAY_API_URL || process.env.NEXT_PUBLIC_API_URL || fallbackApiUrl).replace(/\/$/, "");
  try {
    const response = await fetch(`${apiUrl}/companies/${companyId}/ai-entry/owner-report`, {
      method: "GET",
      cache: "no-store",
      headers: {
        Authorization: request.headers.get("authorization") ?? ""
      }
    });
    const data = await response.json().catch(() => ({ detail: "Request failed." }));
    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch {
    return NextResponse.json({ detail: "ABHAY backend is temporarily unavailable." }, { status: 502 });
  }
}
