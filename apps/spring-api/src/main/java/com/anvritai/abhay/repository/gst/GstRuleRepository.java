package com.anvritai.abhay.repository.gst;

import com.anvritai.abhay.domain.gst.GstRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstRuleRepository extends JpaRepository<GstRule, UUID> {
    List<GstRule> findAllByCompanyIdOrderByName(UUID companyId);
    Optional<GstRule> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
