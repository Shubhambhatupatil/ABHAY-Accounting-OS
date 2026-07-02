package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CompanyDtos {
    private CompanyDtos() {
    }

    public record CompanyCreateRequest(
            @NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$") String gstin,
            @Pattern(regexp = "^$|^[0-9]{2}$") String stateCode,
            @Size(max = 100) String industry,
            @NotNull LocalDate financialYearStart,
            @NotNull LocalDate financialYearEnd) {
    }

    public record CompanyUpdateRequest(
            @Size(min = 1, max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$") String gstin,
            @Pattern(regexp = "^$|^[0-9]{2}$") String stateCode,
            @Size(max = 100) String industry) {
    }

    public record MemberCreateRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull RoleCode role) {
    }

    public record CompanyResponse(
            UUID id,
            String legalName,
            String tradeName,
            String gstin,
            String stateCode,
            String industry,
            RoleCode myRole,
            UUID activeFinancialYearId,
            String activeFinancialYearLabel,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record MemberResponse(
            UUID id,
            UUID userId,
            String name,
            String email,
            RoleCode role,
            Instant createdAt) {
    }

    public record AuditLogResponse(
            UUID id,
            String action,
            String entityType,
            UUID entityId,
            UUID actorUserId,
            String details,
            Instant createdAt) {
    }

    public record SelectionResponse(UUID selectedCompanyId) {
    }

    public record FinancialYearLockResponse(
            UUID financialYearId,
            boolean locked,
            Instant lockedAt) {
    }
}
