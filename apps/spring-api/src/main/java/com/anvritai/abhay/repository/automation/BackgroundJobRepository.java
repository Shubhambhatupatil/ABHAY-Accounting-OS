package com.anvritai.abhay.repository.automation;
import com.anvritai.abhay.domain.automation.BackgroundJob;
import java.time.Instant; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob,UUID>{
 List<BackgroundJob> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
 Optional<BackgroundJob> findByIdAndCompanyId(UUID id,UUID companyId);
 List<BackgroundJob> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAt(String status,Instant due);
}
