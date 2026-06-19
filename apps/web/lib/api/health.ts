export type AbhayHealthStatus = {
  backendOnline: boolean;
  aiReady: boolean;
  message: string;
  source: string;
  lastCheck: "checking" | "success" | "failed";
};

export async function checkAbhayHealth(): Promise<AbhayHealthStatus> {
  const source = "/api/abhay-health";
  try {
    const response = await fetch(source, { cache: "no-store" });
    if (!response.ok) {
      return {
        backendOnline: false,
        aiReady: false,
        message: "ABHAY Intelligence is syncing. Your dashboard remains available.",
        source,
        lastCheck: "failed"
      };
    }

    const data = await response.json().catch(() => null) as { backendOnline?: boolean; aiReady?: boolean } | null;
    if (process.env.NODE_ENV === "development") {
      console.log("ABHAY health response", data);
    }

    const online = data?.backendOnline === true && data?.aiReady === true;
    if (online) {
      return {
        backendOnline: true,
        aiReady: true,
        message: "ABHAY Intelligence ready",
        source,
        lastCheck: "success"
      };
    }

    return {
      backendOnline: false,
      aiReady: false,
      message: "ABHAY Intelligence is syncing. Your dashboard remains available.",
      source,
      lastCheck: "failed"
    };
  } catch {
    return {
      backendOnline: false,
      aiReady: false,
      message: "ABHAY Intelligence is syncing. Your dashboard remains available.",
      source,
      lastCheck: "failed"
    };
  }
}
