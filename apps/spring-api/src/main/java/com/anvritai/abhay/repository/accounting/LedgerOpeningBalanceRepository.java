package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.LedgerOpeningBalance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerOpeningBalanceRepository extends JpaRepository<LedgerOpeningBalance, UUID> {
    Optional<LedgerOpeningBalance> findByCompanyIdAndFinancialYearIdAndLedgerId(
            UUID companyId, UUID financialYearId, UUID ledgerId);

    @EntityGraph(attributePaths = {"ledger", "ledger.ledgerGroup"})
    List<LedgerOpeningBalance> findAllByCompanyIdAndFinancialYearId(UUID companyId, UUID financialYearId);
}
