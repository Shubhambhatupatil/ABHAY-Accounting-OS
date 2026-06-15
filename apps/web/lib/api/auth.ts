import { publicEnv } from "@/lib/config";

export type SessionUser = {
  id: string;
  email: string | null;
  auth_role: string;
};

export async function verifyApiSession(accessToken: string): Promise<SessionUser> {
  let response: Response;
  try {
    response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}/auth/session/verify`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    });
  } catch {
    throw new Error(`API not reachable. Check NEXT_PUBLIC_API_URL. Current API: ${publicEnv.NEXT_PUBLIC_API_URL}`);
  }

  if (!response.ok) {
    throw new Error("The API could not verify this session.");
  }

  return response.json() as Promise<SessionUser>;
}
