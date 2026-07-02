package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.Ledger;
import com.anvritai.abhay.domain.accounting.LedgerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<Ledger, UUID> {
    @EntityGraph(attributePaths = {"ledgerGroup"})
    List<Ledger> findAllByCompanyIdOrderByName(UUID companyId);
    List<Ledger> findByCompanyIdAndNameContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);

    @EntityGraph(attributePaths = {"ledgerGroup"})
    Optional<Ledger> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @EntityGraph(attributePaths = {"ledgerGroup"})
    List<Ledger> findAllByCompanyIdAndLedgerTypeInAndActiveTrueOrderByName(
            UUID companyId, List<LedgerType> ledgerTypes);
}
