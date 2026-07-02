package com.anvritai.abhay.repository.gst;

import com.anvritai.abhay.domain.gst.GstAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstAlertRepository extends JpaRepository<GstAlert, UUID> {
    @EntityGraph(attributePaths = "invoice")
    List<GstAlert> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Optional<GstAlert> findByIdAndCompanyId(UUID id, UUID companyId);
    long countByCompanyIdAndResolvedFalse(UUID companyId);
    void deleteAllByCompanyIdAndInvoiceIdAndResolvedFalse(UUID companyId, UUID invoiceId);
}
