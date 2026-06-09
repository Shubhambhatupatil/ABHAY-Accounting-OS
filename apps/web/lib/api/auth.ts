import { publicEnv } from "@/lib/config";

export type SessionUser = {
  id: string;
  email: string | null;
  auth_role: string;
};

export async function verifyApiSession(accessToken: string): Promise<SessionUser> {
  const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_URL}/auth/session/verify`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  if (!response.ok) {
    throw new Error("The API could not verify this session.");
  }

  return response.json() as Promise<SessionUser>;
}

