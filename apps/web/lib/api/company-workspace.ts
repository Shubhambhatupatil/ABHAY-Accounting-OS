"use client";

import type { SupabaseClient } from "@supabase/supabase-js";

export type CompanyRole = "Owner" | "Admin" | "Accountant" | "Auditor" | "Viewer";

export type WorkspaceCompany = {
  id: string;
  company_name: string;
  gstin: string | null;
  industry: string | null;
  state: string | null;
  financial_year: string | null;
  created_at: string;
};

export type CompanyMember = {
  id: string;
  company_id: string;
  user_id: string;
  role: CompanyRole;
  created_at: string;
};

export type CompanyOnboardingPayload = {
  company_name: string;
  gstin?: string | null;
  industry?: string | null;
  state?: string | null;
  financial_year?: string | null;
};

type AuthCapableSupabase = SupabaseClient & {
  auth: SupabaseClient["auth"];
};

export async function listWorkspaceCompanies(supabase: AuthCapableSupabase) {
  const { data, error } = await supabase
    .from("companies")
    .select("id,company_name,gstin,industry,state,financial_year,created_at")
    .order("created_at", { ascending: false })
    .returns<WorkspaceCompany[]>();

  if (error) {
    throw new Error("Company workspace is syncing. Please refresh in a moment.");
  }

  return data;
}

export async function createWorkspaceCompany(supabase: AuthCapableSupabase, payload: CompanyOnboardingPayload) {
  const {
    data: { user },
    error: userError
  } = await supabase.auth.getUser();
  if (userError || !user) {
    throw new Error("Please login to create a company workspace.");
  }

  const companyId = crypto.randomUUID();
  const createdAt = new Date().toISOString();
  const company: WorkspaceCompany = {
    id: companyId,
    company_name: payload.company_name,
    gstin: payload.gstin ?? null,
    industry: payload.industry ?? null,
    state: payload.state ?? null,
    financial_year: payload.financial_year ?? null,
    created_at: createdAt
  };

  const { error: companyError } = await supabase
    .from("companies")
    .insert(company);

  if (companyError) {
    throw new Error("Company workspace could not be created.");
  }

  const { error: memberError } = await supabase.from("company_members").insert({
    company_id: companyId,
    user_id: user.id,
    role: "Owner"
  });

  if (memberError) {
    throw new Error("Company created, but owner membership could not be saved.");
  }

  return company;
}

export async function listCompanyMembers(supabase: AuthCapableSupabase, companyId: string) {
  const { data, error } = await supabase
    .from("company_members")
    .select("id,company_id,user_id,role,created_at")
    .eq("company_id", companyId)
    .order("created_at", { ascending: true })
    .returns<CompanyMember[]>();

  if (error) {
    throw new Error("Team members could not be loaded.");
  }

  return data;
}

export async function inviteCompanyMember(
  supabase: AuthCapableSupabase,
  companyId: string,
  userId: string,
  role: CompanyRole
) {
  const { data, error } = await supabase
    .from("company_members")
    .insert({ company_id: companyId, user_id: userId, role })
    .select("id,company_id,user_id,role,created_at")
    .single<CompanyMember>();

  if (error) {
    throw new Error("Invite could not be saved. Only Owner/Admin can invite members.");
  }

  return data;
}

export async function updateCompanyMemberRole(
  supabase: AuthCapableSupabase,
  memberId: string,
  role: CompanyRole
) {
  const { data, error } = await supabase
    .from("company_members")
    .update({ role })
    .eq("id", memberId)
    .select("id,company_id,user_id,role,created_at")
    .single<CompanyMember>();

  if (error) {
    throw new Error("Role could not be updated. Only Owner/Admin can manage roles.");
  }

  return data;
}
