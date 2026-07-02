package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.MemorySuggestion;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemorySuggestionRepository extends JpaRepository<MemorySuggestion,UUID>{
 Optional<MemorySuggestion> findByIdAndCompanyId(UUID id,UUID companyId);
 long countByCompanyId(UUID companyId); void deleteAllByCompanyId(UUID companyId);
}
