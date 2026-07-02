package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.TreasuryAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreasuryAlertRepository extends JpaRepository<TreasuryAlert, UUID> {
    List<TreasuryAlert> findAllByCompanyIdAndResolvedFalseOrderByCreatedAtDesc(UUID companyId);
    Optional<TreasuryAlert> findByCompanyIdAndAlertTypeAndResolvedFalse(UUID companyId, String alertType);
}
