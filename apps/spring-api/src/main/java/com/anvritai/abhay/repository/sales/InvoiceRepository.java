package com.anvritai.abhay.repository.sales;

import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    @EntityGraph(attributePaths = {"customer", "vendor", "items", "items.item", "financialYear"})
    List<Invoice> findAllByCompanyIdOrderByInvoiceDateDescInvoiceNumberDesc(UUID companyId);
    List<Invoice> findByCompanyIdAndInvoiceNumberContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths = {"customer", "vendor", "items", "items.item", "financialYear"})
    List<Invoice> findAllByCompanyIdAndInvoiceTypeOrderByInvoiceDateDescInvoiceNumberDesc(
            UUID companyId, InvoiceType invoiceType);
    @EntityGraph(attributePaths = {"customer", "vendor", "items", "items.item", "financialYear"})
    Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "customer.ledger", "vendor", "vendor.ledger", "items", "items.item"})
    @Query("select invoice from Invoice invoice where invoice.id = :id and invoice.company.id = :companyId")
    Optional<Invoice> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);
    boolean existsByCompanyIdAndFinancialYearIdAndInvoiceTypeAndPartyKeyAndInvoiceNumberIgnoreCase(
            UUID companyId, UUID financialYearId, InvoiceType invoiceType, UUID partyKey, String invoiceNumber);
    List<Invoice> findAllByCompanyIdAndStatusInOrderByDueDate(UUID companyId, List<InvoiceStatus> statuses);
    @EntityGraph(attributePaths = {"customer", "vendor", "items", "items.item", "financialYear"})
    List<Invoice> findAllByCompanyIdAndStatusInAndInvoiceDateBetweenOrderByInvoiceDateDescInvoiceNumberDesc(
            UUID companyId, List<InvoiceStatus> statuses, LocalDate dateFrom, LocalDate dateTo);
    long countByCompanyIdAndFinancialYearIdAndInvoiceTypeAndPartyKeyAndInvoiceNumberIgnoreCase(
            UUID companyId, UUID financialYearId, InvoiceType invoiceType, UUID partyKey, String invoiceNumber);
}
