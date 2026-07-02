package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.BankTransaction;
import com.anvritai.abhay.domain.banking.ReconciliationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {
    @EntityGraph(attributePaths = {"bankAccount", "bankAccount.ledger"})
    List<BankTransaction> findAllByCompanyIdAndBankAccountIdOrderByTransactionDateAscCreatedAtAsc(
            UUID companyId, UUID bankAccountId);
    @EntityGraph(attributePaths = {"bankAccount", "bankAccount.ledger"})
    List<BankTransaction> findAllByCompanyIdOrderByTransactionDateDescCreatedAtDesc(UUID companyId);
    @EntityGraph(attributePaths = {"bankAccount", "bankAccount.ledger"})
    List<BankTransaction> findAllByCompanyIdAndReconciliationStatusOrderByTransactionDateDesc(
            UUID companyId, ReconciliationStatus status);
    boolean existsByCompanyIdAndBankAccountIdAndRawHash(UUID companyId, UUID bankAccountId, String rawHash);
    long countByCompanyIdAndReconciliationStatus(UUID companyId, ReconciliationStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"bankAccount", "bankAccount.ledger"})
    @Query("select transaction from BankTransaction transaction where transaction.id = :id and transaction.company.id = :companyId")
    Optional<BankTransaction> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
