import { NextResponse } from "next/server";
import { safeApiErrorMessage } from "@/lib/api/safe-error";

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
    if (!response.ok) {
      return NextResponse.json({ detail: safeApiErrorMessage(data.detail, "Unable to load companies.") }, { status: response.status });
    }
    return NextResponse.json(data);
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
    if (!response.ok) {
      return NextResponse.json({ detail: safeApiErrorMessage(data.detail, "Unable to create company.") }, { status: response.status });
    }
    return NextResponse.json(data);
  } catch {
    return NextResponse.json({ detail: "ABHAY backend is temporarily unavailable." }, { status: 502 });
  }
}
