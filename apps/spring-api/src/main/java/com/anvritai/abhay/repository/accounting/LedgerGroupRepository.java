package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.LedgerGroup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerGroupRepository extends JpaRepository<LedgerGroup, UUID> {
    List<LedgerGroup> findAllByCompanyIdOrderByName(UUID companyId);
    Optional<LedgerGroup> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
