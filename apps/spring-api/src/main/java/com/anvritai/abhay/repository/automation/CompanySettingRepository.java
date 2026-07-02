package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.CompanySetting;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanySettingRepository extends JpaRepository<CompanySetting,UUID>{
 List<CompanySetting> findAllByCompanyIdOrderByCategoryAscSettingKeyAsc(UUID companyId);
 List<CompanySetting> findAllByCompanyIdAndCategoryOrderBySettingKey(UUID companyId,String category);
 Optional<CompanySetting> findByCompanyIdAndCategoryAndSettingKey(UUID companyId,String category,String settingKey);
}
