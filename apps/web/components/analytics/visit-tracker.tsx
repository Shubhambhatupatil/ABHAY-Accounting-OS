"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

export function VisitTracker() {
  const pathname = usePathname();
  const trackedPaths = useRef(new Set<string>());

  useEffect(() => {
    if (!pathname || trackedPaths.current.has(pathname)) return;
    trackedPaths.current.add(pathname);

    void fetch("/api/analytics/visit", {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path: pathname })
    }).catch(() => undefined);
  }, [pathname]);

  return null;
}
