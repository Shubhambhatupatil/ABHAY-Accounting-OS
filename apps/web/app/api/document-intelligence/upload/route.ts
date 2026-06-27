import { NextRequest, NextResponse } from "next/server";
import { safeApiErrorMessage } from "@/lib/api/safe-error";

const fallbackApiUrl = "https://abhay-api-x027.onrender.com";
const maxUploadBytes = 10 * 1024 * 1024;

function apiBaseUrl() {
  return (process.env.ABHAY_API_URL || process.env.NEXT_PUBLIC_API_URL || fallbackApiUrl).replace(/\/$/, "");
}

export async function POST(request: NextRequest) {
  const companyId = request.nextUrl.searchParams.get("company_id") ?? request.nextUrl.searchParams.get("companyId");
  if (!companyId) {
    return NextResponse.json({ detail: "Please select a company first." }, { status: 400 });
  }

  const authorization = request.headers.get("authorization");
  if (!authorization) {
    return NextResponse.json({ detail: "Please login again." }, { status: 401 });
  }

  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (contentLength > maxUploadBytes) {
    return NextResponse.json({ detail: "File too large for Alpha. Upload a document up to 10MB." }, { status: 413 });
  }

  const fileBytes = await request.arrayBuffer();
  if (fileBytes.byteLength > maxUploadBytes) {
    return NextResponse.json({ detail: "File too large for Alpha. Upload a document up to 10MB." }, { status: 413 });
  }
  const contentType = request.headers.get("content-type") || "application/octet-stream";
  const fileName = request.headers.get("x-file-name") || "uploaded-document";

  try {
    const response = await fetch(`${apiBaseUrl()}/companies/${companyId}/document-intelligence/upload`, {
      method: "POST",
      cache: "no-store",
      headers: {
        Authorization: authorization,
        "Content-Type": contentType,
        "X-File-Name": fileName
      },
      body: fileBytes
    });
    const data = await response.json().catch(() => ({ detail: "Document analysis failed." }));
    if (!response.ok) {
      return NextResponse.json(
        { detail: safeApiErrorMessage(data.detail, "Document analysis failed. Please try again.") },
        { status: response.status }
      );
    }
    return NextResponse.json(data);
  } catch {
    return NextResponse.json(
      { detail: "ABHAY Document Intelligence is temporarily unavailable." },
      { status: 502 }
    );
  }
}
