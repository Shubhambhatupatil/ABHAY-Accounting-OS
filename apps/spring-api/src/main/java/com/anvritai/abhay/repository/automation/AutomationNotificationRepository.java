package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.AutomationNotification;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AutomationNotificationRepository extends JpaRepository<AutomationNotification,UUID>{
 List<AutomationNotification> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
 Optional<AutomationNotification> findByIdAndCompanyId(UUID id,UUID companyId);
 long countByCompanyIdAndReadAtIsNull(UUID companyId);
}
