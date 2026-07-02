package com.anvritai.abhay.repository;

import com.anvritai.abhay.domain.FinancialYear;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialYearRepository extends JpaRepository<FinancialYear, UUID> {
    List<FinancialYear> findAllByCompanyIdOrderByStartsOnDesc(UUID companyId);
    Optional<FinancialYear> findFirstByCompanyIdAndActiveTrueOrderByStartsOnDesc(UUID companyId);
    Optional<FinancialYear> findByIdAndCompanyId(UUID id, UUID companyId);
}
