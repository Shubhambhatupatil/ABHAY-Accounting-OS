package com.anvritai.abhay.repository.sales;

import com.anvritai.abhay.domain.sales.InvoicePayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {
    List<InvoicePayment> findAllByCompanyIdOrderByPaymentDateDescCreatedAtDesc(UUID companyId);
    Optional<InvoicePayment> findByIdAndCompanyId(UUID id, UUID companyId);
    List<InvoicePayment> findAllByCompanyIdAndInvoiceIdOrderByPaymentDateAscCreatedAtAsc(
            UUID companyId, UUID invoiceId);
    @Query("select coalesce(sum(payment.amount), 0) from InvoicePayment payment "
            + "where payment.company.id = :companyId and payment.invoice.id = :invoiceId")
    BigDecimal totalPaid(@Param("companyId") UUID companyId, @Param("invoiceId") UUID invoiceId);
}
