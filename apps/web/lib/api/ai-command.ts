export type AiCommandResponse = {
  ok: boolean;
  summary: string;
  actions: string[];
  confidence: number;
  calculation?: string | null;
  base_amount?: number | null;
  gst_rate?: number | null;
  gst_amount?: number | null;
  total?: number | null;
};

export async function runAiCommand(command: string, context: Record<string, unknown> = {}) {
  const response = await fetch("/api/ai-command", {
    method: "POST",
    cache: "no-store",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ command, context })
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "AI command failed." })) as { detail?: string };
    throw new Error(error.detail ?? "AI command failed.");
  }

  return response.json() as Promise<AiCommandResponse>;
}
