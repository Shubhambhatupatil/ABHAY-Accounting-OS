package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.AutomationRule;
import java.time.Instant; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AutomationRuleRepository extends JpaRepository<AutomationRule,UUID>{
 List<AutomationRule> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
 Optional<AutomationRule> findByIdAndCompanyId(UUID id,UUID companyId);
 List<AutomationRule> findAllByActiveTrueAndNextRunAtLessThanEqual(Instant due);
}
