import { z } from "zod";

const publicEnvSchema = z.object({
  NEXT_PUBLIC_SUPABASE_URL: z.string().url().catch("https://placeholder.supabase.co"),
  NEXT_PUBLIC_SUPABASE_ANON_KEY: z.string().min(1).catch("placeholder_key"),
  NEXT_PUBLIC_API_URL: z.string().url().catch("http://127.0.0.1:8000"),
  NEXT_PUBLIC_ALPHA_DEMO_MODE: z.string().catch("false")
});

export const publicEnv = publicEnvSchema.parse({
  NEXT_PUBLIC_SUPABASE_URL: process.env.NEXT_PUBLIC_SUPABASE_URL,
  NEXT_PUBLIC_SUPABASE_ANON_KEY: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
  NEXT_PUBLIC_ALPHA_DEMO_MODE: process.env.NEXT_PUBLIC_ALPHA_DEMO_MODE
});
