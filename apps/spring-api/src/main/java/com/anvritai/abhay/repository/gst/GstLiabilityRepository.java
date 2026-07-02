package com.anvritai.abhay.repository.gst;

import com.anvritai.abhay.domain.gst.GstLiability;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstLiabilityRepository extends JpaRepository<GstLiability, UUID> {
    Optional<GstLiability> findByCompanyIdAndPeriodStartAndPeriodEnd(
            UUID companyId, LocalDate periodStart, LocalDate periodEnd);
}
