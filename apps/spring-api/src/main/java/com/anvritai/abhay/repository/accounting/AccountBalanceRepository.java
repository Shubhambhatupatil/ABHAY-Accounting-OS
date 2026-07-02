package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.AccountBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance from AccountBalance balance
            where balance.company.id = :companyId
              and balance.financialYear.id = :financialYearId
              and balance.ledger.id = :ledgerId
            """)
    Optional<AccountBalance> lockByScope(
            @Param("companyId") UUID companyId,
            @Param("financialYearId") UUID financialYearId,
            @Param("ledgerId") UUID ledgerId);

    @EntityGraph(attributePaths = {"ledger", "ledger.ledgerGroup"})
    List<AccountBalance> findAllByCompanyIdOrderByLedgerName(UUID companyId);

    @EntityGraph(attributePaths = {"ledger", "ledger.ledgerGroup"})
    List<AccountBalance> findAllByCompanyIdAndFinancialYearIdOrderByLedgerName(
            UUID companyId, UUID financialYearId);
}
