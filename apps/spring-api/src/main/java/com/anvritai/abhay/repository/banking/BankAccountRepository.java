package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.BankAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    @EntityGraph(attributePaths = {"ledger", "ledger.ledgerGroup"})
    List<BankAccount> findAllByCompanyIdOrderByBankNameAscAccountNameAsc(UUID companyId);
    List<BankAccount> findByCompanyIdAndBankNameContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths = {"ledger", "ledger.ledgerGroup"})
    Optional<BankAccount> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndLedgerId(UUID companyId, UUID ledgerId);
    boolean existsByCompanyIdAndLedgerIdAndIdNot(UUID companyId, UUID ledgerId, UUID id);
}
