import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { publicEnv } from "@/lib/config";

type SiteVisitRow = {
  path: string;
  referrer: string | null;
  user_agent: string | null;
  ip_hash: string;
  created_at: string;
};

export async function GET() {
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
    data: { user }
  } = await supabase.auth.getUser();

  if (!user) {
    return NextResponse.json({ detail: "Please sign in to view analytics." }, { status: 401 });
  }

  const { data, error } = await supabase
    .from("site_visits")
    .select("path,referrer,user_agent,ip_hash,created_at")
    .order("created_at", { ascending: false })
    .limit(1000)
    .returns<SiteVisitRow[]>();

  if (error) {
    return NextResponse.json(emptySummary("Analytics is not available yet."));
  }

  return NextResponse.json(buildSummary(data ?? []));
}

function buildSummary(rows: SiteVisitRow[]) {
  const todayKey = new Date().toISOString().slice(0, 10);
  const uniqueVisitors = new Set(rows.map((row) => row.ip_hash)).size;
  const visitsToday = rows.filter((row) => row.created_at.slice(0, 10) === todayKey).length;
  const pageCounts = new Map<string, number>();
  rows.forEach((row) => pageCounts.set(row.path, (pageCounts.get(row.path) ?? 0) + 1));

  const topPages = Array.from(pageCounts.entries())
    .sort((first, second) => second[1] - first[1])
    .slice(0, 5)
    .map(([path, visits]) => ({ path, visits }));

  return {
    totalVisits: rows.length,
    visitsToday,
    uniqueVisitors,
    topPages,
    lastVisits: rows.slice(0, 20).map((row) => ({
      path: row.path,
      referrer: row.referrer,
      createdAt: row.created_at,
      ...parseDevice(row.user_agent)
    })),
    message: null
  };
}

function emptySummary(message: string) {
  return {
    totalVisits: 0,
    visitsToday: 0,
    uniqueVisitors: 0,
    topPages: [],
    lastVisits: [],
    message
  };
}

function parseDevice(userAgent: string | null) {
  const agent = userAgent ?? "";
  const device = /mobile|android|iphone/i.test(agent) ? "Mobile" : "Desktop";
  let browser = "Browser";
  if (/edg\//i.test(agent)) browser = "Edge";
  else if (/chrome|crios/i.test(agent)) browser = "Chrome";
  else if (/safari/i.test(agent)) browser = "Safari";
  else if (/firefox/i.test(agent)) browser = "Firefox";
  return { device, browser };
}
