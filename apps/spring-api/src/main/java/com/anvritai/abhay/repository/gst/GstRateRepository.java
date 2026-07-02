package com.anvritai.abhay.repository.gst;

import com.anvritai.abhay.domain.gst.GstRate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstRateRepository extends JpaRepository<GstRate, UUID> {
    List<GstRate> findAllByCompanyIdOrderByRateAscCessRateAsc(UUID companyId);
    Optional<GstRate> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndRateAndCessRate(UUID companyId, BigDecimal rate, BigDecimal cessRate);
}
