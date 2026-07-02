package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.BankReconciliationSuggestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankReconciliationSuggestionRepository extends JpaRepository<BankReconciliationSuggestion, UUID> {
    @EntityGraph(attributePaths = {"bankTransaction", "bankTransaction.bankAccount", "voucher", "invoicePayment"})
    List<BankReconciliationSuggestion> findAllByCompanyIdAndActiveTrueOrderByConfidenceDescCreatedAtDesc(UUID companyId);
    void deleteAllByCompanyIdAndBankTransactionId(UUID companyId, UUID transactionId);
}
