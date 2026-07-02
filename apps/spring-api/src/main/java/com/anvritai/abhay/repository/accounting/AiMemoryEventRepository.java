package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.AiMemoryEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMemoryEventRepository extends JpaRepository<AiMemoryEvent, UUID> {
    long countByCompanyIdAndEntityId(UUID companyId, UUID entityId);
}
