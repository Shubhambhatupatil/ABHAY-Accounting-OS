package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.CompanyMember;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.repository.CompanyMemberRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CompanyAccessService {

    private final CompanyMemberRepository members;

    public CompanyAccessService(CompanyMemberRepository members) {
        this.members = members;
    }

    public CompanyMember requireMembership(UUID companyId, UUID userId) {
        return members.findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> new CompanyAccessException("You do not have access to this company."));
    }

    public CompanyMember requireRole(UUID companyId, UUID userId, RoleCode... allowed) {
        CompanyMember member = requireMembership(companyId, userId);
        RoleCode actual = RoleCode.valueOf(member.getRole().getCode());
        if (!Set.of(allowed).contains(actual)) {
            throw new CompanyAccessException("Your company role does not permit this action.");
        }
        return member;
    }
}
