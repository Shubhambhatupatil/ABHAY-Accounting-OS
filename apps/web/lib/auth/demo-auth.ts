"use client";

import { publicEnv } from "@/lib/config";

export const LOCAL_DEMO_TOKEN = "abhay-local-demo-token";
export const LOCAL_DEMO_STORAGE_KEY = "abhay.localDemoToken";

export function isPlaceholderSupabaseConfig() {
  const url = publicEnv.NEXT_PUBLIC_SUPABASE_URL;
  const key = publicEnv.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  return (
    url.includes("placeholder") ||
    url.includes("example.supabase.co") ||
    url.includes("placeholder.supabase.co") ||
    url.includes("your-project.supabase.co") ||
    key.includes("placeholder") ||
    key.includes("placeholder_key") ||
    key.includes("replace-with") ||
    key.includes("local-build") ||
    key === "placeholder"
  );
}

export function isLocalDevelopmentApi() {
  const apiUrl = publicEnv.NEXT_PUBLIC_API_URL;
  return apiUrl.includes("127.0.0.1") || apiUrl.includes("localhost");
}

export function isHostedAlphaDemoUrl() {
  if (typeof window === "undefined") return false;
  const hostname = window.location.hostname.toLowerCase();
  return (
    hostname.endsWith(".vercel.app") ||
    hostname.includes("abhay") ||
    hostname.includes("anvritai")
  );
}

export function isAlphaDemoFallbackAllowed() {
  return isPlaceholderSupabaseConfig() || isLocalDevelopmentApi() || isHostedAlphaDemoUrl();
}

export function startLocalDemoSession() {
  window.localStorage.setItem(LOCAL_DEMO_STORAGE_KEY, LOCAL_DEMO_TOKEN);
  return LOCAL_DEMO_TOKEN;
}

export function ensureLocalDemoSession() {
  if (!isAlphaDemoFallbackAllowed()) return null;
  return startLocalDemoSession();
}

export function getLocalDemoToken() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(LOCAL_DEMO_STORAGE_KEY);
}

export function getAlphaDemoTokenForAuthFailure(currentToken?: string | null) {
  if (currentToken === LOCAL_DEMO_TOKEN || !isAlphaDemoFallbackAllowed()) return null;
  return startLocalDemoSession();
}

async function apiAcceptsToken(token: string) {
  const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}/auth/session/verify`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    }
  });
  return response.ok;
}

type SessionCapableSupabase = {
  auth: {
    getSession: () => Promise<{ data: { session: { access_token: string } | null } }>;
  };
};

export async function getAccessToken(supabase: SessionCapableSupabase) {
  try {
    const { data } = await supabase.auth.getSession();
    const token = data.session?.access_token ?? getLocalDemoToken() ?? ensureLocalDemoSession();
    if (!token || token === LOCAL_DEMO_TOKEN) return token;
    try {
      if (await apiAcceptsToken(token)) return token;
    } catch {
      return getAlphaDemoTokenForAuthFailure(token) ?? token;
    }
    return getAlphaDemoTokenForAuthFailure(token) ?? token;
  } catch {
    return getLocalDemoToken() ?? ensureLocalDemoSession();
  }
}
