package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.AutomationTemplate;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AutomationTemplateRepository extends JpaRepository<AutomationTemplate,UUID>{List<AutomationTemplate> findAllByActiveTrueOrderByName();}
