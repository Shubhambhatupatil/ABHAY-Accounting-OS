package com.anvritai.abhay.repository;

import com.anvritai.abhay.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);
}
