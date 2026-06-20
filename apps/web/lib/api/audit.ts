import type { SupabaseClient } from "@supabase/supabase-js";

type AuditPayload = Record<string, string | number | boolean | null | undefined>;

export async function writeClientAuditLog(
  supabase: SupabaseClient,
  actionType: string,
  entityType: string,
  eventPayload: AuditPayload = {}
) {
  try {
    const {
      data: { user }
    } = await supabase.auth.getUser();
    if (!user) return;

    const { error } = await supabase.from("audit_logs").insert({
      company_id: null,
      actor_id: user.id,
      action_type: actionType,
      entity_type: entityType,
      event_payload: eventPayload
    });

    if (error && process.env.NODE_ENV === "development") {
      console.warn("ABHAY audit log unavailable", error.message);
    }
  } catch (error) {
    if (process.env.NODE_ENV === "development") {
      console.warn("ABHAY audit log unavailable", error);
    }
  }
}
