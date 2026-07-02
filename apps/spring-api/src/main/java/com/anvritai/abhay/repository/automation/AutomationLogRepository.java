package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.AutomationLog;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface AutomationLogRepository extends JpaRepository<AutomationLog,UUID>{}
