"use client";

import { publicEnv } from "@/lib/config";

export const LOCAL_DEMO_TOKEN = "abhay-local-demo-token";
export const LOCAL_DEMO_STORAGE_KEY = "abhay_demo_token";
export const ALPHA_DEMO_MODE_STORAGE_KEY = "abhay_alpha_demo";
export const ALPHA_DEMO_MODE_COOKIE = "abhay_alpha_demo";
export const CLIENT_DEMO_MODE_STORAGE_KEY = "abhay_client_demo";
export const CLIENT_DEMO_MODE_COOKIE = "abhay_client_demo";

const LEGACY_LOCAL_DEMO_STORAGE_KEY = "abhay.localDemoToken";
const LEGACY_ALPHA_DEMO_MODE_STORAGE_KEY = "abhay.alphaDemoMode";
const ACCESS_TOKEN_TIMEOUT_MS = 2500;

export function isAlphaDemoModeEnabled() {
  return publicEnv.NEXT_PUBLIC_ALPHA_DEMO_MODE.toLowerCase() === "true";
}

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
  return isPlaceholderSupabaseConfig() || isLocalDevelopmentApi() || isAlphaDemoModeEnabled() || isHostedAlphaDemoUrl();
}

export function startLocalDemoSession() {
  window.localStorage.setItem(ALPHA_DEMO_MODE_STORAGE_KEY, "true");
  window.localStorage.setItem(LOCAL_DEMO_STORAGE_KEY, LOCAL_DEMO_TOKEN);
  document.cookie = `${ALPHA_DEMO_MODE_COOKIE}=true; path=/; max-age=1209600; samesite=lax`;
  window.localStorage.removeItem(LEGACY_ALPHA_DEMO_MODE_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_LOCAL_DEMO_STORAGE_KEY);
  return LOCAL_DEMO_TOKEN;
}

export function startClientDemoSession() {
  window.localStorage.setItem(CLIENT_DEMO_MODE_STORAGE_KEY, "true");
  window.localStorage.setItem(LOCAL_DEMO_STORAGE_KEY, LOCAL_DEMO_TOKEN);
  document.cookie = `${CLIENT_DEMO_MODE_COOKIE}=true; path=/; max-age=1209600; samesite=lax`;
  document.cookie = `${ALPHA_DEMO_MODE_COOKIE}=true; path=/; max-age=1209600; samesite=lax`;
  window.localStorage.removeItem(LEGACY_ALPHA_DEMO_MODE_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_LOCAL_DEMO_STORAGE_KEY);
  return LOCAL_DEMO_TOKEN;
}

export function ensureLocalDemoSession() {
  if (!isAlphaDemoFallbackAllowed()) return null;
  return startLocalDemoSession();
}

export function getLocalDemoToken() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(LOCAL_DEMO_STORAGE_KEY) ?? window.localStorage.getItem(LEGACY_LOCAL_DEMO_STORAGE_KEY);
}

export function hasAlphaDemoModeFlag() {
  if (typeof window === "undefined") return false;
  return (
    window.localStorage.getItem(ALPHA_DEMO_MODE_STORAGE_KEY) === "true" ||
    window.localStorage.getItem(CLIENT_DEMO_MODE_STORAGE_KEY) === "true" ||
    window.localStorage.getItem(LEGACY_ALPHA_DEMO_MODE_STORAGE_KEY) === "true"
  );
}

export function hasClientDemoModeFlag() {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(CLIENT_DEMO_MODE_STORAGE_KEY) === "true";
}

export function clearLocalDemoSession() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(ALPHA_DEMO_MODE_STORAGE_KEY);
  window.localStorage.removeItem(CLIENT_DEMO_MODE_STORAGE_KEY);
  window.localStorage.removeItem(LOCAL_DEMO_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_ALPHA_DEMO_MODE_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_LOCAL_DEMO_STORAGE_KEY);
  document.cookie = `${ALPHA_DEMO_MODE_COOKIE}=; path=/; max-age=0; samesite=lax`;
  document.cookie = `${CLIENT_DEMO_MODE_COOKIE}=; path=/; max-age=0; samesite=lax`;
}

export function tokenSourceFor(token: string | null | undefined): "supabase" | "demo" | "missing" {
  if (!token) return "missing";
  return token === LOCAL_DEMO_TOKEN ? "demo" : "supabase";
}

type SessionCapableSupabase = {
  auth: {
    getSession: () => Promise<{ data: { session: { access_token: string } | null } }>;
  };
};

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => {
      window.setTimeout(() => reject(new Error("Session check timed out.")), timeoutMs);
    })
  ]);
}

export async function getAccessToken(supabase: SessionCapableSupabase) {
  try {
    if (hasClientDemoModeFlag()) {
      return startClientDemoSession();
    }
    if (getLocalDemoToken() === LOCAL_DEMO_TOKEN) {
      return hasAlphaDemoModeFlag() ? startLocalDemoSession() : startClientDemoSession();
    }
    if (hasAlphaDemoModeFlag()) {
      return startLocalDemoSession();
    }
    const { data } = await withTimeout(supabase.auth.getSession(), ACCESS_TOKEN_TIMEOUT_MS);
    return data.session?.access_token ?? null;
  } catch {
    return hasAlphaDemoModeFlag() ? startLocalDemoSession() : null;
  }
}
