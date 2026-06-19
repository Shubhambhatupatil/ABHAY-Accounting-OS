import { NextRequest, NextResponse } from "next/server";

const fallbackApiUrl = "https://abhay-api-x027.onrender.com";

export async function GET(request: NextRequest) {
  return proxyGstrCsv(request, "gstr1.csv", "gstr1-draft.csv");
}

async function proxyGstrCsv(request: NextRequest, endpoint: string, filename: string) {
  const companyId = request.nextUrl.searchParams.get("company_id") ?? request.nextUrl.searchParams.get("companyId");
  if (!companyId) {
    return NextResponse.json({ detail: "company_id is required." }, { status: 400 });
  }

  const apiUrl = (process.env.ABHAY_API_URL || process.env.NEXT_PUBLIC_API_URL || fallbackApiUrl).replace(/\/$/, "");
  try {
    const response = await fetch(`${apiUrl}/companies/${companyId}/reports/${endpoint}`, {
      method: "GET",
      cache: "no-store",
      headers: {
        Authorization: request.headers.get("authorization") ?? ""
      }
    });

    if (!response.ok) {
      const detail = await response.text().catch(() => "CSV export failed.");
      return NextResponse.json({ detail }, { status: response.status });
    }

    return new NextResponse(await response.text(), {
      status: 200,
      headers: {
        "Content-Type": "text/csv",
        "Content-Disposition": `attachment; filename="${filename}"`
      }
    });
  } catch {
    return NextResponse.json({ detail: "ABHAY CSV export is temporarily unavailable." }, { status: 502 });
  }
}
