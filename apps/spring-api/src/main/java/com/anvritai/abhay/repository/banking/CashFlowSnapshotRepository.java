package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.CashFlowSnapshot;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashFlowSnapshotRepository extends JpaRepository<CashFlowSnapshot, UUID> {
    Optional<CashFlowSnapshot> findByCompanyIdAndSnapshotDate(UUID companyId, LocalDate snapshotDate);
}
