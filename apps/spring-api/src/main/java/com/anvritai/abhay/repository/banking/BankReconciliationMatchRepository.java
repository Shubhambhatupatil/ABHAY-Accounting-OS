package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.BankReconciliationMatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankReconciliationMatchRepository extends JpaRepository<BankReconciliationMatch, UUID> {
    @EntityGraph(attributePaths = {"bankTransaction", "bankTransaction.bankAccount", "voucher", "invoicePayment"})
    Optional<BankReconciliationMatch> findByCompanyIdAndBankTransactionId(UUID companyId, UUID transactionId);
    boolean existsByCompanyIdAndVoucherIdAndActiveTrue(UUID companyId, UUID voucherId);
    boolean existsByCompanyIdAndInvoicePaymentIdAndActiveTrue(UUID companyId, UUID invoicePaymentId);
    @EntityGraph(attributePaths = {"bankTransaction", "bankTransaction.bankAccount", "voucher", "invoicePayment"})
    List<BankReconciliationMatch> findAllByCompanyIdOrderByConfirmedAtDesc(UUID companyId);
}
