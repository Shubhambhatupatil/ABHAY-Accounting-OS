import { NextResponse } from "next/server";

const fallbackApiUrl = "https://abhay-api-x027.onrender.com";

export async function POST(request: Request) {
  const body = await request.json().catch(() => null) as { command?: string; context?: Record<string, unknown> } | null;
  const command = body?.command?.trim();

  if (!command) {
    return NextResponse.json({ detail: "Command is required." }, { status: 400 });
  }

  const apiUrl = (process.env.ABHAY_API_URL || fallbackApiUrl).replace(/\/$/, "");

  try {
    const response = await fetch(`${apiUrl}/ai/command`, {
      method: "POST",
      cache: "no-store",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        command,
        context: body?.context ?? {}
      })
    });

    if (!response.ok) {
      return NextResponse.json(
        { detail: "ABHAY AI command engine is temporarily unavailable." },
        { status: 502 }
      );
    }

    return NextResponse.json(await response.json());
  } catch {
    return NextResponse.json(
      { detail: "ABHAY AI command engine is temporarily unavailable." },
      { status: 502 }
    );
  }
}
