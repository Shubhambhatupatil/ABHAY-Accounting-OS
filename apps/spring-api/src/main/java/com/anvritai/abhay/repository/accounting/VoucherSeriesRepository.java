package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.VoucherSeries;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherSeriesRepository extends JpaRepository<VoucherSeries, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select series from VoucherSeries series
            where series.company.id = :companyId
              and series.financialYear.id = :financialYearId
              and series.voucherType.id = :voucherTypeId
            """)
    Optional<VoucherSeries> lockByScope(
            @Param("companyId") UUID companyId,
            @Param("financialYearId") UUID financialYearId,
            @Param("voucherTypeId") UUID voucherTypeId);
}
