package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryPatternRepository extends JpaRepository<MemoryPattern,UUID>{
 List<MemoryPattern> findAllByCompanyIdOrderByConfidenceScoreDesc(UUID companyId);
 Optional<MemoryPattern> findByCompanyIdAndPatternKey(UUID companyId,String patternKey);
 List<MemoryPattern> findAllByCompanyIdAndSuggestionTypeAndSubjectKeyIgnoreCaseOrderByConfidenceScoreDesc(
   UUID companyId,String suggestionType,String subjectKey);
 long countByCompanyId(UUID companyId);
 void deleteAllByCompanyId(UUID companyId);
}
