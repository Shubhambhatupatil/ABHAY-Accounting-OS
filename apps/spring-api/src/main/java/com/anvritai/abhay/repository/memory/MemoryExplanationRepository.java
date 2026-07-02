package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.MemoryExplanation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryExplanationRepository extends JpaRepository<MemoryExplanation,UUID>{
 Optional<MemoryExplanation> findBySuggestionId(UUID suggestionId); void deleteAllByCompanyId(UUID companyId);
}
