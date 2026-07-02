package com.anvritai.abhay.repository;

import com.anvritai.abhay.domain.CompanyMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, UUID> {
    Optional<CompanyMember> findByCompanyIdAndUserId(UUID companyId, UUID userId);
    List<CompanyMember> findAllByCompanyIdOrderByCreatedAt(UUID companyId);
    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);
}
