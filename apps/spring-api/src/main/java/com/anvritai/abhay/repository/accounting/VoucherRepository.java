package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.Voucher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, UUID>, JpaSpecificationExecutor<Voucher> {

    @EntityGraph(attributePaths = {"voucherType", "financialYear", "lines", "lines.ledger"})
    Optional<Voucher> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"voucherType", "financialYear", "lines", "lines.ledger"})
    @Query("select voucher from Voucher voucher where voucher.id = :id and voucher.company.id = :companyId")
    Optional<Voucher> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @EntityGraph(attributePaths = {"voucherType", "financialYear", "lines", "lines.ledger"})
    List<Voucher> findAllByCompanyIdOrderByVoucherDateDescVoucherNumberDesc(UUID companyId);
    List<Voucher> findByCompanyIdAndVoucherNumberContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
}
