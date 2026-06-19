import { NextResponse } from "next/server";

const fallbackApiUrl = "https://abhay-api-x027.onrender.com";

function apiBaseUrl() {
  return (process.env.ABHAY_API_URL || process.env.NEXT_PUBLIC_API_URL || fallbackApiUrl).replace(/\/$/, "");
}

function authorizationHeader(request: Request) {
  return request.headers.get("authorization") ?? "";
}

export async function GET(request: Request) {
  try {
    const response = await fetch(`${apiBaseUrl()}/companies`, {
      method: "GET",
      cache: "no-store",
      headers: {
        Authorization: authorizationHeader(request)
      }
    });
    const data = await response.json().catch(() => ({ detail: "Unable to load companies." }));
    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch {
    return NextResponse.json({ detail: "ABHAY backend is temporarily unavailable." }, { status: 502 });
  }
}

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  try {
    const response = await fetch(`${apiBaseUrl()}/companies`, {
      method: "POST",
      cache: "no-store",
      headers: {
        Authorization: authorizationHeader(request),
        "Content-Type": "application/json"
      },
      body: JSON.stringify(body ?? {})
    });
    const data = await response.json().catch(() => ({ detail: "Unable to create company." }));
    return NextResponse.json(data, { status: response.ok ? 200 : response.status });
  } catch {
    return NextResponse.json({ detail: "ABHAY backend is temporarily unavailable." }, { status: 502 });
  }
}
