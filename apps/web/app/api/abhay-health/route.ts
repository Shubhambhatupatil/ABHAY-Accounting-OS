import { NextResponse } from "next/server";

const abhayApiHealthUrl = "https://abhay-api-x027.onrender.com/health";

export async function GET() {
  try {
    const response = await fetch(abhayApiHealthUrl, { cache: "no-store" });
    const data = await response.json().catch(() => null) as { status?: string; service?: string } | null;
    const online = response.ok && (
      data?.status?.toLowerCase() === "ok" ||
      data?.service?.toLowerCase() === "abhay-api"
    );

    return NextResponse.json({
      backendOnline: online,
      aiReady: online
    });
  } catch {
    return NextResponse.json({
      backendOnline: false,
      aiReady: false
    });
  }
}
