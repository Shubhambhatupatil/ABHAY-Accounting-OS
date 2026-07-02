package com.anvritai.abhay.repository.gst;

import com.anvritai.abhay.domain.gst.GstReturn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstReturnRepository extends JpaRepository<GstReturn, UUID> {
    @EntityGraph(attributePaths = {"items", "items.invoice"})
    List<GstReturn> findAllByCompanyIdOrderByPeriodEndDescCreatedAtDesc(UUID companyId);
    @EntityGraph(attributePaths = {"items", "items.invoice"})
    Optional<GstReturn> findByIdAndCompanyId(UUID id, UUID companyId);
}
