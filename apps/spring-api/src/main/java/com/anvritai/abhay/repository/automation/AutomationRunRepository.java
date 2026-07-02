package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.AutomationRun;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AutomationRunRepository extends JpaRepository<AutomationRun,UUID>{List<AutomationRun> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);}
