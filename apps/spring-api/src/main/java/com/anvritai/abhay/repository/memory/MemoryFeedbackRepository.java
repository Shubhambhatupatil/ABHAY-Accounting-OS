package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.MemoryFeedback;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryFeedbackRepository extends JpaRepository<MemoryFeedback,UUID>{
 boolean existsBySuggestionId(UUID suggestionId); long countByCompanyId(UUID companyId); void deleteAllByCompanyId(UUID companyId);
}
